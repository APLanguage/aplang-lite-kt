package com.github.aplanguage.aplanglite.parser

import com.github.aplanguage.aplanglite.interpreter.Interpreter
import com.github.aplanguage.aplanglite.interpreter.InterpreterException
import com.github.aplanguage.aplanglite.interpreter.ReturnValue
import com.github.aplanguage.aplanglite.interpreter.Structure
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.tokenizer.ValueKeyword
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import java.math.BigDecimal

sealed class Expression {
  data class Program(val uses: List<GriddedObject<Expression>>, val declarations: List<GriddedObject<Expression>>) : Expression()

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>)

  data class Type(val path: Path)

  sealed class Invocation {
    data class FunctionCall(val arguments: List<GriddedObject<Expression>>) : Invocation()
    data class ArrayCall(val expr: GriddedObject<Expression>) : Invocation()
  }

  sealed class Declaration : Expression() {
    data class UseDeclaration(val path: GriddedObject<Path>, val all: Boolean, val asOther: GriddedObject<Token.IdentifierToken>?) : Declaration()

    data class VarDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val type: GriddedObject<Type>?,
      val expr: GriddedObject<Expression>?
    ) : Declaration()


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

    open fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue = ReturnValue.Unit

    data class ForStatement(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val iterableExpr: GriddedObject<Expression>,
      val statement: GriddedObject<Expression>
    ) : Statement() {
      override fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
        val iterableValue = when (val expr = iterableExpr.obj) {
          is BrokenExpression -> throw InterpreterException("Cannot execute broken at ${iterableExpr.area()}.")
          else -> when (val iterableValue = interpreter.runExpression(scope, expr)) {
            is ReturnValue.IterableValue -> iterableValue
            else -> throw InterpreterException("No ${iterableExpr.obj.javaClass.simpleName} as iterable allowed at ${iterableExpr.area()}.")
          }
        }
        for (obj in iterableValue.iterable) {
          interpreter.runExpression(
            Interpreter.Scope(
              mutableMapOf(
                identifier.obj.identifier to Structure.VarStructure(
                  identifier.obj.identifier,
                  null, null, obj
                )
              )
            ), statement.obj
          )
        }
        return ReturnValue.Unit
      }
    }

    data class ReturnStatement(val expr: GriddedObject<Expression>?) : Statement()

    class BreakStatement : Statement() {
      override fun equals(other: Any?): Boolean {
        return other != null && other.javaClass.isInstance(this)
      }

      override fun hashCode(): Int {
        return javaClass.hashCode()
      }
    }

    data class WhileStatement(val expr: GriddedObject<Expression>, val statement: GriddedObject<Expression>?) : Statement()

    data class IfStatement(
      val condition: GriddedObject<Expression>,
      val thenStmt: GriddedObject<Expression>,
      val elseStmt: GriddedObject<Expression>?
    ) : Statement() {
      override fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
        val conditionValue: ReturnValue.BooleanValue = when (val expr = condition.obj) {
          is BrokenExpression -> throw InterpreterException("Cannot execute broken at ${condition.area()}.")
          is DataExpression -> expr.run(interpreter, scope).let {
            if (it is ReturnValue.BooleanValue) it
            else throw InterpreterException("Expected BooleanValue, got ${it.javaClass.simpleName} with value: $it")
          }
          else -> throw InterpreterException("No ${condition.obj.javaClass.simpleName} as condition allowed at ${condition.area()}.")
        }
        if (conditionValue.boolean) {
          interpreter.runExpression(Interpreter.Scope(mutableMapOf(), scope), thenStmt.obj)
        } else if (elseStmt != null) {
          interpreter.runExpression(Interpreter.Scope(mutableMapOf(), scope), elseStmt.obj)
        }
        return ReturnValue.Unit
      }
    }
  }

  data class Block(val statements: List<GriddedObject<Expression>>) : Expression() {
    fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
      var returnValue: ReturnValue = ReturnValue.Unit
      val blockScope = Interpreter.Scope(mutableMapOf(), scope)
      for (statement in statements) {
        returnValue = interpreter.runExpression(blockScope, statement.obj)
      }
      return returnValue
    }
  }

  sealed class DataExpression : Expression() {

    open fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue = ReturnValue.Unit

    data class Assignment(
      val call: GriddedObject<Expression>,
      val op: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<Expression>
    ) : DataExpression()

    data class IfExpression(
      val condition: GriddedObject<Expression>,
      val thenExpr: GriddedObject<Expression>,
      val elseExpr: GriddedObject<Expression>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
        val conditionValue: ReturnValue.BooleanValue = when (val expr = condition.obj) {
          is BrokenExpression -> throw InterpreterException("Cannot execute broken at ${condition.area()}.")
          is DataExpression -> expr.run(interpreter, scope).let {
            if (it is ReturnValue.BooleanValue) it
            else throw InterpreterException("Expected BooleanValue, got ${it.javaClass.simpleName} with value: $it")
          }
          else -> throw InterpreterException("No ${condition.obj.javaClass.simpleName} as condition allowed at ${condition.area()}.")
        }
        return interpreter.runExpression(Interpreter.Scope(mutableMapOf(), scope), if (conditionValue.boolean) thenExpr.obj else elseExpr.obj)
      }
    }

    data class BinaryOperation(
      val first: GriddedObject<Expression>,
      val operations: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<Expression>>>
    ) : DataExpression() {
      override fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
        val firstValue = interpreter.runExpression(scope, first.obj)
        return operations.fold(first.repack(firstValue)) { value, pair ->
          if (!value.obj.supportBinaryOperation(pair.first.obj.codeToken)) {
            throw InterpreterException("Binary Operation ${pair.first.obj.codeToken.name} (at ${pair.first.area()}) not supported on ${value.obj.javaClass.simpleName} at ${value.area()}.")
          }
          val secondValue = interpreter.runExpression(scope, pair.second.obj)
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
    ) : DataExpression()

    data class Call(
      val primary: GriddedObject<Expression>,
      val invocations: List<GriddedObject<Invocation>>,
      val calls: List<Pair<GriddedObject<GriddedObject<Token.IdentifierToken>>, List<GriddedObject<Invocation>>>>
    ) : DataExpression()

    sealed class Primary : DataExpression() {
      data class DirectValue(val value: Token.ValueToken) : Primary() {
        override fun run(interpreter: Interpreter, scope: Interpreter.Scope): ReturnValue {
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
      data class IdentifierExpression(val identifier: String) : Primary()
    }
  }

  data class BrokenExpression(val area: Area) : Expression()
}
