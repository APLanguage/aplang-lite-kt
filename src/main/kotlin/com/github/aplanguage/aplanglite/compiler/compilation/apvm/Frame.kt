package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Class

class Frame {
  val code = mutableListOf<BytecodeChunk>()
  val pool: Pool
  val registerAllocator: RegisterAllocator
  val arguments: List<LocalVariable>
  val localVariables = mutableListOf<LocalVariable>()
  private val sections = mutableListOf<Int>()

  fun enterScope() {
    sections.add(localVariables.size)
    registerAllocator.enterScope()
  }

  fun register(name: String, type: Class): LocalVariable {
    val variable = LocalVariable(name, type)
    variable.register = registerAllocator.register(type)
    localVariables.add(variable)
    return variable
  }

  fun variable(name: String): LocalVariable? = localVariables.firstOrNull { it.name == name } ?: arguments.firstOrNull { it.name == name }

  fun leaveScope() {
    registerAllocator.leaveScope()
    repeat(localVariables.size - (sections.removeLastOrNull() ?: return)) { localVariables.removeLast() }
  }

  constructor(pool: Pool, arguments: List<LocalVariable>, self: Class? = null) {
    this.pool = pool
    this.arguments = if (self != null) listOf(LocalVariable("this", self)) + arguments else arguments
    registerAllocator = RegisterAllocator()
    this.arguments.forEach { it.register = registerAllocator.register(it) }
  }

  constructor(pool: Pool, self: Class? = null) : this(pool, emptyList(), self)

  fun push(bytecode: BytecodeChunk) {
    code.add(bytecode)
  }
}
