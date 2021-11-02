package com.github.aplanguage.aplanglite.utils

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

inline fun <T> listOfUntilNull(generator: () -> T?): List<T> {
  var temp: T? = generator()
  val list = mutableListOf<T>()
  while (temp != null) {
    list.add(temp)
    temp = generator()
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

fun readFromChannel(channel: ReadableByteChannel, bytes: Int): ByteBuffer? = ByteBuffer.allocate(bytes).also {
  if (channel.read(it) != it.capacity()) return null
}

inline fun ubyteFromChannel(channel: ReadableByteChannel) = readFromChannel(channel, 1)?.get()?.toUByte()
inline fun ushortFromChannel(channel: ReadableByteChannel) = readFromChannel(channel, 2)?.short?.toUShort()

interface ByteBufferable {
  fun toByteBuffer(): ByteBuffer
}
