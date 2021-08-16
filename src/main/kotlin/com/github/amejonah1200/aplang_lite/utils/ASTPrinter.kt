package com.github.amejonah1200.aplang_lite.utils

import com.github.amejonah1200.aplang_lite.parser.Expression
import com.github.amejonah1200.aplang_lite.tokenizer.Token
import java.math.BigInteger

object ASTPrinter {

  fun print(any: Any?) {
    for (line in convert(any)) {
      println(line)
    }
  }

  private fun convert(any: Any?): List<String> {
    if (any == null) return emptyList()
    return when (any) {
      is String -> listOf(any)
      is GriddedObject<*> -> convertGridded(any)
      is Map<*, *> -> if (any.isEmpty()) emptyList() else if (any.size == 1) {
        val lines = convert(any.values.first())
        if (lines.isEmpty()) listOf("{ \"${any.keys.first()}\" : ?}")
        else if (lines.size == 1) listOf("{ \"${any.keys.first()}\" : ${lines.first()} }")
        else listOf("{ \"${any.keys.first()}\" : ${lines.first()}", *lines.subList(1, lines.size).toTypedArray() , "}")
      } else any.filter { it.key != null && it.value != null }
        .let {
          if (it.isEmpty()) emptyList()
          else listOf("{", *it.map { entry -> "\"${entry.key}\" :${convert(entry.value).joinToString("\n") { "   $it" }}" }.toTypedArray(), "}")
        }
      is List<*> -> any.filterNotNull().map { convert(it) }.filter { it.isNotEmpty() }.let {
        it.map {
          if (it.size == 1) "[${it[0]}]"
          else "[\n${it.joinToString("\n") { "   $it" }}\n]"
        }
      }
      is Pair<*, *> -> listOf("(", "   " + convert(any.first), "   " + convert(any.second), ")")
      is Expression -> objToLines(convertObjWithFields(any))
      is Token -> objToLines(convertObjWithFields(any))
      is Expression.Invocation -> objToLines(convertObjWithFields(any))
      is BigInteger -> listOf(any.toString())
      is Boolean -> listOf(any.toString())
      is Enum<*> -> listOf(any.name)
      else -> throw RuntimeException("shrug -> ${any.javaClass.simpleName}")
    }
  }

  private fun convertObjWithFields(any: Any): Pair<String, Map<String, Any>> {
    return any.javaClass.declaredFields.mapNotNull {
      it.trySetAccessible()
      val obj = it.get(any) ?: return@mapNotNull null
      Pair(it.name, obj)
    }.toMap().filterValues {
      when (it) {
        is List<*> -> it.isNotEmpty()
        is Map<*, *> -> it.isNotEmpty()
        else -> true
      }
    }.let { Pair(any.javaClass.simpleName, it) }
  }

  private fun objToLines(obj: Pair<String, Map<String, Any>>): List<String> {
    val fields = convert(obj.second)
    return if (fields.isEmpty()) listOf(obj.first)
    else if (fields.size == 1) listOf(obj.first + fields.first())
    else listOf(obj.first + "(", *fields.subList(1, fields.size - 1).map { "  $it" }.toTypedArray(), ")")
  }

  private fun convertGridded(griddedObject: GriddedObject<*>): List<String> {
    val lines = convert(griddedObject.obj)
    val prefix = when (griddedObject) {
      is OneLineObject<*> -> "[${griddedObject.start.y}:${griddedObject.start.x}+${griddedObject.length} "
      is MultiLineObject<*> -> "[${griddedObject.start.x}:${griddedObject.start.y} -> ${griddedObject.end.x}:${griddedObject.end.y} "
    }
    return if (lines.isEmpty()) listOf(prefix.trimEnd() + "]")
    else if (lines.size == 1) listOf(prefix + lines[0] + "]")
    else listOf(prefix + lines[0], *lines.subList(1, lines.size - 1).map { "  $it" }.toTypedArray(), lines.last() + "]")
  }
}
