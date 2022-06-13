package com.github.aplanguage.aplanglite

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.CompileResult
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.CompileResult.Failure.MultipleInput
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.CompileResult.Failure.SingleInput
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.APLangFile
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.compileSingleInputIntoAPLangFile
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.parser.ParseResult
import com.github.aplanguage.aplanglite.parser.ParserException
import com.github.aplanguage.aplanglite.utils.Underliner

object Main {

  @JvmStatic
  fun main(args: Array<String>) {
    StandardLibrary.STD_LIB.path()
    val str = """
      fn main() {
         var person = Person()
         var i: I32 = 0
         i += 3
         println(person)
         println("Before: " + person.name)
         person.name = "AP"
         println("After: " + person.name)
         println(person.sayHi("everyone"))
      }

      class Person {
        var name: String = "Default"

        fn sayHi(target: String) : String {
          return name + ": Hi, " + target + "!"
        }
      }
    """.trimIndent()
//    val channel = FileChannel.open(Path.of("comp.bin"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
//    compile(str).write(channel)
//    channel.close()
    compile(str)?.print()
  }

  private fun compile(code: String): APLangFile? {
    val underliner = Underliner(code.lines())
    try {
      when (val result = compileSingleInputIntoAPLangFile(code)) {
        CompileResult.NoFiles -> println("No files to compile")
        is SingleInput.ParsingFailure -> {
          if (result.path == null) println("Parsing failed")
          else println("Parsing failed in file ${result.path}")
          when (val parsingError = result.error) {
            is ParseResult.ParserFailure -> {
              when (val errors = parsingError.errors) {
                is Either.Left -> {
                  errors.value.area?.also(underliner::underline)
                  errors.value.message?.also(::println)
                  System.out.flush()
                  errors.value.printStackTrace()
                  System.out.flush()
                }
                is Either.Right -> for (error in errors.value) {
                  underliner.underline(error.area)
                  error.message?.apply(::println)
                  System.out.flush()
                  error.exception.printStackTrace()
                  System.out.flush()
                }
              }
            }

            is ParseResult.TokenizerFailure -> {
              when (val errors = parsingError.errors) {
                is Either.Left -> {
                  errors.value.printStackTrace()
                  System.out.flush()
                }
                is Either.Right -> for (error in errors.value) {
                  underliner.underline(error)
                  println("ERROR: ${error.obj.name}")
                  System.out.flush()
                }
              }
            }
            else -> throw IllegalStateException("Unreachable")
          }
        }

        is SingleInput.TypeCheckFailure -> {
          if (result.path == null) println("Type checking failed")
          else println("Type checking failed in file ${result.path}")
          for (error in result.errors) {
            error.areas.forEach { underliner.underline(it) }
            error.message?.also { println("ERROR: $it") }
            error.printStackTrace()
            System.out.flush()
          }
        }

        is MultipleInput -> throw IllegalStateException("Unreachable")
        is CompileResult.SuccessAPLangFile -> return result.apLangFile
      }
    } catch (e: ParserException) {
      e.area?.also(underliner::underline)
      e.printStackTrace()
    } catch (e: TypeCheckException) {
      e.areas.forEach { underliner.underline(it) }
      e.printStackTrace()
    }
    System.out.flush()
    println("Failed to compile")
    return null
  }
}



