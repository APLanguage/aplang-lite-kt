package com.github.aplanguage.aplanglite.compiler.stdlib

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.CodeToken.*
import kotlin.math.max
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.RegisterAllocator.Type as RegisterType

enum class PrimitiveType : Namespace.Typeable {
  UNIT("Unit", RegisterType.REFERENCE),
  ANY("Any", RegisterType.REFERENCE),
  NOTHING("Nothing", RegisterType.REFERENCE),
  VOID("Void", RegisterType.REFERENCE),
  STRING("String", RegisterType.REFERENCE),
  I8("I8", RegisterType.BIT_8),
  I16("I16", RegisterType.BIT_16),
  I32("I32", RegisterType.BIT_32),
  I64("I64", RegisterType.BIT_64),
  U8("U8", RegisterType.BIT_8),
  U16("U16", RegisterType.BIT_16),
  U32("U32", RegisterType.BIT_32),
  U64("U64", RegisterType.BIT_64),
  F32("F32", RegisterType.BIT_32),
  F64("F64", RegisterType.BIT_64),
  FLOAT(F32, "Float", RegisterType.BIT_32),
  DOUBLE(F64, "Double", RegisterType.BIT_64),
  CHAR("Char", RegisterType.BIT_64),
  BOOL("Bool", RegisterType.BIT_8);

  val clazz: Namespace.Class
  val clazzName: String?
  val registerType: RegisterType

  constructor(name: String, registerType: RegisterType) {
    clazz = Namespace.Class(name, listOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
    clazzName = name
    this.registerType = registerType
  }

  constructor(alias: PrimitiveType, name: String, registerType: RegisterType) {
    clazz = alias.clazz
    clazzName = name
    this.registerType = registerType
  }

  fun isPrimitive(): Boolean = ordinal > 3
  fun isFloatingPoint() = this == F32 || this == F64
  fun isInteger() = this == I8 || this == I16 || this == I32 || this == I64 || this == U8 || this == U16 || this == U32 || this == U64


  companion object {
    fun ofName(name: String) = values().firstOrNull { it.clazz.name == name }
    fun ofClass(clazz: Namespace.Class) = values().firstOrNull { it.clazz == clazz } ?: ANY
    fun isPrimitive(clazz: Namespace.Class) = NUMERICS.any { it.clazz == clazz } || clazz == CHAR.clazz || clazz == BOOL.clazz

    val classes = values().map { it.clazz }.distinct()
    val INTEGERS = listOf(I8, I16, I32, I64, U8, U16, U32, U64)
    val SIGNED_INTEGERS = listOf(I8, I16, I32, I64)
    val UNSIGNED_INTEGERS = listOf(U8, U16, U32, U64)
    val FLOATS = listOf(F32, F64)
    val NUMERICS = INTEGERS + FLOATS

    init {
      values().forEach { type -> if (type != ANY) type.clazz.supers.add(Either.Right(ANY.clazz)) }
    }
  }

  override fun type() = clazz

  fun binary(operator: CodeToken, other: PrimitiveType): PrimitiveType {
    if (operator == EQUAL || operator == BANG_EQUAL) return when {
      this == other -> BOOL
      this in INTEGERS && other in INTEGERS -> BOOL
      this in FLOATS && other in FLOATS -> BOOL
      else -> VOID
    }
    if (this == STRING) return when (operator) {
      PLUS, PLUS_EQUAL -> STRING
      STAR, STAR_EQUAL -> if (other in UNSIGNED_INTEGERS) STRING else VOID
      else -> VOID
    }
    return when (operator) {
      PLUS, MINUS, PLUS_EQUAL, MINUS_EQUAL, STAR, STAR_EQUAL, SLASH, SLASH_EQUAL -> when {
        this == other -> this
        other == STRING -> if (operator == PLUS || operator == PLUS_EQUAL) STRING else VOID
        this in SIGNED_INTEGERS && other in SIGNED_INTEGERS -> values()[max(this.ordinal, other.ordinal)]
        this in UNSIGNED_INTEGERS && other in UNSIGNED_INTEGERS -> values()[max(this.ordinal, other.ordinal)]
        this in FLOATS || other in FLOATS -> values()[max(this.ordinal, other.ordinal)]
        else -> VOID
      }
      PERCENTAGE, PERCENTAGE_EQUAL -> if (this in INTEGERS && other in INTEGERS) this else VOID
      STAR_STAR, STAR_STAR_EQUAL -> when {
        this in INTEGERS && other in UNSIGNED_INTEGERS -> this
        this in NUMERICS && other in SIGNED_INTEGERS -> F64
        else -> VOID
      }
      LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> if (this in NUMERICS && other in NUMERICS) BOOL else VOID
      DOUBLE_VERTICAL_BAR, AMPERSAND_AMPERSAND -> if (this == BOOL && other == BOOL) BOOL else VOID
      else -> VOID
    }
  }

  fun unary(operator: CodeToken): Namespace.Typeable {
    if (this == ANY) return VOID
    return when (operator) {
      BANG -> if (this == BOOL) this else VOID
      TILDE -> if (this in INTEGERS) this else VOID
      MINUS -> if (this in NUMERICS) this else VOID
      else -> VOID
    }
  }
}
