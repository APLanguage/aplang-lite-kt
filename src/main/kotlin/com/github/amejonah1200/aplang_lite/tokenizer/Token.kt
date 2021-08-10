package com.github.amejonah1200.aplang_lite.tokenizer

import java.math.BigInteger
import java.util.*

abstract class Token {
  data class KeywordToken(val keyword: Keyword) : Token()

  data class PrimitiveKeywordToken(val keyword: PrimitiveKeyword) : Token()

  data class ValueKeywordToken(val keyword: ValueKeyword) : Token()

  data class SignToken(val codeToken: CodeToken) : Token()

  data class StringToken(val string: String) : Token()

  data class CharToken(val char: String) : Token()

  data class IntegerToken(val int: BigInteger) : Token()

  data class FloatToken(val first: BigInteger, val second: BigInteger) : Token()

  data class IdentifierToken(val identifier: String) : Token()
}

enum class CodeToken(val stringRepresentation: String) {
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
   * ~=
   */
  TILDE_EQUAL("~="),

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
  IF, ELSE, WHILE, FOR, RETURN, BREAK,
  VAR, VAL,
  PUB,
  CLASS, SUPER, THIS,
  USE
}

enum class PrimitiveKeyword {
  CHR, STR,
  U64, U32, U16, U8,
  I64, I32, I16, I8,
  FLOAT, DOUBLE, BOOL,
}

enum class ValueKeyword {
  TRUE,
  FALSE
}

val LONGEST_TOKEN_LENGTH: Int = arrayOf(
  CodeToken.values().map(CodeToken::stringRepresentation),
  Keyword.values().map(Keyword::name),
  PrimitiveKeyword.values().map(PrimitiveKeyword::name),
  ValueKeyword.values().map(ValueKeyword::name)
).maxOf { it.maxByOrNull(String::length)!!.length }
val KEYWORDS = listOf(Keyword.values().map { it.name.lowercase() },
  PrimitiveKeyword.values().map { it.name.lowercase() },
  ValueKeyword.values().map { it.name.lowercase() }).flatten()

fun parseToken(string: String): Optional<Pair<Token, Int>> {
  if (string.isEmpty()) return Optional.empty()
  if (string[0].isLetter()) {
    var index = Optional.empty<Int>()
    for (i in KEYWORDS.indices) {
      if (index.isEmpty && string.startsWith(KEYWORDS[i])) index = Optional.of(i)
      else if (index.isPresent && KEYWORDS[i].length > KEYWORDS[index.get()].length && string.startsWith(KEYWORDS[i])) index = Optional.of(i)
    }
    return index.map {
      Keyword.values().getOrNull(it) ?: PrimitiveKeyword.values().getOrNull(it - Keyword.values().size)
      ?: ValueKeyword.values()[it - Keyword.values().size - PrimitiveKeyword.values().size]
    }.map { keyword ->
      val length = keyword.name.length
      when (keyword) {
        is Keyword -> Token.KeywordToken(keyword)
        is PrimitiveKeyword -> Token.PrimitiveKeywordToken(keyword)
        is ValueKeyword -> Token.ValueKeywordToken(keyword)
        else -> throw IllegalStateException("should never happen")
      }.let { Pair(it, length) }
    }
  } else {

    return Optional.ofNullable(CodeToken.values().filter {
      string.startsWith(it.stringRepresentation)
    }.reduceOrNull { acc, codeToken ->
      if (acc.stringRepresentation.length > codeToken.stringRepresentation.length) acc else codeToken
    }).map { Pair(Token.SignToken(it), it.stringRepresentation.length) }
  }
}
