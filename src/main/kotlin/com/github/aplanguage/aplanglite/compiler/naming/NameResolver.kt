package com.github.aplanguage.aplanglite.compiler.naming

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.compilation.RegisterAllocator
import com.github.aplanguage.aplanglite.compiler.stdlib.StandardLibrary.STD_LIB
import com.github.aplanguage.aplanglite.parser.Parser
import com.github.aplanguage.aplanglite.tokenizer.scan
import com.github.aplanguage.aplanglite.utils.CharScanner
import com.github.aplanguage.aplanglite.utils.GriddedObject
import com.github.aplanguage.aplanglite.utils.TokenScanner
import com.github.aplanguage.aplanglite.utils.Underliner
import java.io.File
import java.nio.charset.StandardCharsets


data class LocalVariable(val name: String, val type: Namespace.Class) : Namespace.Typeable, Namespace.Settable {
  var register: RegisterAllocator.Register? = null
  override fun type() = type
  override fun isStatic() = true
}

class NameResolver {

  val namespace: Namespace
  val otherNamespaces: List<Namespace>
  val frame: Frame

  constructor(namespace: Namespace, otherNamespaces: List<Namespace>, frame: Frame) {
    this.namespace = namespace
    this.otherNamespaces = otherNamespaces
    this.frame = frame
  }

  constructor(file: File, otherNamespaces: List<Namespace>, frame: Frame) {
    val result = scan(CharScanner(file.readText(StandardCharsets.UTF_8)))
    val underliner = Underliner(file.readLines(StandardCharsets.UTF_8))
    if (result.liteErrors.isEmpty()) {
      val program = Parser(TokenScanner(result.tokens), underliner).program() ?: throw Exception("No program found")
      namespace = Namespace.ofProgram(null, program.obj)
      this.otherNamespaces = otherNamespaces
      this.frame = frame
    } else {
      for (error in result.liteErrors) {
        underliner.underline(error)
        println(error.obj)
      }
      throw Exception("Failed to parse file")
    }
  }


  fun resolveField(name: String): Namespace.Field? =
    namespace.resolveFieldsInScope(name).firstOrNull() ?: otherNamespaces.firstNotNullOfOrNull { it.root().findFields(name).firstOrNull() }

  fun resolveLocalVariable(name: String): LocalVariable? = frame.localVariables.lastOrNull { it.name == name }

  fun resolveMethod(name: String): List<Namespace.Method> =
    namespace.resolveMethodsInScope(name) + resolveClass(name).map { it.constructor } + otherNamespaces.flatMap { it.root().findMethods(name) }

  fun resolveClass(name: String): List<Namespace.Class> = namespace.resolveClassPath(name) + STD_LIB.findClasses(name)
}

class Frame(val expectedType: Namespace.Class? = null) {
  val localVariables = mutableListOf<LocalVariable>()
  private val sections = mutableListOf<Int>()

  fun startSection() {
    sections.add(localVariables.size)
  }

  fun endSection() {
    repeat(localVariables.size - (sections.removeLastOrNull() ?: return)) { localVariables.removeLast() }
  }

  fun register(name: String, type: Namespace.Class): LocalVariable = LocalVariable(name, type).also { localVariables.add(it) }
}

fun Either<GriddedObject<String>, Namespace.Class>.resolve(nameResolver: NameResolver) =
  fold({ nameResolver.resolveClass(it.obj).firstOrNull() ?: throw Exception("Class not found: ${it.obj} at ${it.area()}") }, { it })
