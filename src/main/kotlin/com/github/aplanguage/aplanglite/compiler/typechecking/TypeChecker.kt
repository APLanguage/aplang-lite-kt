package com.github.aplanguage.aplanglite.compiler.typechecking

import arrow.core.Either
import arrow.core.handleError
import arrow.core.right
import com.github.aplanguage.aplanglite.compiler.naming.NameResolver
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.DataExpression.BinaryOperation.BinaryOpType
import com.github.aplanguage.aplanglite.parser.expression.DataExpression.OopExpression.OopOpType
import com.github.aplanguage.aplanglite.parser.expression.DataExpressionVisitor
import com.github.aplanguage.aplanglite.parser.expression.type
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.tokenizer.ValueKeyword
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import com.github.aplanguage.aplanglite.utils.allZip

class TypeCheckException(message: String, val areas: List<Area>) : Exception(message) {
  constructor(message: String, vararg areas: Area) : this(message, areas.toList())
}

class TypeChecker(val nameResolver: NameResolver) : DataExpressionVisitor<Unit, Namespace.Class> {
  override fun visitAssignment(assignment: DataExpression.Assignment, context: Unit): Namespace.Class {
    val left = assignment.call.type(this, context)
    val right = assignment.expr.type(this, context)
    val tk = assignment.op.obj.codeToken
    if (tk == CodeToken.EQUAL) {
      if (right == left || right.allSuperClasses().contains(left)) return right
      throw TypeCheckException("Type mismatch: ${left.path()} and ${right.path()}", listOf(assignment.call.area(), assignment.expr.area()))
    }
    val leftPrimitive = left.primitiveType()
    val rightPrimitive = right.primitiveType()
    val result = leftPrimitive.binary(tk, rightPrimitive)
    if (result == PrimitiveType.VOID) {
      throw TypeCheckException(
        "Type mismatch: ${left.path()} ($leftPrimitive) ${tk.stringRepresentation} ($rightPrimitive) ${right.path()}",
        listOf(assignment.call.area(), assignment.op.area(), assignment.expr.area())
      )
    }
    return result.clazz
  }

  override fun visitIf(ifExpr: DataExpression.IfExpression, context: Unit): Namespace.Class {
    val condType = ifExpr.condition.type(this, context)
    if (PrimitiveType.ofClass(condType) != PrimitiveType.BOOL) {
      throw TypeCheckException("Condition of if expression must be boolean", listOf(ifExpr.condition.area()))
    }
    val thenType = ifExpr.thenExpr.type(this, context)
    val elseType = ifExpr.elseExpr.type(this, context)
    val matchingType = if (thenType == elseType) {
      thenType
    } else {
      val elseSupers = elseType.allSuperClasses()
      thenType.allSuperClasses().firstOrNull { elseSupers.contains(it) }
    }
    if (matchingType == null) {
      throw TypeCheckException(
        "then and else expression type mismatch, got ${thenType.path()} and ${elseType.path()}",
        listOf(ifExpr.thenExpr.area(), ifExpr.elseExpr.area())
      )
    }
    return matchingType
  }

  override fun visitOop(oop: DataExpression.OopExpression, context: Unit): Namespace.Class {
    return when (oop.oopOpType) {
      OopOpType.AS -> {
        val type = oop.expr.type(this, context)
        val targetType = oop.typeToCast(nameResolver)
        if (targetType != type && !targetType.allSuperClasses().contains(type)) {
          throw TypeCheckException(
            "type mismatch, got ${type.path()} and ${targetType.path()}",
            listOf(oop.expr.area())
          )
        }
        targetType
      }
      OopOpType.IS, OopOpType.IS_NOT -> {
        val asPrimE = PrimitiveType.ofClass(oop.expr.type(this, context))
        if (asPrimE.isPrimitive() || asPrimE == PrimitiveType.STRING) throw TypeCheckException(
          "you can't use is/is not operator on a clearly primitive type",
          listOf(oop.expr.area())
        )
        PrimitiveType.BOOL.clazz
      }
    }
  }

