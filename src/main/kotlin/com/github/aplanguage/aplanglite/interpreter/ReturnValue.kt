package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.tokenizer.CodeToken

sealed class ReturnValue {

  open fun supportBinaryOperation(token: CodeToken): Boolean = false

  object Unit : ReturnValue() {
    override fun toString() = "Unit"
  }


  sealed class Number : ReturnValue() {
    data class FloatNumber(val number: Double) : Number() {
      override fun supportBinaryOperation(token: CodeToken) = token in arrayOf(
        CodeToken.BANG_EQUAL,
        CodeToken.EQUAL_EQUAL,
        CodeToken.GREATER,
        CodeToken.GREATER_EQUAL,
        CodeToken.LESS,
        CodeToken.LESS_EQUAL,
        CodeToken.PLUS,
        CodeToken.MINUS,
        CodeToken.SLASH,
        CodeToken.STAR,
        CodeToken.STAR_STAR
      )
    }

    data class IntegerNumber(val number: Long) : Number() {
      override fun supportBinaryOperation(token: CodeToken) = token in arrayOf(
        CodeToken.BANG_EQUAL,
        CodeToken.EQUAL_EQUAL,
        CodeToken.GREATER,
        CodeToken.GREATER_EQUAL,
        CodeToken.LESS,
        CodeToken.LESS_EQUAL,
        CodeToken.PLUS,
        CodeToken.MINUS,
        CodeToken.SLASH,
        CodeToken.STAR,
        CodeToken.STAR_STAR,
        CodeToken.PERCENTAGE,
        CodeToken.VERTICAL_BAR,
        CodeToken.CIRCUMFLEX,
        CodeToken.AMPERSAND,
        CodeToken.GREATER_GREATER,
        CodeToken.LESS_LESS,
        CodeToken.GREATER_GREATER_GREATER
      )
    }
  }

  data class StringValue(val string: String) : ReturnValue() {
    override fun supportBinaryOperation(token: CodeToken) = token in arrayOf(CodeToken.PLUS, CodeToken.STAR)
  }

  data class IterableValue(val iterable: Iterable<ReturnValue>) : ReturnValue() {
    val iterator: Iterator<ReturnValue> = iterable.iterator()

    fun hasNext() = iterator.hasNext()
    fun advance() = iterator.next()
  }

  data class ObjectValue(val identifier: String, val fields: Map<String, ReturnValue>) : ReturnValue()
  data class BooleanValue(val boolean: Boolean) : ReturnValue() {
    override fun supportBinaryOperation(token: CodeToken) =
      token in arrayOf(CodeToken.DOUBLE_VERTICAL_BAR, CodeToken.AMPERSAND_AMPERSAND, CodeToken.BANG_EQUAL, CodeToken.EQUAL_EQUAL)
  }
}
