package com.github.aplanguage.aplanglite.tokenizer

import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.utils.put
import com.github.aplanguage.aplanglite.utils.putInt
import com.github.aplanguage.aplanglite.utils.putLong
import com.github.aplanguage.aplanglite.utils.putShort
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

sealed class Token {
  data class KeywordToken(val keyword: Keyword) : Token()

  data class SignToken(val codeToken: CodeToken) : Token()

  data class IdentifierToken(val identifier: String) : Token()

  sealed class ValueToken : Token() {

    abstract fun asPrimitive(): PrimitiveType

    data class ValueKeywordToken(val keyword: ValueKeyword) : ValueToken() {
      override fun asPrimitive() = when (keyword) {
        ValueKeyword.TRUE, ValueKeyword.FALSE -> PrimitiveType.BOOL
        ValueKeyword.THIS -> PrimitiveType.ANY
        else -> PrimitiveType.NOTHING
      }
    }

    sealed class LiteralToken : ValueToken() {
      abstract fun stringify(): String

      data class StringToken(val string: String) : LiteralToken() {
        override fun asPrimitive(): PrimitiveType = PrimitiveType.STRING
        override fun stringify() = string.substring(0, minOf(string.length, 20)) + if (string.length > 20) "..." else ""
      }

      data class CharToken(val char: String) : LiteralToken() {
        override fun stringify() = "'$char'"
        override fun asPrimitive(): PrimitiveType = PrimitiveType.CHAR
        fun toChar(): Char = when (char) {
          "\\n" -> '\n'
          "\\t" -> '\t'
          "\\r" -> '\r'
          "\\b" -> '\b'
          "\\'" -> '\''
          "\\\"" -> '"'
          "\\\\" -> '\\'
          else -> char[0]
        }
      }

      sealed class IntegerToken : LiteralToken() {
        abstract fun putIntoByteBuffer(byteBuffer: ByteBuffer)

        data class I8Token(val i8: Byte) : IntegerToken() {
          override fun stringify() = "${i8}i8"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.put(i8)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.I8
        }

        data class I16Token(val i16: Short) : IntegerToken() {
          override fun stringify() = "${i16}i16"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putShort(i16)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.I16
        }

        data class I32Token(val i32: Int) : IntegerToken() {
          override fun stringify() = "${i32}i32"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putInt(i32)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.I32
        }

        data class I64Token(val i64: Long) : IntegerToken() {
          override fun stringify() = "${i64}i64"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putLong(i64)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.I64
        }

        data class U8Token(val u8: UByte) : IntegerToken() {
          override fun stringify() = "${u8}u8"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.put(u8)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.U8
        }

        data class U16Token(val u16: UShort) : IntegerToken() {
          override fun stringify() = "${u16}u16"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putShort(u16)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.U16
        }

        data class U32Token(val u32: UInt) : IntegerToken() {
          override fun stringify() = "${u32}u32"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putInt(u32)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.U32
        }

        data class U64Token(val u64: ULong) : IntegerToken() {
          override fun stringify() = "${u64}u64"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putLong(u64)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.U64
        }

        companion object {
          fun tokenOf(type: PrimitiveType, bigint: BigInteger): IntegerToken {
            return when (type) {
              PrimitiveType.I8 -> I8Token(bigint.toByte())
              PrimitiveType.I16 -> I16Token(bigint.toShort())
              PrimitiveType.I32 -> I32Token(bigint.toInt())
              PrimitiveType.I64 -> I64Token(bigint.toLong())
              PrimitiveType.U8 -> U8Token(bigint.toByte().toUByte())
              PrimitiveType.U16 -> U16Token(bigint.toShort().toUShort())
              PrimitiveType.U32 -> U32Token(bigint.toInt().toUInt())
              PrimitiveType.U64 -> U64Token(bigint.toLong().toULong())
              else -> throw IllegalArgumentException("Unsupported int type: $type")
            }
          }
        }
      }

      sealed class FloatToken : LiteralToken() {
        abstract fun putIntoByteBuffer(byteBuffer: ByteBuffer)

        data class F32Token(val f32: Float) : FloatToken() {
          override fun stringify() = "${f32}f32"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putFloat(f32)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.F32
        }

        data class F64Token(val f64: Double) : FloatToken() {
          override fun stringify() = "${f64}f64"
          override fun putIntoByteBuffer(byteBuffer: ByteBuffer) {
            byteBuffer.putDouble(f64)
          }

          override fun asPrimitive(): PrimitiveType = PrimitiveType.F64
        }

        companion object {
          fun tokenOf(type: PrimitiveType, bigint: BigDecimal): FloatToken {
            return when (type) {
              PrimitiveType.FLOAT, PrimitiveType.F32 -> F32Token(bigint.toFloat())
              PrimitiveType.DOUBLE, PrimitiveType.F64 -> F64Token(bigint.toDouble())
              else -> throw IllegalArgumentException("Unsupported float type: $type")
            }
          }
        }
      }
    }
  }
}

enum class CodeToken(val stringRepresentation: String) {
  HASH_TAG("#"),

  /**
   * (
   */
  LEFT_PAREN("("),

  /**
   * )
   */
  RIGHT_PAREN(")"),

  /**
   * {
   */
  LEFT_BRACE("{"),

  /**
   * }
   */
  RIGHT_BRACE("}"),

