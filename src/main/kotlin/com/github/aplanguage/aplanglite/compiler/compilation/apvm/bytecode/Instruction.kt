package com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ResultTarget
import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Token.ValueToken.LiteralToken
import com.github.aplanguage.aplanglite.utils.ByteBufferable
import com.github.aplanguage.aplanglite.utils.nextBit
import com.github.aplanguage.aplanglite.utils.plus
import com.github.aplanguage.aplanglite.utils.put
import com.github.aplanguage.aplanglite.utils.putShort
import com.github.aplanguage.aplanglite.utils.ubyte
import com.github.aplanguage.aplanglite.utils.ushort
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import kotlin.experimental.or

sealed class Instruction : ByteBufferable {

  sealed class Target {
    abstract val id: UByte

    object Stack : Target() {
      override val id: UByte = 0u
    }

    data class Register(val index: UByte) : Target() {
      override val id: UByte = 1u
    }

    data class Field(val index: UShort) : Target() {
      override val id: UByte = if (index > 0xFFu) 2u else 3u
    }
  }

  enum class NumberType {
    U8, U16, U32, U64, I8, I16, I32, I64, Float, Double;

    companion object {
      fun PrimitiveType.toNumberType(): NumberType = when (this) {
        PrimitiveType.U8 -> U8
        PrimitiveType.U16 -> U16
        PrimitiveType.U32 -> U32
        PrimitiveType.U64 -> U64
        PrimitiveType.I8 -> I8
        PrimitiveType.I16 -> I16
        PrimitiveType.I32 -> I32
        PrimitiveType.I64 -> I64
        PrimitiveType.F32, PrimitiveType.FLOAT -> Float
        PrimitiveType.F64, PrimitiveType.DOUBLE -> Double
        PrimitiveType.BOOL -> U8
        else -> throw IllegalArgumentException("Cannot convert ${this.name} to NumberType")
      }
    }
  }

  abstract fun byteSize(): Int
  abstract fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R

  object NoOp : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(1)

