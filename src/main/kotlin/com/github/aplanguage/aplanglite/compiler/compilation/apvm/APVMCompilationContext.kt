package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.compilation.CompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.FieldCompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.MethodCompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Field
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Method

class APVMCompilationContext(val pool: Pool) : CompilationContext<APVMMethodContext, APVMFieldContext> {

  override fun methodCompilationContext(method: Method) = APVMMethodContext(pool)
  override fun fieldCompilationContext(field: Field) =  APVMFieldContext(pool)
}

class APVMMethodContext(val pool: Pool) : MethodCompilationContext {
  var resolvedRegisters: List<RegisterAllocator.Type> = listOf()
  var instructions: List<Instruction> = listOf()

  override fun compile(method: Method) {
    val frame = Frame(
      pool,
      method.parameters.map { it.localVariable ?: LocalVariable(it.name, it.type()) })
    frame.enterScope()
    val ins = method.exprs.flatMap { it.obj.visit(ExpressionToBytecodeVisitor(frame), null).instructions() }
    frame.leaveScope()
    instructions = ins
    resolvedRegisters = frame.registerAllocator.registers.map { it.type }
  }
}

class APVMFieldContext(val pool: Pool) : FieldCompilationContext {
  var instructions: List<Instruction>? = null
  override fun compile(field: Field) {
    instructions = field.expr?.obj?.visit(ExpressionToBytecodeVisitor(Frame(pool)), ResultTarget.Stack)?.instructions()
  }
}
