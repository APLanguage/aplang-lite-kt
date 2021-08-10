package parser

import com.github.amejonah1200.aplang_lite.tokenizer.*
import com.github.amejonah1200.aplang_lite.utils.CharScanner
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ParserTest {

  @Test
  fun parseTokenTest() {
    for (keyword in KEYWORDS) {
      val tk = parseToken(keyword)
      assert(tk.isPresent)
    }
  }

  @TestFactory
  fun parseSignTokenTest(): List<DynamicTest> {
    return CodeToken.values().map { sign ->
      dynamicTest("Test $sign with ${sign.stringRepresentation}") {
        val scanResult = scan(CharScanner(sign.stringRepresentation))
        assert(scanResult.liteErrors.isEmpty())
        if (sign == CodeToken.SLASH_SLASH) {
          assertEquals(0, scanResult.tokens.size)
        } else {
          assertEquals(1, scanResult.tokens.size)
          assertContentEquals(listOf(sign), scanResult.tokens.map { it.obj as Token.SignToken }.map { it.codeToken }.toList())
        }
      }
    }
  }
}
