package com.github.amejonah1200.aplang_lite.parser

import com.github.amejonah1200.aplang_lite.tokenizer.Token
import com.github.amejonah1200.aplang_lite.utils.GriddedObject

sealed class Expression {
  data class Program(val uses: List<GriddedObject<Expression>>, val declarations: List<GriddedObject<Expression>>) : Expression()

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>) : Expression()

  data class UseDeclaration(val path: GriddedObject<Expression>?, val all: Boolean, val asOther: GriddedObject<Token.IdentifierToken>?) : Expression()

  data class VarDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val type: GriddedObject<Type>?,
    val expr: GriddedObject<Expression>?
  ) : Expression()

  data class Type(val path: Path)

  data class FunctionDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val parameters: Map<GriddedObject<Token.IdentifierToken>, GriddedObject<Type>>,
    val type: GriddedObject<Type>?,
    val block: GriddedObject<Block>
  ) : Expression()

  data class ClassDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val superTypes: List<GriddedObject<Type>>,
    val content: GriddedObject<Program>?
  ) : Expression()

  data class ForStatement(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val expr: GriddedObject<Expression>,
    val statement: GriddedObject<Expression>
  ) : Expression()

  data class ReturnStatement(val expr: GriddedObject<Expression>?) : Expression()

  class BreakStatement : Expression() {
    override fun equals(other: Any?): Boolean {
      return other != null && other.javaClass.isInstance(this)
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }

  data class WhileStatement(val expr: GriddedObject<Expression>, val statement: GriddedObject<Expression>?) : Expression()

  data class IfStatement(
    val condition: GriddedObject<Expression>,
    val thenStmt: GriddedObject<Expression>,
    val elseStmt: GriddedObject<Expression>?
  ) : Expression()

  data class Block(val statements: List<GriddedObject<Expression>>) : Expression()

  data class Assignment(
    val call: GriddedObject<Expression>,
    val op: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<Expression>
  ) : Expression()

  data class IfExpression(
    val condition: GriddedObject<Expression>,
    val thenExpr: GriddedObject<Expression>,
    val elseExpr: GriddedObject<Expression>
  ) : Expression()

  data class BinaryOperation(
    val first: GriddedObject<Expression>,
    val ors: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<Expression>>>
  ) : Expression()

  data class UnaryOperation(
    val operation: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<Expression>
  ) : Expression()

  data class Call(
    val primary: GriddedObject<Expression>,
    val invocations: List<GriddedObject<Invocation>>,
    val calls: List<Pair<GriddedObject<GriddedObject<Token.IdentifierToken>>, List<GriddedObject<Invocation>>>>
  ) : Expression()

  sealed class Invocation {
    data class FunctionCall(val arguments: List<GriddedObject<Expression>>) : Invocation()
    data class ArrayCall(val expr: GriddedObject<Expression>) : Invocation()
  }

  sealed class Primary : Expression() {
    data class DirectValue(val value: Token.ValueToken) : Primary()
    data class IdentifierExpression(val identifier: Token.IdentifierToken) : Primary()
  }
}
