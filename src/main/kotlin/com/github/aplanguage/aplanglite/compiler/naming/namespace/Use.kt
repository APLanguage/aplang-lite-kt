package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import com.github.aplanguage.aplanglite.utils.GriddedObject

class Use(var path: Either<GriddedObject<String>, Class>, val star: Boolean, val alias: GriddedObject<String>? = null) {
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
