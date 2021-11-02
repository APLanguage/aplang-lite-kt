package com.github.aplanguage.aplanglite.compiler

import com.github.aplanguage.aplanglite.compiler.ReferenceInfo.ClassReference.PrimitiveReference
import com.github.aplanguage.aplanglite.utils.*
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets

private fun List<ByteBufferable>.toByteBuffer(sizeBytes: Int): ByteBuffer {
  if (when (sizeBytes) {
      1 -> size > 256
      2 -> size > 65536
      4 -> true
      else -> throw IllegalArgumentException("sizeBytes == 1, 2 or 4, got $sizeBytes.")
    }
  ) throw IllegalStateException("Size of Writable List exceeds max limit, limit $size max ${if (sizeBytes == 1) 256 else 65536}.")
  val buffs = map(ByteBufferable::toByteBuffer)
  return ByteBuffer.allocate(sizeBytes + buffs.sumOf { it.limit() }).apply {
    if (sizeBytes == 1) put(size.toByte())
    else putShort(size.toShort())
    for (buff in buffs) put(buff.flip())
  }.flip()
}

sealed class ConstantInfo : ByteBufferable {
  class StringConstant(string: String) : ConstantInfo() {
    val utf8Array = string.encodeToByteArray()
    override fun toByteBuffer() = ByteBuffer.allocate(4 + utf8Array.size).putInt(utf8Array.size).put(utf8Array).flip()
  }

  companion object {
    fun read(channel: ReadableByteChannel): ConstantInfo {
      return StringConstant(
        StandardCharsets.UTF_8.decode(readFromChannel(channel, (readFromChannel(channel, 4)!!).int)!!).toString()
      )
    }
  }
}

sealed class ReferenceInfo(open val id: UShort, val name: String, val parent: UShort?) : ByteBufferable {

  open class ClassReference(id: UShort, name: String, parent: UShort?) : ReferenceInfo(id, name, parent) {
    sealed class PrimitiveReference(id: UShort, name: String, val byteSize: Int) : ClassReference(id, name, null) {

      override fun toByteBuffer() = throw IllegalAccessException("You cannot compile a Primitive Reference!")

      object U8 : PrimitiveReference(0u, "U8", 1)
      object U16 : PrimitiveReference(1u, "U16", 2)
      object U32 : PrimitiveReference(2u, "U32", 4)
      object U64 : PrimitiveReference(3u, "U64", 8)
      object I8 : PrimitiveReference(4u, "I8", 1)
      object I16 : PrimitiveReference(5u, "I16", 2)
      object I32 : PrimitiveReference(6u, "I32", 4)
      object I64 : PrimitiveReference(7u, "I64", 8)
      object Float : PrimitiveReference(8u, "Float", 4)
      object Double : PrimitiveReference(9u, "Double", 8)

