package com.github.aplanguage.aplanglite.compiler.stdlib

import arrow.core.Either
import arrow.core.right
import com.github.aplanguage.aplanglite.compiler.naming.ClassBuilder
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Namespace
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Method
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Class
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Namespace.Companion.setParent


object StandardLibrary {

  val ITERABLE_CLASS = ClassBuilder("Iterable")
    .method("hasNext", listOf(), PrimitiveType.BOOL.clazz)
    .method("next", listOf(), PrimitiveType.ANY.clazz)
    .build()
  val LIST_CLASS = ClassBuilder("List").apply { superTypes.add(ITERABLE_CLASS) }
    .method("add", listOf(PrimitiveType.ANY.clazz))
    .method("get", listOf(PrimitiveType.U64.clazz), PrimitiveType.ANY.clazz)
    .method("size", listOf(), PrimitiveType.U64.clazz)
    .build()

  val STD_LIB = Namespace(
    listOf(),
    mutableListOf(),
    mutableListOf(
      method("timeNow", listOf(), PrimitiveType.U64.clazz),
      method("print", listOf(PrimitiveType.ANY.clazz)),
      method("println", listOf(PrimitiveType.ANY.clazz)),
      method("range", listOf(PrimitiveType.I32.clazz, PrimitiveType.I32.clazz), ITERABLE_CLASS)
    ),
    PrimitiveType.classes.toMutableList()
  ).also { std ->
    ITERABLE_CLASS.apply {
      setParent(std)
      std.classes.add(this)
    }
    LIST_CLASS.apply {
      setParent(std)
      std.classes.add(this)
    }
    PrimitiveType.STRING.clazz.methods.apply {
      add(method("concat", listOf(PrimitiveType.STRING.clazz), PrimitiveType.STRING.clazz))
      add(method("repeat", listOf(PrimitiveType.U8.clazz), PrimitiveType.STRING.clazz))
      add(method("repeat", listOf(PrimitiveType.U16.clazz), PrimitiveType.STRING.clazz))
      add(method("repeat", listOf(PrimitiveType.U32.clazz), PrimitiveType.STRING.clazz))
      add(method("repeat", listOf(PrimitiveType.U64.clazz), PrimitiveType.STRING.clazz))
      add(method("length", listOf(), PrimitiveType.U64.clazz))
    }
    std.setParent()
  }
}

private fun method(name: String, params: List<Class>, returnType: Class? = null): Method =
  Method(name, returnType?.let { Either.Right(it) }, Either.Right(listOf()),).apply {
    params.forEach { addParameter("<?>", it.right()) }
  }

