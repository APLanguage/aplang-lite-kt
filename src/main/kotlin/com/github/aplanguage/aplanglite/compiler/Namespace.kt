package com.github.aplanguage.aplanglite.compiler

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.compiler.typechecking.StatementTypeChecker
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeChecker
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

  data class Method(
    val name: String,
    val parameters: MutableList<Pair<String, Either<GriddedObject<String>, Class>>>,
    var returnType: Either<GriddedObject<String>, Class>?,
    val exprs: Either<List<GriddedObject<Statement>>, List<Instruction>>
  ) : Typeable {
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
        parameters.forEach { (name, type) ->
          type.fold({ throw IllegalStateException("Parameters not resolved!") }, { register(name, it) })
        }
      })))
      exprs.mapLeft { it.forEach { it.obj.visit(checker) } }
    }
  }

  data class Field(
    val name: String,
    var type: Either<GriddedObject<String>, Class>?,
    var expr: Either<GriddedObject<Expression>, List<Instruction>>?
  ) : Typeable {
    lateinit var parent: Namespace

    fun asDescriptorString() = "${parent.path().let { if (it.isEmpty()) "" else "$it%" }}$name:${type?.orNull()?.path() ?: "?"}"

    override fun type(): Class = type?.orNull() ?: throw TypeResolveException("The type of ${asDescriptorString()} was not resolved!")
    fun typeCheck(namespaces: Set<Namespace>) {
      expr?.mapLeft { TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()))) }
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

    fun allSuperClasses(): List<Class> = supers.map {
      it.fold({ throw TypeResolveException("The type of ${it.obj} was not resolved.", it.area()) },
        { it })
    } + supers.flatMap {
      it.fold({ throw TypeResolveException("The type of ${it.obj} was not resolved.", it.area()) },
        { it.allSuperClasses() })
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
            parameters.map { it.first.obj to Either.Left(it.second.repack { it.path.asString() }) }.toMutableList(),
            type?.repack { it.path.asString() }?.let { Either.Left(it) },
            Either.Left(code)
          )
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
        name to type.handleError { path ->
          val resolved = resolveClassPath(path.obj) + namespaces.flatMap { it.resolveInnerClassPath(path.obj) }
          if (resolved.isEmpty()) {
            throw Exception("Could not resolve class path $path for parameter")
          } else resolved.first()
        }
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

}

