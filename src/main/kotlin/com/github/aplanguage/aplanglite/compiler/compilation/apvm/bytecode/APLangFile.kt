package com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.ReferenceInfo.ClassReference.PrimitiveReference
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.RegisterAllocator
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.utils.ByteBufferable
import com.github.aplanguage.aplanglite.utils.byte
import com.github.aplanguage.aplanglite.utils.double
import com.github.aplanguage.aplanglite.utils.float
import com.github.aplanguage.aplanglite.utils.int
import com.github.aplanguage.aplanglite.utils.listOfMapUntilNull
import com.github.aplanguage.aplanglite.utils.listOfTimes
import com.github.aplanguage.aplanglite.utils.listOfTimesIndexed
import com.github.aplanguage.aplanglite.utils.long
import com.github.aplanguage.aplanglite.utils.put
import com.github.aplanguage.aplanglite.utils.putCollection
import com.github.aplanguage.aplanglite.utils.putInt
import com.github.aplanguage.aplanglite.utils.putLong
import com.github.aplanguage.aplanglite.utils.putShort
import com.github.aplanguage.aplanglite.utils.read
import com.github.aplanguage.aplanglite.utils.short
import com.github.aplanguage.aplanglite.utils.string1
import com.github.aplanguage.aplanglite.utils.string4
import com.github.aplanguage.aplanglite.utils.ubyte
import com.github.aplanguage.aplanglite.utils.ushort
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets

private fun List<ByteBufferable>.toByteBuffer(sizeBytes: Int): ByteBuffer {
  if (when (sizeBytes) {
      1 -> size > 256
      2 -> size > 65536
      4 -> size > Int.MAX_VALUE
      else -> throw IllegalArgumentException("sizeBytes == 1, 2 or 4, got $sizeBytes.")
    }
  ) throw IllegalStateException("Size of Writable List exceeds max limit of $sizeBytes, limit $size max ${if (sizeBytes == 1) 256 else if (sizeBytes == 2) 65536 else Int.MAX_VALUE}.")
  val buffs = map(ByteBufferable::toByteBuffer)
  return ByteBuffer.allocate(sizeBytes + buffs.sumOf { it.limit() }).apply {
    if (sizeBytes == 1) put(size.toByte())
    else putShort(size.toShort())
    for (buff in buffs) put(buff)
  }.flip()
}

sealed class ConstantInfo(val id: UShort) : ByteBufferable {
  class StringConstant(val string: String, id: UShort) : ConstantInfo(id) {
    val utf8Array = string.encodeToByteArray()
    override fun toByteBuffer() = ByteBuffer.allocate(5 + utf8Array.size).put(6u).putInt(utf8Array.size).put(utf8Array).flip()
  }

  class ByteConstant(val value: Byte, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(2).put(0u).put(value).flip()
  }

  class ShortConstant(val value: Short, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(3).put(1u).putShort(value).flip()
  }

  class IntConstant(val value: Int, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(5).put(2u).putInt(value).flip()
  }

  class LongConstant(val value: Long, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(9).put(3u).putLong(value).flip()
  }

  class FloatConstant(val value: Float, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(5).put(4u).putFloat(value).flip()
  }

  class DoubleConstant(val value: Double, id: UShort) : ConstantInfo(id) {
    override fun toByteBuffer() = ByteBuffer.allocate(9).put(5u).putDouble(value).flip()
  }


