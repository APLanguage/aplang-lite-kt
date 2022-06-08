package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.naming.NameResolver
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.GriddedObject

sealed class DataExpression : Expression() {

  protected var type: Namespace.Class? = null

  abstract fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R

  fun <C> type(dataExpressionVisitor: DataExpressionVisitor<C, Namespace.Class>, context: C): Namespace.Class {
    if (type == null) {
      type = visit(dataExpressionVisitor, context)
    }
    return type!!
  }

  fun type() = type ?: throw IllegalStateException("Type was not resolved!")

  data class Assignment(
    val call: GriddedObject<DataExpression>,
    val op: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitAssignment(this, context)
  }

  data class IfExpression(
    val condition: GriddedObject<DataExpression>,
    val thenExpr: GriddedObject<DataExpression>,
    val elseExpr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitIf(this, context)
  }

  data class OopExpression(
    val expr: GriddedObject<DataExpression>,
    val oopOpType: OopOpType,
    var typeToCast: Either<GriddedObject<Type>, Namespace.Class>
  ) : DataExpression() {
    enum class OopOpType {
      AS, IS, IS_NOT
    }

    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitOop(this, context)

    fun typeToCast(nameResolver: NameResolver): Namespace.Class {
      return when (typeToCast) {
        is Either.Left -> {
          typeToCast = typeToCast.handleError { nameResolver.resolveClass(it.obj.path.asString()).first() }
          (typeToCast as Either.Right).value
        }
        is Either.Right -> (typeToCast as Either.Right).value
      }
    }
  }

  data class BinaryOperation(
    val opType: BinaryOpType,
    val first: GriddedObject<DataExpression>,
    val operations: NonEmptyList<Pair<GriddedObject<Token.SignToken>, GriddedObject<DataExpression>>>
  ) : DataExpression() {
    enum class BinaryOpType {
      LOGIC_OR, LOGIC_AND, EQUALITY, COMPARISON, TERM, FACTOR, BIT_OP
    }

    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitBinary(this, context)
  }

  data class UnaryOperation(
    val operation: GriddedObject<Token.SignToken>,
    val expr: GriddedObject<DataExpression>
  ) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitUnary(this, context)
  }

  data class FunctionCall(val identifier: GriddedObject<String>, val arguments: List<GriddedObject<DataExpression>>) : DataExpression() {
    var resolvedFunction: Namespace.Method? = null
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitFunctionCall(this, context)
  }

  data class Call(
    val primary: GriddedObject<DataExpression>,
    var call: Either<GriddedObject<FunctionCall>, Either<GriddedObject<String>, Namespace.Field>>
  ) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitCall(this, context)
  }

  data class DirectValue(val value: Token.ValueToken) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitDirectValue(this, context)
  }

  data class IdentifierExpression(var identifier: GriddedObject<String>) : DataExpression() {
    var resolved: Namespace.Typeable? = null
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitIdentifier(this, context)
  }

  data class PrimitiveHolder(val primitive: PrimitiveType) : DataExpression() {
    override fun <C, R> visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C): R =
      dataExpressionVisitor.visitPrimitive(this, context)
  }
}

inline fun <C, R> GriddedObject<DataExpression>.visit(dataExpressionVisitor: DataExpressionVisitor<C, R>, context: C) =
  obj.visit(dataExpressionVisitor, context)

inline fun <C> GriddedObject<DataExpression>.type(dataExpressionVisitor: DataExpressionVisitor<C, Namespace.Class>, context: C) =
  obj.type(dataExpressionVisitor, context)
