package com.github.aplanguage.aplanglite.compiler.naming

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Field
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Method
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Class


class ClassBuilder(val name: String) {
  val fields = mutableListOf<Field>()
  val methods = mutableListOf<Method>()
  val classes = mutableListOf<ClassBuilder>()
  val superTypes = mutableListOf<Class>()

  fun clazz(name: String, init: ClassBuilder.() -> Unit = {}): ClassBuilder {
    classes.add(ClassBuilder(name).also(init))
    return this
  }

  fun clazz(classBuilder: ClassBuilder): ClassBuilder {
    classes.add(classBuilder)
    return this
  }

  fun field(name: String, type: Class): ClassBuilder {
    fields.add(Field(name, Either.Right(type), null))
    return this
  }

  fun method(name: String, parameters: List<Class>, returnType: Class? = null): ClassBuilder {
    methods.add(
      Method(name, returnType?.let { Either.Right(it) }, listOf())
        .apply {
          parameters.map { "<?>" to Either.Right(it) }.forEach { (name, type) -> addParameter(name, type) }
        }
    )
    return this
  }

  fun build(): Class =
    Class(
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
        it.parameters.forEach { param -> param.clazz = param.clazz.map { type -> if (type == SELF_CLASS) self else type } }
      }
    }

  companion object {
    val SELF_CB = ClassBuilder("self")
    val SELF_CLASS = SELF_CB.build()
  }
}
