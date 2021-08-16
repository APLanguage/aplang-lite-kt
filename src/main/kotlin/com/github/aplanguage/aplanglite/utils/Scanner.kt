package com.github.aplanguage.aplanglite.utils

import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Keyword
import com.github.aplanguage.aplanglite.tokenizer.Token


open class Scanner<T>(val elements: List<T>) {
  var peek: Int = 0
    set(value) {
      field = clamp(0, value, elements.size)
    }
  var position: Int = 0
    set(value) {
      peek = value
      field = peek

    }

  private val sections = mutableListOf<Pair<Int, Int>>()

  fun startSection() {
    sections.add(Pair(position, peek))
  }

  fun actualSection() = sections.lastOrNull()

  fun endSection(rewind: Boolean = false) {
    val lastSection = sections.removeLastOrNull() ?: return
    if (rewind) {
      position = lastSection.first
      peek = lastSection.second
    }
  }

  fun advance(nb: Int, consume: Boolean, use_peek: Boolean) {
    if (consume) {
      if (use_peek) {
        setPositionToPeek()
      } else {
        position += nb
        resetPeek()
      }
    } else if (use_peek) {
      peek += nb
    }
  }

  fun consume(): T? = elements.getOrNull(position)?.also { position += 1 }

  fun consumeWithPredicate(predicate: (T) -> Boolean): T? {
    val obj = consume() ?: return null
    if (predicate(obj)) return obj
    rewindPosition(1)
    return null
  }

  open fun <T> consumeMatchingClass(clazz: Class<T>) = consumeWithPredicate(clazz::isInstance)

  fun peek(): T? = elements.getOrNull(peek)?.also { peek += 1 }

  fun peekSearchIgnore(ignore: (T) -> Boolean, predicate: (T) -> Boolean): Boolean {
    while (peekSearchWithPredicate(ignore)) {
    }
    rewindPeek(1)
    return peekSearchWithPredicate(predicate)
  }

  fun peekSearchWithPredicate(predicate: (T) -> Boolean): Boolean {
    return peek()?.let(predicate::invoke) ?: false
  }

  open fun <T> peekMatchingClass(clazz: Class<T>) = peekWithPredicate(clazz::isInstance)

  private fun peekWithPredicate(predicate: (T) -> Boolean): T? {
    val obj = peek() ?: return null
    if (predicate(obj)) return obj
    rewindPeek(1)
    return null
  }

  fun advancePeek(nb: Int) {
    if (nb == 0) {
      return; }
    if (peek + nb < elements.size) {
      peek += nb
    } else {
      peek = elements.size
    }
  }

  fun rewindPeek(nb: Int) {
    if (peek > nb) {
      peek -= nb
    } else {
      peek = 0
    }
  }

  fun resetPeek() {
    peek = position
  }

  fun isPeekEOF() = peek >= elements.size

  fun advancePosition(nb: Int) {
    if (nb == 0) return
    if (position + nb < elements.size) {
      position += nb
    } else {
      position = elements.size
    }
    resetPeek()
  }

  fun rewindPosition(nb: Int) {
    if (position > nb) {
      position -= nb
    } else {
      position = 0
    }
    resetPeek()
  }

  fun reset() {
    position = 0
    resetPeek()
  }

  fun setPositionToPeek() {
    if (peek > elements.size) {
      peek = elements.size
    }
    position = peek
  }

  fun isPositionEOF() = position >= elements.size
}

fun Scanner<Char>.toCharScanner() = CharScanner(elements.joinToString(""))

class CharScanner(val str: String) : Scanner<Char>(str.toCharArray().asList()) {
  fun searchNextChars(consume: Boolean, use_peek: Boolean, predicate: (Char) -> Boolean): String {
    val result = StringBuilder()
    val startPos = if (use_peek) {
      peek
    } else {
      position
    }
    var i = 0
    while (startPos + i < elements.size && predicate(elements[startPos + i])) {
      result.append(elements[startPos + i])
      i += 1
    }
    advance(i, consume, use_peek)
    return result.toString()
  }

  fun nextChars(nb: Int, consume: Boolean, use_peek: Boolean, fail_on_not_reach: Boolean): String? {
    val result = StringBuilder()
    val startPos = if (use_peek) {
      peek
    } else {
      position
    }
    if (startPos + nb >= elements.size && fail_on_not_reach) return null
    var nbToAdvance = nb
    for (i in 0 until nb) {
      if (startPos + i < elements.size) {
        result.append(elements[startPos + i])
      } else {
        nbToAdvance = i
        break
      }
    }
    advance(nbToAdvance, consume, use_peek)
    return result.toString()
  }

  fun searchConsumeChars(predicate: (Char) -> Boolean): String = searchNextChars(consume = true, use_peek = false, predicate = predicate)
  fun consumeChars(nb: Int, fail_on_not_reach: Boolean): String? = nextChars(
    nb,
    consume = true,
    use_peek = false,
    fail_on_not_reach = fail_on_not_reach
  )

