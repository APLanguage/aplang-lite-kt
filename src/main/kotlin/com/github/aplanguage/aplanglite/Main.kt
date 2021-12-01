package com.github.aplanguage.aplanglite

import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary
import com.github.aplanguage.aplanglite.parser.Parser
import com.github.aplanguage.aplanglite.tokenizer.scan
import com.github.aplanguage.aplanglite.utils.ASTPrinter
import com.github.aplanguage.aplanglite.utils.CharScanner
import com.github.aplanguage.aplanglite.utils.TokenScanner
import com.github.aplanguage.aplanglite.utils.Underliner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

object Main {

  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size == 3) {
      tokenizerTests(Path.of(args[0]))
      parserTests(Path.of(args[1]))
      // interpreterTests(Path.of(args[2]))
    }
  }

  private fun tokenizerTests(path: Path) {
    println("TOKENIZER:")
    return
    if (path.isDirectory()) {
      val files = path.toFile().walkTopDown().filter { it.isFile }.toList()
      println("Scanning " + files.size + " files in " + path)
      for (file in files) {
        println("Scanning " + file.name)
        val scan = scan(CharScanner(Files.readString(file.toPath())))
        if (scan.liteErrors.isNotEmpty()) {
          println("Errors found: ${scan.liteErrors}")
          println("-------------------------------------")
          continue
        }
        println(scan.tokens.joinToString("\n"))
        println("-------------------------------------")
      }
    } else {
      println("Scanning 1 file: $path")
      val scan = scan(CharScanner(Files.readString(path)))
      if (scan.liteErrors.isNotEmpty()) {
        println("Errors found: ${scan.liteErrors}")
        return
      }
      println(scan.tokens.joinToString("\n"))
    }
  }

  private fun parserTests(path: Path) {
    println("PARSER:")
    if (path.isDirectory()) {
      val files = path.toFile().walkTopDown().filter { it.isFile }.toList()
      println("Scanning " + files.size + " files in " + path)
      for (file in files) {
        println("Scanning " + file.name)
        val scan = scan(CharScanner(Files.readString(file.toPath())));
        if (scan.liteErrors.isNotEmpty()) {
          println("Errors found: ${scan.liteErrors}")
          println("-------------------------------------")
          continue
        }
        println("Parsing...")
        val underliner = Underliner(Files.readAllLines(file.toPath()))
        val code = Parser(TokenScanner(scan.tokens), underliner ).program()
//        println("As AST:")
//        ASTPrinter.print(code)
        if (code != null) {
//          println("Structure")
          val namespace = Namespace.ofProgram(null, code.obj)
//          ASTPrinter.print(namespace)
          println("Resolving...")
          namespace.resolve(setOf(StandardLibrary.STD_LIB))
//          ASTPrinter.print(namespace)
          println("Type checking...")
          namespace.typeCheck(setOf(StandardLibrary.STD_LIB)).forEach { (msg, areas)->
            for (area in areas) {
              underliner.underline(area)
            }
            println(msg)
          }
        }
        println("-------------------------------------")
      }
    } else {
      println("Scanning " + path)
      val scan = scan(CharScanner(Files.readString(path)));
      if (scan.liteErrors.isNotEmpty()) {
        println("Errors found: ${scan.liteErrors}")
        println("-------------------------------------")
        return
      }
      println("Parsing...")
      val code = Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(path))).program()
      println("As AST:")
      ASTPrinter.print(code)
      if (code != null) {
        println("Structure")
        val namespace = Namespace.ofProgram(null, code.obj)
        ASTPrinter.print(namespace)
        println("Resolving...")
        namespace.resolve(setOf(StandardLibrary.STD_LIB))
        ASTPrinter.print(namespace)
      }
      println("-------------------------------------")
    }
  }
}