      companion object {
        fun fromId(id: Int): PrimitiveReference? = when (id) {
          0 -> U8
          1 -> U16
          2 -> U32
          3 -> U64
          4 -> I8
          5 -> I16
          6 -> I32
          7 -> I64
          8 -> Float
          9 -> Double
          else -> null
        }
      }
    }

    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val out = ByteBuffer.allocate(nameUtf8.size + (if (parent != null) 4 else 2))
        .put(if (parent != null) 0b100 else 0)
      if (parent != null) out.putShort(parent)
      return out.put(nameUtf8.size.toByte()).put(nameUtf8).flip()
    }
  }

  class FieldReference(id: UShort, name: String, parent: UShort?, val type: ClassReference) : ReferenceInfo(id, name, parent) {
    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val out = ByteBuffer.allocate(nameUtf8.size + (if (parent != null) 4 else 2) + (if (type !is PrimitiveReference) 2 else 0))
        .put(buildRefInfoByte(this, type, parent != null, 1u))
      if (parent != null) out.putShort(parent)
      out.put(nameUtf8.size).put(nameUtf8)
      if (type !is PrimitiveReference) out.putShort(type.id)
      return out.flip()
    }
  }

  class MethodReference(
    id: UShort,
    name: String,
    parent: UShort?,
    val returnType: ClassReference?,
    val parameters: List<ClassReference>
  ) : ReferenceInfo(id, name, parent) {
    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val parametersBB = parameters.foldRight(ByteBuffer.allocate(parameters.size * 3 + 1)) { p, bb ->
        if (p is PrimitiveReference) bb.put(p.id + 1u) else bb.put(0).putShort(p.id)
      }.flip()
      val out = ByteBuffer.allocate(
        1
                + (if (parent != null) 2 else 0)
                + 1 + nameUtf8.size
                + (if (returnType !is PrimitiveReference) 2 else 0)
                + parametersBB.limit()
      ).put(buildRefInfoByte(this, returnType, parent != null, 2u))
      if (parent != null) out.putShort(parent)
      out.put(nameUtf8.size).put(nameUtf8)
      if (returnType != null && returnType !is PrimitiveReference) out.putShort(returnType.id)
      out.put(parametersBB)
      return out.flip()
    }

  }

  companion object {
    fun read(id: UShort, refSearch: (Int) -> ClassReference, channel: ReadableByteChannel): ReferenceInfo? {
      val byte = readFromChannel(channel, 1)!!.get().toInt()
      val parent = if (byte and 0b0100 != 0) readFromChannel(channel, 2)!!.short.toUShort()
      else null
      val name = StandardCharsets.UTF_8.decode(readFromChannel(channel, ubyteFromChannel(channel)!!.toInt())!!).toString()
      val type = when (val fourtype = if (byte and 0b1000 != 0) -1 else (byte and 0xF0).shr(4)) {
        -1 -> null
        0 -> refSearch(readFromChannel(channel, 2)!!.short.toUShort().toInt())
        else -> PrimitiveReference.fromId(fourtype)
      }
      return when (byte and 0b11) {
        0 -> ClassReference(id, name, parent)
        1 -> FieldReference(id, name, parent, type!!)
        2 -> MethodReference(id, name, parent, type, listOfTimes(ubyteFromChannel(channel)!!.toInt()) {
          when (val fourtype = readFromChannel(channel, 1)!!.get().toInt()) {
            0 -> refSearch(readFromChannel(channel, 2)!!.short.toUShort().toInt())
            else -> PrimitiveReference.fromId(fourtype - 1)!!
          }
        })
        else -> null
      }
    }

  }
}

private fun buildRefInfoByte(self: ReferenceInfo, type: ReferenceInfo.ClassReference?, parent: Boolean, refType: UInt): Int {
  return ((
          when (type) {
            null -> 0u
            !is PrimitiveReference -> 0b1000u
            else -> ((type.id + 1u) * 2u + 1u).shl(3)
          }
          ).or(
      when (self) {
        is ReferenceInfo.ClassReference -> if (parent) 0b0100u else 0b0000u
        is ReferenceInfo.FieldReference -> if (parent) 0b0101u else 0b0001u
        is ReferenceInfo.MethodReference -> if (parent) 0b0100u else 0b0010u
      }
    )).toInt()
}