  override fun visitBinary(binary: DataExpression.BinaryOperation, context: Unit): Namespace.Class {
    return binary.operations.fold(binary.first) { left, (op, right) ->
      val leftType = left.type(this, context)
      val rightType = right.type(this, context)
      val opType = leftType.primitiveType().binary(op.obj.codeToken, rightType.primitiveType())
      if (opType == PrimitiveType.VOID) {
        throw TypeCheckException(
          "Not applicable: ${PrimitiveType.ofClass(leftType)} (${leftType.path()}) ${op.obj.codeToken.stringRepresentation} ${
            PrimitiveType.ofClass(rightType)
          } (${rightType.path()})", listOf(left.area(), op.area(), right.area())
        )
      }
      if (binary.opType == BinaryOpType.COMPARISON || binary.opType == BinaryOpType.EQUALITY) right
      else GriddedObject.of(left.startCoords(), DataExpression.PrimitiveHolder(opType), right.endCoords())
    }.let {
      if (binary.opType == BinaryOpType.COMPARISON || binary.opType == BinaryOpType.EQUALITY) PrimitiveType.BOOL.clazz
      else it.type(this, context)
    }
  }

  override fun visitUnary(unary: DataExpression.UnaryOperation, context: Unit): Namespace.Class {
    val exprType = unary.expr.type(this, context)
    val primitive = PrimitiveType.ofClass(exprType)
    val result = primitive.unary(unary.operation.obj.codeToken)
    if (result != PrimitiveType.VOID) return exprType
    throw TypeCheckException(
      "${unary.operation.obj.codeToken} cannot be applied to ${exprType.path()} ($primitive)",
      listOf(unary.operation.area(), unary.expr.area())
    )
  }

  override fun visitFunctionCall(functionCall: DataExpression.FunctionCall, context: Unit): Namespace.Class =
    visitFunctionCall(functionCall, nameResolver.resolveMethod(functionCall.identifier.obj), context)

  fun visitFunctionCall(functionCall: DataExpression.FunctionCall, methods: List<Namespace.Method>, context: Unit): Namespace.Class {
    val arguments = functionCall.arguments.map { it.type(this, context) }
    val applicants = methods.filter { it.parameters.size == arguments.size }.filter { method ->
      method.parameters.allZip(arguments) { (_, paramType), arg ->
        paramType.fold(
          { throw IllegalStateException("Parameter not resolved of ${method.asDescriptorString()}") },
          { paramType -> arg.isSubclassOf(paramType) }
        )
      }
    }
    if (applicants.isEmpty()) {
      throw TypeCheckException(
        "No method found for ${functionCall.identifier.obj}(${arguments.joinToString { it.path() }})",
        listOf(functionCall.identifier.area().let {
          if (functionCall.arguments.isEmpty()) it else it.expandTo(functionCall.arguments.last().area().endCoords())
        })
      )
    } else if (applicants.size > 1) {
      throw TypeCheckException(
        "Ambiguous call to ${functionCall.identifier.obj}(${arguments.joinToString { it.path() }})${
          if (arguments.isEmpty()) "" else ": " + arguments.joinToString(", ") { it.path() }
        }!\nFound:\n  ${applicants.joinToString("\n  ") { it.asDescriptorString() }}",
        listOf(functionCall.identifier.area().let {
          if (functionCall.arguments.isEmpty()) it else it.expandTo(functionCall.arguments.last().area().endCoords())
        })
      )
    }
    functionCall.resolvedFunction = applicants.first()
    return applicants.first().returnType?.let { it.orNull() ?: throw IllegalStateException("Return Type not resolved!") }
      ?: PrimitiveType.UNIT.clazz
  }

  override fun visitCall(call: DataExpression.Call, context: Unit): Namespace.Class {
    return call.primary.type(this, context).let { prim ->
      call.call.fold({ funcCall ->
        visitFunctionCall(
          funcCall.obj,
          prim.findMethods(funcCall.obj.identifier.obj) + prim.supers.flatMap {
            it.orNull()!!.resolveMethodsInScope(funcCall.obj.identifier.obj)
          }, context
        )
      }, {
        val field = it.handleError {
          prim.findFields(it.obj).firstOrNull() ?: throw TypeCheckException(
            "no such field ${it.obj} in ${prim.path()}",
            listOf(it.area())
          )
        }
        call.call = field.right()
        (field as Either.Right).value.type()
      })
    }
  }

  override fun visitDirectValue(directValue: DataExpression.DirectValue, context: Unit): Namespace.Class {
    return if (directValue.value is Token.ValueToken.ValueKeywordToken && directValue.value.keyword == ValueKeyword.THIS)
      nameResolver.namespace as? Namespace.Class ?: throw TypeCheckException("Cannot use 'this' outside of a class")
    else directValue.value.asPrimitive().clazz
  }

  override fun visitIdentifier(identifier: DataExpression.IdentifierExpression, context: Unit): Namespace.Class {
    if (identifier.resolved == null) {
      val (str, area) = identifier.identifier
      identifier.resolved =
        nameResolver.resolveLocalVariable(str) ?: nameResolver.resolveField(str) ?: throw TypeCheckException("unknown identifier $str", area)
    }
    return identifier.resolved!!.type()!!
  }

  override fun visitPrimitive(primitiveHolder: DataExpression.PrimitiveHolder, context: Unit): Namespace.Class = primitiveHolder.primitive.clazz
}

fun <N : Namespace.Typeable> Either<*, N>.type() = orNull()?.type() ?: throw IllegalStateException("Type not resolved!")

