package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.APLangFile
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.parser.Parser
import com.github.aplanguage.aplanglite.parser.ParserError
import com.github.aplanguage.aplanglite.tokenizer.ScanErrorType
import com.github.aplanguage.aplanglite.tokenizer.scan
import com.github.aplanguage.aplanglite.utils.OneLineObject
import com.github.aplanguage.aplanglite.utils.TokenScanner
import com.github.aplanguage.aplanglite.utils.Underliner
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

sealed class ParseResult {
  data class Success(val ast: Namespace?) : ParseResult()
  data class TokenizerFailure(val errors: List<OneLineObject<ScanErrorType>>) : ParseResult()
  data class ParserFailure(val errors: List<ParserError>) : ParseResult()
}

sealed class CompileResult {
  data class SuccessAPLangFile(val apLangFile: APLangFile) : CompileResult()
  sealed class Failure : CompileResult() {
    sealed class SingleInput(val path: Path?) : Failure() {
      class ParsingFailure(val error: ParseResult, path: Path? = null) : SingleInput(path)
      class TypeCheckFailure(val errors: List<TypeCheckException>, path: Path? = null) : SingleInput(path)
    }

    sealed class MultipleInput : CompileResult() {
      data class ParsingFailure(val errors: List<Pair<Path, ParseResult>>) : MultipleInput()
      data class TypeCheckFailure(val errors: List<Pair<Path, List<TypeCheckException>>>) : MultipleInput()
    }
  }

  object NoFiles : CompileResult()
}

object APVMCompiler {

  fun compileSingleFileIntoAPLangFile(path: Path): CompileResult {
    require(!path.isDirectory()) { "Path must be a file" }
    return compileSingleInputIntoAPLangFile(path.readText(), path)
  }

  fun compileSingleInputIntoAPLangFile(source: String, path: Path? = null): CompileResult {
    StandardLibrary.STD_LIB.path() // initialize standard library
    return when (val result = parse(source)) {
      is ParseResult.Success -> {
        if (result.ast == null) CompileResult.NoFiles
        else {
          result.ast.resolve(setOf(StandardLibrary.STD_LIB))
          val typeCheckResults = result.ast.typeCheck(setOf(StandardLibrary.STD_LIB))
          if (typeCheckResults.isNotEmpty()) CompileResult.Failure.SingleInput.TypeCheckFailure(typeCheckResults, path)
          else {
            val pool = Pool()
            result.ast.compile(APVMCompilationContext(pool))
            return CompileResult.SuccessAPLangFile(APLangFile.ofNamespace(pool, result.ast))
          }
        }
      }

      else -> CompileResult.Failure.SingleInput.ParsingFailure(result, path)
    }
  }

  fun parse(source: Path): List<Pair<Path, ParseResult>> {
    if (source.isDirectory()) {
      val results = mutableListOf<Pair<Path, ParseResult>>()
      Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (file.endsWith(".aplang")) {
            results.add(file to parse(file.readText()))
          }
          return FileVisitResult.CONTINUE
        }
      })
      return results
    }
    if (source.endsWith(".aplang")) {
      return listOf(source to parse(source.readText()))
    }
    return listOf()
  }

  fun parse(source: String): ParseResult {
    val result = scan(source)
    if (result.liteErrors.isNotEmpty()) {
      return ParseResult.TokenizerFailure(result.liteErrors)
    }
    val underliner = Underliner(source.lines())
    val parser = Parser(TokenScanner(result.tokens), underliner)
    val ast = parser.program()
    if (parser.errors.isNotEmpty()) {
      return ParseResult.ParserFailure(parser.errors)
    }
    return ParseResult.Success(ast?.let { Namespace.ofProgram(it.obj) })
  }
}
