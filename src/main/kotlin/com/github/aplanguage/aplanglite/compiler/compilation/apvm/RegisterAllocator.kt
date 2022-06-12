package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Class
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Typeable
import com.github.aplanguage.aplanglite.utils.lastOrPut

class RegisterAllocator {
  enum class Type {

    BIT_8,
    BIT_16,
    BIT_32,
    BIT_64,
    REFERENCE;

    val byteSize: Byte
      get() = when (this) {
        BIT_8 -> 1
        BIT_16 -> 2
        BIT_32 -> 4
        BIT_64 -> 8
        REFERENCE -> 0
      }
  }

  inner class Register(val id: Int, val type: Type) {
    var used: Boolean = false

    fun use() {
      if (used) throw IllegalStateException("Register $id is already in use!")
      used = true
      scopes.lastOrPut { mutableListOf() }.add(this)
    }
  }

  val registers = mutableListOf<Register>()
  private val scopes = mutableListOf<MutableList<Register>>()

  fun register(type: Type): Register {
    val register = registers.firstOrNull { !it.used && it.type == type } ?: Register(registers.size, type).also(registers::add)
    register.use()
    return register
  }

  fun register(type: Class): Register = register(type.primitiveType().registerType)
  fun register(type: Typeable) = register(type.type()!!)

  operator fun get(index: Int) = registers[index]

  fun enterScope() {
    scopes.add(mutableListOf())
  }

  fun leaveScope() {
    scopes.removeLastOrNull()?.forEach { it.used = false }
  }

  companion object {
    inline fun Either<Register, Int>.registerIndex(): Int = when (this) {
      is Either.Left -> this.value.id
      is Either.Right -> this.value
    }
  }
}
