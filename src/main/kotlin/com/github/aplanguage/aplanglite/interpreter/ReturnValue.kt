package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import java.lang.invoke.MethodHandle
import java.math.BigInteger
import kotlin.math.pow

sealed class ReturnValue {

  open fun supportBinaryOperation(token: CodeToken): Boolean = false

  open fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue = ReturnValue.Unit

  open fun supportUnaryOperation(token: CodeToken): Boolean = false

  open fun applyUnaryOp(token: CodeToken): ReturnValue = ReturnValue.Unit

  open fun asString(): String = this.javaClass.simpleName

  object Unit : ReturnValue() {
    override fun toString() = "Unit"
  }

  object Null : ReturnValue() {
    override fun toString() = "Null"
  }

  data class TriggeredReturn(val returnValue: ReturnValue?) : ReturnValue()

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

      override fun asString() = number.toString()

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

      override fun asString() = number.toString()

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
          CodeToken.PERCENTAGE -> when (second) {
            is IntegerNumber -> IntegerNumber(number % second.number)
            else -> Unit
          }
          else -> throw InterpreterException("Not supported ${token.name}")
        }
      }

      override fun supportUnaryOperation(token: CodeToken) = token == CodeToken.TILDE

      override fun applyUnaryOp(token: CodeToken) = if (token == CodeToken.TILDE) IntegerNumber(number.inv()) else Unit
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

    override fun asString() = string
  }

  data class IterableValue(val iterable: Iterable<ReturnValue>) : ReturnValue(), Iterable<ReturnValue> {
    val iterator: Iterator<ReturnValue> = iterable.iterator()

    fun hasNext() = iterator.hasNext()
    fun advance() = iterator.next()
    override fun iterator() = iterator
  }


  sealed class PropertiesNFunctionsValue(val identifier: String) : ReturnValue() {

    abstract fun fields(interpreter: Interpreter, scope: Interpreter.Scope): Map<String, FieldValue>

    abstract fun functions(interpreter: Interpreter, scope: Interpreter.Scope): Map<String, ReturnValue.CallableValue.CallableFunctionValue>

    fun scope(interpreter: Interpreter, scope: Interpreter.Scope) =
      Interpreter.Scope(fields(interpreter, scope).toMutableMap(), functions(interpreter, scope), null)

    data class FieldValue(val varStructure: Structure.VarStructure) : PropertiesNFunctionsValue(varStructure.identifier) {
      override fun fields(interpreter: Interpreter, scope: Interpreter.Scope): Map<String, FieldValue> {
        val value = varStructure.evaluateValue(interpreter, scope)
        return if (value is PropertiesNFunctionsValue) value.fields(interpreter, scope)
        else mapOf()
      }

      override fun functions(interpreter: Interpreter, scope: Interpreter.Scope): Map<String, CallableValue.CallableFunctionValue> {
        val value = varStructure.evaluateValue(interpreter, scope)
        return if (value is PropertiesNFunctionsValue) value.functions(interpreter, scope)
        else mapOf()
      }

      fun value(interpreter: Interpreter, scope: Interpreter.Scope) = varStructure.evaluateValue(interpreter, scope)

      override fun supportBinaryOperation(token: CodeToken): Boolean = varStructure.value!!.supportBinaryOperation(token)

      override fun applyBinaryOp(token: CodeToken, second: ReturnValue): ReturnValue = varStructure.value!!.applyBinaryOp(token, second)

      override fun supportUnaryOperation(token: CodeToken): Boolean = varStructure.value!!.supportUnaryOperation(token)

      override fun applyUnaryOp(token: CodeToken): ReturnValue = varStructure.value!!.applyUnaryOp(token)

      override fun asString(): String = varStructure.value!!.asString()

    }

    class InstanceValue(
      identifier: String,
      private val fields: Map<String, FieldValue>,
      private val functions: Map<String, CallableValue.CallableFunctionValue>
    ) : PropertiesNFunctionsValue(identifier) {
      override fun fields(interpreter: Interpreter, scope: Interpreter.Scope) = fields

      override fun functions(interpreter: Interpreter, scope: Interpreter.Scope) = functions

      fun callFunction(identifier: String, interpreter: Interpreter, scope: Interpreter.Scope, arguments: Array<ReturnValue>): ReturnValue {
        val func = functions[identifier] ?: throw InterpreterException("No callable function named $identifier found in ${this.identifier}.")
        return func.callable(scope, interpreter, arguments)
      }

      fun callFieldValue(identifier: String, interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
        val fieldValue = fields[identifier] ?: throw InterpreterException("No field named $identifier found in ${this.identifier}.")
        return fieldValue.varStructure.evaluateValue(interpreter, scope)
      }

    }
  }

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

    override fun supportUnaryOperation(token: CodeToken) = token == CodeToken.BANG

    override fun applyUnaryOp(token: CodeToken) = if (token == CodeToken.BANG) BooleanValue(!boolean) else Unit

    override fun asString() = boolean.toString()
  }

  open class CallableValue(val callable: (Interpreter.Scope, Interpreter, Array<ReturnValue>) -> ReturnValue) : ReturnValue() {
    data class CallableFunctionValue(
      val identifier: String,
      val method: MethodHandle
    ) : CallableValue({ _, _, args ->
      val ret = if (method.type().parameterCount() == 1 && method.type().parameterType(0).isArray)
        method.invoke(args)
      else {
        if (method.type().parameterCount() != args.size) throw InterpreterException(
          "Wrong argument count passed in ${identifier}(${
            method.type().parameterArray().joinToString(", ") { it.simpleName }
          })${method.type().returnType().simpleName}, expected ${method.type().parameterCount()} got ${args.size}."
        ) else if (!method.type().parameterArray().zip(args).fold(true) { matches, pair ->
            matches && pair.first.isInstance(pair.second)
          }) {
          throw InterpreterException(
            "Passed wrong argument types in ${identifier}(${
              method.type().parameterArray().joinToString(", ") { it.simpleName }
            })${method.type().returnType().simpleName}, got ${args.map { it.javaClass.simpleName }.toString()}."
          )
        }
        method.invokeWithArguments(args.toList())
      }
      if (ret !is ReturnValue) ReturnValue.Unit
      else ret
    }) {

      fun supportTypes(arguments: Array<ReturnValue>): Boolean {
        return rightAmount(arguments.size) && method.type().parameterArray().zip(arguments).fold(true) { matches, pair ->
          matches && pair.first.isInstance(pair.second)
        }
      }

      fun rightAmount(arguments: Int) = method.type().parameterCount() == arguments
    }

  }

}
