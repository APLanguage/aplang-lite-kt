package com.github.aplanguage.aplanglite.parser

import arrow.core.Either
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject


sealed class Expression {
  data class Program(
    val packageDeclaration: GriddedObject<PackageDeclaration>?,
    val uses: List<GriddedObject<Declaration.UseDeclaration>>,
    val vars: List<GriddedObject<Declaration.VarDeclaration>>,
    val functions: List<GriddedObject<Declaration.FunctionDeclaration>>,
    val classes: List<GriddedObject<Declaration.ClassDeclaration>>
  )

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>) {
    fun asString() = identifiers.joinToString(".") { it.obj.identifier }
  }

  data class PackageDeclaration(val path: GriddedObject<Path>)

  data class Type(val path: Path)

  sealed class Declaration : Expression() {
    data class UseDeclaration(val path: GriddedObject<Path>, val all: Boolean, val asOther: GriddedObject<String>?) : Declaration()

    data class VarDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val type: GriddedObject<Type>?,
      val expr: GriddedObject<Expression>?
    ) : Declaration()

    data class FunctionDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val parameters: List<Pair<GriddedObject<String>, GriddedObject<Type>>>,
      val type: GriddedObject<Type>?,
      val code: List<GriddedObject<Statement>>
    ) : Declaration()

    data class ClassDeclaration(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val superTypes: List<GriddedObject<Type>>,
      val uses: List<GriddedObject<UseDeclaration>>,
      val vars: List<GriddedObject<VarDeclaration>>,
      val functions: List<GriddedObject<FunctionDeclaration>>,
      val classes: List<GriddedObject<ClassDeclaration>>
    ) : Declaration() {
      fun asProgram() = Program(null, uses, vars, functions, classes)
    }

    companion object {
      fun Declaration.asStatement() = Statement.DeclarationStatement(this)
    }
  }

  sealed class Statement : Expression() {

    data class ForStatement(
      val identifier: GriddedObject<Token.IdentifierToken>,
      val iterableExpr: GriddedObject<Expression>,
      val statement: GriddedObject<Expression>
    ) : Statement()

    data class ReturnStatement(val expr: GriddedObject<Expression>?) : Statement()

    data class DeclarationStatement(val declaration: Declaration) : Statement()

    class BreakStatement : Statement() {
      override fun equals(other: Any?): Boolean {
        return other != null && other.javaClass.isInstance(this)
      }

      override fun hashCode(): Int {
        return javaClass.hashCode()
      }
    }

    data class WhileStatement(val condition: GriddedObject<Expression>, val statement: GriddedObject<Expression>?) : Statement()

    data class IfStatement(
      val condition: GriddedObject<Expression>,
      val thenStmt: GriddedObject<Expression>,
      val elseStmt: GriddedObject<Expression>?
    ) : Statement()

    data class Block(val statements: List<GriddedObject<Expression>>) : Statement()

    data class ExpressionStatement(val expr: Expression) : Statement()
  }

  sealed class DataExpression : Expression() {

    data class Assignment(
      val call: GriddedObject<DataExpression>,
      val op: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<DataExpression>
    ) : DataExpression()

    data class IfExpression(
      val condition: GriddedObject<DataExpression>,
      val thenExpr: GriddedObject<DataExpression>,
      val elseExpr: GriddedObject<DataExpression>
    ) : DataExpression()

    data class OopExpression(val expr: GriddedObject<DataExpression>, val oopOpType: OopOpType, val type: GriddedObject<Type>) : DataExpression() {
      enum class OopOpType {
        AS, IS, IS_NOT
      }
    }

    data class BinaryOperation(
      val first: GriddedObject<DataExpression>,
      val operations: List<Pair<GriddedObject<Token.SignToken>, GriddedObject<DataExpression>>>
    ) : DataExpression()

    data class UnaryOperation(
      val operation: GriddedObject<Token.SignToken>,
      val expr: GriddedObject<DataExpression>
    ) : DataExpression()

    data class FunctionCall(val identifier: GriddedObject<String>, val arguments: List<GriddedObject<Expression>>) : DataExpression()

    data class Call(
      val primary: GriddedObject<DataExpression>,
      val call: Either<GriddedObject<FunctionCall>, GriddedObject<String>>
    ) : DataExpression()

    data class DirectValue(val value: Token.ValueToken) : DataExpression()
    data class IdentifierExpression(val identifier: String) : DataExpression()
  }

  data class BrokenExpression(val area: Area) : Expression()
}
