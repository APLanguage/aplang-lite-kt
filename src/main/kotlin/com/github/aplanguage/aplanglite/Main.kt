package com.github.aplanguage.aplanglite

import com.github.aplanguage.aplanglite.interpreter.Interpreter
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
      println("TOKENIZER:")
      tokenizerTests(Path.of(args[0]))
      println("PARSER:")
      parserTests(Path.of(args[1]))
      println("INTERPRETER:")
      interpreterTests(Path.of(args[2]))
    }
  }

  private fun tokenizerTests(path: Path) {
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
        ASTPrinter.print(Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(file.toPath()))).program())
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
      ASTPrinter.print(Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(path))).program())
      println("-------------------------------------")
    }
  }

  private fun interpreterTests(path: Path) {
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
        val program = Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(file.toPath()))).program()
        if (program == null) {
          println("Could not be parsed!")
          continue
        }
        println("Executing....")
        Interpreter().compileAndRun(program.obj)
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
      val program = Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(path))).program()
      if (program == null) {
        println("Could not be parsed!")
        return
      }
      println("Executing....")
      Interpreter().compileAndRun(program.obj)
      println("-------------------------------------")
    }
  }
}