    override fun byteSize() = 1
  }

  data class Call(val static: Boolean, val ignore: Boolean, val index: UShort) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() =
      ByteBuffer.allocate(if (index > 0xFFu) 3 else 2).apply {
        put(0b0001 nextBit static nextBit ignore nextBit (index > 0xFFu) nextBit false)
        if (index > 0xFFu) putShort(index)
        else put(index.toByte())
      }.flip()

    override fun byteSize() = if (index > 0xFFu) 3 else 2

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): Call {
        val wide = id and 0b0010 != 0
        return Call(
          id and 0b1000 != 0, id and 0b0100 != 0, if (wide) channel.ushort()!!
          else channel.ubyte()!!.toUShort()
        )
      }
    }
  }

  /**
   * PPPP T--- XXXX XXXX
   * -: General purpose bits, VM must ignore them.
   * If the function returns a value:
   * @param target T: Stack(0)/Register(1)
   * @param index X: If T set, this is the index of the register to return.
   * If T == 0 or function has no return value, X can be used for general purpose and must be ignored by the VM.
   */
  data class Return(val target: Boolean, val index: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(2).put(if (target) 0b00101000 else 0b00100000).put(index).flip()

    override fun byteSize() = 2
  }

  data class If(val condition: IfCondition, var location: UShort) : Instruction() {
    val wide: Boolean
      get() = if (condition == IfCondition.JUMP_RELATIVE) location.toShort().let { it > Byte.MAX_VALUE || it < Byte.MIN_VALUE } else location > 0xFFu
    var label: Label? = null
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)

    enum class IfCondition {
      JUMP_ABSOLUTE, MINUS_ONE, ZERO, ZERO_MINUS_ONE, ONE, ONE_MINUS_ONE, ONE_ZERO, JUMP_RELATIVE
    }

    override fun toByteBuffer() =
      ByteBuffer.allocate(if (wide) 3 else 2).put(((if (wide) 0b00111000 else 0b00110000) or condition.ordinal).toByte())
        .apply {
          if (wide) putShort(location)
          else put(location.toByte())
        }.flip()

    override fun byteSize() = if (wide) 3 else 2
    fun resolveRelative(ins: List<Instruction>) {
      if (label == null) throw IllegalStateException("Label not set")
      val labelIndex = ins.indexOf(this.label!!)
      if (labelIndex == -1) throw IllegalStateException("Label not found")
      val selfIndex = ins.indexOfFirst { it === this }
      location = if (selfIndex < labelIndex) {
        ins.subList(selfIndex + 1, labelIndex).sumOf(Instruction::byteSize).toUShort()
      } else {
        (-ins.subList(labelIndex + 1, selfIndex).sumOf(Instruction::byteSize)).toUShort()
      }
    }

    companion object {
      @JvmStatic
      fun jmpToLabel(condition: IfCondition, label: Label): If {
        return If(condition, 0u).apply { this.label = label }
      }

      @JvmStatic
      fun read(id: Int, channel: ReadableByteChannel): If {
        val wide = id and 0b1000 != 0
        return If(IfCondition.values()[id and 0b111], if (wide) channel.ushort()!! else channel.ubyte()!!.toUShort())
      }
    }
  }

  /**
   * Inversion, Negation
   * PPPP TDDD XXXX XXXX
   * P: Instruction Id
   * @param mode M: Mode: Inversion(0)/Negation(1)
   * Inversion: bitwise NOT
   * Negation: Boolean: negation (!), Number: multiply by -1 (-)
   * @param index X: Register-Index
   * @param dataType DDD:
   *   000 - 8 bit int
   *   001 - 16 bit int
   *   010 - 32 bit int
   *   011 - 64 bit int
   *   100 - 32 bit float
   *   101 - 64 bit float
   *   110 - boolean // a boolean is a byte which has either 1 (true) or 0 (false) as value, effectivly (~b & 0b1).
   */
  data class InvNegion(val mode: Boolean, val dataType: InversionDataType) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    enum class InversionDataType {
      I8, I16, I32, I64, F32, F64, BOOL;

      companion object {
        fun PrimitiveType.toInversionDataType() = when (this) {
          PrimitiveType.I8, PrimitiveType.U8 -> I8
          PrimitiveType.I16, PrimitiveType.U16 -> I16
          PrimitiveType.I32, PrimitiveType.U32 -> I32
          PrimitiveType.I64, PrimitiveType.U64 -> I64
          PrimitiveType.F32, PrimitiveType.FLOAT -> F32
          PrimitiveType.F64, PrimitiveType.DOUBLE -> F64
          PrimitiveType.BOOL -> BOOL
          else -> throw IllegalArgumentException("Unknown primitive type for conversion: ${this.name}")
        }
      }
    }

    override fun toByteBuffer() = ByteBuffer.allocate(2).put(((if (mode) 0b01001000 else 0b01000000) or dataType.ordinal).toByte()).flip()

    override fun byteSize() = 1
  }


  /**
   * PPPP T--- AAAA BBBB
   * A/B:
   *   0000 - U8
   *   0001 - U16
   *   0010 - U32
   *   0011 - U64
   *   0100 - I8
   *   0101 - I16
   *   0110 - I32
   *   0111 - I64
   *   1000 - Float
   *   1001 - Double
   * @param target T: Target: Stack(0)/Register(1)
   * If T set, next byte will be the register index
   */
  data class Conversion(val target: Boolean, val fromType: NumberType, val toType: NumberType, val index: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() =
      ByteBuffer.allocate(if (target) 3 else 2).put(if (target) 0b01011000 else 0b01010000).put(fromType.ordinal shl 4 or toType.ordinal).apply {
        if (target) put(index)
        flip()
      }

    override fun byteSize() = if (target) 3 else 2

    companion object {

      fun stack(fromType: NumberType, toType: NumberType) = Conversion(false, fromType, toType, 0u)
      fun register(fromType: NumberType, toType: NumberType, index: UByte) = Conversion(true, fromType, toType, index)

      fun read(id: Int, channel: ReadableByteChannel): Conversion {
        val target = id and 0b1000 != 0
        val types = channel.ubyte()!!.toInt()
        return Conversion(
          target, NumberType.values()[types and 0xF0 shr 4], NumberType.values()[types and 0x0F], if (target) channel.ubyte()!! else 0u
        )
      }
    }
  }

  data class Math(
    val operation: MathOperation,
    val firstOperandRegister: Boolean,
    val secondOperandRegister: Boolean,
    val targetRegister: Boolean,
    val firstOperandIndex: UByte,
    val secondOperandIndex: UByte,
    val targetIndex: UByte,
    val type: NumberType
  ) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)

    enum class MathOperation(val id: Int) {
      PLUS(0b00000), MINUS(0b00001), MULTIPLY(0b00010), POWER(0b00011), DIVIDE(0b00100), DIVIDE_INT(0b00101), REMAINDER(0b00110), BIT_OR(0b01000), BIT_AND(
        0b01001
      ),
      BIT_XOR(0b01010), BIT_SHIFT_RIGHT(0b01011), BIT_SHIFT_RIGHT_UNSIGNED(0b01100), BIT_SHIFT_LEFT(0b01101), LOGIC_OR(0b10000), LOGIC_AND(0b10001), EQUAL(
        0b10010
      ),
      NOT_EQUAL(0b10011), LESS(0b11000), LESS_EQUAL(0b11001), GREATER(0b11010), GREATER_EQUAL(0b11011);

      fun invert() = when (this) {
        EQUAL -> NOT_EQUAL
        NOT_EQUAL -> EQUAL
        LESS -> GREATER_EQUAL
        LESS_EQUAL -> GREATER
        GREATER -> LESS_EQUAL
        GREATER_EQUAL -> LESS
        else -> throw IllegalStateException("Math operation ${this.name} is nor a comparison nor a equality operation!")
      }

      companion object {
        fun CodeToken.toMathOperation() = when (this) {
          CodeToken.PLUS, CodeToken.PLUS_EQUAL -> PLUS
          CodeToken.MINUS, CodeToken.MINUS_EQUAL -> MINUS
          CodeToken.STAR, CodeToken.STAR_EQUAL -> MULTIPLY
          CodeToken.STAR_STAR, CodeToken.STAR_STAR_EQUAL -> POWER
          CodeToken.SLASH, CodeToken.SLASH_EQUAL -> throw IllegalStateException("For division, the operator must be computed externally")
          CodeToken.PERCENTAGE, CodeToken.PERCENTAGE_EQUAL -> REMAINDER
          CodeToken.VERTICAL_BAR, CodeToken.VERTICAL_BAR_EQUAL -> BIT_OR
          CodeToken.AMPERSAND, CodeToken.AMPERSAND_EQUAL -> BIT_AND
          CodeToken.CIRCUMFLEX, CodeToken.CIRCUMFLEX_EQUAL -> BIT_XOR
          CodeToken.GREATER_GREATER, CodeToken.GREATER_GREATER_EQUAL -> BIT_SHIFT_RIGHT
          CodeToken.GREATER_GREATER_GREATER, CodeToken.GREATER_GREATER_GREATER_EQUAL -> BIT_SHIFT_RIGHT_UNSIGNED
          CodeToken.LESS_LESS, CodeToken.LESS_LESS_EQUAL -> BIT_SHIFT_LEFT
          CodeToken.DOUBLE_VERTICAL_BAR -> LOGIC_OR
          CodeToken.AMPERSAND_AMPERSAND -> LOGIC_AND
          CodeToken.EQUAL_EQUAL -> EQUAL
          CodeToken.BANG_EQUAL -> NOT_EQUAL
          CodeToken.LESS -> LESS
          CodeToken.LESS_EQUAL -> LESS_EQUAL
          CodeToken.GREATER -> GREATER
          CodeToken.GREATER_EQUAL -> GREATER_EQUAL
          else -> throw IllegalArgumentException("Unknown math operation: ${this.name}")
        }
      }
    }

    override fun toByteBuffer() = ByteBuffer.allocate(2 + firstOperandRegister + secondOperandRegister + targetRegister)
      .putShort((0b0110 nextBit firstOperandRegister nextBit secondOperandRegister nextBit targetRegister) shl 9 or operation.id or (type.ordinal shl 5))
      .apply {
        if (firstOperandRegister) put(firstOperandIndex)
        if (secondOperandRegister) put(secondOperandIndex)
        if (targetRegister) put(targetIndex)
        flip()
      }

    override fun byteSize() = 2 + firstOperandRegister + secondOperandRegister + targetRegister

    companion object {

      fun stackOp(operation: MathOperation, type: NumberType) = Math(operation, false, false, false, 0u, 0u, 0u, type)
      fun stackToResultTarget(operation: MathOperation, type: NumberType, resultTarget: ResultTarget): Math {
        return when (resultTarget) {
          is ResultTarget.Register -> Math(operation, false, false, true, 0u, 0u, resultTarget.registerId().toUByte(), type)
          is ResultTarget.Stack -> stackOp(operation, type)
          else -> throw IllegalArgumentException("Unknown result target for math ops: ${resultTarget.javaClass.simpleName}")
        }
      }
      fun targetAndStackToTarget(operation: MathOperation, type: NumberType, first: ResultTarget, target: ResultTarget): Math {
        return when (first) {
          is ResultTarget.Register -> when (target) {
            is ResultTarget.Register -> Math(operation, true, false, true, first.registerId().toUByte(), 0u, target.registerId().toUByte(), type)
            is ResultTarget.Stack -> Math(operation, true, false, false, first.registerId().toUByte(), 0u, 0u, type)
            else -> throw IllegalArgumentException("Unknown result target for math ops: ${target.javaClass.simpleName}")
          }
          is ResultTarget.Stack -> stackToResultTarget(operation, type, target)
          else -> throw IllegalArgumentException("Unknown result target for math ops: ${first.javaClass.simpleName}")
        }
      }

      fun read(id: Int, channel: ReadableByteChannel): Math {
        val instruction = id shl 8 or channel.ubyte()!!.toInt()
        val firstOperandRegister = id and 0b1000 != 0
        val secondOperandRegister = id and 0b0100 != 0
        val targetRegister = id and 0b0010 != 0
        val op = instruction and 0b11111
        return Math(
          MathOperation.values().first { it.id == op },
          firstOperandRegister,
          secondOperandRegister,
          targetRegister,
          if (firstOperandRegister) channel.ubyte()!!
          else 0u,
          if (secondOperandRegister) channel.ubyte()!!
          else 0u,
          if (targetRegister) channel.ubyte()!!
          else 0u,
          NumberType.values()[instruction and 0b0000_0001_1110_0000]
        )
      }
    }
  }

  /**
   * Load from/Store to Register
   * @param index Register index
   * @param mode Load(0)/Store(1) mode
   * Load: Takes from the register and push on the stack.
   * Store: Takes from the stack and puts into a register
   */
  data class LoadStore(val mode: Boolean, val index: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(2).put(if (mode) 0b01111000 else 0b01110000).put(index).flip()

    override fun toString(): String {
      return "LoadStore(mode=${if (mode) "Store" else "Load"}, index=$index)"
    }

    override fun byteSize() = 2

    companion object {
      /**
       * Load from Register into Stack
       */
      @JvmStatic
      fun load(index: UByte) = LoadStore(false, index)

      /**
       * Store from Stack into Register
       */
      @JvmStatic
      fun store(index: UByte) = LoadStore(true, index)
    }
  }

  /**
   * Get from/Put to field
   * PPPP MWR- XXXX XXXX
   * P: Instruction Id
   * @param mode M: Mode : Get(0)/Put(1)
   * @param wide W: If set, next byte expands the index from 8 to 16 bit.
   * @param register R: Target: Stack(0)/Register(1), if set, next byte (after index) decide register index.
   * @param reversed V: Reversed: If this Field is virtual and this is a PUT, the obj ref will be poped first, then the actual value.
   * X: Index
   * Load: Takes from the field and push on the stack/register.
   * Store: Takes from the stack/register and puts into the field.
   */
  data class GetPut(
    val mode: Boolean,
    val wide: Boolean,
    val register: Boolean,
    val reversed: Boolean,
    val referenceIndex: UShort,
    val registerIndex: UByte
  ) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(2 + wide + register).put(
      (
              if (mode)
                if (register) 0b01111010
                else 0b01111000
              else
                if (register) 0b01110010
                else 0b01110000
              ) + reversed
    ).apply {
      if (wide) putShort(referenceIndex)
      else put(referenceIndex.toUByte())
      if (register) put(registerIndex)
    }.flip()

    override fun byteSize() = 2 + wide + register
    override fun toString(): String {
      return "GetPut(mode=$mode, wide=$wide, referenceIndex=$referenceIndex, register=$register${if (register) ", registerIndex=$registerIndex" else ""})"
    }

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): GetPut {
        val wide = id and 0b1000 != 0
        val register = id and 0b0010 != 0
        val reversed = id and 0b0001 != 0
        return GetPut(
          id and 0b1000 != 0, wide, register, reversed,
          if (register)
            if (wide) channel.ushort()!!
            else channel.ubyte()!!.toUShort()
          else 0u,
          if (!register) channel.ubyte()!! else 0u
        )
      }

      fun put(referenceIndex: UShort, reversed: Boolean = false): GetPut {
        return GetPut(true, referenceIndex > 0xFFu, false, reversed, referenceIndex, 0u)
      }

      fun put(referenceIndex: UShort, registerIndex: UByte, reversed: Boolean = false): GetPut {
        return GetPut(true, referenceIndex > 0xFFu, true, reversed, referenceIndex, registerIndex)
      }

      fun get(referenceIndex: UShort): GetPut {
        return GetPut(false, referenceIndex > 0xFFu, false, false, referenceIndex, 0u)
      }

      fun get(referenceIndex: UShort, registerIndex: UByte): GetPut {
        return GetPut(false, referenceIndex > 0xFFu, true, false, referenceIndex, registerIndex)
      }

    }
  }

  data class PopStack(val entries: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b10100000.toByte() or entries.toByte()).flip()

    override fun byteSize() = 1
  }

  data class DuplicateStack(val entries: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b10110000.toByte() or entries.toByte()).flip()

    override fun byteSize() = 1
  }

  data class SwapStack(val entries: UByte) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b11000000.toByte() or entries.toByte()).flip()

    override fun byteSize() = 1
  }

  sealed class Constant(val target: Target) : Instruction() {
    /**
     * DICT : Direct Int Constant
     * PPPP TTDD
     * TT - Target: Stack(0)/Register(1)/Field(2)/Field-Wide(3)
     * DD -
     * 00 - 8 bit
     * 01 - 16 bit
     * 10 - 32 bit
     * 11 - 64 bit
     */
    class DirectInteger(val value: LiteralToken.IntegerToken, target: Target) : Constant(target) {
      override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
      override fun toByteBuffer() = ByteBuffer.allocate(byteSize()).apply {
        put((0b11010000u or (target.id.toUInt() shl 2) or (value.asPrimitive().registerType.ordinal.toUInt())).toUByte())
        if (target is Target.Register) put(target.index)
        else if (target is Target.Field) {
          if (target.index > 0xFFu) put(target.index.toUByte()) else putShort(target.index)
        }
        value.putIntoByteBuffer(this)
      }.flip()

      override fun byteSize() = 1 + value.asPrimitive().registerType.byteSize + (target is Target.Field && target.index > 0xFFu)
    }

    /**
     * DFCT : Direct Float Constant
     * PPPP TT-D
     * TT - Target: Stack(0)/Register(1)/Field(2)/Field-Wide(3)
     * D -
     *   0 - 32 bit float
     *   1 - 64 bit float
     */
    class DirectFloat(val value: LiteralToken.FloatToken, target: Target) : Constant(target) {
      override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
      override fun toByteBuffer() = ByteBuffer.allocate(byteSize()).apply {
        put((0b11100000u or (target.id.toUInt() shl 2) or (value.asPrimitive().registerType.ordinal.toUInt() - 2u)).toUByte())
        if (target is Target.Register) put(target.index)
        else if (target is Target.Field) {
          if (target.index > 0xFFu) put(target.index.toUByte()) else putShort(target.index)
        }
        value.putIntoByteBuffer(this)
      }.flip()

      override fun byteSize() = 1 + value.asPrimitive().registerType.byteSize + (target is Target.Field && target.index > 0xFFu)
    }

    /**
     * ICST : Indirect Constant - pushes the value in the constant pool onto the stack
     * PPPP TTW- XXXX XXXX
     * TT - Target: Stack(0)/Register(1)/Field(2)/Field-Wide(3)
     * W - one byte more for the index
     */
    class Indirect(val index: UShort, target: Target) : Constant(target) {
      override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
      override fun byteSize() = 4 + (if(target is Target.Field) 1 + (target.index > 0xFFu) else 1) + (index > 0xFFu)

      override fun toByteBuffer() = ByteBuffer.allocate(byteSize()).apply {
        put((0b11110000u or (target.id.toUInt() shl 2) or (if (index > 0xFFu) 1u else 0u)).toUByte())
        if (target is Target.Register) put(target.index)
        else if (target is Target.Field) {
          if (target.index > 0xFFu) put(target.index.toUByte()) else putShort(target.index)
        }
        if (index > 0xFFu) putShort(index) else put(index.toUByte())
      }.flip()
    }
  }

  /**
   * OOP
   * PPPP --AA
   * @param oop A: Cast(0)/Check(1)/CheckNot(2)
   * On check, it pushes the result on the stack
   */
  class Oop(val oop: DataExpression.OopExpression.OopOpType, val index: UShort) : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun toByteBuffer() = ByteBuffer.allocate(1)
      .apply {
        put((if (index > 0xFFu) 0b10011000u else 0b10010000u).toUByte() or oop.ordinal.toUByte())
        if (index > 0xFFu) putShort(index) else put(index.toUByte())
      }.flip()

    override fun byteSize() = 1
  }

  class Label : Instruction() {
    override fun <C, R> visit(visitor: InstructionVisitor<C, R>, context: C): R = visitor.visit(this, context)
    override fun byteSize() = 0

    override fun toByteBuffer() = ByteBuffer.allocate(0).flip()
    override fun equals(other: Any?): Boolean {
      return this === other
    }

    override fun hashCode(): Int {
      return System.identityHashCode(this)
    }
  }

  companion object {
    fun read(channel: ReadableByteChannel): Instruction {
      val id = channel.ubyte()!!.toInt()
      return when (id shr 4) {
        0 -> NoOp
        1 -> Call.read(id, channel)
        2 -> Return(id and 0b1000 != 0, channel.ubyte()!!)
        3 -> If.read(id, channel)
        4 -> InvNegion(id and 0b1000 != 0, InvNegion.InversionDataType.values()[id and 0b11])
        5 -> Conversion.read(id, channel)
        6 -> Math.read(id, channel)
        7 -> LoadStore(id and 0b1000 != 0, channel.ubyte()!!)
        8 -> GetPut.read(id, channel)
        9 -> Oop(
          DataExpression.OopExpression.OopOpType.values().getOrNull(id and 0b11) ?: throw IllegalArgumentException("Invalid Oop Op Type"),
          if (id and 0b1000 != 0) channel.ushort()!! else channel.ubyte()!!.toUShort()
        )
        10 -> PopStack((id and 0x0F).toUByte())
        11 -> DuplicateStack((id and 0x0F).toUByte())
        12 -> SwapStack((id and 0x0F).toUByte())
        else -> throw NotImplementedError("No Instruction for id $id.")
      }
    }
  }
}
