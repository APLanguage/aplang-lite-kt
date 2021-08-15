package com.github.amejonah1200.aplang_lite.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.lang.reflect.Field
import java.lang.reflect.Modifier

inline fun <T> listOfUntilNull(generator: () -> T?): List<T> {
  var temp: T? = generator()
  val list = mutableListOf<T>()
  while (temp != null) {
    list.add(temp)
    temp = generator()
  }
  return list.toList()
}

fun prettyPrintAST(gridded: GriddedObject<*>?): String {
  return GsonBuilder().setPrettyPrinting().create().toJson(gridded)
}
