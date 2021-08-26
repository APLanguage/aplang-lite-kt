package com.github.aplanguage.aplanglite.interpreter

sealed class ReturnValue {
  object Unit : ReturnValue() {
    override fun toString() = "Unit"
  }

  sealed class Number : ReturnValue() {
    data class FloatNumber(val number: Double) : Number()
    data class IntegerNumber(val number: Long) : Number()
  }

  data class StringValue(val string: String) : ReturnValue()

  data class IterableValue(val iterable: Iterable<ReturnValue>) : ReturnValue() {
    val iterator: Iterator<ReturnValue> = iterable.iterator()

    fun hasNext() = iterator.hasNext()
    fun advance() = iterator.next()
  }

  data class ObjectValue(val identifier: String, val fields: Map<String, ReturnValue>) : ReturnValue()
  data class BooleanValue(val boolean: Boolean) : ReturnValue()
}
