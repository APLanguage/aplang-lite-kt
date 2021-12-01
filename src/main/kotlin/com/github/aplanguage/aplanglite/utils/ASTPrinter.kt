package com.github.aplanguage.aplanglite.utils

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.parser.expression.Expression
import com.github.aplanguage.aplanglite.tokenizer.Token
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
      is String -> listOf("\"$any\"")
      is GriddedObject<*> -> convertGridded(any)
      is Map<*, *> -> if (any.isEmpty()) emptyList() else if (any.size == 1) {
        val lines = convert(any.values.first())
        if (lines.isEmpty()) listOf("{ \"${any.keys.first()}\" : ?}")
        else if (lines.size == 1) listOf("{ \"${any.keys.first()}\" : ${lines.first()} }")
        else listOf("{ \"${any.keys.first()}\" : ${lines.first()}", *lines.subList(1, lines.size).toTypedArray(), "}")
      } else any.filter { it.key != null && it.value != null }
        .let {
          if (it.isEmpty()) emptyList()
          else listOf("{", *it.map { entry ->
            val obj = convert(entry.value)
            if (obj.size == 1) listOf("  \"${entry.key}\" : ${obj.first()}")
            else listOf("  \"${entry.key}\" : ${obj.firstOrNull() ?: "[]"}", *obj.drop(1).toTypedArray())
          }.flatten().toTypedArray(), "  }")
        }
      is List<*> -> any.filterNotNull().map { convert(it) }.filter { it.isNotEmpty() }.let { elements ->
        elements.map { element ->
          if (element.size == 1) listOf(element[0]).toMutableList()
          else element.map { s -> "    $s" }.toMutableList()
        }.mapIndexed { index, element ->
          if (index != elements.size - 1) {
            element[element.size - 1] = element.last() + ","
          }
          element
        }.toMutableList()
      }.apply {
        if (isNotEmpty()) {
          first()[0] = "[" + first().first()
          last()[last().size - 1] = last().last() + "]"
        }
      }.flatten()
      is Pair<*, *> -> listOf("(", *convert(any.first).map { "  $it" }.toTypedArray(), *convert(any.second).map { "  $it" }.toTypedArray(), ")")
//      is ReturnValue.CallableValue -> listOf("CallableValue(${any})")
//      is Expression.Invocation, is Structure, is ReturnValue, is Scope
      is Expression, is Token, is Expression.Program -> objToLines(convertObjWithFields(any))
      is BigInteger, is Boolean, is Double, is Long, is Int -> listOf(any.toString())
      is Enum<*> -> listOf(any.name)
      is Area -> listOf(
        when (any) {
          is OneLineArea -> "[${any.start.y}:${any.start.x}+${any.length}]"
          is MultiLineArea -> "[${any.start.x}:${any.start.y} -> ${any.end.x}:${any.end.y}]"
        }
      )
      is Expression.Type -> listOf("Type(Path(${any.path.identifiers.joinToString(".") { it.obj.identifier }}))")
      is Expression.Path -> listOf("Path(${any.identifiers.joinToString(".") { it.obj.identifier }})")
      is Namespace -> listOf(
        when (any) {
          is Namespace.Class -> "Class(${any.path()},"
          else -> "Namespace("
        },
        *convert(
          mapOf(
            "uses" to any.uses,
            "methods" to any.methods,
            "fields" to any.fields,
            "classes" to any.classes
          )
        ).drop(1).dropLast(1).toTypedArray(),
        ")"
      )
      is Namespace.Field -> listOf("Field(${any.name}, ${any.type})")
      is Namespace.Method -> listOf("Method(${any.name}, ${any.returnType})")
      is Namespace.Use -> listOf("Use(${any.path}${if (any.star) " ,*" else ""}${if (any.alias != null) " ,${any.alias}" else ""})")
      is Either.Left<*> -> listOf("Left(${any.value})")
      is Either.Right<*> -> listOf("Right(${any.value})")
      else -> throw RuntimeException("shrug -> ${any.javaClass.simpleName} $any")
    }
  }

  fun objToString(any: Any) = objToLines(convertObjWithFields(any)).joinToString("\n")

  fun convertObjWithFields(any: Any): Pair<String, Map<String, Any>> {
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

  fun objToLines(obj: Pair<String, Map<String, Any>>): List<String> {
    val fields = convert(if (obj.second.size == 1) obj.second.values.first() else obj.second)
    return if (fields.isEmpty()) listOf(obj.first)
    else if (fields.size == 1) listOf(obj.first + "(" + fields.first() + ")")
    else listOf(obj.first + "(" + fields.first(), *fields.subList(1, fields.size - 1).map { "  $it" }.toTypedArray(), fields.last() + ")")
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
