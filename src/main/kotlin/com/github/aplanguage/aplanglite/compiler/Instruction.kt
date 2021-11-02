package com.github.aplanguage.aplanglite.compiler

import com.github.aplanguage.aplanglite.utils.*
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
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

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): Call {
        val wide = id and 0b0010 != 0
        return Call(
          id and 0b1000 != 0,
          id and 0b0100 != 0,
          wide,
          if (wide) ushortFromChannel(channel)!!
          else ubyteFromChannel(channel)!!.toUShort()
        )
      }
    }
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
      .put(((if (wide) 0b00111000 else 0b00110000) or condition.ordinal).toByte())
      .apply {
        if (wide) putShort(location)
        else put(location.toByte())
        flip()
      }

    override fun byteSize() = if (wide) 3 else 2

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): If {
        val wide = id and 0b1000 != 0
        return If(IfCondition.values()[id and 0b111], wide, if (wide) ushortFromChannel(channel)!! else ubyteFromChannel(channel)!!.toUShort())
      }
    }
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

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): Conversion {
        val target = id and 0b1000 != 0
        val types = ubyteFromChannel(channel)!!.toInt()
        return Conversion(
          target,
          NumberType.values()[types and 0xF0 shr 4],
          NumberType.values()[types and 0x0F],
          if (target) ubyteFromChannel(channel)!! else 0u
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

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): Math {
        val instruction = id shl 8 or ubyteFromChannel(channel)!!.toInt()
        val firstOperandRegister = id and 0b1000 != 0
        val secondOperandRegister = id and 0b0100 != 0
        val targetRegister = id and 0b0010 != 0
        val op = instruction and 0b11111
        return Math(
          MathOperation.values().first { it.id == op }, firstOperandRegister, secondOperandRegister, targetRegister,
          if (firstOperandRegister) ubyteFromChannel(channel)!!
          else 0u,
          if (secondOperandRegister) ubyteFromChannel(channel)!!
          else 0u,
          if (targetRegister) ubyteFromChannel(channel)!!
          else 0u,
          NumberType.values()[instruction and 0b0000_0001_1110_0000]
        )
      }
    }
  }

  data class LoadStore(val mode: Boolean, val index: UByte) : Instruction() {
    override fun toByteBuffer() = ByteBuffer.allocate(2).put(if (mode) 0b01111000 else 0b01110000).put(index).flip()

    override fun toString(): String {
      return "LoadStore(mode=${if (mode) "Store" else "Load"}, index=$index)"
    }

    override fun byteSize() = 2
  }

  data class GetPut(val mode: Boolean, val wide: Boolean, val register: Boolean, val referenceIndex: UShort, val registerIndex: UByte) :
    Instruction() {
    override fun toByteBuffer() =
      ByteBuffer.allocate(2 + wide + register)
        .put(if (mode) 0b01111000 else 0b01110000)
        .apply {
          if (wide) putShort(referenceIndex)
          else
            if (register) put(registerIndex)
            else put(referenceIndex.toUByte())
        }.flip()

    override fun byteSize() = 2 + wide + register
    override fun toString(): String {
      return "GetPut(mode=$mode, wide=$wide, referenceIndex=$referenceIndex, register=$register${if (register) ", registerIndex=$registerIndex" else ""})"
    }

    companion object {
      fun read(id: Int, channel: ReadableByteChannel): GetPut {
        val wide = id and 0b1000 != 0
        val register = id and 0b0010 != 0
        return GetPut(
          id and 0b1000 != 0,
          wide,
          register,
          if (register)
            if (wide) ushortFromChannel(channel)!!
            else ubyteFromChannel(channel)!!.toUShort()
          else 0u,
          if (!register) ubyteFromChannel(channel)!!
          else 0u
        )
      }
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

  companion object {
    fun read(channel: ReadableByteChannel): Instruction {
      val id = ubyteFromChannel(channel)!!.toInt()
      return when (id shr 4) {
        0 -> NoOp
        1 -> Call.read(id, channel)
        2 -> Return(id and 0b1000 != 0, ubyteFromChannel(channel)!!)
        3 -> If.read(id, channel)
        4 -> Inversion(id and 0b1000 != 0, Inversion.InversionDataType.values()[id and 0b11], ubyteFromChannel(channel)!!)
        5 -> Conversion.read(id, channel)
        6 -> Math.read(id, channel)
        7 -> LoadStore(id and 0b1000 != 0, ubyteFromChannel(channel)!!)
        8 -> GetPut.read(id, channel)
        9 -> PushStack((id and 0x0F).toUByte())
        10 -> PopStack((id and 0x0F).toUByte())
        11 -> DuplicateStack((id and 0x0F).toUByte())
        12 -> SwapStack((id and 0x0F).toUByte())
        else -> throw NotImplementedError("No Instruction for id $id.")
      }
    }
  }
}