  fun peekChar(): Char? {
    return elements.getOrNull(peek)?.also { peek += 1 }
  }

  fun peekChars(nb: Int, fail_on_not_reach: Boolean): String? =
    nextChars(nb, consume = false, use_peek = true, fail_on_not_reach = fail_on_not_reach)

  fun peekSearch(str: String): Boolean =
    nextChars(str.length, consume = false, use_peek = true, fail_on_not_reach = true)?.let {
      (str == it).also { result -> if (!result) rewindPeek(str.length) }
    } ?: false

  fun peekSearchChar(chr: Char): Boolean = peekChar()?.let { it == chr } ?: false
  fun peekSearchChars(predicate: (Char) -> Boolean): String = searchNextChars(consume = false, use_peek = true, predicate = predicate)
}

//class TokenScanner(elements: List<OneLineObject<Token>>) : Scanner<OneLineObject<Token>>(elements) {
//  fun peek_or_eof(): OneLineObject<Token> {}
//  fun peek_rewind_not_eof(amount: Int) {}
//  fun consume_or_eof(): OneLineObject<Token> {}
//  fun consume_rewind_not_eof(amount: Int) {}
//}

fun <T> Scanner<GriddedObject<T>>.toGriddedScanner() = GriddedScanner(elements)

open class GriddedScanner<T>(elements: List<GriddedObject<T>>) : Scanner<GriddedObject<T>>(elements) {

  fun peekPreviousCoords(): Area {
    return if (peek == 0) peekCoords()
    else elements[peek - 1].area()
  }

  fun peekCoords() = elements[peek].area()

  fun peekNextCoords(): Area {
    return if (peek + 1 >= elements.size) peekCoords()
    else elements[peek + 1].area()
  }

  fun positionPreviousCoords(): Area {
    return if (position == 0) positionCoords()
    else elements[position - 1].area()
  }

  fun positionCoords() = if (isPositionEOF()) EOF_AREA else elements[position].area()

  fun positionNextCoords(): Area {
    return if (position + 1 >= elements.size) peekCoords()
    else elements[position + 1].area()
  }

  fun <T2 : T> consumeMatchingInnerClass(clazz: Class<out T2>): GriddedObject<T2>? {
    val griddedObject = consume() ?: return null
    if (clazz.isInstance(griddedObject.obj)) return griddedObject.asGriddedObjectOfType(clazz)
    rewindPosition(1)
    return null
  }

  fun <T2 : T> peekMatchingInnerClass(clazz: Class<out T2>): GriddedObject<T2>? {
    val griddedObject = peek() ?: return null
    if (clazz.isInstance(griddedObject.obj)) return griddedObject.asGriddedObjectOfType(clazz)
    rewindPeek(1)
    return null
  }

}

fun GriddedScanner<Token>.toTokenScanner() = TokenScanner(elements)

class TokenScanner(elements: List<GriddedObject<Token>>) : GriddedScanner<Token>(elements) {
  fun consumeMatchingKeywordToken(keyword: Keyword): GriddedObject<Token.KeywordToken>? {
    val griddedObject = consumeMatchingInnerClass(Token.KeywordToken::class.java) ?: return null
    if (griddedObject.obj.keyword == keyword) return griddedObject
    rewindPosition(1)
    return null
  }

  fun consumeMatchingCodeToken(codeToken: CodeToken): GriddedObject<Token.SignToken>? {
    val griddedObject = consumeMatchingInnerClass(Token.SignToken::class.java) ?: return null
    if (griddedObject.obj.codeToken == codeToken) return griddedObject
    rewindPosition(1)
    return null
  }

  fun consumeMatchingCodeTokens(tokens: Array<CodeToken>): GriddedObject<Token.SignToken>? {
    val griddedObject = consumeMatchingInnerClass(Token.SignToken::class.java) ?: return null
    if (griddedObject.obj.codeToken in tokens) return griddedObject
    rewindPosition(1)
    return null
  }

  fun peekMatchingKeywordToken(keyword: Keyword): GriddedObject<Token.KeywordToken>? {
    val griddedObject = peekMatchingInnerClass(Token.KeywordToken::class.java) ?: return null
    if (griddedObject.obj.keyword == keyword) return griddedObject
    rewindPosition(1)
    return null
  }

  fun peekMatchingCodeToken(codeToken: CodeToken): GriddedObject<Token.SignToken>? {
    val griddedObject = peekMatchingInnerClass(Token.SignToken::class.java) ?: return null
    if (griddedObject.obj.codeToken == codeToken) return griddedObject
    rewindPosition(1)
    return null
  }
}

private fun clamp(lower: Int, x: Int, upper: Int) = if (x < lower) lower else if (x > upper) upper else x
