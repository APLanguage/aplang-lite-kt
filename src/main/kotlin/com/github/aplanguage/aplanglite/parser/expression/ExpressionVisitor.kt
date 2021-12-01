package com.github.aplanguage.aplanglite.parser.expression

import com.github.aplanguage.aplanglite.parser.expression.DataExpression.*
import com.github.aplanguage.aplanglite.parser.expression.Statement.*

interface DataExpressionVisitor<out R> {
  fun visitAssignment(assignment: Assignment): R
  fun visitIf(ifExpr: IfExpression): R
  fun visitOop(oop: OopExpression): R
  fun visitBinary(binary: BinaryOperation): R
  fun visitUnary(unary: UnaryOperation): R
  fun visitFunctionCall(functionCall: FunctionCall): R
  fun visitCall(call: Call): R
  fun visitDirectValue(directValue: DirectValue): R
  fun visitIdentifier(identifier: IdentifierExpression): R
  fun visitPrimitive(primitiveHolder: PrimitiveHolder): R
}

interface StatementVisitor<out R> {
  fun visitFor(forStmt: ForStatement): R
  fun visitReturn(returnStmt: ReturnStatement): R
  fun visitDeclaration(declarationStmt: DeclarationStatement): R
  fun visitBreak(breakStmt: BreakStatement): R
  fun visitWhile(whileStmt: WhileStatement): R
  fun visitIf(ifStmt: IfStatement): R
  fun visitBlock(block: Block): R
  fun visitExpression(expression: ExpressionStatement): R
}
