package com.github.amejonah1200.aplang_lite.tokenizer

import com.github.amejonah1200.aplang_lite.utils.CharScanner
import com.github.amejonah1200.aplang_lite.utils.OneLineObject
import java.math.BigInteger

enum class ScanErrorType {
  NUMBER_FORMAT
}

data class ScanResult(val tokens: List<OneLineObject<Token>>, val liteErrors: List<OneLineObject<ScanErrorType>>)

class ParserException(msg: String) : RuntimeException(msg)

fun scan(scanner: CharScanner): ScanResult {
  scanner.reset()
  val tokens = mutableListOf<OneLineObject<Token>>()
  val errors = mutableListOf<OneLineObject<ScanErrorType>>()
  var posX = 0
  var posY = 0
  var buffer: String?
  while (!scanner.isPositionEOF()) {
    posX += scanner.searchConsumeChars {
      it.isWhitespace() && it != '\n'
    }.length
    if (scanner.peekSearchChar('\n')) {
      scanner.advancePosition(1)
      posX = 0
      posY += 1
      continue
    }
    scanner.resetPeek()
    val startPos = scanner.position
    buffer = scanner.peekChars(LONGEST_TOKEN_LENGTH, false)
    if (buffer == null || buffer.isEmpty()) break
    else if (buffer[0].isWhitespace()) continue
    else if (buffer[0].isDigit()) {
      scanner.resetPeek()
      val tk = if (scanner.peekSearch("0x")) {
        scanner.advancePosition(2)
        val nb = scanner.searchConsumeChars {
          it.isDigit() || it in 'A'..'F' || it in 'a'..'f'
        }
        if (nb.isEmpty()) {
          scanner.rewindPosition(1)
          Token.IntegerToken(BigInteger.ZERO)
        } else {
          Token.IntegerToken(BigInteger(nb, 16))
        }
      } else if (scanner.peekSearch("0b")) {
        scanner.advancePosition(2)
        val nb = scanner.searchConsumeChars(Char::isDigit)
        if (nb.isEmpty()) {
          scanner.rewindPosition(1)
          Token.IntegerToken(BigInteger.ZERO)
        } else {
          val bi = nb.toBigIntegerOrNull(2)
          if (bi == null) {
            errors.add(OneLineObject(posX, posY, ScanErrorType.NUMBER_FORMAT, scanner.position - startPos))
            Token.IntegerToken(BigInteger(nb))
          } else Token.IntegerToken(bi)
        }
      } else {
        val nb = scanner.searchConsumeChars(Char::isDigit)
        if (scanner.peekSearchChar('.') && scanner.peekSearchWithPredicate(Char::isDigit)) {
          scanner.advancePosition(1)
          Token.FloatToken(BigInteger(nb), BigInteger(scanner.searchConsumeChars(Char::isDigit)))
        } else Token.IntegerToken(BigInteger(nb))
      }
      tokens.add(OneLineObject(posX, posY, tk, scanner.position - startPos))
      posX += scanner.position - startPos
      continue
    }
    when (buffer[0]) {
      '"' -> {
        val match = Regex("\".*?\"(?<!\\\\\")").find(scanner.str, scanner.position)
          ?: throw ParserException("No closing quoting mark for string at $posY:$posX")
        scanner.advancePosition(match.value.length)
        tokens.add(OneLineObject(posX, posY, Token.StringToken(match.value.substring(1, match.value.length - 1)), scanner.position - startPos))
        posX += scanner.position - startPos
      }
      '\'' -> {
        val match = Regex("'(?:\\\\[^\\\\]|.)'").find(scanner.str, scanner.position)
          ?: throw ParserException("No closing apostrophe mark for char at $posY:$posX")
        if (match.range.first != scanner.position) throw ParserException("No closing apostrophe mark for string at $posY:$posX")
        scanner.advancePosition(match.value.length)
        tokens.add(OneLineObject(posX, posY, Token.CharToken(match.value.substring(1, match.value.length - 1)), scanner.position - startPos))
        posX += scanner.position - startPos
      }
      else -> {
        val foundToken = parseToken(buffer)
        if (foundToken != null) {
          val tk = foundToken.first
          if (tk is Token.SignToken && tk.codeToken == CodeToken.SLASH_SLASH) {
            scanner.searchConsumeChars { chr -> chr != '\n' }
            posY += 1
            posX = 0
          } else {
            tokens.add(OneLineObject(posX, posY, tk, foundToken.second))
            posX += foundToken.second
            scanner.advancePosition(foundToken.second)
          }
        } else {
          val identifier = scanner.searchConsumeChars { it.isLetterOrDigit() || it == '_' }
          if (identifier.isEmpty()) {
            throw ParserException("Unknown char '${buffer[0]}' at $posY:$posX")
          }
          tokens.add(OneLineObject(posX, posY, Token.IdentifierToken(identifier), scanner.position - startPos))
          posX += scanner.position - startPos
        }
      }
    }
  }
  return ScanResult(tokens, errors)
}

