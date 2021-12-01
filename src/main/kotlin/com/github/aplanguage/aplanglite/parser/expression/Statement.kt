package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.utils.GriddedObject

sealed class Statement : Expression() {

  abstract fun <R> visit(visitor: StatementVisitor<R>): R

  data class ForStatement(
    val identifier: GriddedObject<String>,
    var type: Either<GriddedObject<String>, Namespace.Class>,
    val iterableExpr: GriddedObject<DataExpression>,
    val statement: GriddedObject<Statement>
  ) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitFor(this)
  }

  data class ReturnStatement(val expr: GriddedObject<DataExpression>?) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitReturn(this)
  }

  data class DeclarationStatement(val declaration: Declaration) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitDeclaration(this)
  }

  object BreakStatement : Statement() {
    override fun equals(other: Any?): Boolean = other != null && other.javaClass.isInstance(this)

    override fun hashCode(): Int = javaClass.hashCode()

    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitBreak(this)

  }

  data class WhileStatement(val condition: GriddedObject<DataExpression>, val statement: GriddedObject<Statement>?) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitWhile(this)
  }

  data class IfStatement(
    val condition: GriddedObject<DataExpression>,
    val thenStmt: GriddedObject<Statement>,
    val elseStmt: GriddedObject<Statement>?
  ) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitIf(this)
  }

  data class Block(val statements: List<GriddedObject<Statement>>) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitBlock(this)
  }

  data class ExpressionStatement(val expr: DataExpression) : Statement() {
    override fun <R> visit(visitor: StatementVisitor<R>): R = visitor.visitExpression(this)
  }
}