class FieldInfo(
  val name: String,
  val value: FieldValue,
) : ByteBufferable {
  sealed class FieldValue(val type: ReferenceInfo.ClassReference) {

    abstract fun putToByteBuffer(byteBuffer: ByteBuffer): ByteBuffer
    abstract fun byteSize(): Int

    class Code(code: List<Instruction>, type: ReferenceInfo.ClassReference) : FieldValue(type) {
      val asBuffer: ByteBuffer = code.toByteBuffer(4)
      override fun putToByteBuffer(byteBuffer: ByteBuffer) = byteBuffer.put(asBuffer)
      override fun byteSize() = asBuffer.limit()
    }

    class Constant(val index: UShort, type: ReferenceInfo.ClassReference) : FieldValue(type) {
      override fun putToByteBuffer(byteBuffer: ByteBuffer) = byteBuffer.putShort(index)
      override fun byteSize() = 2
    }

    sealed class DirectValue(type: PrimitiveReference, val putter: (ByteBuffer) -> ByteBuffer) : FieldValue(type) {
      override fun byteSize() = (type as PrimitiveReference).byteSize
      override fun putToByteBuffer(byteBuffer: ByteBuffer) = putter(byteBuffer)

      class U8(val value: UByte) : DirectValue(PrimitiveReference.U8, { it.put(value) })
      class U16(val value: UShort) : DirectValue(PrimitiveReference.U16, { it.putShort(value) })
      class U32(val value: UInt) : DirectValue(PrimitiveReference.U32, { it.putInt(value) })
      class U64(val value: ULong) : DirectValue(PrimitiveReference.U64, { it.putLong(value) })
      class I8(val value: Byte) : DirectValue(PrimitiveReference.I8, { it.put(value) })
      class I16(val value: Short) : DirectValue(PrimitiveReference.I16, { it.putShort(value) })
      class I32(val value: Int) : DirectValue(PrimitiveReference.I32, { it.putInt(value) })
      class I64(val value: Long) : DirectValue(PrimitiveReference.I64, { it.putLong(value) })
      class FloatValue(val value: Float) : DirectValue(PrimitiveReference.Float, { it.putFloat(value) })
      class DoubleValue(val value: Double) : DirectValue(PrimitiveReference.Double, { it.putDouble(value) })

      companion object {
        fun read(type: PrimitiveReference, channel: ReadableByteChannel): DirectValue {
          val bb = readFromChannel(channel, type.byteSize)!!
          return when (type) {
            PrimitiveReference.Double -> DoubleValue(bb.double)
            PrimitiveReference.Float -> FloatValue(bb.float)
            PrimitiveReference.I8 -> I8(bb.get())
            PrimitiveReference.I16 -> I16(bb.short)
            PrimitiveReference.I32 -> I32(bb.int)
            PrimitiveReference.I64 -> I64(bb.long)
            PrimitiveReference.U8 -> U8(bb.get().toUByte())
            PrimitiveReference.U16 -> U16(bb.short.toUShort())
            PrimitiveReference.U32 -> U32(bb.int.toUInt())
            PrimitiveReference.U64 -> U64(bb.long.toULong())
          }
        }
      }
    }
  }

  override fun toByteBuffer(): ByteBuffer {
    val nameUtf8 = name.encodeToByteArray()
    val out = ByteBuffer.allocate(
      1
              + 1 + nameUtf8.size
              + (if (value.type !is PrimitiveReference) 2 else 0)
              + value.byteSize()
    ).put(
      (if (value.type !is PrimitiveReference) 0u else (value.type.id + 1u).shl(4))
              or when (value) {
        is FieldValue.Code -> 0u
        is FieldValue.Constant -> 1u
        is FieldValue.DirectValue -> 2u
      }
    ).put(nameUtf8.size.toByte()).put(nameUtf8)
    if (value.type !is PrimitiveReference) out.putShort(value.type.id)
    value.putToByteBuffer(out)
    return out.flip()
  }

  companion object {
    fun read(refSearch: (Int) -> ReferenceInfo.ClassReference, channel: ReadableByteChannel): FieldInfo {
      val temp = readFromChannel(channel, 2)!!
      val infobyte = temp.get().toUByte().toInt()
      val name = StandardCharsets.UTF_8.decode(readFromChannel(channel, temp.get().toUByte().toInt())!!).toString()
      val fourtype = infobyte ushr 4
      val type = if (fourtype == 0) refSearch(readFromChannel(channel, 2)!!.short.toUShort().toInt())
      else PrimitiveReference.fromId(fourtype - 1)!!
      return FieldInfo(
        name, when (infobyte and 0b11) {
          0 -> FieldValue.Code(listOfTimes(readFromChannel(channel, 4)!!.int) { Instruction.read(channel) }, type)
          1 -> FieldValue.Constant(readFromChannel(channel, 2)!!.short.toUShort(), type)
          2 -> if (type is PrimitiveReference) FieldValue.DirectValue.read(type, channel) else throw IllegalStateException("._.")
          else -> throw IllegalStateException(".-.")
        }
      )
    }
  }
}

class MethodInfo(
  val name: String,
  val returnType: ReferenceInfo.ClassReference?,
  val parameters: List<ReferenceInfo.ClassReference>,
  val code: List<Instruction>
) : ByteBufferable {
  override fun toByteBuffer(): ByteBuffer {
    val nameUtf8 = name.encodeToByteArray()
    val code = code.toByteBuffer(4)
    val parametersBB = parameters.foldRight(ByteBuffer.allocate(parameters.size * 3 + 1)) { p, bb ->
      if (p is PrimitiveReference) bb.put(p.id + 1u) else bb.put(0).putShort(p.id)
    }.flip()
    val out = ByteBuffer.allocate(
      1
              + 1 + nameUtf8.size
              + (if (returnType != null) 2 else 0)
              + parametersBB.limit()
              + code.limit()
    ).put(
      when (returnType) {
        null -> 0u
        !is PrimitiveReference -> 0b1000u
        else -> ((returnType.id + 1u) * 2u + 1u).shl(3)
      }
    ).put(nameUtf8.size.toByte()).put(nameUtf8)
    if (returnType != null && returnType !is PrimitiveReference) out.putShort(returnType.id)
    out.put(parametersBB).put(code)
    return out.flip()
  }

  companion object {
    fun read(refSearch: (Int) -> ReferenceInfo.ClassReference, channel: ReadableByteChannel): MethodInfo {
      val temp = readFromChannel(channel, 2)!!
      val fourtype = temp.get().toUByte().toInt().let {
        if (it and 0b1000 == 0) -1
        else it shr 4
      }
      return MethodInfo(
        StandardCharsets.UTF_8.decode(readFromChannel(channel, temp.get().toUByte().toInt())!!).toString(),
        when (fourtype) {
          -1 -> null
          0 -> refSearch(readFromChannel(channel, 2)!!.get().toUShort().toInt())
          else -> PrimitiveReference.fromId(fourtype - 1)
        },
        listOfTimes(ubyteFromChannel(channel)!!.toInt()) {
          when (val fourtype = readFromChannel(channel, 1)!!.get().toInt()) {
            0 -> refSearch(readFromChannel(channel, 2)!!.short.toUShort().toInt())
            else -> PrimitiveReference.fromId(fourtype - 1)!!
          }
        },
        listOfTimes(readFromChannel(channel, 4)!!.int) { Instruction.read(channel) }
      )
    }
  }

}

