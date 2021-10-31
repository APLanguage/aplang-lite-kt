package com.github.aplanguage.aplanglite.compiler

import com.github.aplanguage.aplanglite.utils.*
import java.nio.ByteBuffer
import kotlin.experimental.or

sealed class Instruction : ByteBufferable {

  enum class NumberType {
    U8, U16, U32, U64, I8, I16, I32, I64, Float, Double
  }

  abstract fun byteSize(): Int

  object NoOp : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(1)

    override fun byteSize() = 1
  }

  data class Call(val static: Boolean, val ignore: Boolean, val wide: Boolean, val index: UShort) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(if (wide) 3 else 2)
      .put(0b0001 nextBit static nextBit ignore nextBit wide nextBit false)
      .apply {
        if (wide) putShort(index)
        else put(index.toByte())
        flip()
      }

    override fun byteSize() = if (wide) 3 else 2
  }

  data class Return(val target: Boolean, val index: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(2)
      .put(if (target) 0b00101000 else 0b00100000).put(index).flip()

    override fun byteSize() = 2
  }

  data class If(val condition: IfCondition, val wide: Boolean, val location: UShort) : Instruction() {

    enum class IfCondition {
      NOP,
      MINUS_ONE,
      ZERO,
      ZERO_MINUS_ONE,
      ONE,
      ONE_MINUS_ONE,
      ONE_ZERO,
      JUMP
    }

    override fun toByteBuffer() = ByteBuffer.allocate(if (wide) 3 else 2)
      .put(((if (wide) 0b00110001 else 0b00110000) or (condition.ordinal shr 1)).toByte())
      .apply {
        if (wide) putShort(location)
        else put(location.toByte())
        flip()
      }

    override fun byteSize() = if (wide) 3 else 2
  }

  data class Inversion(val target: Boolean, val dataType: InversionDataType, val index: UByte) : Instruction() {
    enum class InversionDataType {
      BYTE,
      INT,
      BIG_INT,
      BOOL
    }

    override fun toByteBuffer() = ByteBuffer.allocate(2)
      .put(((if (target) 0b01001000 else 0b01000000) or dataType.ordinal).toByte())
      .flip()

    override fun byteSize() = 2
  }

  data class Conversion(val target: Boolean, val fromType: NumberType, val toType: NumberType, val index: UByte) : Instruction() {


    override fun toByteBuffer() = ByteBuffer.allocate(if (target) 3 else 2)
      .put(if (target) 0b01011000 else 0b01010000)
      .put(fromType.ordinal shl 4 or toType.ordinal)
      .apply {
        if (target) put(index)
        flip()
      }

    override fun byteSize() = if (target) 3 else 2
  }

  data class Math(
    val operation: MathOperation,
    val firstOperandRegister: Boolean,
    val firstOperandIndex: UByte,
    val secondOperandRegister: Boolean,
    val secondOperandIndex: UByte,
    val targetRegister: Boolean,
    val targetIndex: UByte,
    val type: NumberType
  ) : Instruction() {

    enum class MathOperation(val id: Int) {
      PLUS(0b00000),
      MINUS(0b00001),
      MULTIPLY(0b00010),
      POWER(0b00011),
      DIVIDE(0b00100),
      DIVIDE_INT(0b00101),
      REMAINDER(0b00110),
      BIT_OR(0b01000),
      BIT_AND(0b01001),
      BIT_XOR(0b01010),
      BIT_SHIFT_RIGHT(0b01011),
      BIT_SHIFT_RIGHT_UNSIGNED(0b01100),
      BIT_SHIFT_LEFT(0b01101),
      LOGIC_OR(0b10000),
      LOGIC_AND(0b10001),
      EQUAL(0b10010),
      NOT_EQUAL(0b10011),
      LESS(0b11000),
      LESS_EQUAL(0b11001),
      GREATER(0b11010),
      GREATER_EQUAL(0b11011)
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
  }

  data class LoadStore(val mode: Boolean, val index: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(2).put(if (mode) 0b01111000 else 0b01110000).put(index).flip()

    override fun toString(): String {
      return "LoadStore(mode=${if (mode) "Store" else "Load"}, index=$index)"
    }

    override fun byteSize() = 2
  }

  data class GetPut(val mode: Boolean, val wide: Boolean, val referenceIndex: UShort, val register: Boolean, val registerIndex: UByte) :
    Instruction() {
    override fun toByteBuffer() =
      ByteBuffer.allocate(2 + wide + register)
        .put(if (mode) 0b01111000 else 0b01110000)
        .apply {
          if (wide) putShort(referenceIndex)
          else put(registerIndex)
        }.flip()

    override fun byteSize() = 2 + wide + register
    override fun toString(): String {
      return "GetPut(mode=$mode, wide=$wide, referenceIndex=$referenceIndex, register=$register${if (register) ", registerIndex=$registerIndex" else ""})"
    }
  }

  data class PushStack(val bytes: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b10010000.toByte() or bytes.toByte()).flip()

    override fun byteSize() = 1
  }

  data class PopStack(val bytes: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b10100000.toByte() or bytes.toByte()).flip()

    override fun byteSize() = 1
  }

  data class DuplicateStack(val bytes: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b10110000.toByte() or bytes.toByte()).flip()

    override fun byteSize() = 1
  }

  data class SwapStack(val bytes: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(1).put(0b11000000.toByte() or bytes.toByte()).flip()

    override fun byteSize() = 1
  }
}

