package com.github.aplanguage.aplanglite.compiler.typechecking

import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.parser.expression.Declaration
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.parser.expression.StatementVisitor
import com.github.aplanguage.aplanglite.parser.expression.visit

class StatementTypeChecker(val typeChecker: TypeChecker) : StatementVisitor<Unit, PrimitiveType> {
  val frame = typeChecker.nameResolver.frame
  override fun visitFor(forStmt: Statement.ForStatement, context: Unit): PrimitiveType {
    val type = forStmt.iterableExpr.obj.type(typeChecker, context)
    if (!type.isSubclassOf(StandardLibrary.ITERABLE_CLASS)) throw TypeCheckException("Iterable expected, got ${type.path()}", forStmt.iterableExpr.area())
    forStmt.type = forStmt.type.handleError {
      typeChecker.nameResolver.resolveClass(it.obj).firstOrNull() ?: throw TypeCheckException("Class ${it.obj} cannot be resolved", it.area())
    }
    frame.startSection()
    frame.register(forStmt.identifier.obj, forStmt.type.orNull()!!)
    forStmt.statement.visit(this, context)
    frame.endSection()
    return PrimitiveType.VOID
  }

  override fun visitReturn(returnStmt: Statement.ReturnStatement, context: Unit): PrimitiveType {
    return returnStmt.expr?.repack { it.type(typeChecker, context) }?.also {
      if (typeChecker.nameResolver.frame.expectedType != it.obj)
        throw TypeCheckException(
          "Returning ${it.obj.path()} but method return type is ${typeChecker.nameResolver.frame.expectedType}",
          listOf(it.area())
        )
    }?.let { PrimitiveType.ofClass(it.obj) } ?: PrimitiveType.VOID
  }

  override fun visitDeclaration(declarationStmt: Statement.DeclarationStatement, context: Unit): PrimitiveType {
    when (declarationStmt.declaration) {
      is Declaration.VarDeclaration -> {
        declarationStmt.declaration.type = declarationStmt.declaration.type.handleError { providedType ->
          val type = declarationStmt.declaration.expr?.obj?.type(typeChecker, context)
          if (providedType == null) {
            type ?: throw TypeCheckException(
              "Cannot infer type of ${declarationStmt.declaration.identifier.obj}",
              listOf(declarationStmt.declaration.identifier.area())
            )
          } else {
            typeChecker.nameResolver.resolveClass(providedType.obj.path.asString()).firstOrNull()?.also {
              if (type != null && it != type) throw TypeCheckException(
                "Type mismatch: ${it.path()} != ${type.path()}",
                listOf(declarationStmt.declaration.expr.area(), providedType.area())
              )
            } ?: throw TypeCheckException(
              "Cannot resolve type ${providedType.obj.path.asString()}",
              listOf(providedType.area())
            )
          }
        }
        val type = declarationStmt.declaration.type.orNull()!!
        frame.register(declarationStmt.declaration.identifier.obj, type)
        return PrimitiveType.ofClass(type)
      }
      else -> throw IllegalStateException("Unsupported declaration type ${declarationStmt.declaration.javaClass.simpleName}")
    }
  }

  override fun visitBreak(breakStmt: Statement.BreakStatement, context: Unit) = PrimitiveType.VOID

  override fun visitWhile(whileStmt: Statement.WhileStatement, context: Unit): PrimitiveType {
    if (whileStmt.condition.obj.type(typeChecker, context).primitiveType() != PrimitiveType.BOOL) {
      throw TypeCheckException(
        "Condition of while statement must be of type boolean",
        listOf(whileStmt.condition.area())
      )
    }
    frame.startSection()
    whileStmt.statement?.run {
      obj.visit(this@StatementTypeChecker, context)
    }
    frame.endSection()
    return PrimitiveType.VOID
  }

  override fun visitIf(ifStmt: Statement.IfStatement, context: Unit): PrimitiveType {
    if (ifStmt.condition.obj.type(typeChecker, context).primitiveType() != PrimitiveType.BOOL) {
      throw TypeCheckException(
        "Condition must be of type boolean",
        listOf(ifStmt.condition.area())
      )
    }
    ifStmt.thenStmt.visit(this, context)
    ifStmt.elseStmt?.run { visit(this@StatementTypeChecker, context) }
    return PrimitiveType.VOID
  }

  override fun visitBlock(block: Statement.Block, context: Unit): PrimitiveType {
    frame.startSection()
    for (stmt in block.statements) {
      stmt.visit(this, context)
    }
    frame.endSection()
    return PrimitiveType.VOID
  }

  override fun visitExpression(expression: Statement.ExpressionStatement, context: Unit) = expression.expr.type(typeChecker, context).primitiveType()
}