  /**
   * [
   */
  LEFT_BRACKET("["),

  /**
   * ]
   */
  RIGHT_BRACKET("]"),

  /**
   * ,
   */
  COMMA(","),

  /**
   * ;
   */
  SEMICOLON(";"),

  /**
   * /
   */
  SLASH("/"),

  /**
   * //
   */
  SLASH_SLASH("//"),

  /**
   * ~
   */
  TILDE("~"),

  /**
   * ?
   */
  QUESTION_MARK("?"),

  /**
   * %
   */
  PERCENTAGE("%"),

  /**
   * ^
   */
  CIRCUMFLEX("^"),

  /**
   * *
   */
  STAR("*"),

  /**
   * **
   */
  STAR_STAR("**"),

  /**
   * |
   */
  VERTICAL_BAR("|"),

  /**
   * ||
   */
  DOUBLE_VERTICAL_BAR("||"),

  /**
   * &
   */
  AMPERSAND("&"),

  /**
   * &&
   */
  AMPERSAND_AMPERSAND("&&"),

  /**
   * !
   */
  BANG("!"),

  /**
   * !=
   */
  BANG_EQUAL("!="),

  /**
   * !is
   */
  BANG_IS("!is"),

  /**
   * =
   */
  EQUAL("="),

  /**
   * ==
   */
  EQUAL_EQUAL("=="),

  /**
   * +=
   */
  PLUS_EQUAL("+="),

  /**
   * -=
   */
  MINUS_EQUAL("-="),

  /**
   * *=
   */
  STAR_EQUAL("*="),

  /**
   * **=
   */
  STAR_STAR_EQUAL("**="),

  /**
   * /=
   */
  SLASH_EQUAL("/="),

  /**
   * ^=
   */
  CIRCUMFLEX_EQUAL("^="),

  /**
   * &=
   */
  AMPERSAND_EQUAL("&="),

  /**
   * |=
   */
  VERTICAL_BAR_EQUAL("|="),

  /**
   * %=
   */
  PERCENTAGE_EQUAL("%="),

  /**
   * <<=
   */
  LESS_LESS_EQUAL("<<="),

  /**
   * >>=
   */
  GREATER_GREATER_EQUAL(">>="),

  /**
   * >>>=
   */
  GREATER_GREATER_GREATER_EQUAL(">>>="),

  /**
   * >
   */
  GREATER(">"),

  /**
   * >>
   */
  GREATER_GREATER(">>"),

  /**
   * >>>
   */
  GREATER_GREATER_GREATER(">>>"),

  /**
   * >=
   */
  GREATER_EQUAL(">="),

  /**
   * <
   */
  LESS("<"),

  /**
   * <<
   */
  LESS_LESS("<<"),

  /**
   * <=
   */
  LESS_EQUAL("<="),

  /**
   * -
   */
  MINUS("-"),

  /**
   * +
   */
  PLUS("+"),

  /**
   * :
   */
  COLON(":"),

  /**
   * ::
   */
  COLON_COLON("::"),

  /**
   * .
   */
  DOT("."),

  /**
   * ..
   */
  DOT_DOT(".."),

  /**
   * ..=
   */
  DOT_DOT_EQUAL("..="),

  /**
   * ->
   */
  ARROW_SIMPLE_RIGHT("->"),

  /**
   * <-
   */
  ARROW_SIMPLE_LEFT("<-"),

  // =>
  // ARROW_DOUBLE_RIGHT
}

enum class Keyword {
  FN,
  IF, ELSE, WHILE, FOR, RETURN, BREAK,
  VAR,
  CLASS, SUPER, AS, IS,
  PACKAGE, USE
}

enum class ValueKeyword {
  TRUE, FALSE, THIS
}

val LONGEST_TOKEN_LENGTH: Int = arrayOf(
  CodeToken.values().map(CodeToken::stringRepresentation),
  Keyword.values().map(Keyword::name),
  ValueKeyword.values().map(ValueKeyword::name)
).maxOf { it.maxByOrNull(String::length)!!.length }
val KEYWORDS = listOf(
  Keyword.values().map { it.name.lowercase() },
  ValueKeyword.values().map { it.name.lowercase() }).flatten()

fun parseToken(string: String): Pair<Token, Int>? {
  if (string.isEmpty()) return null
  return if (string[0].isLetter()) {
    var index: Int? = null
    for (i in KEYWORDS.indices) {
      if (index == null && string.startsWith(KEYWORDS[i])) index = i
      else if (index != null && KEYWORDS[i].length > KEYWORDS[index].length && string.startsWith(KEYWORDS[i])) index = i
    }
    index?.let {
      Keyword.values().getOrNull(it) ?: ValueKeyword.values()[it - Keyword.values().size]
    }?.let { keyword ->
      val length = keyword.name.length
      when (keyword) {
        is Keyword -> Token.KeywordToken(keyword)
        is ValueKeyword -> Token.ValueToken.ValueKeywordToken(keyword)
        else -> throw IllegalStateException("should never happen")
      }.let { Pair(it, length) }
    }
  } else {
    CodeToken.values().filter {
      string.startsWith(it.stringRepresentation)
    }.reduceOrNull { acc, codeToken ->
      if (acc.stringRepresentation.length > codeToken.stringRepresentation.length) acc else codeToken
    }?.let { Pair(Token.SignToken(it), it.stringRepresentation.length) }
  }
}
