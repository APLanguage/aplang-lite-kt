package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import java.math.BigInteger
import kotlin.math.pow

sealed class ReturnValue {

  open fun supportBinaryOperation(token: CodeToken): Boolean = false

  open fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue = ReturnValue.Unit

  object Unit : ReturnValue() {
    override fun toString() = "Unit"
  }

  object Null : ReturnValue() {
    override fun toString() = "Null"
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

      override fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue {
        return when (token) {
          CodeToken.BANG_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number != second.number)
            else -> Unit
          }
          CodeToken.EQUAL_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number == second.number)
            else -> Unit
          }
          CodeToken.GREATER -> when (second) {
            is FloatNumber -> BooleanValue(number > second.number)
            is IntegerNumber -> BooleanValue(number > second.number)
            else -> Unit
          }
          CodeToken.GREATER_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number >= second.number)
            is IntegerNumber -> BooleanValue(number >= second.number)
            else -> Unit
          }
          CodeToken.LESS -> when (second) {
            is FloatNumber -> BooleanValue(number < second.number)
            is IntegerNumber -> BooleanValue(number < second.number)
            else -> Unit
          }
          CodeToken.LESS_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number <= second.number)
            is IntegerNumber -> BooleanValue(number <= second.number)
            else -> Unit
          }
          CodeToken.PLUS -> when (second) {
            is FloatNumber -> FloatNumber(number + second.number)
            is IntegerNumber -> FloatNumber(number + second.number)
            else -> Unit
          }
          CodeToken.MINUS -> when (second) {
            is FloatNumber -> FloatNumber(number - second.number)
            is IntegerNumber -> FloatNumber(number - second.number)
            else -> Unit
          }
          CodeToken.SLASH -> when (second) {
            is FloatNumber -> FloatNumber(number + second.number)
            is IntegerNumber -> FloatNumber(number + second.number)
            else -> Unit
          }
          CodeToken.STAR -> when (second) {
            is FloatNumber -> FloatNumber(number * second.number)
            is IntegerNumber -> FloatNumber(number * second.number)
            else -> Unit
          }
          CodeToken.STAR_STAR -> when (second) {
            is FloatNumber -> FloatNumber(number.pow(second.number))
            is IntegerNumber -> FloatNumber(number.pow(second.number.toInt()))
            else -> Unit
          }
          else -> throw InterpreterException("Not supported ${token.name}")
        }
      }
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

      override fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue {
        return when (token) {
          CodeToken.BANG_EQUAL -> when (second) {
            is IntegerNumber -> BooleanValue(number != second.number)
            else -> Unit
          }
          CodeToken.EQUAL_EQUAL -> when (second) {
            is IntegerNumber -> BooleanValue(number == second.number)
            else -> Unit
          }
          CodeToken.GREATER -> when (second) {
            is FloatNumber -> BooleanValue(number > second.number)
            is IntegerNumber -> BooleanValue(number > second.number)
            else -> Unit
          }
          CodeToken.GREATER_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number >= second.number)
            is IntegerNumber -> BooleanValue(number >= second.number)
            else -> Unit
          }
          CodeToken.LESS -> when (second) {
            is FloatNumber -> BooleanValue(number < second.number)
            is IntegerNumber -> BooleanValue(number < second.number)
            else -> Unit
          }
          CodeToken.LESS_EQUAL -> when (second) {
            is FloatNumber -> BooleanValue(number <= second.number)
            is IntegerNumber -> BooleanValue(number <= second.number)
            else -> Unit
          }
          CodeToken.PLUS -> when (second) {
            is FloatNumber -> FloatNumber(number + second.number)
            is IntegerNumber -> IntegerNumber(number + second.number)
            else -> Unit
          }
          CodeToken.MINUS -> when (second) {
            is FloatNumber -> FloatNumber(number - second.number)
            is IntegerNumber -> IntegerNumber(number - second.number)
            else -> Unit
          }
          CodeToken.SLASH -> when (second) {
            is FloatNumber -> FloatNumber(number + second.number)
            is IntegerNumber -> IntegerNumber(number + second.number)
            else -> Unit
          }
          CodeToken.STAR -> when (second) {
            is FloatNumber -> FloatNumber(number * second.number)
            is IntegerNumber -> IntegerNumber(number * second.number)
            else -> Unit
          }
          CodeToken.STAR_STAR -> when (second) {
            is FloatNumber -> FloatNumber(number.toDouble().pow(second.number))
            is IntegerNumber -> IntegerNumber(BigInteger.valueOf(number).pow(second.number.toInt()).toLong())
            else -> Unit
          }
          else -> throw InterpreterException("Not supported ${token.name}")
        }
      }
    }
  }

  data class StringValue(val string: String) : ReturnValue() {
    override fun supportBinaryOperation(token: CodeToken) = token in arrayOf(CodeToken.PLUS, CodeToken.STAR)

    override fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue {
      return when (token) {
        CodeToken.PLUS -> if (second is StringValue) StringValue(string + second.string) else Unit
        CodeToken.STAR -> if (second is Number.IntegerNumber) StringValue(string.repeat(second.number.toInt())) else Unit
        else -> throw InterpreterException("Not supported ${token.name}")
      }
    }
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

    override fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue {
      return if (second !is BooleanValue) Unit
      else when (token) {
        CodeToken.DOUBLE_VERTICAL_BAR -> BooleanValue(boolean || second.boolean)
        CodeToken.AMPERSAND_AMPERSAND -> BooleanValue(boolean && second.boolean)
        CodeToken.BANG_EQUAL -> BooleanValue(boolean != second.boolean)
        CodeToken.EQUAL_EQUAL -> BooleanValue(boolean == second.boolean)
        else -> throw InterpreterException("Not supported ${token.name}")
      }
    }
  }
}
