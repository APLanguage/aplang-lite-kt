package com.github.aplanguage.aplanglite.compiler.stdlib

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.ClassBuilder
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.compiler.Namespace.Companion.setParent


object StandardLibrary {

  val STD_LIB = Namespace(
    "aplang",
    listOf(),
    mutableListOf(),
    mutableListOf(
      method("timeNow", listOf(), PrimitiveType.U64.clazz),
      method("print", listOf(PrimitiveType.ANY.clazz)),
      method("println", listOf(PrimitiveType.ANY.clazz))
    ),
    PrimitiveType.classes.toMutableList()
  ).apply {
    setParent()
  }

  val ITERABLE_CLASS = ClassBuilder("Iterable")
    .method("hasNext", listOf(), PrimitiveType.BOOL.clazz)
    .method("next", listOf(), PrimitiveType.ANY.clazz)
    .build().apply {
      setParent(STD_LIB)
      STD_LIB.classes.add(this)
    }
  val LIST_CLASS = ClassBuilder("List").apply { superTypes.add(ITERABLE_CLASS) }
    .method("add", listOf(PrimitiveType.ANY.clazz))
    .method("get", listOf(PrimitiveType.U64.clazz), PrimitiveType.ANY.clazz)
    .method("size", listOf(), PrimitiveType.U64.clazz)
    .build().apply {
      setParent(STD_LIB)
      STD_LIB.classes.add(this)
    }

}

private fun method(name: String, params: List<Namespace.Class>, returnType: Namespace.Class? = null): Namespace.Method =
  Namespace.Method(name, params.map { "<?>" to Either.Right(it) }.toMutableList(), returnType?.let { Either.Right(it) }, Either.Right(listOf()))

