package com.github.aplanguage.aplanglite.tokenizer

import com.github.aplanguage.aplanglite.compiler.stdlib.PrimitiveType
import com.github.aplanguage.aplanglite.tokenizer.Token.ValueToken.LiteralToken
import com.github.aplanguage.aplanglite.utils.CharScanner
import com.github.aplanguage.aplanglite.utils.OneLineObject

enum class ScanErrorType {
  NUMBER_FORMAT
}

data class ScanResult(val tokens: List<OneLineObject<Token>>, val liteErrors: List<OneLineObject<ScanErrorType>>)

class TokenizerException(msg: String) : RuntimeException(msg)

fun scan(source: String) = scan(CharScanner(source))

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
      tokens.add(OneLineObject(posX, posY, parseNumberLiteral(scanner, posX, posY), scanner.position - startPos))
      posX += scanner.position - startPos
      continue
    }
    when (buffer[0]) {
      '"' -> {
        val match = Regex("\".*?\"(?<!\\\\\")").find(scanner.str, scanner.position)
          ?: throw TokenizerException("No closing quoting mark for string at $posY:$posX")
        scanner.advancePosition(match.value.length)
        tokens.add(
          OneLineObject(
            posX,
            posY,
            LiteralToken.StringToken(match.value.substring(1, match.value.length - 1)),
            scanner.position - startPos
          )
        )
        posX += scanner.position - startPos
      }
      '\'' -> {
        val match = Regex("'(?:\\\\[^\\\\]|.)'").find(scanner.str, scanner.position)
          ?: throw TokenizerException("No closing apostrophe mark for char at $posY:$posX")
        if (match.range.first != scanner.position) throw TokenizerException("No closing apostrophe mark for string at $posY:$posX")
        scanner.advancePosition(match.value.length)
        tokens.add(
          OneLineObject(
            posX,
            posY,
            LiteralToken.CharToken(match.value.substring(1, match.value.length - 1)),
            scanner.position - startPos
          )
        )
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
            throw TokenizerException("Unknown char '${buffer[0]}' at $posY:$posX")
          }
          tokens.add(OneLineObject(posX, posY, Token.IdentifierToken(identifier), scanner.position - startPos))
          posX += scanner.position - startPos
        }
      }
    }
  }
  return ScanResult(tokens, errors)
}

private fun parseNumberLiteral(scanner: CharScanner, posX: Int, posY: Int): LiteralToken {
  return if (scanner.peekSearch("0x", true)) {
    parseNumberLiteral(scanner, "0x", { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '_' }, "hexadecimal", "0-9a-fA-F", posX, posY)
  } else if (scanner.peekSearch("0b", true)) {
    parseNumberLiteral(scanner, "0b", { it.isDigit() || it == '_' }, "binary", "0 or 1", posX, posY)
  } else parseNumberLiteral(scanner, "", { it.isDigit() || it == '_' }, "decimal", "digit", posX, posY)
}

private fun parseNumberLiteral(
  scanner: CharScanner,
  prefix: String,
  filter: (Char) -> Boolean,
  itsA: String, expectedOne: String,
  posX: Int, posY: Int
): LiteralToken {
  val startPos = scanner.position
  scanner.advancePosition(prefix.length)
  val nb = scanner.searchConsumeChars(filter)
  if (nb.isEmpty()) {
    throw TokenizerException("Invalid $itsA number at $posY:${posX + scanner.position - startPos}. Expected at least one $expectedOne")
  }
  return nbToTk(prefix, nb.replace("_", ""), scanner.searchConsumeChars { it.isDigit() || it == '_' || it.uppercaseChar() in 'A'..'Z' }, posX, posY)
}

private fun nbToTk(prefix: String, nb: String, suffix: String, posX: Int, posY: Int): LiteralToken {
  val type = suffix.uppercase().let { suffix ->
    when (suffix) {
      "D", "F64" -> PrimitiveType.DOUBLE
      "F", "F32" -> PrimitiveType.FLOAT
      "" -> PrimitiveType.I32
      else -> PrimitiveType.INTEGERS.firstOrNull { it.name == suffix }
    }
  } ?: throw TokenizerException("Invalid number suffix '$suffix' at $posY:$posX")
  if (type.isFloatingPoint()) {
    if (prefix.isNotEmpty()) throw TokenizerException("Invalid number prefix '$prefix' at $posY:$posX for floating point number.")
    return LiteralToken.FloatToken.tokenOf(type, nb.toBigDecimal())
  }
  val bigInt = if (prefix == "0x") nb.toBigInteger(16) else if (prefix == "0b") nb.toBigInteger(2) else nb.toBigInteger()
  if (bigInt.bitLength() > type.registerType.byteSize) {
    throw TokenizerException("Number '$nb' is too big for ${type.name} at $posY:$posX")
  }
  return LiteralToken.IntegerToken.tokenOf(type, bigInt)
}

