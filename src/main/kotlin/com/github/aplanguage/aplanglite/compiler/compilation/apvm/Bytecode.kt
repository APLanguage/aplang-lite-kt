package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nel
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.RegisterAllocator.Companion.registerIndex
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
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


fun List<BytecodeChunk>.write(output: WritableByteChannel) {
  forEach { it.write(output) }
}

fun List<BytecodeChunk>.size(): Int {
  return sumOf { it.size }
}

fun List<BytecodeChunk>.instructions(): List<Instruction> {
  return flatMap { it.instructions() }
}
