package com.github.aplanguage.aplanglite.compiler

import com.github.aplanguage.aplanglite.compiler.ReferenceInfo.ClassReference.PrimitiveReference
import com.github.aplanguage.aplanglite.utils.*
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

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

sealed class ConstantInfo : ByteBufferable{
  class StringConstant(string: String) : ConstantInfo() {
    val utf8Array = string.encodeToByteArray()
    override fun toByteBuffer() = ByteBuffer.allocate(4 + utf8Array.size).putInt(utf8Array.size).put(utf8Array).flip()
  }
}

sealed class ReferenceInfo(open val id: UShort, val name: String, val parent: ClassReference?) : ByteBufferable {

  open class ClassReference(id: UShort, name: String, parent: ClassReference?) : ReferenceInfo(id, name, parent) {
    sealed class PrimitiveReference(id: UShort, name: String) : ClassReference(id, name, null) {

      override fun toByteBuffer() = throw IllegalAccessException("You cannot compile a Primitive Reference!")

      object U8 : PrimitiveReference(0u, "U8")
      object U16 : PrimitiveReference(1u, "U16")
      object U32 : PrimitiveReference(2u, "U32")
      object U64 : PrimitiveReference(3u, "U64")
      object I8 : PrimitiveReference(4u, "I8")
      object I16 : PrimitiveReference(5u, "I16")
      object I32 : PrimitiveReference(6u, "I32")
      object I64 : PrimitiveReference(7u, "I64")
      object Float : PrimitiveReference(8u, "Float")
      object Double : PrimitiveReference(9u, "Double")
    }

    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val out = ByteBuffer.allocate(nameUtf8.size + (if (parent != null) 4 else 2))
        .put(if (parent != null) 0b100 else 0)
      if (parent != null) out.putShort(parent.id)
      return out.put(nameUtf8.size.toByte()).put(nameUtf8).flip()
    }
  }

  class FieldReference(id: UShort, name: String, parent: ClassReference?, val type: ClassReference) : ReferenceInfo(id, name, parent) {
    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val out = ByteBuffer.allocate(nameUtf8.size + (if (parent != null) 4 else 2) + (if (type !is PrimitiveReference) 2 else 0))
        .put(buildRefInfoByte(this, type, parent != null, 1u))
      if (parent != null) out.putShort(parent.id)
      out.put(nameUtf8.size).put(nameUtf8)
      if (type !is PrimitiveReference) out.putShort(type.id)
      return out.flip()
    }
  }

  class MethodReference(
    id: UShort,
    name: String,
    parent: ClassReference?,
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
      if (parent != null) out.putShort(parent.id)
      out.put(nameUtf8.size).put(nameUtf8)
      if (returnType != null && returnType !is PrimitiveReference) out.putShort(returnType.id)
      out.put(parametersBB)
      return out.flip()
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

    sealed class DirectValue(type: PrimitiveReference, val byteSize: Int, val putter: (ByteBuffer) -> ByteBuffer) : FieldValue(type) {
      override fun byteSize() = byteSize
      override fun putToByteBuffer(byteBuffer: ByteBuffer) = putter(byteBuffer)

      class U8(val value: UByte) : DirectValue(PrimitiveReference.U8, 1, { it.put(value) })
      class U16(val value: UShort) : DirectValue(PrimitiveReference.U16, 2, { it.putShort(value) })
      class U32(val value: UInt) : DirectValue(PrimitiveReference.U32, 4, { it.putInt(value) })
      class U64(val value: ULong) : DirectValue(PrimitiveReference.U64, 8, { it.putLong(value) })
      class I8(val value: Byte) : DirectValue(PrimitiveReference.I8, 1, { it.put(value) })
      class I16(val value: Short) : DirectValue(PrimitiveReference.I16, 2, { it.putShort(value) })
      class I32(val value: Int) : DirectValue(PrimitiveReference.I32, 4, { it.putInt(value) })
      class I64(val value: Long) : DirectValue(PrimitiveReference.I64, 8, { it.putLong(value) })
      class FloatValue(val value: Float) : DirectValue(PrimitiveReference.Float, 4, { it.putFloat(value) })
      class DoubleValue(val value: Double) : DirectValue(PrimitiveReference.Double, 8, { it.putDouble(value) })
    }
  }

  override fun toByteBuffer(): ByteBuffer {
    val nameUtf8 = name.encodeToByteArray()
    val out = ByteBuffer.allocate(
      1
              + 1 + nameUtf8.size
              + (if (value.type !is PrimitiveReference) 2 else 0)
              + value.byteSize()
    )
      .put(
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

}
