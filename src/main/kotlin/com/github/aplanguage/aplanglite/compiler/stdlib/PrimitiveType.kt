package com.github.aplanguage.aplanglite.compiler.stdlib

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.CodeToken.*
import kotlin.math.max

enum class PrimitiveType : Namespace.Typeable {
  NOTHING("Noting"),
  ANY("Any"),
  VOID("Void"),
  STRING("String"),
  I8("I8"),
  I16("I16"),
  I32("I32"),
  I64("I64"),
  U8("U8"),
  U16("U16"),
  U32("U32"),
  U64("U64"),
  F32("F32"),
  F64("F64"),
  FLOAT(F32, "Float"),
  DOUBLE(F64, "Double"),
  CHAR("Char"),
  BOOL("Bool");

  val clazz: Namespace.Class
  val clazzName: String?

  constructor(name: String) {
    clazz = Namespace.Class(name, listOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
    clazzName = name
  }

  constructor(alias: PrimitiveType, name: String) {
    clazz = alias.clazz
    clazzName = name
  }

  fun isPrimitive(): Boolean = ordinal > 3

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
