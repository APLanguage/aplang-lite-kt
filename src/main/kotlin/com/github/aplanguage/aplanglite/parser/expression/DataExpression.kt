package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.NameResolver
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.GriddedObject

sealed class DataExpression : Expression() {

  protected var type: Namespace.Class? = null

  abstract fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R

  fun type(dataExpressionVisitor: DataExpressionVisitor<Namespace.Class>) : Namespace.Class {
    if (type == null) {
      type = visit(dataExpressionVisitor)
    }
    return type!!
  }

  data class Assignment(
    val call: GriddedObject<DataExpression>,
    val op: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitAssignment(this)
  }

  data class IfExpression(
    val condition: GriddedObject<DataExpression>,
    val thenExpr: GriddedObject<DataExpression>,
    val elseExpr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitIf(this)
  }

  data class OopExpression(
    val expr: GriddedObject<DataExpression>,
    val oopOpType: OopOpType,
    var typeToCast: Either<GriddedObject<Type>, Namespace.Class>
  ) : DataExpression() {
    enum class OopOpType {
      AS, IS, IS_NOT
    }

    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitOop(this)

    fun typeToCast(nameResolver: NameResolver): Namespace.Class {
      return when(typeToCast) {
        is Either.Left -> {
          typeToCast = typeToCast.handleError { nameResolver.resolveClass(it.obj.path.asString()).first() }
          (typeToCast as Either.Right).value
        }
        is Either.Right -> (typeToCast as Either.Right).value
      }
    }
  }

  data class BinaryOperation(
    val first: GriddedObject<DataExpression>,
    val operations: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<DataExpression>>>
  ) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitBinary(this)
  }

  data class UnaryOperation(
    val operation: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitUnary(this)
  }

  data class FunctionCall(val identifier: GriddedObject<String>, val arguments: List<GriddedObject<DataExpression>>) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitFunctionCall(this)
  }

  data class Call(
    val primary: GriddedObject<DataExpression>,
    val call: Either<GriddedObject<FunctionCall>, GriddedObject<String>>
  ) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitCall(this)
  }

  data class DirectValue(val value: Token.ValueToken) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitDirectValue(this)
  }

  data class IdentifierExpression(val identifier: GriddedObject<String>) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitIdentifier(this)
  }

  data class PrimitiveHolder(val primitive: PrimitiveType) : DataExpression() {
    override fun <R> visit(dataExpressionVisitor: DataExpressionVisitor<R>): R =
      dataExpressionVisitor.visitPrimitive(this)

  }
}
