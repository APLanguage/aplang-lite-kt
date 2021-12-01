package com.github.aplanguage.aplanglite.compiler.typechecking

import com.github.aplanguage.aplanglite.compiler.NameResolver
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.DataExpression.OopExpression.OopOpType
import com.github.aplanguage.aplanglite.parser.expression.DataExpressionVisitor
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import com.github.aplanguage.aplanglite.utils.allZip

class TypeCheckException(message: String, val areas: List<Area>) : Exception(message) {
  constructor(message: String, vararg areas: Area) : this(message, areas.toList())
}

class TypeChecker(val nameResolver: NameResolver) : DataExpressionVisitor<Namespace.Class> {
  override fun visitAssignment(assignment: DataExpression.Assignment): Namespace.Class {
    val left = assignment.call.obj.type(this)
    val right = assignment.expr.obj.type(this)
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

  override fun visitIf(ifExpr: DataExpression.IfExpression): Namespace.Class {
    val condType = ifExpr.condition.obj.type(this)
    if (PrimitiveType.ofClass(condType) != PrimitiveType.BOOL) {
      throw TypeCheckException("Condition of if expression must be boolean", listOf(ifExpr.condition.area()))
    }
    val thenType = ifExpr.thenExpr.obj.type(this)
    val elseType = ifExpr.elseExpr.obj.type(this)
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

  override fun visitOop(oop: DataExpression.OopExpression): Namespace.Class {
    return when (oop.oopOpType) {
      OopOpType.AS -> {
        val type = oop.expr.obj.type(this)
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
        val asPrimE = PrimitiveType.ofClass(oop.expr.obj.type(this))
        if (asPrimE.isPrimitive() || asPrimE == PrimitiveType.STRING) throw TypeCheckException(
          "you can't use is/is not operator on a clearly primitive type",
          listOf(oop.expr.area())
        )
        PrimitiveType.BOOL.clazz
      }
    }
  }

  override fun visitBinary(binary: DataExpression.BinaryOperation): Namespace.Class {
    return binary.operations.fold(binary.first) { left, (op, right) ->
      val leftType = left.obj.type(this)
      val rightType = right.obj.type(this)
      val opType = PrimitiveType.ofClass(leftType).binary(op.obj.codeToken, PrimitiveType.ofClass(rightType))
      if (opType == PrimitiveType.VOID) {
        throw TypeCheckException(
          "Not applicable: ${PrimitiveType.ofClass(leftType)} (${leftType.path()}) ${op.obj.codeToken.stringRepresentation} ${
            PrimitiveType.ofClass(rightType)
          } (${rightType.path()})", listOf(left.area(), op.area(), right.area())
        )
      }
      GriddedObject.of(left.startCoords(), DataExpression.PrimitiveHolder(opType), right.endCoords())
    }.obj.type(this)
  }

  override fun visitUnary(unary: DataExpression.UnaryOperation): Namespace.Class {
    val exprType = unary.expr.obj.type(this)
    val primitive = PrimitiveType.ofClass(exprType)
    val result = primitive.unary(unary.operation.obj.codeToken)
    if (result != PrimitiveType.VOID) return exprType
    throw TypeCheckException(
      "${unary.operation.obj.codeToken} cannot be applied to ${exprType.path()} ($primitive)",
      listOf(unary.operation.area(), unary.expr.area())
    )
  }

  override fun visitFunctionCall(functionCall: DataExpression.FunctionCall): Namespace.Class =
    visitFunctionCall(functionCall, nameResolver.resolveMethod(functionCall.identifier.obj))

  fun visitFunctionCall(functionCall: DataExpression.FunctionCall, methods: List<Namespace.Method>): Namespace.Class {
    val arguments = functionCall.arguments.map { it.obj.type(this) }
    val applicants = methods.filter { it.parameters.size == arguments.size }.filter { method ->
      method.parameters.allZip(arguments) { (_, paramType), arg ->
        paramType.fold(
          { throw IllegalStateException("Parameter not resolved of ${method.asDescriptorString()}") },
          { paramType ->
            println(method.name)
            println(paramType.primitiveType())
            paramType == PrimitiveType.ANY.clazz || paramType == arg || arg.allSuperClasses().contains(paramType)
          })
      }
    }
    if (applicants.size > 1) {
      throw TypeCheckException(
        "Ambiguous call to ${functionCall.identifier.obj}(${arguments.joinToString { it.path() }})${
          if (arguments.isEmpty()) "" else ": " + arguments.joinToString(", ") { it.path() }
        }!\nFound:\n  ${applicants.joinToString("\n  ") { it.asDescriptorString() }}",
        listOf(functionCall.identifier.area().let {
          if (functionCall.arguments.isEmpty()) it else it.expandTo(functionCall.arguments.last().area().endCoords())
        })
      )
    }
    return applicants.firstOrNull()?.returnType?.let { it.orNull() ?: throw IllegalStateException("Return Type not resolved!") }
      ?: throw TypeCheckException(
        "No method found for ${functionCall.identifier.obj}(${arguments.joinToString { it.path() }})",
        listOf(functionCall.identifier.area().let {
          if (functionCall.arguments.isEmpty()) it else it.expandTo(functionCall.arguments.last().area().endCoords())
        })
      )
  }

  override fun visitCall(call: DataExpression.Call): Namespace.Class {
    return call.primary.obj.type(this).let { prim ->
      call.call.fold({ funcCall ->
        visitFunctionCall(
          funcCall.obj,
          prim.findMethods(funcCall.obj.identifier.obj) + prim.supers.flatMap {
            it.orNull()!!.resolveMethodsInScope(funcCall.obj.identifier.obj)
          })
      }, {
        prim.findFields(it.obj).firstOrNull()?.type() ?: throw TypeCheckException(
          "no such field ${it.obj} in ${prim.path()}",
          listOf(it.area())
        )
      })
    }
  }

  override fun visitDirectValue(directValue: DataExpression.DirectValue): Namespace.Class = directValue.value.asPrimitive().clazz

  override fun visitIdentifier(identifier: DataExpression.IdentifierExpression): Namespace.Class {
    return (nameResolver.resolveLocalVariable(identifier.identifier.obj) ?: nameResolver.resolveField(identifier.identifier.obj))?.type()
      ?: throw TypeCheckException("unknown identifier ${identifier.identifier}", identifier.identifier.area())
  }

  override fun visitPrimitive(primitiveHolder: DataExpression.PrimitiveHolder): Namespace.Class = primitiveHolder.primitive.clazz
}

