package com.github.amejonah1200.aplang_lite.utils

import java.util.*
import java.util.function.Predicate


open class Scanner<T>(val elements: List<T>) {
  var peek: Int = 0
    set(value) {
      field = clamp(0, value, elements.size - 1)
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

  fun consume(): Optional<T> {
    val result = Optional.ofNullable(elements.getOrNull(position))
    result.ifPresent {
      position += 1
      peek = position
    }
    return result
  }

  fun peek(): Optional<T> {
    val result = Optional.ofNullable(elements.getOrNull(peek))
    result.ifPresent { peek += 1 }
    return result
  }

  fun <F1 : Predicate<T>, F2 : Predicate<T>> peekSearchIgnore(ignore: F1, predicate: F2): Boolean {
    while (peekSearchWithPredicate(ignore)) {
    }
    rewindPeek(1)
    return peekSearchWithPredicate(predicate)
  }

  fun <F : Predicate<T>> peekSearchWithPredicate(predicate: F): Boolean {
    return peek().map(predicate::test).orElse(false)
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
    if (nb == 0) {
      return; }
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
  fun searchNextChars(consume: Boolean, use_peek: Boolean, predicate: Predicate<Char>): String {
    val result = StringBuilder()
    val startPos = if (use_peek) {
      peek
    } else {
      position
    }
    var i = 0
    while (startPos + i < elements.size) {
      if (predicate.test(elements[startPos + i])) {
        result.append(elements[startPos + i])
      } else break
      i += 1
    }
    advance(i, consume, use_peek)
    return result.toString()
  }

  fun nextChars(nb: Int, consume: Boolean, use_peek: Boolean, fail_on_not_reach: Boolean): Optional<String> {
    val result = StringBuilder()
    val start_pos = if (use_peek) {
      peek
    } else {
      position
    }
    if (start_pos + nb >= elements.size && fail_on_not_reach) return Optional.empty()
    var nbToAdvance = nb
    for (i in 0 until nb) {
      if (start_pos + i < elements.size) {
        result.append(elements[start_pos + i])
      } else {
        nbToAdvance = i
        break
      }
    }
    advance(nbToAdvance, consume, use_peek)
    return Optional.of(result.toString())
  }

  fun searchConsumeChars(predicate: Predicate<Char>): String = searchNextChars(consume = true, use_peek = false, predicate = predicate)
  fun consumeChars(nb: Int, fail_on_not_reach: Boolean): Optional<String> = nextChars(
    nb,
    consume = true,
    use_peek = false,
    fail_on_not_reach = fail_on_not_reach
  )

  fun peekChar(): Optional<Char> {
    val result = Optional.ofNullable(elements.getOrNull(peek));
    result.ifPresent { peek += 1 }
    return result
  }

  fun peekChars(nb: Int, fail_on_not_reach: Boolean): Optional<String> =
    nextChars(nb, consume = false, use_peek = true, fail_on_not_reach = fail_on_not_reach)

  fun peekSearch(str: String): Boolean =
    nextChars(str.length, consume = false, use_peek = true, fail_on_not_reach = true).map(str::equals).orElse(false)

  fun peekSearchChar(chr: Char): Boolean = peekChar().map { it == chr }.orElse(false)
  fun peekSearchChars(predicate: Predicate<Char>): String = searchNextChars(consume = false, use_peek = true, predicate = predicate)
}

//class TokenScanner(elements: List<OneLineObject<Token>>) : Scanner<OneLineObject<Token>>(elements) {
//  fun peek_or_eof(): OneLineObject<Token> {}
//  fun peek_rewind_not_eof(amount: Int) {}
//  fun peek_previous_coords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {}
//  fun peek_coords(): Triple<Int, Int, Int> {}
//  fun peek_next_coords(): Pair<Pair<Int, Int>, Pair<Int, Int>> {}
//  fun consume_or_eof(): OneLineObject<Token> {}
//  fun consume_rewind_not_eof(amount: Int) {}
//  fun consume_coords(): Pair<Int, Int> {}
//  fun consume_next_coords(): Pair<Int, Int> {}
//}

private fun clamp(lower: Int, x: Int, upper: Int) = if (x < lower) lower else if (x > upper) upper else x
