package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import arrow.core.handleError
import arrow.core.left
import arrow.core.right
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.utils.GriddedObject

class Class(
  val name: String,
  uses: List<Use>,
  fields: MutableList<Field>,
  methods: MutableList<Method>,
  classes: MutableList<Class>,
  val supers: MutableList<Either<GriddedObject<String>, Class>>
) : Namespace(uses, fields, methods, classes) {
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
