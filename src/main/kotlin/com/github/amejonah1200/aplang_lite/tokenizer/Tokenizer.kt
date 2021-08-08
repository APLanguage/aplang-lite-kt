package com.github.amejonah1200.aplang_lite.tokenizer

import com.github.amejonah1200.aplang_lite.utils.CharScanner
import java.util.*

data class ScanResult(val tokens: List<Token>)

class ParserException(msg: String) : RuntimeException(msg)

fun scan(scanner: CharScanner): ScanResult {
  scanner.reset()
  val list = mutableListOf<Token>()
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
    buffer = scanner.consumeChars(LONGEST_TOKEN_LENGTH, false)
    if (buffer.isEmpty) break
    else if (buffer.get()[0].isWhitespace()) continue
    else if (buffer.get()[0].isDigit()) {
      //parse Digit
    }
    when (buffer.get()[0]) {
      '"' -> {
        scanner.rewindPosition(buffer.get().length)
        val match = Regex("\".*?\"(?<!\\\\\")").find(scanner.str, scanner.position)
          ?: throw ParserException("No closing quoting mark for string at $posY:$posX")
        list.add(Token.StringToken(match.value.substring(1, match.value.length - 1)))
        scanner.advancePosition(match.value.length)
      }
      '\'' -> {
        scanner.rewindPosition(buffer.get().length)

      }
      else -> {
        val tkOpt = parseToken(buffer.get())
        tkOpt.ifPresentOrElse(
          {
            scanner.rewindPosition(LONGEST_TOKEN_LENGTH - it.length())
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
  return ScanResult(list)
}