class ClassInfo(
  val name: String,
  val supers: List<ReferenceInfo.ClassReference>,
  val fields: List<FieldInfo>,
  val methods: List<MethodInfo>,
  val classes: List<ClassInfo>
) : ByteBufferable {
  override fun toByteBuffer(): ByteBuffer {
    val nameUtf8 = name.encodeToByteArray()
    val fields = fields.toByteBuffer(1)
    val methods = methods.toByteBuffer(1)
    val classes = classes.toByteBuffer(1)
    return ByteBuffer.allocate(2 + nameUtf8.size + supers.size * 2 + fields.limit() + methods.limit() + classes.limit())
      .put(nameUtf8.size.toByte()).put(nameUtf8)
      .putCollection(1, supers) { bb, sup -> bb.putShort(sup.id) }
      .put(fields).put(methods).put(classes).flip()
  }

  companion object {
    fun read(refSearch: (Int) -> ReferenceInfo.ClassReference, channel: ReadableByteChannel): ClassInfo {
      val name = StandardCharsets.UTF_8.decode(readFromChannel(channel, ubyteFromChannel(channel)!!.toInt())!!).toString()
      val supersBB = readFromChannel(channel, ubyteFromChannel(channel)!!.toInt() * 2)!!
      return ClassInfo(
        name,
        listOfTimes(supersBB.capacity() / 2) { refSearch(supersBB.short.toUShort().toInt()) },
        listOfTimes(ubyteFromChannel(channel)!!.toInt()) { FieldInfo.read(refSearch, channel) },
        listOfTimes(ubyteFromChannel(channel)!!.toInt()) { MethodInfo.read(refSearch, channel) },
        listOfTimes(ubyteFromChannel(channel)!!.toInt()) { read(refSearch, channel) })
    }
  }
}

class APLangFile(
  val version: UByte,
  val constantPool: List<ConstantInfo>,
  val referencePool: List<ReferenceInfo>,
  val fields: List<FieldInfo>,
  val methods: List<MethodInfo>,
  val classes: List<ClassInfo>
) {

  fun write(channel: WritableByteChannel) {
    val magic = "APLANG-lite".encodeToByteArray()
    channel.write(ByteBuffer.allocate(magic.size + 1).put(magic).put(version).flip())
    channel.write(constantPool.toByteBuffer(2))
    channel.write(referencePool.toByteBuffer(2))
    channel.write(fields.toByteBuffer(1))
    channel.write(methods.toByteBuffer(1))
    channel.write(classes.toByteBuffer(1))
  }

  companion object {

    fun read(channel: ReadableByteChannel): APLangFile? {
      val magic = ByteBuffer.allocate(11)
      if (channel.read(magic) != 11 || StandardCharsets.UTF_8.decode(magic.flip()).toString() != "APLANG-lite") return null
      val temp = readFromChannel(channel, 3)!!
      val version = temp.get().toUByte()
      val constants = (0 until temp.short.toUShort().toInt()).map { ConstantInfo.read(channel) }
      val references = mutableListOf<ReferenceInfo>()
      for (counter in 0 until readFromChannel(channel, 2)!!.short.toUShort().toInt()) {
        references.add(ReferenceInfo.read(counter.toUShort(), { references[it] as ReferenceInfo.ClassReference }, channel)!!)
      }
      return APLangFile(version, constants, references, listOfTimes(readFromChannel(channel, 2)!!.short.toUShort().toInt()) {
        FieldInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      }, listOfTimes(readFromChannel(channel, 2)!!.short.toUShort().toInt()) {
        MethodInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      }, listOfTimes(readFromChannel(channel, 2)!!.short.toUShort().toInt()) {
        ClassInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      })
    }
  }

}
