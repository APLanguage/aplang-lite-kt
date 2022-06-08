package com.github.aplanguage.aplanglite.parser.expression

import com.github.aplanguage.aplanglite.parser.expression.DataExpression.*
import com.github.aplanguage.aplanglite.parser.expression.Statement.*

interface DataExpressionVisitor<C, out R> {
  fun visitAssignment(assignment: Assignment, context: C): R
  fun visitIf(ifExpr: IfExpression, context: C): R
  fun visitOop(oop: OopExpression, context: C): R
  fun visitBinary(binary: BinaryOperation, context: C): R
  fun visitUnary(unary: UnaryOperation, context: C): R
  fun visitFunctionCall(functionCall: FunctionCall, context: C): R
  fun visitCall(call: Call, context: C): R
  fun visitDirectValue(directValue: DirectValue, context: C): R
  fun visitIdentifier(identifier: IdentifierExpression, context: C): R
  fun visitPrimitive(primitiveHolder: PrimitiveHolder, context: C): R
}

interface StatementVisitor<C, out R> {
  fun visitFor(forStmt: ForStatement, context: C): R
  fun visitReturn(returnStmt: ReturnStatement, context: C): R
  fun visitDeclaration(declarationStmt: DeclarationStatement, context: C): R
  fun visitBreak(breakStmt: BreakStatement, context: C): R
  fun visitWhile(whileStmt: WhileStatement, context: C): R
  fun visitIf(ifStmt: IfStatement, context: C): R
  fun visitBlock(block: Block, context: C): R
  fun visitExpression(expression: ExpressionStatement, context: C): R
}
