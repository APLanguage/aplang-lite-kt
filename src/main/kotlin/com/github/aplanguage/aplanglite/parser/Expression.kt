package com.github.aplanguage.aplanglite.parser

import com.github.aplanguage.aplanglite.interpreter.*
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.tokenizer.ValueKeyword
import com.github.aplanguage.aplanglite.utils.ASTPrinter
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import java.math.BigDecimal

sealed class Expression {
  data class Program(val uses: List<GriddedObject<Expression>>, val declarations: List<GriddedObject<Expression>>) : Expression()

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>)

  data class Type(val path: Path)

  sealed class Invocation {

    abstract fun call(callableValue: ReturnValue.CallableValue, scope: Scope, interpreter: Interpreter): ReturnValue

    data class FunctionCall(val arguments: List<GriddedObject<Expression>>) : Invocation() {
      override fun call(callableValue: ReturnValue.CallableValue, scope: Scope, interpreter: Interpreter): ReturnValue {
        return callableValue.call(interpreter, scope, arguments.map { interpreter.runExpression(scope, it.obj) }.toTypedArray())
      }
    }

    data class ArrayCall(val expr: GriddedObject<Expression>) : Invocation() {
      override fun call(callableValue: ReturnValue.CallableValue, scope: Scope, interpreter: Interpreter): ReturnValue {
        TODO("Not yet implemented")
      }
    }
  }

  sealed class Declaration : Expression() {
    data class UseDeclaration(val path: GriddedObject<Path>, val all: Boolean, val asOther: GriddedObject<Token.IdentifierToken>?) : Declaration()

    data class VarDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val type: GriddedObject<Type>?,
      val expr: GriddedObject<Expression>?
    ) : Declaration() {
      fun toFieldValue(): ReturnValue.PropertiesNFunctionsValue.FieldValue {
        return ReturnValue.PropertiesNFunctionsValue.FieldValue(Structure.VarStructure(identifier.obj.identifier, type?.obj, expr?.obj, null))
      }
    }


    data class FunctionDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val parameters: List<Pair<GriddedObject<Token.IdentifierToken>, GriddedObject<Type>>>,
      val type: GriddedObject<Type>?,
      val block: GriddedObject<Block>
    ) : Declaration()

    data class ClassDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val superTypes: List<GriddedObject<Type>>,
      val content: GriddedObject<Program>?
    ) : Declaration()
  }

  sealed class Statement : Expression() {

    open fun run(interpreter: Interpreter, scope: Scope): ReturnValue = ReturnValue.Unit

    data class ForStatement(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val iterableExpr: GriddedObject<Expression>,
      val statement: GriddedObject<Expression>
    ) : Statement() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val iterableValue = when (val expr = iterableExpr.obj) {
          else -> when (val iterableValue = interpreter.runExpression(scope, expr)) {
            is ReturnValue.IterableValue -> iterableValue
            else -> throw InterpreterException("No ${iterableExpr.obj.javaClass.simpleName} (${iterableValue.javaClass.simpleName}) as iterable allowed at ${iterableExpr.area()}.")
          }
        }
        for (obj in iterableValue.iterable) {
          val stmtObj = statement.obj
          if (stmtObj is BreakStatement) return ReturnValue.Unit
          if (stmtObj is ReturnStatement) return ReturnValue.TriggeredReturn(stmtObj.expr?.let { interpreter.runExpression(scope, it.obj) })
          interpreter.runExpression(
            Scope(
              mutableMapOf(
                identifier.obj.identifier to Structure.VarStructure(
                  identifier.obj.identifier,
                  null, null, obj
                ).toFieldValue()
              ), mapOf(), scope
            ), stmtObj
          ).also { if (it is ReturnValue.TriggeredReturn) return it }
        }
        return ReturnValue.Unit
      }
    }

    data class ReturnStatement(val expr: GriddedObject<Expression>?) : Statement() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        return ReturnValue.TriggeredReturn(expr?.obj?.let { interpreter.runExpression(scope, it) })
      }
    }

    class BreakStatement : Statement() {
      override fun equals(other: Any?): Boolean {
        return other != null && other.javaClass.isInstance(this)
      }

      override fun hashCode(): Int {
        return javaClass.hashCode()
      }
    }

    data class WhileStatement(val condition: GriddedObject<Expression>, val statement: GriddedObject<Expression>?) : Statement() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        while (when (val expr = condition.obj) {
            is DataExpression -> expr.run(interpreter, scope).let {
              if (it is ReturnValue.BooleanValue) it
              else throw InterpreterException("Expected BooleanValue, got ${it.javaClass.simpleName} with value: $it")
            }
            else -> throw InterpreterException("No ${condition.obj.javaClass.simpleName} as condition allowed at ${condition.area()}.")
          }.boolean
        ) {
          if (statement != null) {
            val stmtObj = statement.obj
            if (stmtObj is BreakStatement) return ReturnValue.Unit
            if (stmtObj is ReturnStatement) return ReturnValue.TriggeredReturn(stmtObj.expr?.let { interpreter.runExpression(scope, it.obj) })
            interpreter.runExpression(Scope(mutableMapOf(), mapOf(), scope), stmtObj)
              .also { if (it is ReturnValue.TriggeredReturn) return it }
          }
        }
        return ReturnValue.Unit
      }
    }

    data class IfStatement(
      val condition: GriddedObject<Expression>,
      val thenStmt: GriddedObject<Expression>,
      val elseStmt: GriddedObject<Expression>?
    ) : Statement() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val conditionValue: ReturnValue.BooleanValue = when (val expr = condition.obj) {
          is DataExpression -> expr.run(interpreter, scope).let {
            if (it is ReturnValue.BooleanValue) it
            else throw InterpreterException("Expected BooleanValue, got ${it.javaClass.simpleName} with value: $it")
          }
          else -> throw InterpreterException("No ${condition.obj.javaClass.simpleName} as condition allowed at ${condition.area()}.")
        }
        if (conditionValue.boolean) {
          interpreter.runExpression(Scope(mutableMapOf(), mapOf(), scope), thenStmt.obj)
            .also { if (it is ReturnValue.TriggeredReturn) return it }
        } else if (elseStmt != null) {
          interpreter.runExpression(Scope(mutableMapOf(), mapOf(), scope), elseStmt.obj)
            .also { if (it is ReturnValue.TriggeredReturn) return it }
        }
        return ReturnValue.Unit
      }
    }
  }

  data class Block(val statements: List<GriddedObject<Expression>>) : Expression() {
    fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
      var returnValue: ReturnValue = ReturnValue.Unit
      val blockScope = Scope(mutableMapOf(), mapOf(), scope)
      for (statement in statements) {
        returnValue = interpreter.runExpression(blockScope, statement.obj).also { if (it is ReturnValue.TriggeredReturn) return it }
      }
      return returnValue
    }
  }

  sealed class DataExpression : Expression() {

    open fun run(interpreter: Interpreter, scope: Scope): ReturnValue = ReturnValue.Unit

    data class Assignment(
      val call: GriddedObject<Expression>,
      val op: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<Expression>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val value = interpreter.runExpression(scope, call.obj)
        if (value !is ReturnValue.PropertiesNFunctionsValue.FieldValue) throw InterpreterException("You cannot assign a value to ${value.javaClass.simpleName}.")
        val expr = interpreter.runExpression(scope, expr.obj)
        value.varStructure.value = when (val tk = op.obj.codeToken) {
          CodeToken.EQUAL -> expr
          else -> {
            if (value.value(interpreter, scope).supportBinaryOperation(tk)) {
              value.varStructure.value!!.applyBinaryOp(tk, expr).also {
                if (it == ReturnValue.Unit) throw InterpreterException("${tk.name} is not supported for ${value.varStructure.value?.javaClass?.simpleName} and ${expr.javaClass.simpleName}.")
              }
            } else {
              throw InterpreterException("${value.varStructure.value?.javaClass?.simpleName} does not support ${tk.name}.")
            }
          }
        }
        return value
      }
    }

    data class IfExpression(
      val condition: GriddedObject<Expression>,
      val thenExpr: GriddedObject<Expression>,
      val elseExpr: GriddedObject<Expression>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val conditionValue: ReturnValue.BooleanValue = when (val expr = condition.obj) {
          is BrokenExpression -> throw InterpreterException("Cannot execute broken at ${condition.area()}.")
          is DataExpression -> expr.run(interpreter, scope).let {
            if (it is ReturnValue.BooleanValue) it
            else throw InterpreterException("Expected BooleanValue, got ${it.javaClass.simpleName} with value: $it")
          }
          else -> throw InterpreterException("No ${condition.obj.javaClass.simpleName} as condition allowed at ${condition.area()}.")
        }
        return interpreter.runExpression(
          Scope(mutableMapOf(), mapOf(), scope),
          if (conditionValue.boolean) thenExpr.obj else elseExpr.obj
        )
      }
    }

    data class BinaryOperation(
      val first: GriddedObject<Expression>,
      val operations: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<Expression>>>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val firstValue = interpreter.runExpression(scope, first.obj)
        return operations.fold(first.repack(firstValue)) { value, pair ->
          if (!value.obj.supportBinaryOperation(pair.first.obj.codeToken)) {
            throw InterpreterException("Binary Operation ${pair.first.obj.codeToken.name} (at ${pair.first.area()}) not supported on ${value.obj.javaClass.simpleName} at ${value.area()}.")
          }
          val secondValue = interpreter.runExpression(scope, pair.second.obj).let {
            if(it is ReturnValue.PropertiesNFunctionsValue.FieldValue) it.value(interpreter, scope)
            else it
          }
          if (!secondValue.supportBinaryOperation(pair.first.obj.codeToken)) {
            throw InterpreterException("Binary Operation ${pair.first.obj.codeToken.name} (at ${pair.first.area()}) not supported on ${secondValue.javaClass.simpleName} at ${pair.second.area()}.")
          }
          val retVal = value.obj.applyBinaryOp(pair.first.obj.codeToken, secondValue)
          if (retVal == ReturnValue.Unit) throw InterpreterException(
            "Binary Operation ${pair.first.obj.codeToken.name} (at ${pair.first.area()})" +
                    " not supported on ${value.obj.javaClass.simpleName} at ${value.area()} (Left) and " +
                    " ${secondValue.javaClass.simpleName} at ${pair.second.area()} (Right)."
          )
          pair.second.repack(retVal)
        }.obj
      }
    }

    data class UnaryOperation(
      val operation: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<Expression>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        val value = interpreter.runExpression(scope, expr.obj)
        return if (value.supportUnaryOperation(operation.obj.codeToken)) value.applyUnaryOp(operation.obj.codeToken).also {
          if (it !is ReturnValue.Unit) throw InterpreterException("Unsupported Unary Operation ${operation.obj.codeToken.name} for ${value.javaClass.simpleName} at ${expr.area()}.")
        }
        else throw InterpreterException("Unsupported Unary Operation ${operation.obj.codeToken.name} for ${value.javaClass.simpleName} at ${expr.area()}.")
      }
    }

    data class Call(
      val primary: GriddedObject<Expression>,
      val invocations: List<GriddedObject<Invocation>>,
      val calls: List<Pair<GriddedObject<GriddedObject<Token.IdentifierToken>>, List<GriddedObject<Invocation>>>>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
        var ret: ReturnValue = when (val prim = primary.obj) {
          is Primary.IdentifierExpression ->
            if (invocations.isNotEmpty()) scope.findCallable(prim.identifier)
              ?: throw InterpreterException("No function ${prim.identifier} found in scope ${ASTPrinter.objToString(scope)}.")
            else interpreter.runExpression(scope, prim)
          else -> interpreter.runExpression(scope, prim)
        }
        ret = callInvocations(ret, invocations, scope, interpreter)
        if (calls.isNotEmpty()) {
          ret = calls.fold(ret) { retVal, (identifier, calls) ->
            if (retVal !is ReturnValue.PropertiesNFunctionsValue) throw InterpreterException("${ret.javaClass.simpleName} has no properties/functions nor can be called")
            val valScope = retVal.scope(interpreter, scope)
            if (calls.isNotEmpty()) callInvocations(
              valScope.findCallable(identifier.obj.obj.identifier)
                ?: throw InterpreterException("No function ${identifier.obj.obj.identifier} found in scope ${ASTPrinter.objToString(valScope)}."),
              calls, valScope, interpreter
            )
            else valScope.findField(identifier.obj.obj.identifier)
              ?: throw InterpreterException("No field ${identifier.obj.obj.identifier} found in scope ${ASTPrinter.objToString(valScope)}.")
          }
        }
        return ret
      }

      private fun callInvocations(
        ret: ReturnValue,
        invocations: List<GriddedObject<Invocation>>,
        scope: Scope,
        interpreter: Interpreter
      ): ReturnValue {
        return invocations.fold(ret) { retVal, invoc ->
          when (retVal) {
            is ReturnValue.CallableValue -> {
              val invocation = invocations.first()
              invocation.obj.call(retVal, scope, interpreter)
            }
            else -> throw InterpreterException("${retVal.javaClass.simpleName} is not callable for ${invoc.area()}.")
          }
        }
      }
    }

    sealed class Primary : DataExpression() {
      data class DirectValue(val value: Token.ValueToken) : Primary() {
        override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
          return when (value) {
            is Token.ValueToken.LiteralToken -> when (value) {
              is Token.ValueToken.LiteralToken.FloatToken -> ReturnValue.Number.FloatNumber(
                BigDecimal.valueOf(value.first.toLong()).add(BigDecimal.valueOf(value.second.toLong()).movePointLeft(value.second.toString().length))
                  .toDouble()
              )
              is Token.ValueToken.LiteralToken.CharToken -> TODO()
              is Token.ValueToken.LiteralToken.IntegerToken -> ReturnValue.Number.IntegerNumber(value.int.toLong())
              is Token.ValueToken.LiteralToken.StringToken -> ReturnValue.StringValue(value.string)
            }
            is Token.ValueToken.ValueKeywordToken -> when (value.keyword) {
              ValueKeyword.TRUE -> ReturnValue.BooleanValue(true)
              ValueKeyword.FALSE -> ReturnValue.BooleanValue(false)
              ValueKeyword.NULL -> ReturnValue.Null
            }
          }
        }
      }

      data class IdentifierExpression(val identifier: String) : Primary() {
        override fun run(interpreter: Interpreter, scope: Scope): ReturnValue {
          return scope.findField(identifier) ?: scope.findCallable(identifier) ?: throw InterpreterException(
            "No $identifier found in scope: " + ASTPrinter.objToLines(ASTPrinter.convertObjWithFields(scope)).joinToString("\n")
          )
        }
      }
    }
  }

  data class BrokenExpression(val area: Area) : Expression()
}
