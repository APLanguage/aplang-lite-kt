package com.github.amejonah1200.aplang_lite.tokenizer

import com.github.amejonah1200.aplang_lite.utils.CharScanner
import java.math.BigInteger
import java.util.*

enum class ScanErrorType {
  NUMBER_FORMAT
}

data class ScanResult(val tokens: List<Token>, val liteErrors: List<ScanErrorType>)

class ParserException(msg: String) : RuntimeException(msg)

fun scan(scanner: CharScanner): ScanResult {
  scanner.reset()
  val list = mutableListOf<Token>()
  val errors = mutableListOf<ScanErrorType>()
  var posX = 0
  var posY = 0
  var buffer: Optional<String>
  while (true) {
    posX += scanner.searchConsumeChars {
      it.isWhitespace() && it != '\n'
    }.length
    if (scanner.peekSearchChar('\n')) {
      scanner.advancePosition(1)
      posX = 0
      posY += 1
      continue
    }
    buffer = scanner.peekChars(LONGEST_TOKEN_LENGTH, false)
    if (buffer.isEmpty) break
    else if (buffer.get()[0].isWhitespace()) continue
    else if (buffer.get()[0].isDigit()) {
      scanner.resetPeek()
      if (scanner.peekSearch("0x")) {
        scanner.advancePosition(2)
        val nb = scanner.searchConsumeChars {
          it.isDigit() || it in 'A'..'F' || it in 'a'..'f'
        }
        if (nb.isEmpty()) {
          list.add(Token.IntegerToken(BigInteger.ZERO))
          scanner.rewindPosition(1)
        } else {
          list.add(Token.IntegerToken(BigInteger(nb, 16)))
        }
      } else if (scanner.peekSearch("0b")) {
        scanner.advancePosition(2)
        val nb = scanner.searchConsumeChars(Char::isDigit)
        if (nb.isEmpty()) {
          list.add(Token.IntegerToken(BigInteger.ZERO))
          scanner.rewindPosition(1)
        } else {
          val bi = nb.toBigIntegerOrNull(2)
          if (bi == null) {
            list.add(Token.IntegerToken(BigInteger(nb)))
            errors.add(ScanErrorType.NUMBER_FORMAT)
          } else list.add(Token.IntegerToken(bi))
        }
      } else {
        val nb = scanner.searchConsumeChars(Char::isDigit)
        if (scanner.peekSearchChar('.') && scanner.peekSearchWithPredicate(Char::isDigit)) {
          scanner.advancePosition(1)
          list.add(Token.FloatToken(BigInteger(nb), BigInteger(scanner.searchConsumeChars(Char::isDigit))))
        } else list.add(Token.IntegerToken(BigInteger(nb)))
      }
      continue
    }
    when (buffer.get()[0]) {
      '"' -> {
        val match = Regex("\".*?\"(?<!\\\\\")").find(scanner.str, scanner.position)
          ?: throw ParserException("No closing quoting mark for string at $posY:$posX")
        list.add(Token.StringToken(match.value.substring(1, match.value.length - 1)))
        scanner.advancePosition(match.value.length)
      }
      '\'' -> {
        val match = Regex("'(?:\\\\[^\\\\]|.)'").find(scanner.str, scanner.position)
          ?: throw ParserException("No closing apostrophe mark for string at $posY:$posX")
        if (match.range.first != scanner.position) throw ParserException("No closing apostrophe mark for string at $posY:$posX")
        list.add(Token.CharToken(match.value.substring(1, match.value.length - 1)))
        scanner.advancePosition(match.value.length)
      }
      else -> {
        val tkOpt = parseToken(buffer.get())
        tkOpt.ifPresentOrElse(
          {
            scanner.advancePosition(LONGEST_TOKEN_LENGTH)
            posX += it.length()
            if (it is Token.SignToken && it.codeToken == CodeToken.SLASH_SLASH) {
              scanner.searchConsumeChars { chr -> chr != '\n' }
              posY += 1
              posX = 0
            } else list.add(it)
          }) {
          throw ParserException("Unknown char '${buffer.get()[0]}' at $posY:$posX")
        }
      }
    }
  }
  return ScanResult(list, errors)
}

