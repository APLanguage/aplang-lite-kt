package com.github.aplanguage.aplanglite

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.APLangFile
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeCheckException
import com.github.aplanguage.aplanglite.parser.Parser
import com.github.aplanguage.aplanglite.parser.ParserException
import com.github.aplanguage.aplanglite.tokenizer.scan
import com.github.aplanguage.aplanglite.utils.CharScanner
import com.github.aplanguage.aplanglite.utils.TokenScanner
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
      val parser = Parser(TokenScanner(scan(CharScanner(code)).tokens), underliner)
      val program = parser.program()
      if (program == null) {
        println("Failed to parse")
        return null
      }
      if (parser.errors().isNotEmpty()) {
        parser.errors().forEach {
          underliner.underline(it.area)
          it.message?.also(::println)
          it.exception.printStackTrace()
          System.out.flush()
        }
        println("Failed to parse")
        return null
      }
      val pool = Pool()
      StandardLibrary.STD_LIB.path()
      val namespace = Namespace.ofProgram("", program.obj)
      namespace.resolve(setOf(StandardLibrary.STD_LIB))
      val typecheckErrors = namespace.typeCheck(setOf(StandardLibrary.STD_LIB))
      if (typecheckErrors.isNotEmpty()) {
        typecheckErrors.forEach { exception ->
          exception.areas.forEach(underliner::underline)
          exception.printStackTrace()
          System.out.flush()
        }
        println("Failed to typecheck")
        return null
      }
      namespace.compile(pool)
      return APLangFile.ofNamespace(pool, namespace)
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