  companion object {
    fun readOne(channel: ReadableByteChannel, id: UShort): ConstantInfo {
      return when (val size = channel.ubyte()!!.toInt()) {
        6 -> StringConstant(channel.string4()!!, id)

        0 -> ByteConstant(channel.byte()!!, id)
        1 -> ShortConstant(channel.short()!!, id)
        2 -> IntConstant(channel.int()!!, id)
        3 -> LongConstant(channel.long()!!, id)

        4 -> FloatConstant(channel.float()!!, id)
        5 -> DoubleConstant(channel.double()!!, id)
        else -> throw IllegalStateException("Unknown constant type $size.")
      }
    }

    fun readAll(channel: ReadableByteChannel, size: Int): List<ConstantInfo> {
      return listOfTimesIndexed(size) { readOne(channel, it.toUShort()) }
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

  open class FieldReference(id: UShort, name: String, parent: UShort?, val type: ClassReference) : ReferenceInfo(id, name, parent) {
    override fun toByteBuffer(): ByteBuffer {
      val nameUtf8 = name.encodeToByteArray()
      val out = ByteBuffer.allocate(nameUtf8.size + (if (parent != null) 4 else 2) + (if (type !is PrimitiveReference) 2 else 0))
        .put(buildRefInfoByte(this, type))
      if (parent != null) out.putShort(parent)
      out.put(nameUtf8.size).put(nameUtf8)
      if (type !is PrimitiveReference) out.putShort(type.id)
      return out.flip()
    }
  }

  sealed class ResolvedReferenceInfo(id: UShort, name: String, parent: UShort?) : ReferenceInfo(id, name, parent) {
    abstract val reference: ReferenceInfo

    class ResolvedClassReferenceInfo(val clazz: Namespace.Class, val classReference: ClassReference) :
      ResolvedReferenceInfo(classReference.id, classReference.name, classReference.parent) {
      override val reference: ReferenceInfo
        get() = classReference

      override fun toByteBuffer() = classReference.toByteBuffer()
    }

    class ResolvedFieldReferenceInfo(val field: Namespace.Field, val fieldReference: FieldReference) :
      ResolvedReferenceInfo(fieldReference.id, fieldReference.name, fieldReference.parent) {
      override val reference: ReferenceInfo
        get() = fieldReference

      override fun toByteBuffer() = fieldReference.toByteBuffer()
    }

    class ResolvedMethodReferenceInfo(val method: Namespace.Method, val methodReference: MethodReference) :
      ResolvedReferenceInfo(methodReference.id, methodReference.name, methodReference.parent) {
      override val reference: ReferenceInfo
        get() = methodReference

      override fun toByteBuffer() = methodReference.toByteBuffer()
    }
  }

  open class MethodReference(
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
      ).put(buildRefInfoByte(this, returnType))
      if (parent != null) out.putShort(parent)
      out.put(nameUtf8.size).put(nameUtf8)
      if (returnType != null && returnType !is PrimitiveReference) out.putShort(returnType.id)
      out.put(parametersBB)
      return out.flip()
    }
  }

  companion object {
    fun read(id: UShort, refSearch: (Int) -> ClassReference, channel: ReadableByteChannel): ReferenceInfo? {
      val byte = channel.ubyte()!!.toInt()
      val parent = if (byte and 0b0100 != 0) channel.ushort()!! else null
      val name = channel.string1()!!
      val type = when (val fourtype = if (byte and 0b1000 != 0) -1 else (byte and 0xF0).shr(4)) {
        -1 -> null
        0 -> refSearch(channel.ushort()!!.toInt())
        else -> PrimitiveReference.fromId(fourtype)
      }
      return when (byte and 0b11) {
        0 -> ClassReference(id, name, parent)
        1 -> FieldReference(id, name, parent, type!!)
        2 -> MethodReference(id, name, parent, type, listOfTimes(channel.ubyte()!!.toInt()) {
          when (val fourtype = channel.ubyte()!!.toInt()) {
            0 -> refSearch(channel.ushort()!!.toInt())
            else -> PrimitiveReference.fromId(fourtype - 1)!!
          }
        })
        else -> null
      }
    }

  }
}

fun ReferenceInfo.stringify(referencePool: List<ReferenceInfo>): String {
  return "#$id " + listOfMapUntilNull(parent) {
    referencePool[it.toInt()].parent
  }.asReversed().joinToString(".") { referencePool[it.toInt()].name } + when (this) {
    is ReferenceInfo.ClassReference -> ".$name"
    is ReferenceInfo.FieldReference -> "%$name"
    is ReferenceInfo.MethodReference -> "#$name" + parameters.joinToString(", ", "(", ")") {
      "[${it.stringify(referencePool)}]"
    }
    is ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo -> return methodReference.stringify(referencePool)
    is ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo -> return fieldReference.stringify(referencePool)
    is ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo -> return classReference.stringify(referencePool)
    else -> throw IllegalStateException("Unknown reference type ${javaClass.simpleName}")
  }.let { if (parent == null) it.substring(1) else it }
}

