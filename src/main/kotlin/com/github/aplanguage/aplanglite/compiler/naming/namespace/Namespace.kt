package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.compilation.CompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.FieldCompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.MethodCompilationContext
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.parser.expression.Expression
import com.github.aplanguage.aplanglite.utils.Area

class TypeResolveException(message: String, val area: Area? = null) : Exception(message)

interface Typeable {
  fun type(): Class?
}

interface VirtualTypeable {
  fun virtualType(): Class?
}

interface Settable : Typeable {
  fun isStatic(): Boolean
}


open class Namespace(
  val uses: List<Use>,
  val fields: MutableList<Field>,
  val methods: MutableList<Method>,
  val classes: MutableList<Class>
) {

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
    val EMPTY = Namespace(listOf(), mutableListOf(), mutableListOf(), mutableListOf())

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
          varDeclr.obj.expr
        )
      }
      val methods = expression.functions.map { funcDeclr ->
        funcDeclr.obj.run {
          Method(
            identifier.obj.identifier,
            type?.repack { it.path.asString() }?.let { Either.Left(it) },
            code,
          ).apply {
            for (param in this@run.parameters) {
              addParameter(
                param.first.obj, Either.Left(param.second.repack { it.path.asString() })
              )
            }
          }
        }
      }
      return Namespace(uses, fields.toMutableList(), methods.toMutableList(), classes.toMutableList()).apply { setParent() }
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

  fun typeCheck(namespaces: Set<Namespace>): List<TypeCheckException> {
    return classes.flatMap { it.typeCheck(namespaces) } + fields.mapNotNull {
      try {
        it.typeCheck(namespaces)
        null
      } catch (e: TypeCheckException) {
        e
      }
    } + methods.mapNotNull {
      try {
        it.typeCheck(namespaces)
        null
      } catch (e: TypeCheckException) {
        e
      }
    }
  }

  open fun path() = ""

  open fun <CC : CompilationContext<MCC, FCC>, MCC : MethodCompilationContext, FCC : FieldCompilationContext> compile(context: CC) {
    fields.forEach {
      val fieldContext = context.fieldCompilationContext(it) ?: throw IllegalStateException("No field compilation context")
      it.compilationContexts.add(fieldContext)
      context.compile(it, fieldContext)
    }
    methods.forEach {
      val methodContext = context.methodCompilationContext(it) ?: throw IllegalStateException("No method compilation context")
      it.compilationContexts.add(methodContext)
      context.compile(it, methodContext)
    }
    classes.forEach { it.compile(context) }
  }

}

