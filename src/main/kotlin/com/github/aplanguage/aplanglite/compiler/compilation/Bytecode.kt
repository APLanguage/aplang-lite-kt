package com.github.aplanguage.aplanglite.compiler.compilation

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nel
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction.Constant
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction.InvNegion.InversionDataType.Companion.toInversionDataType
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction.Math.MathOperation
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction.Math.MathOperation.Companion.toMathOperation
import com.github.aplanguage.aplanglite.compiler.bytecode.Instruction.NumberType.Companion.toNumberType
import com.github.aplanguage.aplanglite.compiler.compilation.BytecodeChunk.Companion.chunk
import com.github.aplanguage.aplanglite.compiler.compilation.BytecodeChunk.Companion.instructions
import com.github.aplanguage.aplanglite.compiler.compilation.RegisterAllocator.Companion.registerIndex
import com.github.aplanguage.aplanglite.compiler.compilation.ResultTarget.Companion.target
import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.DataExpression.BinaryOperation.BinaryOpType
import com.github.aplanguage.aplanglite.parser.expression.DataExpressionVisitor
import com.github.aplanguage.aplanglite.parser.expression.Declaration
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.parser.expression.StatementVisitor
import com.github.aplanguage.aplanglite.parser.expression.visit
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.tokenizer.Token.ValueToken.LiteralToken
import com.github.aplanguage.aplanglite.tokenizer.ValueKeyword
import com.github.aplanguage.aplanglite.utils.Area
import java.nio.channels.WritableByteChannel

sealed class ResultTarget {
  abstract fun instructionTarget(frame: Frame): Instruction.Target

  object Stack : ResultTarget() {
    override fun instructionTarget(frame: Frame): Instruction.Target = Instruction.Target.Stack
  }

  class Register(private val register: Either<RegisterAllocator.Register, Int>) : ResultTarget() {
    override fun instructionTarget(frame: Frame): Instruction.Target = Instruction.Target.Register(register.registerIndex().toUByte())
    fun registerId() = register.fold({ it.id }, { it })
  }

  class Field(val field: Namespace.Field, val instance: Either<RegisterAllocator.Register, Int>? = null) : ResultTarget() {
    override fun instructionTarget(frame: Frame): Instruction.Target = Instruction.Target.Field(frame.pool[field].id)
  }

  class AsSettable(var settable: Namespace.Settable? = null) : ResultTarget() {
    override fun instructionTarget(frame: Frame): Instruction.Target =
      throw IllegalStateException("AsSettable should not be used as an instruction target")
  }

  object Discard : ResultTarget() {
    override fun instructionTarget(frame: Frame): Instruction.Target =
      throw IllegalStateException("Discard should not be used as an instruction target")
  }

  companion object {
    inline fun RegisterAllocator.Register.target(): Register = Register(this.left())
    inline fun Namespace.Field.target(instance: Either<RegisterAllocator.Register, Int>? = null) = Field(this, instance)
    inline fun Namespace.Field.target(registerAllocator: RegisterAllocator): Field =
      if (virtualType() == null) target(registerAllocator[0].left()) else target()

    inline fun Namespace.Settable.target(frame: Frame? = null) = when (this) {
      is Namespace.Field -> target()
      is LocalVariable -> target(frame)
      else -> throw IllegalStateException("Settable target not supported")
    }

    inline fun LocalVariable.target(frame: Frame? = null): Register {
      if (register == null && frame != null) register = frame.variable(name)?.register
      return Register(register?.left() ?: throw IllegalStateException("Local variable has no register assigned"))
    }
  }
}

class CompilationException(message: String, val area: Area? = null) : Exception(message)

sealed class BytecodeChunk {
  abstract val size: Int
  abstract fun write(output: WritableByteChannel)
  abstract fun instructions(): NonEmptyList<Instruction>

  abstract operator fun plus(other: BytecodeChunk): BytecodeChunk

  sealed class InstructionsChunk : BytecodeChunk() {
    class SingleInstructionChunk(val instruction: Instruction) : InstructionsChunk() {
      override val size: Int
        get() = instruction.byteSize()

