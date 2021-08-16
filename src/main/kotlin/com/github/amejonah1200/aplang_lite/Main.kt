package com.github.amejonah1200.aplang_lite

import com.github.amejonah1200.aplang_lite.parser.Parser
import com.github.amejonah1200.aplang_lite.tokenizer.ScanResult
import com.github.amejonah1200.aplang_lite.tokenizer.scan
import com.github.amejonah1200.aplang_lite.utils.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

object Main {

  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size == 2) {
      val tokenizerTests = Path.of(args[0])
      if (tokenizerTests.isDirectory()) {
        val files = tokenizerTests.toFile().walkTopDown().filter { it.isFile }.toList()
        println("Scanning " + files.size + " files in " + args[0])
        for (file in files) {
          println("Scanning " + file.name)
          println(scan(CharScanner(Files.readString(file.toPath()))).tokens.joinToString("\n"))
          println("-------------------------------------")
        }
      } else {
        println("Scanning 1 file: " + args[0])
        println(scan(CharScanner(Files.readString(Path.of(args[0])))).tokens.joinToString("\n"))
      }

      val parserTests = Path.of(args[1])
      if (parserTests.isDirectory()) {
        val files = parserTests.toFile().walkTopDown().filter { it.isFile }.toList()
        println("Scanning " + files.size + " files in " + args[0])
        val scans = mutableListOf<Pair<File, ScanResult>>()
        for (file in files) {
          println("Scanning " + file.name)
          val underliner = Underliner(Files.readAllLines(file.toPath()))
          println(scan(CharScanner(Files.readString(file.toPath()))).let { Pair(file, it) }.also(scans::add).second.tokens.joinToString("\n"))
          println("-------------------------------------")
        }
        for (indexedScan in scans.withIndex()) {
          println("Parsing " + indexedScan.value.first.name)
          if (indexedScan.value.second.liteErrors.isNotEmpty()) println("Scan n" + indexedScan.index + " is not valid")
          else {
            val program =
              Parser(TokenScanner(indexedScan.value.second.tokens), Underliner(Files.readAllLines(indexedScan.value.first.toPath()))).program()
//            println(prettyPrintAST(program))
////            println()
            ASTPrinter.print(program)
//            val underliner = Underliner(Files.readAllLines(indexedScan.value.first.toPath()))
//            program?.obj?.declarations?.forEach { underliner.underline(it.startCoords(), it.endCoords()) }
          }
          println("-------------------------------------")
        }
      } else {
        println("Scanning 1 file: " + args[0])
        val scan = scan(CharScanner(Files.readString(Path.of(args[0]))))
        println(scan.tokens.joinToString("\n"))
        if (scan.liteErrors.isNotEmpty()) println("Scan is not valid")
        else println(Parser(TokenScanner(scan.tokens), Underliner(Files.readAllLines(Path.of(args[0])))).program())
      }
    }
  }
}
