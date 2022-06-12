package com.github.aplanguage.aplanglite.parser

import com.github.aplanguage.aplanglite.compiler.naming.namespace.Namespace
import com.github.aplanguage.aplanglite.tokenizer.ScanErrorType
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.OneLineObject

class ParserException(msg: String, val area: Area? = null) : RuntimeException(msg)

data class ParserError(val exception: ParserException, val area: Area, val message: String? = null)

sealed class ParseResult {
  data class Success(val ast: Namespace?) : ParseResult()
  data class TokenizerFailure(val errors: List<OneLineObject<ScanErrorType>>) : ParseResult()
  data class ParserFailure(val errors: List<ParserError>) : ParseResult()
}