      override fun write(output: WritableByteChannel) {
        output.write(instruction.toByteBuffer())
      }

      override fun instructions(): NonEmptyList<Instruction> = instruction.nel()
      override fun plus(other: BytecodeChunk) =
        if (other == NoOpChunk) this else MultipleInstructionsChunk(NonEmptyList(instruction, other.instructions()))
    }

    class MultipleInstructionsChunk(val instructions: NonEmptyList<Instruction>) : InstructionsChunk() {
      override val size: Int
        get() = instructions.sumOf { it.byteSize() }

      override fun write(output: WritableByteChannel) {
        instructions.forEach { output.write(it.toByteBuffer()) }
      }

      override fun instructions(): NonEmptyList<Instruction> = NonEmptyList.fromListUnsafe(instructions)
      override fun plus(other: BytecodeChunk) = if (other == NoOpChunk) this else MultipleInstructionsChunk(instructions + other.instructions())
    }

  }

  object NoOpChunk : BytecodeChunk() {
    override val size: Int
      get() = 1

    override fun write(output: WritableByteChannel) {}

    override fun instructions(): NonEmptyList<Instruction> = Instruction.NoOp.nel()
    override fun plus(other: BytecodeChunk) = other
  }

  companion object {
    fun instruction(instruction: Instruction): BytecodeChunk = InstructionsChunk.SingleInstructionChunk(instruction)
    fun instructions(instructions: List<Instruction>): BytecodeChunk = if (instructions.isEmpty()) NoOpChunk else
      if (instructions.size == 1) instruction(instructions.first()) else instructions(NonEmptyList.fromListUnsafe(instructions))

    fun instructions(instructions: NonEmptyList<Instruction>): BytecodeChunk = InstructionsChunk.MultipleInstructionsChunk(instructions)
    fun instructions(vararg instructions: Instruction): BytecodeChunk =
      if (instructions.isEmpty()) NoOpChunk else if (instructions.size == 1) instruction(instructions.first()) else instructions(
        NonEmptyList.fromListUnsafe(instructions.toList())
      )

    fun instructions(vararg chunks: BytecodeChunk) = chunks.filter { it != NoOpChunk }.flatMap { it.instructions() }.chunk()
    fun Instruction.chunk(): BytecodeChunk = if (this == Instruction.NoOp) NoOpChunk else instruction(this)
    fun List<Instruction>.chunk(): BytecodeChunk = instructions(this.filter { it != Instruction.NoOp })
    fun NonEmptyList<Instruction>.chunk(): BytecodeChunk = instructions(this.filter { it != Instruction.NoOp })
  }
}


