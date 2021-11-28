package com.github.aplanguage.aplanglite.compiler

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.parser.Expression
import com.github.aplanguage.aplanglite.utils.GriddedObject

open class Namespace(val uses: List<Use>, val fields: List<Field>, val methods: List<Method>, val classes: List<Class>) {
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

  data class Method(
    val name: String,
    val parameters: MutableList<Either<GriddedObject<String>, Class>>,
    var returnType: Either<GriddedObject<String>, Class>?,
    val exprs: Either<List<GriddedObject<Expression.Statement>>, List<Instruction>>
  ) {
    lateinit var parent: Namespace
  }

  data class Field(
    val name: String,
    var type: Either<GriddedObject<String>, Class>?,
    val expr: Either<GriddedObject<Expression>, Either<Expression, List<Instruction>>>?
  ) {
    lateinit var parent: Namespace
  }

  class Class(
    val name: String,
    uses: List<Use>,
    methods: List<Method>,
    fields: List<Field>,
    classes: List<Class>,
    val supers: MutableList<Either<GriddedObject<String>, Class>>
  ) : Namespace(uses, fields, methods, classes) {
    lateinit var parent: Namespace

    override fun resolveFieldsInScope(name: String): List<Field> = findFields(name) + root().resolveFieldsInScope(name)

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

  open fun root() = this


  companion object {
    fun ofProgram(expression: Expression.Program): Namespace {
      val uses = expression.uses.map {
        Use(Either.Left(it.obj.path.repack { it.asString() }), it.obj.all, it.obj.asOther)
      }
      val classes = expression.classes.map {
        val namespace = ofProgram(it.obj.asProgram())
        Class(
          it.obj.identifier.obj.identifier,
          namespace.uses,
          namespace.methods,
          namespace.fields,
          namespace.classes,
          it.obj.superTypes.map { Either.Left(it.repack { it.path.asString() }) }.toMutableList()
        )
      }
      val fields = expression.vars.map { varDeclr ->
        Field(
          varDeclr.obj.identifier.obj.identifier,
          varDeclr.obj.type?.repack { it.path.asString() }?.let { Either.Left(it) },
          varDeclr.obj.expr?.let { Either.Left(it) })
      }
      val methods = expression.functions.map { funcDeclr ->
        funcDeclr.obj.run {
          Method(
            identifier.obj.identifier,
            parameters.map { Either.Left(it.second.repack { it.path.asString() }) }.toMutableList(),
            type?.repack { it.path.asString() }?.let { Either.Left(it) },
            Either.Left(code)
          )
        }

      }
      return Namespace(uses, fields, methods, classes).apply { setParent() }
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
      it.parameters.replaceAll {
        it.handleError { path ->
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

  open fun path() = ""

}