private fun buildRefInfoByte(self: ReferenceInfo, type: ReferenceInfo.ClassReference?): Int {
  val parent = self.parent != null
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
        is ReferenceInfo.ResolvedReferenceInfo -> return buildRefInfoByte(self.reference, type)
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

    class Code(val code: List<Instruction>, type: ReferenceInfo.ClassReference) : FieldValue(type) {
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
          val bb = channel.read(type.byteSize)!!
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

  fun printAsString(pool: Pool): String {
    return "$name: ${value.type.stringify(pool.referencePool)}\n  Value: " + when (value) {
      is FieldValue.Code -> {
        "Code:\n    ${value.code.joinToString(separator = "\n    ") { it.visit(BytecodeStringifier(pool), Unit) }}\n"
      }
      is FieldValue.Constant -> "Indirect Constant: #${value.index}"
      is FieldValue.DirectValue -> "Constant: ${
        when (value) {
          is FieldValue.DirectValue.U8 -> value.value.toString() + "u8"
          is FieldValue.DirectValue.U16 -> value.value.toString() + "u16"
          is FieldValue.DirectValue.U32 -> value.value.toString() + "u32"
          is FieldValue.DirectValue.U64 -> value.value.toString() + "u64"
          is FieldValue.DirectValue.I8 -> value.value.toString() + "i8"
          is FieldValue.DirectValue.I16 -> value.value.toString() + "i16"
          is FieldValue.DirectValue.I32 -> value.value.toString() + "i32"
          is FieldValue.DirectValue.I64 -> value.value.toString() + "i64"
          is FieldValue.DirectValue.FloatValue -> value.value.toString() + "f32"
          is FieldValue.DirectValue.DoubleValue -> value.value.toString() + "f64"
        }
      }"
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
      val temp = channel.read(2)!!
      val infobyte = temp.get().toUByte().toInt()
      val name = channel.string1()!!
      val fourtype = infobyte ushr 4
      val type = if (fourtype == 0) refSearch(channel.ushort()!!.toInt())
      else PrimitiveReference.fromId(fourtype - 1)!!
      return FieldInfo(
        name, when (infobyte and 0b11) {
          0 -> FieldValue.Code(listOfTimes(channel.int()!!) { Instruction.read(channel) }, type)
          1 -> FieldValue.Constant(channel.ushort()!!, type)
          2 -> if (type is PrimitiveReference) FieldValue.DirectValue.read(type, channel) else throw IllegalStateException("._.")
          else -> throw IllegalStateException("Unknown field value type")
        }
      )
    }

    fun of(pool: Pool, field: Namespace.Field): FieldInfo {
      return FieldInfo(
        field.name,
        FieldValue.Code(field.expr!!.orNull() ?: throw IllegalStateException("Field was not compiled"), pool[field.type()].classReference)
      )
    }
  }
}

