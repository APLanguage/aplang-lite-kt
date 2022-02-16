package com.github.aplanguage.aplanglite.compiler.naming

import arrow.core.Either
import arrow.core.handleError
import arrow.core.left
import arrow.core.right
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ExpressionToBytecodeVisitor
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.RegisterAllocator
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ResultTarget
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.compiler.typechecking.StatementTypeChecker
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeChecker
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.Expression
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject

class TypeResolveException(message: String, val area: Area? = null) : Exception(message)

open class Namespace(
  val pack: String?,
  val uses: List<Use>,
  val fields: MutableList<Field>,
  val methods: MutableList<Method>,
  val classes: MutableList<Class>
) {
  data class Use(var path: Either<GriddedObject<String>, Class>, val star: Boolean, val alias: GriddedObject<String>? = null) {
    fun targetClasses() =
      (path.orNull() ?: throw IllegalStateException("The use path ${(path as Either.Left).value}${if (star) ".*" else ""} was not resolved!")).let {
        if (star) it.classes
        else listOf(it)
      }

    fun findClass(path: String) = findClass(path.split("."))
    fun findClass(path: List<String>) = if (path.size == 1 && path.first() == alias?.obj) targetClasses()
    else path.dropLast(1).fold(listOf(*targetClasses().toTypedArray())) { acc, name ->
      acc.filter { it.name == name }.flatMap { it.classes }
    }.filter { it.name == path.last() }
  }

  interface Typeable {
    fun type(): Class?
  }

  interface VirtualTypeable {
    fun virtualType(): Class?
  }

  interface Settable : Typeable {
    fun isStatic(): Boolean
  }

  data class Method(
    val name: String,
    var returnType: Either<GriddedObject<String>, Class>?,
    var exprs: Either<List<GriddedObject<Statement>>, List<Instruction>>
  ) : Typeable, VirtualTypeable {
    var resolvedRegisters: List<RegisterAllocator.Type> = listOf()
    val parameters = mutableListOf<MethodParameter>()
    lateinit var parent: Namespace

    fun asDescriptorString(): String {
      return parent.path().let { if (it.isEmpty()) "" else "$it#" } + name + "(${
        parameters.joinToString(", ") { (_, type) ->
          type.fold({ it.obj }, { it.path() })
        }
      })" + (returnType?.fold({ ": $it" }, { ": ${it.path()}" }) ?: "")
    }

    override fun type(): Class? = returnType?.let {
      it.orNull() ?: throw TypeResolveException(
        "The return type ${(it as Either.Left).value} of ${asDescriptorString()} was not resolved!",
        it.value.area()
      )
    }

    fun typeCheck(namespaces: Set<Namespace>) {
      val checker = StatementTypeChecker(TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()).apply {
        this@Method.parameters.forEach { parameter ->
          parameter.clazz.fold({ throw IllegalStateException("Parameters not resolved!") }, { register(parameter.name, it) })
        }
      })))
      exprs.mapLeft { it.forEach { it.obj.visit(checker, Unit) } }
    }

    fun addParameter(name: String, clazz: Either<GriddedObject<String>, Class>) {
      parameters.add(MethodParameter(name, clazz))
    }

    override fun virtualType() = parent as? Class
    fun compile(pool: Pool) {
      val frame =
        com.github.aplanguage.aplanglite.compiler.compilation.apvm.Frame(
          pool,
          parameters.map { it.localVariable ?: LocalVariable(it.name, it.type()) })
      exprs = exprs.handleError {
        frame.enterScope()
        val ins = it.flatMap { it.obj.visit(ExpressionToBytecodeVisitor(frame), null).instructions() }
        frame.leaveScope()
        ins
      }
      resolvedRegisters = frame.registerAllocator.registers.map { it.type }
    }

    inner class MethodParameter(val name: String, var clazz: Either<GriddedObject<String>, Class>) : Typeable {
      var localVariable: LocalVariable? = null
      operator fun component1() = name
      operator fun component2() = clazz
      override fun type(): Class = clazz.orNull() ?: throw IllegalStateException("The method parameter $name was not resolved!")
    }
  }

  data class Field(
    val name: String,
    var type: Either<GriddedObject<String>, Class>?,
    var expr: Either<GriddedObject<DataExpression>, List<Instruction>>?
  ) : Typeable, VirtualTypeable, Settable {
    lateinit var parent: Namespace

    fun asDescriptorString() = "${parent.path().let { if (it.isEmpty()) "" else "$it%" }}$name:${type?.orNull()?.path() ?: "?"}"

    override fun type(): Class = type?.orNull() ?: throw TypeResolveException("The type of ${asDescriptorString()} was not resolved!")
    fun typeCheck(namespaces: Set<Namespace>) {
      expr?.mapLeft { TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()))) }
    }

    override fun virtualType() = parent as? Class
    override fun isStatic() = parent !is Class
    fun compile(pool: Pool) {
      expr = expr?.handleError {
        it.obj.visit(ExpressionToBytecodeVisitor(com.github.aplanguage.aplanglite.compiler.compilation.apvm.Frame(pool)), ResultTarget.Stack)
          .instructions()
      }
    }
  }

  class Class(
    val name: String,
    uses: List<Use>,
    fields: MutableList<Field>,
    methods: MutableList<Method>,
    classes: MutableList<Class>,
    val supers: MutableList<Either<GriddedObject<String>, Class>>
  ) : Namespace(null, uses, fields, methods, classes) {
    lateinit var parent: Namespace
    val constructor = Method("<init>", this.right(), listOf<GriddedObject<Statement>>().left()).apply { parent = this@Class }

    fun allSuperClasses(): List<Class> = supers.map {
      it.fold({ throw TypeResolveException("The type of ${it.obj} was not resolved.", it.area()) }, { it })
    } + supers.flatMap {
      it.fold({ throw TypeResolveException("The type of ${it.obj} was not resolved.", it.area()) }, { it.allSuperClasses() })
    }

    override fun resolveFieldsInScope(name: String): List<Field> = findFields(name) + root().resolveFieldsInScope(name)

    override fun resolveMethodsInScope(name: String): List<Method> =
      findMethods(name) + supers.flatMap { it.orNull()!!.resolveMethodsInScope(name) } + root().resolveMethodsInScope(name)

    override fun resolve(namespaces: Set<Namespace>) {
      super.resolve(namespaces)
      supers.replaceAll { toResolve ->
        toResolve.handleError { path ->
          val resolved = (parent.uses.flatMap { it.findClass(path.obj) } + parent.resolveOuterClassPath(path.obj, this)).filter { clazz ->
            this != clazz || this !in clazz.classesPath()
          }
          if (resolved.isEmpty()) {
            throw Exception("Could not resolve class path $path for super at ${path.area()}")
          } else resolved.first()
        }
      }
    }

    override fun root(): Namespace = parent.root()

    override fun path(): String = parent.path().let { if (it.isEmpty()) name else "$it.$name" }

    fun classesPath(): List<Class> {
      return if (parent !is Class) listOf(this)
      else (parent as Class).classesPath() + this
    }

    override fun toString(): String = "Class(path='${path()}')"

    override fun resolveInnerClassPath(path: List<String>): List<Class> {
      return if ((path.size == 1 && path.first() == name) || path.joinToString(".") == path()) super.resolveInnerClassPath(path) + this
      else super.resolveInnerClassPath(path)
    }

    override fun resolveOuterClassPath(path: List<String>, notClass: Class?): List<Class> =
      super.resolveOuterClassPath(path, notClass) + parent.resolveOuterClassPath(path, this)

    override fun resolveClassPath(path: List<String>): List<Class> {
      return resolveInnerClassPath(path) + parent.resolveOuterClassPath(
        path, this
      ) + if (path().endsWith(path.joinToString("."))) listOf(this) else listOf()
    }

    fun primitiveType() = PrimitiveType.ofClass(this)
    fun isSubclassOf(clazz: Class): Boolean {
      return this == clazz || clazz == PrimitiveType.ANY.clazz || this in clazz.allSuperClasses()
    }

    fun typeable() = object : Typeable {
      override fun type(): Class = this@Class
    }
  }

  fun resolveClassPath(path: String) = resolveClassPath(path.split("."))
  open fun resolveClassPath(path: List<String>): List<Class> = resolveInnerClassPath(path) + uses.flatMap { it.findClass(path) }

  fun resolveOuterClassPath(path: String, notClass: Class?) = resolveOuterClassPath(path.split("."), notClass)
  open fun resolveOuterClassPath(path: List<String>, notClass: Class?): List<Class> {
    return path.dropLast(1).fold(listOf(*classes.toTypedArray())) { acc, name ->
      acc.filter { it.name == name && it != notClass }.flatMap(Class::classes).filter { it != notClass }
    }.filter { it.name == path.last() }
  }

  fun resolveInnerClassPath(path: String) = resolveInnerClassPath(path.split("."))
  open fun resolveInnerClassPath(path: List<String>): List<Class> = resolveOuterClassPath(path, null)

  fun findClasses(name: String): List<Class> = classes.filter { it.name == name }

  fun findMethods(name: String): List<Method> = methods.filter { it.name == name }

  fun findFields(name: String): List<Field> = fields.filter { it.name == name }

  open fun resolveFieldsInScope(name: String) = findFields(name)

  open fun resolveMethodsInScope(name: String) = findMethods(name)

  open fun root() = this


  companion object {
    val EMPTY = Namespace(null, listOf(), mutableListOf(), mutableListOf(), mutableListOf())

    fun ofProgram(pack: String?, expression: Expression.Program): Namespace {
      val uses = expression.uses.map {
        Use(Either.Left(it.obj.path.repack { it.asString() }), it.obj.all, it.obj.asOther)
      }
      val classes = expression.classes.map {
        val namespace = ofProgram(null, it.obj.asProgram())
        Class(
          it.obj.identifier.obj.identifier,
          namespace.uses,
          namespace.fields,
          namespace.methods,
          namespace.classes,
          it.obj.superTypes.map { Either.Left(it.repack { it.path.asString() }) }.toMutableList()
        )
      }
      val fields = expression.vars.map { varDeclr ->
        Field(
          varDeclr.obj.identifier.obj,
          varDeclr.obj.type.mapLeft { it!!.repack { it.path.asString() } },
          varDeclr.obj.expr?.let { Either.Left(it) })
      }
      val methods = expression.functions.map { funcDeclr ->
        funcDeclr.obj.run {
          Method(
            identifier.obj.identifier,
            type?.repack { it.path.asString() }?.let { Either.Left(it) },
            Either.Left(code),
          ).apply {
            for (param in this@run.parameters) {
              addParameter(
                param.first.obj, Either.Left(param.second.repack { it.path.asString() })
              )
            }
          }
        }
      }
      return Namespace(pack, uses, fields.toMutableList(), methods.toMutableList(), classes.toMutableList()).apply { setParent() }
    }

    fun Class.setParent(namespace: Namespace) {
      parent = namespace
      setParent()
    }

    fun Namespace.setParent() {
      fields.forEach { it.parent = this }
      methods.forEach { it.parent = this }
      classes.forEach { it.setParent(this) }
    }
  }

  open fun resolve(namespaces: Set<Namespace>) {
    uses.forEach { use ->
      use.path = use.path.handleError { path ->
        (root().resolveInnerClassPath(path.obj) + namespaces.flatMap { it.resolveInnerClassPath(path.obj) }).firstOrNull()
          ?: throw IllegalArgumentException("Could not resolve $path for use at ${path.area()}")
      }
    }
    fields.forEach {
      it.type = it.type?.handleError { path ->
        val resolved = resolveClassPath(path.obj) + namespaces.flatMap { it.resolveInnerClassPath(path.obj) }
        if (resolved.isEmpty()) {
          throw Exception("Could not resolve class path $path for field type at ${path.area()}")
        } else resolved.first()
      }
    }
    methods.forEach {
      it.parameters.replaceAll { (name, type) ->
        it.MethodParameter(name,
          type.handleError { path ->
            val resolved = resolveClassPath(path.obj) + namespaces.flatMap { it.resolveInnerClassPath(path.obj) }
            if (resolved.isEmpty()) {
              throw Exception("Could not resolve class path $path for parameter")
            } else resolved.first()
          })
      }
      it.returnType = it.returnType?.handleError { path ->
        val resolved = resolveClassPath(path.obj) + namespaces.flatMap { it.resolveInnerClassPath(path.obj) }
        if (resolved.isEmpty()) {
          throw Exception("Could not resolve class path $path for returnType")
        } else resolved.first()
      }
    }
    classes.forEach { it.resolve(namespaces) }
  }

  fun typeCheck(namespaces: Set<Namespace>): List<Pair<String, List<Area>>> {
    return classes.flatMap { it.typeCheck(namespaces) } + fields.mapNotNull {
      try {
        it.typeCheck(namespaces)
        null
      } catch (e: TypeCheckException) {
        e.message!! to e.areas
      }
    } + methods.mapNotNull {
      try {
        it.typeCheck(namespaces)
        null
      } catch (e: TypeCheckException) {
        e.message!! to e.areas
      }
    }
  }

  open fun path() = ""
  open fun compile(pool: Pool) {
    fields.forEach { it.compile(pool) }
    methods.forEach { it.compile(pool) }
    classes.forEach { it.compile(pool) }
  }

}

