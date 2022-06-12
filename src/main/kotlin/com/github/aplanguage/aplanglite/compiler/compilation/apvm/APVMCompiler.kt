package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.APLangFile
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.parser.ParseResult
import com.github.aplanguage.aplanglite.parser.parse
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readText


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
