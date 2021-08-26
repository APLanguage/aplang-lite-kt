package com.github.aplanguage.aplanglite.parser

import com.github.aplanguage.aplanglite.interpreter.Interpreter
import com.github.aplanguage.aplanglite.interpreter.InterpreterException
import com.github.aplanguage.aplanglite.interpreter.ReturnValue
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject

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
      val expr: GriddedObject<Expression>,
      val statement: GriddedObject<Expression>
    ) : Statement()

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
          is DataExpression -> expr.run(scope).let {
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

  data class Block(val statements: List<GriddedObject<Expression>>) : Expression()

  sealed class DataExpression : Expression() {

    open fun run(scope: Interpreter.Scope): ReturnValue = ReturnValue.Unit

    data class Assignment(
      val call: GriddedObject<Expression>,
      val op: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<Expression>
    ) : DataExpression()

    data class IfExpression(
      val condition: GriddedObject<Expression>,
      val thenExpr: GriddedObject<Expression>,
      val elseExpr: GriddedObject<Expression>
    ) : DataExpression()

    data class BinaryOperation(
      val first: GriddedObject<Expression>,
      val ors: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<Expression>>>
    ) : DataExpression()

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
      data class DirectValue(val value: Token.ValueToken) : Primary()
      data class IdentifierExpression(val identifier: Token.IdentifierToken) : Primary()
    }
  }

  data class BrokenExpression(val area: Area) : Expression()
}