class MethodInfo(
  val name: String,
  val returnType: ReferenceInfo.ClassReference?,
  val parameters: List<ReferenceInfo.ClassReference>,
  val registers: List<RegisterAllocator.Type>,
  val code: List<Instruction>
) : ByteBufferable {
  override fun toByteBuffer(): ByteBuffer {
    val nameUtf8 = name.encodeToByteArray()
    val code = code.toByteBuffer(4)
    val parametersBB = parameters.foldRight(ByteBuffer.allocate(parameters.size * 3 + 1)) { p, bb ->
      if (p is PrimitiveReference) bb.put(p.id + 1u) else bb.put(0).putShort(p.id)
    }.flip()
    val out = ByteBuffer.allocate(
      2
              + 1 + nameUtf8.size
              + (if (returnType != null) 2 else 0)
              + registers.size
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
    out.put(parametersBB).put(ByteBuffer.allocate(registers.size).apply {
      registers.forEach { put(it.ordinal) }
    }.flip()).put(code)
    return out.flip()
  }

  fun printAsString(pool: Pool): String {
    return "$name(${parameters.joinToString(", ") { "${it.name}: ${it.stringify(pool.referencePool)}" }}): ${returnType?.name ?: "void"}\n  Registers: ${registers.size}\n    ${
      registers.joinToString("\n    ") { it.name }
    }\n  Code:\n    ${
      code.joinToString("\n    ") { it.visit(BytecodeStringifier(pool), Unit) }
    }"
  }

  companion object {
    fun read(refSearch: (Int) -> ReferenceInfo.ClassReference, channel: ReadableByteChannel): MethodInfo {
      val temp = channel.read(2)!!
      val fourtype = temp.get().toUByte().toInt().let {
        if (it and 0b1000 == 0) -1
        else it shr 4
      }
      return MethodInfo(
        channel.string1()!!,
        when (fourtype) {
          -1 -> null
          0 -> refSearch(channel.ushort()!!.toInt())
          else -> PrimitiveReference.fromId(fourtype - 1)
        },
        listOfTimes(channel.ubyte()!!.toInt()) {
          when (val fourtype = channel.byte()!!.toInt()) {
            0 -> refSearch(channel.ushort()!!.toInt())
            else -> PrimitiveReference.fromId(fourtype - 1)!!
          }
        },
        listOfTimes(channel.ubyte()!!.toInt()) {
          RegisterAllocator.Type.values()[channel.ubyte()!!.toInt()]
        },
        listOfTimes(channel.int()!!) { Instruction.read(channel) }
      )
    }

    fun of(pool: Pool, method: Namespace.Method): MethodInfo {
      return MethodInfo(
        method.name,
        method.returnType?.let { pool[it.orNull() ?: throw IllegalStateException("Method return type was not resolved")].classReference },
        method.parameters.map { pool[it.clazz.orNull() ?: throw IllegalStateException("Method parameters were not resolved")].classReference },
        method.resolvedRegisters,
        method.exprs.orNull() ?: throw IllegalStateException("Method was not compiled")
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

  fun printAsString(pool: Pool): String {
    return "$name\n  Superclasses: ${supers.joinToString(", ") { it.name }}\n  Fields: ${fields.size}\n    ${
      fields.joinToString("\n  ") { it.printAsString(pool).replace("\n", "\n    ") }
    }\n  Methods: ${methods.size}\n    ${
      methods.joinToString("\n  ") { it.printAsString(pool).replace("\n", "\n    ") }
    }\n  Classes: ${classes.size}\n    ${
      classes.joinToString("\n  ") { it.printAsString(pool).replace("\n", "\n    ") }
    }"
  }

  companion object {
    fun read(refSearch: (Int) -> ReferenceInfo.ClassReference, channel: ReadableByteChannel): ClassInfo {
      val name = channel.string1()!!
      val supersBB = channel.read(channel.ubyte()!!.toInt() * 2)!!
      return ClassInfo(
        name,
        listOfTimes(supersBB.capacity() / 2) { refSearch(supersBB.short.toUShort().toInt()) },
        listOfTimes(channel.ubyte()!!.toInt()) { FieldInfo.read(refSearch, channel) },
        listOfTimes(channel.ubyte()!!.toInt()) { MethodInfo.read(refSearch, channel) },
        listOfTimes(channel.ubyte()!!.toInt()) { read(refSearch, channel) })
    }

    fun of(pool: Pool, clazz: Namespace.Class): ClassInfo {
      return ClassInfo(
        clazz.name,
        clazz.supers.map { pool[it.orNull() ?: throw IllegalStateException("Class super was not resolved")].classReference },
        clazz.fields.map { FieldInfo.of(pool, it) },
        clazz.methods.map { MethodInfo.of(pool, it) },
        clazz.classes.map { of(pool, it) }
      )
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

  fun print() {
    println("Version: $version")
    println("Constant pool: ${constantPool.size}")
    constantPool.forEachIndexed { index, constantInfo ->
      println(
        when (constantInfo) {
          is ConstantInfo.ByteConstant -> "  $index ${constantInfo.value}B"
          is ConstantInfo.DoubleConstant -> "  $index ${constantInfo.value}D"
          is ConstantInfo.FloatConstant -> "  $index ${constantInfo.value}F"
          is ConstantInfo.IntConstant -> "  $index ${constantInfo.value}I"
          is ConstantInfo.LongConstant -> "  $index ${constantInfo.value}L"
          is ConstantInfo.ShortConstant -> "  $index ${constantInfo.value}S"
          is ConstantInfo.StringConstant -> "  $index \"${constantInfo.string.replace("\n", "\\n")}\""
        }
      )
    }
    println("Reference pool: ${referencePool.size}")
    referencePool.forEach {
      println("  " + it.stringify(referencePool))
    }
    println("Fields: ${fields.size}")
    val pool = Pool().apply {
      referencePool.addAll(this@APLangFile.referencePool)
      constantPool.addAll(this@APLangFile.constantPool)
    }
    fields.forEach { println("  ${it.printAsString(pool).replace("\n", "\n  ")}") }
    println("Methods: ${methods.size}")
    methods.forEach { println("  ${it.printAsString(pool).replace("\n", "\n  ")}") }
    println("Classes: ${classes.size}")
    classes.forEach { println("  ${it.printAsString(pool).replace("\n", "\n  ")}") }
  }

  companion object {

    fun read(channel: ReadableByteChannel): APLangFile? {
      val magic = ByteBuffer.allocate(11)
      if (channel.read(magic) != 11 || StandardCharsets.UTF_8.decode(magic.flip()).toString() != "APLANG-lite") return null
      val temp = channel.read(3)!!
      val version = temp.get().toUByte()
      val constants = ConstantInfo.readAll(channel, temp.short.toUShort().toInt())
      val references = mutableListOf<ReferenceInfo>()
      references.addAll(listOfTimesIndexed(channel.ushort()!!.toInt()) { counter ->
        ReferenceInfo.read(counter.toUShort(), { references[it] as ReferenceInfo.ClassReference }, channel)!!
      })
      return APLangFile(version, constants, references, listOfTimes(channel.ushort()!!.toInt()) {
        FieldInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      }, listOfTimes(channel.ushort()!!.toInt()) {
        MethodInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      }, listOfTimes(channel.ushort()!!.toInt()) {
        ClassInfo.read({ references[it] as ReferenceInfo.ClassReference }, channel)
      })
    }

    fun ofNamespace(pool: Pool, namespace: Namespace): APLangFile {
      return APLangFile(
        0u,
        pool.constantPool,
        pool.referencePool,
        namespace.fields.map { FieldInfo.of(pool, it) },
        namespace.methods.map { MethodInfo.of(pool, it) },
        namespace.classes.map { ClassInfo.of(pool, it) })
    }
  }

}
