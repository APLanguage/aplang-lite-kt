package com.github.aplanguage.aplanglite.utils

import arrow.core.NonEmptyList
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.charset.StandardCharsets

inline fun <T> listOfUntilNull(generator: () -> T?): List<T> {
  var temp: T? = generator()
  val list = mutableListOf<T>()
  while (temp != null) {
    list.add(temp)
    temp = generator()
  }
  return list.toList()
}

inline fun <T> listOfMapUntilNull(initial: T?, generator: (T) -> T?): List<T> {
  if (initial == null) return listOf()
  var temp: T? = generator(initial)
  val list = mutableListOf<T>(initial)
  while (temp != null) {
    list.add(temp)
    temp = generator(temp)
  }
  return list.toList()
}

inline fun <T> listOfTimes(i: Int, generator: () -> T): List<T> {
  val list = mutableListOf<T>()
  if (i < 1) return list
  for (counter in 0 until i) {
    list.add(generator())
  }
  return list
}

inline fun <T> listOfTimesIndexed(i: Int, generator: (Int) -> T): List<T> {
  val list = mutableListOf<T>()
  if (i < 1) return list
  for (counter in 0 until i) {
    list.add(generator(counter))
  }
  return list
}

fun <A, B> Iterable<A>.allZip(other: Iterable<B>, check: (A, B) -> Boolean): Boolean {
  val iterator1 = this.iterator()
  val iterator2 = other.iterator()
  while (iterator1.hasNext() && iterator2.hasNext()) {
    if (!check(iterator1.next(), iterator2.next())) return false
  }
  return !iterator1.hasNext() && !iterator2.hasNext()
}


fun <T1, T2> List<T1>.compareEachElement(otherList: List<T2>, compare: (T1, T2) -> Boolean): Boolean {
  if (this.size != otherList.size) return false
  return (0 until this.size).all { compare(this[it], otherList[it]) }
}

fun <E> MutableList<E>.lastOrPut(elementSupplier: () -> E): E {
  if (this.isEmpty()) {
    val element = elementSupplier()
    this.add(element)
    return element
  }
  return this.last()
}

infix fun Int.nextBit(b: Boolean) = this shl 1 or if (b) 1 else 0
operator fun Boolean.plus(other: Boolean) = if (this) (if (other) 2 else 1) else if (other) 1 else 0
operator fun Int.plus(b: Boolean) = if (b) this + 1 else this
operator fun ByteBuffer.plus(other: ByteBuffer): ByteBuffer = ByteBuffer.allocate(limit() + other.limit()).put(this).put(other)
operator fun ByteBufferable.plus(other: ByteBufferable): ByteBuffer = toByteBuffer() + other.toByteBuffer()
inline fun ByteBuffer.put(uByte: UByte): ByteBuffer = put(uByte.toByte())
inline fun ByteBuffer.put(i: Int): ByteBuffer = put(i.toByte())
inline fun ByteBuffer.put(i: UInt): ByteBuffer = put(i.toByte())
inline fun ByteBuffer.putShort(uShort: UShort): ByteBuffer = putShort(uShort.toShort())
inline fun ByteBuffer.putShort(i: Int): ByteBuffer = putShort(i.toShort())
inline fun ByteBuffer.putInt(uInt: UInt): ByteBuffer = putInt(uInt.toInt())
inline fun ByteBuffer.putLong(uLong: ULong): ByteBuffer = putLong(uLong.toLong())
inline fun <T> ByteBuffer.putCollection(byteSize: Int, collection: Collection<T>, putFunc: (ByteBuffer, T) -> Unit): ByteBuffer {
  when (byteSize) {
    1 -> put(collection.size.toByte())
    2 -> putShort(collection.size.toShort())
    4 -> putInt(collection.size)
    else -> throw IllegalArgumentException("Only byteSize of 1, 2 or 4 allowed, got $byteSize!")
  }
  for (item in collection) {
    putFunc(this, item)
  }
  return this
}

fun ByteArray.toByteBuffer(): ByteBuffer = ByteBuffer.allocate(size).put(this)

fun ReadableByteChannel.read(bytes: Int): ByteBuffer? = ByteBuffer.allocate(bytes).also {
  if (read(it) != it.capacity()) return@read null
}

inline fun ReadableByteChannel.ubyte() = read(1)?.get()?.toUByte()
inline fun ReadableByteChannel.ushort() = read(2)?.short?.toUShort()
inline fun ReadableByteChannel.uint() = read(4)?.int?.toUInt()
inline fun ReadableByteChannel.ulong() = read(8)?.long?.toULong()
inline fun ReadableByteChannel.byte() = read(1)?.get()
inline fun ReadableByteChannel.short() = read(2)?.short
inline fun ReadableByteChannel.int() = read(4)?.int
inline fun ReadableByteChannel.long() = read(8)?.long
inline fun ReadableByteChannel.float() = read(4)?.float
inline fun ReadableByteChannel.double() = read(8)?.double
inline fun ReadableByteChannel.string1() = string(1)
inline fun ReadableByteChannel.string2() = string(2)
inline fun ReadableByteChannel.string4() = string(4)
inline fun ReadableByteChannel.string(strLength: Int): String? = read(strLength)?.let { StandardCharsets.UTF_8.decode(it).toString() }


interface ByteBufferable {
  fun toByteBuffer(): ByteBuffer
}

inline fun <T> List<T>.toNonEmptyList() = NonEmptyList.fromListUnsafe(this)
