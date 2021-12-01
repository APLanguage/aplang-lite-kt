package com.github.aplanguage.aplanglite.compiler

import arrow.core.Either

class ClassBuilder(val name: String) {
  val fields = mutableListOf<Namespace.Field>()
  val methods = mutableListOf<Namespace.Method>()
  val classes = mutableListOf<ClassBuilder>()
  val superTypes = mutableListOf<Namespace.Class>()

  fun clazz(name: String, init: ClassBuilder.() -> Unit = {}): ClassBuilder {
    classes.add(ClassBuilder(name).also(init))
    return this
  }

  fun clazz(classBuilder: ClassBuilder): ClassBuilder {
    classes.add(classBuilder)
    return this
  }

  fun field(name: String, type: Namespace.Class): ClassBuilder {
    fields.add(Namespace.Field(name, Either.Right(type), null))
    return this
  }

  fun method(name: String, parameters: List<Namespace.Class>, returnType: Namespace.Class? = null): ClassBuilder {
    methods.add(
      Namespace.Method(
        name, parameters.map { "<?>" to Either.Right(it) }.toMutableList(), returnType?.let { Either.Right(it) },
        Either.Right(listOf())
      )
    )
    return this
  }

  fun build(): Namespace.Class =
    Namespace.Class(
      name,
      listOf(),
      fields,
      methods,
      classes.map { it.build() }.toMutableList(),
      superTypes.map { Either.Right(it) }.toMutableList()
    ).also { self ->
      self.fields.forEach { it.type = it.type?.map { type -> if (type == SELF_CLASS) self else type } }
      self.methods.forEach {
        it.returnType = it.returnType?.map { type -> if (type == SELF_CLASS) self else type }
        it.parameters.replaceAll { (name, typeObj) -> name to typeObj.map { type -> if (type == SELF_CLASS) self else type } }
      }
    }

  companion object {
    val SELF_CB = ClassBuilder("self")
    val SELF_CLASS = SELF_CB.build()
  }
}