class ExpressionToBytecodeVisitor(private val frame: Frame) : DataExpressionVisitor<ResultTarget, BytecodeChunk>,
  StatementVisitor<ExpressionToBytecodeVisitor.Scope?, BytecodeChunk> {

  class Scope(val startLabel: Instruction.Label, val endLabel: Instruction.Label)

  override fun visitAssignment(assignment: DataExpression.Assignment, context: ResultTarget): BytecodeChunk {
    val asSettable = ResultTarget.AsSettable()
    val ins = assignment.call.visit(this, asSettable).instructions().toMutableList()
    val settable = asSettable.settable ?: throw IllegalStateException("No settable found")
    if (assignment.op.obj.codeToken == CodeToken.EQUAL) {
      ins.addAll(
        assignment.expr.visit(
          this,
          when (settable) {
            is Namespace.Field -> settable.target()
            is LocalVariable -> settable.target(frame)
            else -> throw IllegalStateException("Unexpected settable: $settable")
          }
        ).instructions()
      )
    } else {
      if (!settable.isStatic()) ins.add(Instruction.DuplicateStack(0u))
      if (settable is Namespace.Field) ins.add(Instruction.GetPut.get(frame.pool[settable].id))
      ins.addAll(assignment.expr.visit(this, ResultTarget.Stack).instructions())
      if (settable.type() == PrimitiveType.STRING.clazz) {
        ins.add(
          when (val mathop = assignment.op.obj.codeToken.toMathOperation()) {
            MathOperation.PLUS -> Instruction.Call(
              false, false,
              frame.pool[PrimitiveType.STRING.clazz.findMethods("concat").first()].id
            )
            MathOperation.MULTIPLY -> Instruction.Call(
              false, false,
              frame.pool[PrimitiveType.STRING.clazz.findMethods("repeat")
                .first { it.parameters.first().clazz.orNull() == assignment.expr.obj.type() }].id
            )
            else -> throw IllegalStateException("Unsupported operation $mathop on string")
          }
        )
        if (context != ResultTarget.Discard) ins.add(Instruction.DuplicateStack(0u))
        fromTo(ResultTarget.Stack, settable.target(frame))
        if (context != ResultTarget.Discard) fromTo(ResultTarget.Stack, context)
      }
      if (assignment.op.obj.codeToken == CodeToken.SLASH_EQUAL) {
        TODO("Division equal")
      } else {
        when (settable) {
          is Namespace.Field -> instructions(
            Instruction.Math.stackOp(
              assignment.op.obj.codeToken.toMathOperation(),
              settable.type().primitiveType().toNumberType()
            ).chunk(),
            if (context != ResultTarget.Discard) Instruction.DuplicateStack(0u).chunk()
            else BytecodeChunk.NoOpChunk,
            Instruction.GetPut.put(frame.pool[settable].id).chunk(),
            fromTo(ResultTarget.Stack, context).chunk()
          )
          is LocalVariable -> {
            if (context != ResultTarget.Discard) {
              ins.add(
                Instruction.Math.stackOp(
                  assignment.op.obj.codeToken.toMathOperation(),
                  settable.type().primitiveType().toNumberType()
                )
              )
              ins.add(Instruction.DuplicateStack(0u))
              ins.addAll(fromTo(ResultTarget.Stack, settable.target(frame)))
            } else {
              ins.add(
                Instruction.Math.targetAndStackToTarget(
                  assignment.op.obj.codeToken.toMathOperation(),
                  settable.type().primitiveType().toNumberType(),
                  settable.target(frame),
                  settable.target(frame)
                )
              )
            }

          }
          else -> throw IllegalStateException("Unexpected settable: $settable")
        }
      }
    }

    return ins.chunk()
  }

  override fun visitIf(ifExpr: DataExpression.IfExpression, context: ResultTarget): BytecodeChunk {
    val ins = ifExpr.condition.visit(this, ResultTarget.Stack).instructions().toMutableList()
    val labelEnd = Instruction.Label()
    val labelElse = Instruction.Label()
    val jumps = mutableListOf<Instruction.If>()
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.ZERO, labelElse).also(jumps::add))
    ins.addAll(ifExpr.thenExpr.visit(this, ResultTarget.Stack).instructions())
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, labelEnd).also(jumps::add))
    ins.add(labelElse)
    ins.addAll(ifExpr.elseExpr.visit(this, ResultTarget.Stack).instructions())
    ins.add(labelEnd)
    jumps.asReversed().forEach { it.resolveRelative(ins) }
    return ins.filter { it !is Instruction.Label }.chunk()
  }

  override fun visitOop(oop: DataExpression.OopExpression, context: ResultTarget): BytecodeChunk {
    return NonEmptyList(
      Instruction.Oop(
        oop.oopOpType, frame.pool[oop.typeToCast.orNull() ?: throw IllegalStateException("Type to cast was not resolved")].id
      ), fromTo(ResultTarget.Stack, context)
    ).chunk()
  }

  override fun visitBinary(binary: DataExpression.BinaryOperation, context: ResultTarget): BytecodeChunk {
    val firstType = binary.first.obj.type().primitiveType()
    val ins = binary.first.visit(this, ResultTarget.Stack).instructions().toMutableList()
    if (binary.operations.size > 1 && (binary.opType == BinaryOpType.COMPARISON || binary.opType == BinaryOpType.EQUALITY)) {
      val labelTrue = Instruction.Label()
      val labelFalse = Instruction.Label()
      val jumps = mutableListOf<Instruction.If>()
      binary.operations.foldIndexed(firstType) { i, acc, (op, expr) ->
        val type = expr.obj.type().primitiveType()
        applyBinaryOperation(acc, op.obj.codeToken, expr.obj, ins, binary.operations.lastIndex != i)
        if (binary.operations.lastIndex != i) {
          val jump = Instruction.If.jmpToLabel(Instruction.If.IfCondition.ZERO, labelFalse)
          jumps.add(jump)
          ins.add(jump)
          ins.add(Instruction.SwapStack(0u))
          ins.add(Instruction.PopStack(0u))
        }
        type
      }
      ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, labelTrue).also(jumps::add))
      ins.add(labelFalse)
      ins.add(Constant.DirectInteger(LiteralToken.IntegerToken.U8Token(0u), Instruction.Target.Stack))
      ins.add(labelTrue)
      jumps.asReversed().forEach { it.resolveRelative(ins) }
    } else binary.operations.fold(firstType) { last, (op, expr) ->
      applyBinaryOperation(last.binary(op.obj.codeToken, expr.obj.type().primitiveType()), op.obj.codeToken, expr.obj, ins)
    }
    ins.addAll(fromTo(ResultTarget.Stack, context))
    return ins.filter { it !is Instruction.Label }.chunk()
  }

  private fun applyBinaryOperation(
    result: PrimitiveType,
    op: CodeToken,
    right: DataExpression,
    insStack: MutableList<Instruction>, dup: Boolean = false
  ): PrimitiveType {
    insStack.addAll(right.visit(this, ResultTarget.Stack).instructions())
    if (dup) insStack.add(Instruction.DuplicateStack(2u))
    insStack.add(
      if (op == CodeToken.SLASH) {
        Instruction.Math.stackOp(
          if (result.isInteger()) MathOperation.DIVIDE_INT else MathOperation.DIVIDE,
          result.toNumberType()
        )
      } else if (result == PrimitiveType.STRING) {
        when (op.toMathOperation()) {
          MathOperation.PLUS -> Instruction.Call(
            false, false,
            frame.pool[PrimitiveType.STRING.clazz.findMethods("concat").first()].id
          )
          MathOperation.MULTIPLY -> Instruction.Call(
            false, false,
            frame.pool[PrimitiveType.STRING.clazz.findMethods("repeat")
              .first { it.parameters.first().clazz.orNull() == right.type() }].id
          )
          else -> throw IllegalStateException("Unsupported operation $op on string")
        }
      } else Instruction.Math.stackOp(op.toMathOperation(), result.toNumberType())
    )
    return result
  }

  override fun visitUnary(unary: DataExpression.UnaryOperation, context: ResultTarget): BytecodeChunk {
    val type = unary.type().primitiveType()
    return (unary.visit(this, ResultTarget.Stack).instructions() + when (unary.operation.obj.codeToken) {
      CodeToken.TILDE -> {
        if (!type.isInteger()) throw IllegalStateException("Tilde operation can only be applied to integer types")
        Instruction.InvNegion(false, type.toInversionDataType())
      }
      CodeToken.BANG -> {
        if (type != PrimitiveType.BOOL) throw IllegalStateException("Bang operation can only be applied to boolean types")
        Instruction.InvNegion(true, Instruction.InvNegion.InversionDataType.BOOL)
      }
      CodeToken.MINUS -> {
        if (type !in PrimitiveType.NUMERICS) throw IllegalStateException("Minus operation can only be applied to number types")
        Instruction.InvNegion(true, type.toInversionDataType())
      }
      else -> throw IllegalStateException("Unary operation not supported with code token ${unary.operation.obj.codeToken.name}")
    }.nel() + fromTo(ResultTarget.Stack, context)).chunk()
  }

  override fun visitFunctionCall(functionCall: DataExpression.FunctionCall, context: ResultTarget): BytecodeChunk {
    functionCall.resolvedFunction?.let { method ->
      if (method.parent is Namespace.Class) {
        Instruction.LoadStore.load(0u)
      }
    }
    return visitOnlyFunctionCall(functionCall, context)
  }

  override fun visitCall(call: DataExpression.Call, context: ResultTarget): BytecodeChunk {
    return call.primary.visit(this, ResultTarget.Stack) + call.call.fold({ (funcCall) ->
      visitOnlyFunctionCall(funcCall, context)
    }, {
      val field = it.orNull() ?: throw IllegalStateException("Field not resolved")
      when (context) {
        is ResultTarget.AsSettable -> {
          context.settable = field
          BytecodeChunk.NoOpChunk
        }
        else -> fromTo(ResultTarget.Field(field), context).chunk()
      }
    })
  }

  private fun visitOnlyFunctionCall(functionCall: DataExpression.FunctionCall, context: ResultTarget): BytecodeChunk {
    val method = functionCall.resolvedFunction ?: throw IllegalStateException("Function not resolved")
    return (functionCall.arguments.flatMap { it.visit(this, ResultTarget.Stack).instructions() } +
            Instruction.Call(method.parent !is Namespace.Class, context == ResultTarget.Discard, frame.pool[method].id)).chunk() +
            if (context == ResultTarget.Discard) BytecodeChunk.NoOpChunk else fromTo(ResultTarget.Stack, context).chunk()

  }

  override fun visitDirectValue(directValue: DataExpression.DirectValue, context: ResultTarget): BytecodeChunk {
    return when (val tk = directValue.value) {
      is LiteralToken.FloatToken -> Constant.DirectFloat(tk, context.instructionTarget(frame))
      is LiteralToken.IntegerToken -> Constant.DirectInteger(tk, context.instructionTarget(frame))
      is LiteralToken.StringToken -> Constant.Indirect(frame.pool[tk.string].id, context.instructionTarget(frame))
      is LiteralToken.CharToken -> Constant.DirectInteger(
        LiteralToken.IntegerToken.U32Token(tk.toChar().code.toUInt()),
        context.instructionTarget(frame)
      )
      is Token.ValueToken.ValueKeywordToken -> when (tk.keyword) {
        ValueKeyword.TRUE -> Constant.DirectInteger(LiteralToken.IntegerToken.U8Token(1u), context.instructionTarget(frame))
        ValueKeyword.FALSE -> Constant.DirectInteger(LiteralToken.IntegerToken.U8Token(0u), context.instructionTarget(frame))
        else -> throw IllegalArgumentException("Unknown value keyword: ${tk.keyword}")
      }
    }.chunk() + fromTo(ResultTarget.Stack, context).chunk()
  }

  override fun visitIdentifier(identifier: DataExpression.IdentifierExpression, context: ResultTarget): BytecodeChunk {
    val typeable = identifier.resolved ?: throw IllegalStateException("Identifier not resolved")
    println(identifier.identifier.obj + " resolved to " + typeable)
    return if (context is ResultTarget.AsSettable) {
      context.settable = typeable as? Namespace.Settable
        ?: throw IllegalStateException("Unsupported identifier type ${typeable.javaClass.simpleName}, supported types are: Namespace.Field and LocalVariable.")
      BytecodeChunk.NoOpChunk
    } else when (typeable) {
      is Namespace.Field -> fromTo(ResultTarget.Field(typeable), context).chunk()
      is LocalVariable -> fromTo(
        frame.variable(identifier.identifier.obj)?.target() ?: throw CompilationException(
          "Local variable ${identifier.identifier.obj} not found", identifier.identifier.area()
        ), context
      ).chunk()
      else -> throw IllegalStateException("Unsupported identifier type ${typeable.javaClass.simpleName}, supported types are: Namespace.Field and LocalVariable.")
    }
  }

  override fun visitPrimitive(primitiveHolder: DataExpression.PrimitiveHolder, context: ResultTarget): BytecodeChunk {
    throw IllegalStateException("Primitive expressions cannot be compiled to bytecode")
  }

  private fun fromTo(from: ResultTarget, to: ResultTarget): List<Instruction> {
    return when (from) {
      is ResultTarget.Field -> {
        when (to) {
          is ResultTarget.Field -> NonEmptyList(
            Instruction.GetPut.get(frame.pool[from.field].id),
            Instruction.GetPut.put(frame.pool[to.field].id).nel()
          )
          is ResultTarget.Register -> Instruction.GetPut.get(frame.pool[from.field].id, to.registerId().toUByte()).nel()
          ResultTarget.Stack -> Instruction.GetPut.get(frame.pool[from.field].id).nel()
          is ResultTarget.AsSettable -> throw IllegalArgumentException("Cannot assign to an AsSettable")
          is ResultTarget.Discard -> listOf()
        }
      }
      is ResultTarget.Register -> when (to) {
        is ResultTarget.Field -> Instruction.GetPut.put(frame.pool[to.field].id, from.registerId().toUByte()).nel()
        is ResultTarget.Register -> NonEmptyList(
          Instruction.LoadStore.load(from.registerId().toUByte()),
          Instruction.LoadStore.store(to.registerId().toUByte()).nel()
        )
        ResultTarget.Stack -> Instruction.LoadStore.load(from.registerId().toUByte()).nel()
        is ResultTarget.AsSettable -> throw IllegalArgumentException("Cannot assign to an AsSettable")
        is ResultTarget.Discard -> listOf()
      }
      ResultTarget.Stack -> when (to) {
        is ResultTarget.Field -> Instruction.GetPut.put(frame.pool[to.field].id, true).nel()
        is ResultTarget.Register -> Instruction.LoadStore.store(to.registerId().toUByte()).nel()
        ResultTarget.Stack -> listOf()
        is ResultTarget.AsSettable -> throw IllegalArgumentException("Cannot assign to an AsSettable")
        is ResultTarget.Discard -> Instruction.PopStack(0u).nel()
      }
      is ResultTarget.AsSettable -> throw IllegalArgumentException("Cannot get a value from an AsSettable")
      is ResultTarget.Discard -> throw IllegalArgumentException("Cannot get a value from discarding")
    }
  }

  override fun visitFor(forStmt: Statement.ForStatement, context: Scope?): BytecodeChunk {
    val startLabel = Instruction.Label()
    val endLabel = Instruction.Label()
    val ins = mutableListOf<Instruction>()
    frame.enterScope()
    val iterableRegister = frame.registerAllocator.register(RegisterAllocator.Type.REFERENCE)
    ins.addAll(forStmt.iterableExpr.obj.visit(this, iterableRegister.target()).instructions())
    ins.add(startLabel)
    ins.add(Instruction.LoadStore.load(iterableRegister.id.toUByte()))
    ins.add(Instruction.Call(false, false, frame.pool[StandardLibrary.ITERABLE_CLASS.findMethods("hasNext").first()].id))
    val jumps = mutableListOf<Instruction.If>()
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.ZERO, endLabel).also(jumps::add))
    frame.enterScope()
    val entryLocal = frame.register(
      forStmt.identifier.obj, forStmt.type.orNull() ?: throw CompilationException(
        "Cannot infer type of ${forStmt.identifier.obj}", forStmt.identifier.area()
      )
    )
    ins.add(Instruction.LoadStore.load(iterableRegister.id.toUByte()))
    ins.add(Instruction.Call(false, false, frame.pool[StandardLibrary.ITERABLE_CLASS.findMethods("next").first()].id))
    ins.add(Instruction.DuplicateStack(0u))
    ins.add(
      Instruction.Oop(
        DataExpression.OopExpression.OopOpType.AS,
        frame.pool[forStmt.type.orNull() ?: throw IllegalStateException("Type of for was not resolved")].id
      )
    )
    ins.add(Instruction.LoadStore.store(entryLocal.register!!.id.toUByte()))
    ins.addAll(forStmt.statement.visit(this, Scope(startLabel, endLabel)).instructions())
    frame.leaveScope()
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, startLabel).also(jumps::add))
    ins.add(endLabel)
    frame.leaveScope()
    jumps.asReversed().forEach { it.resolveRelative(ins) }
    return ins.filter { it !is Instruction.Label }.chunk()
  }

  override fun visitReturn(returnStmt: Statement.ReturnStatement, context: Scope?): BytecodeChunk {

    return (returnStmt.expr?.obj?.visit(this, ResultTarget.Stack) ?: BytecodeChunk.NoOpChunk) + Instruction.Return(false, 0u).chunk()
  }

  override fun visitDeclaration(declarationStmt: Statement.DeclarationStatement, context: Scope?): BytecodeChunk {
    val varDeclaration =
      declarationStmt.declaration as? Declaration.VarDeclaration ?: throw IllegalArgumentException("Declaration must be a variable declaration")
    val local = frame.register(
      varDeclaration.identifier.obj,
      varDeclaration.type.orNull() ?: throw IllegalStateException("Type of declaration was not resolved")
    )
    println(varDeclaration.expr?.obj?.javaClass?.simpleName)
    return varDeclaration.expr?.obj?.visit(
      this, local.target()
    ) ?: BytecodeChunk.NoOpChunk
  }

  override fun visitBreak(breakStmt: Statement.BreakStatement, context: Scope?): BytecodeChunk {
    if (context == null) {
      throw CompilationException("Break statement outside of loop")
    }
    return Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, context.endLabel).chunk()
  }

  override fun visitWhile(whileStmt: Statement.WhileStatement, context: Scope?): BytecodeChunk {
    val labelStart = Instruction.Label()
    val labelEnd = Instruction.Label()
    val ins = mutableListOf<Instruction>(labelStart)
    ins.addAll(whileStmt.condition.visit(this, ResultTarget.Stack).instructions())
    val jumps = mutableListOf<Instruction.If>()
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.ZERO, labelEnd).also(jumps::add))
    if (whileStmt.statement != null) {
      frame.enterScope()
      ins.addAll(
        whileStmt.statement.visit(this, Scope(labelStart, labelEnd)).instructions().also { jumps.addAll(it.filterIsInstance<Instruction.If>()) }
      )
      frame.leaveScope()
    }
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, labelStart).also(jumps::add))
    ins.add(labelEnd)
    jumps.asReversed().forEach { it.resolveRelative(ins) }
    return ins.filter { it !is Instruction.Label }.chunk()
  }

  override fun visitIf(ifStmt: Statement.IfStatement, context: Scope?): BytecodeChunk {
    val ins = ifStmt.condition.visit(this, ResultTarget.Stack).instructions().toMutableList()
    val labelEnd = Instruction.Label()
    val labelElse = Instruction.Label()
    val jumps = mutableListOf<Instruction.If>()
    ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.ZERO, if (ifStmt.elseStmt != null) labelElse else labelEnd).also(jumps::add))
    frame.enterScope()
    ins.addAll(ifStmt.thenStmt.visit(this, null).instructions())
    frame.leaveScope()
    if (ifStmt.elseStmt != null) {
      ins.add(Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, labelEnd).also(jumps::add))
      ins.add(labelElse)
      frame.enterScope()
      ins.addAll(ifStmt.elseStmt.visit(this, null).instructions())
      frame.leaveScope()
    }
    ins.add(labelEnd)
    jumps.asReversed().forEach { it.resolveRelative(ins) }
    return ins.filter { it !is Instruction.Label }.chunk()
  }

  override fun visitBlock(block: Statement.Block, context: Scope?): BytecodeChunk {
    frame.enterScope()
    return block.statements.map {
      if (it.obj == Statement.BreakStatement) {
        if (context == null) throw CompilationException("Break statement outside of loop.", it.area())
        Instruction.If.jmpToLabel(Instruction.If.IfCondition.JUMP_RELATIVE, context.endLabel).chunk()
      } else it.obj.visit(this, context)
    }.also {
      frame.leaveScope()
    }.reduce { acc, chunk -> acc + chunk }
  }

  override fun visitExpression(expression: Statement.ExpressionStatement, context: Scope?): BytecodeChunk {
    return expression.expr.visit(this, ResultTarget.Discard)
  }

}

fun List<BytecodeChunk>.write(output: WritableByteChannel) {
  forEach { it.write(output) }
}

fun List<BytecodeChunk>.size(): Int {
  return sumOf { it.size }
}

fun List<BytecodeChunk>.instructions(): List<Instruction> {
  return flatMap { it.instructions() }
}
