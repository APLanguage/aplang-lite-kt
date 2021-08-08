package com.github.amejonah1200.aplang_lite.tokenizer

import java.util.*

abstract class Token {
  data class KeywordToken(val keyword: Keyword) : Token() {
    override fun length(): Int = keyword.name.length
  }

  data class PrimitiveKeywordToken(val keyword: PrimitiveKeyword) : Token() {
    override fun length(): Int = keyword.name.length
  }

  data class ValueKeywordToken(val keyword: ValueKeyword) : Token() {
    override fun length(): Int = keyword.name.length
  }

  data class SignToken(val codeToken: CodeToken) : Token() {
    override fun length(): Int = codeToken.stringRepresentation.length
  }

  data class StringToken(val string: String) : Token() {

    override fun length(): Int = string.length + 2
  }

  data class CharToken(val char: Char) : Token() {
    override fun length(): Int = 3
  }

  abstract fun length(): Int
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
  TRUE, FALSE,
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
  CodeToken.values().map(CodeToken::name),
  Keyword.values().map(Keyword::name),
  PrimitiveKeyword.values().map(PrimitiveKeyword::name),
  ValueKeyword.values().map(ValueKeyword::name)
).maxOf { it.maxByOrNull(String::length)!!.length }

val KEYWORDS = listOf(Keyword.values().map { it.name.lowercase() },
  PrimitiveKeyword.values().map { it.name.lowercase() },
  ValueKeyword.values().map { it.name.lowercase() }).flatten()

fun parseToken(string: String): Optional<Token> {
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
    }.map {
      when (it) {
        is Keyword -> Token.KeywordToken(it)
        is PrimitiveKeyword -> Token.PrimitiveKeywordToken(it)
        is ValueKeyword -> Token.ValueKeywordToken(it)
        else -> throw IllegalStateException("should never happen")
      }
    }
  } else {
    return Optional.ofNullable(CodeToken.values().filter { string.startsWith(it.stringRepresentation) }.reduceOrNull { acc, codeToken ->
      if (acc.stringRepresentation.length > codeToken.stringRepresentation.length) acc else codeToken
    }).map { Token.SignToken(it) }
  }
}
