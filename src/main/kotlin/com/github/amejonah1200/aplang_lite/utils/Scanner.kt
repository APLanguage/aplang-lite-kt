package com.github.amejonah1200.aplang_lite.utils


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

class GriddedScanner<T>(elements: List<GriddedObject<T>>) : Scanner<GriddedObject<T>>(elements) {

  fun peekPreviousCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return if (peek == 0) peekCoords()
    else {
      val previous = elements[peek - 1]
      Pair(previous.startCoords(), previous.endCoords())
    }
  }

  fun peekCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    val previous = elements[peek]
    return Pair(previous.startCoords(), previous.endCoords())
  }

  fun peekNextCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return if (peek + 1 >= elements.size) peekCoords()
    else {
      val next = elements[peek + 1]
      Pair(next.startCoords(), next.endCoords())
    }
  }

  fun consumePreviousCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return if (position == 0) consumeCoords()
    else {
      val previous = elements[position - 1]
      Pair(previous.startCoords(), previous.endCoords())
    }
  }

  fun consumeCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    val previous = elements[position]
    return Pair(previous.startCoords(), previous.endCoords())
  }

  fun consumeNextCoords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    return if (position + 1 >= elements.size) consumeCoords()
    else {
      val next = elements[position + 1]
      Pair(next.startCoords(), next.endCoords())
    }
  }

  fun consumeMatchingClass(clazz: Class<T>): GriddedObject<T>? {
    val griddedObject = consume() ?: return null
    if (clazz.isInstance(griddedObject.obj)) return griddedObject
    rewindPosition(1)
    return null
  }

  fun peekMatchingClass(clazz: Class<T>): GriddedObject<T>? {
    val griddedObject = peek() ?: return null
    if (clazz.isInstance(griddedObject.obj)) return griddedObject
    rewindPeek(1)
    return null
  }

}

private fun clamp(lower: Int, x: Int, upper: Int) = if (x < lower) lower else if (x > upper) upper else x
