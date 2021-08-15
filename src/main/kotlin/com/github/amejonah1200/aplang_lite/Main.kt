package com.github.amejonah1200.aplang_lite

import com.github.amejonah1200.aplang_lite.parser.Parser
import com.github.amejonah1200.aplang_lite.tokenizer.ScanResult
import com.github.amejonah1200.aplang_lite.tokenizer.scan
import com.github.amejonah1200.aplang_lite.utils.CharScanner
import com.github.amejonah1200.aplang_lite.utils.TokenScanner
import com.github.amejonah1200.aplang_lite.utils.prettyPrintAST
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
        val scans = mutableListOf<Pair<String, ScanResult>>()
        for (file in files) {
          println("Scanning " + file.name)
          println(scan(CharScanner(Files.readString(file.toPath()))).let {Pair(file.name, it)}.also(scans::add).second.tokens.joinToString("\n"))
          println("-------------------------------------")
        }
        for (indexedScan in scans.withIndex()) {
          println("Parsing " + indexedScan.value.first)
          if (indexedScan.value.second.liteErrors.isNotEmpty()) println("Scan n" + indexedScan.index + " is not valid")
          else println(prettyPrintAST(Parser(TokenScanner(indexedScan.value.second.tokens)).program()))
          println("-------------------------------------")
        }
      } else {
        println("Scanning 1 file: " + args[0])
        val scan = scan(CharScanner(Files.readString(Path.of(args[0]))))
        println(scan.tokens.joinToString("\n"))
        if (scan.liteErrors.isNotEmpty()) println("Scan is not valid")
        else println(Parser(TokenScanner(scan.tokens)).program())
      }
    }
  }
}
