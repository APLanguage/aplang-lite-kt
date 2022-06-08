package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.utils.GriddedObject

sealed class Statement : Expression() {

  abstract fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R

  data class ForStatement(
    val identifier: GriddedObject<String>,
    var type: Either<GriddedObject<String>, Namespace.Class>,
    val iterableExpr: GriddedObject<DataExpression>,
    val statement: GriddedObject<Statement>
  ) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitFor(this, context)
  }

  data class ReturnStatement(val expr: GriddedObject<DataExpression>?) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitReturn(this, context)
  }

  data class DeclarationStatement(val declaration: Declaration) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitDeclaration(this, context)
  }

  object BreakStatement : Statement() {
    override fun equals(other: Any?): Boolean = other != null && other.javaClass.isInstance(this)

    override fun hashCode(): Int = javaClass.hashCode()

    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitBreak(this, context)

  }

  data class WhileStatement(val condition: GriddedObject<DataExpression>, val statement: GriddedObject<Statement>?) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitWhile(this, context)
  }

  data class IfStatement(
    val condition: GriddedObject<DataExpression>,
    val thenStmt: GriddedObject<Statement>,
    val elseStmt: GriddedObject<Statement>?
  ) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitIf(this, context)
  }

  data class Block(val statements: List<GriddedObject<Statement>>) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitBlock(this, context)
  }

  data class ExpressionStatement(val expr: DataExpression) : Statement() {
    override fun <C, R> visit(visitor: StatementVisitor<C, R>, context: C): R = visitor.visitExpression(this, context)
  }
}

inline fun <C, R> GriddedObject<Statement>.visit(visitor: StatementVisitor<C, R>, context: C): R = this.obj.visit(visitor, context)
