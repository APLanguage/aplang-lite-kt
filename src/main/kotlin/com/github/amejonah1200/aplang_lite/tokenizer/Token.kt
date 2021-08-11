package com.github.amejonah1200.aplang_lite.tokenizer

import java.math.BigInteger

sealed class Token {
  data class KeywordToken(val keyword: Keyword) : Token()

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
  FN,
  IF, ELSE, WHILE, FOR, RETURN, BREAK,
  VAR,
  CLASS, SUPER, THIS, AS, IS,
  USE
}

enum class ValueKeyword {
  TRUE,
  FALSE
}

val LONGEST_TOKEN_LENGTH: Int = arrayOf(
  CodeToken.values().map(CodeToken::stringRepresentation),
  Keyword.values().map(Keyword::name),
  ValueKeyword.values().map(ValueKeyword::name)
).maxOf { it.maxByOrNull(String::length)!!.length }
val KEYWORDS = listOf(Keyword.values().map { it.name.lowercase() },
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
        is ValueKeyword -> Token.ValueKeywordToken(keyword)
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
