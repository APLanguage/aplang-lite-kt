package com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction.*
import com.github.aplanguage.aplanglite.tokenizer.Token

interface InstructionVisitor<C, R> {
  fun visit(instruction: NoOp, context: C): R
  fun visit(instruction: Call, context: C): R
  fun visit(instruction: Return, context: C): R
  fun visit(instruction: If, context: C): R
  fun visit(instruction: InvNegion, context: C): R
  fun visit(instruction: Conversion, context: C): R
  fun visit(instruction: Math, context: C): R
  fun visit(instruction: LoadStore, context: C): R
  fun visit(instruction: GetPut, context: C): R
  fun visit(instruction: Oop, context: C): R
  fun visit(instruction: PopStack, context: C): R
  fun visit(instruction: DuplicateStack, context: C): R
  fun visit(instruction: SwapStack, context: C): R
  fun visit(instruction: Constant.DirectInteger, context: C): R
  fun visit(instruction: Constant.DirectFloat, context: C): R
  fun visit(instruction: Constant.Indirect, context: C): R
  fun visit(label: Label, context: C): R
}

class BytecodeStringifier(private val pool: Pool) : InstructionVisitor<Unit, String> {
  override fun visit(instruction: NoOp, context: Unit) = "NOOP"

  override fun visit(instruction: Call, context: Unit): String {
    val attr = if (instruction.static && instruction.ignore) "SI" else if (instruction.static) "S" else if (instruction.ignore) "I" else ""
    return "CALL [$attr] ${pool.referencePool[instruction.index.toInt()].stringify()}"
  }

  override fun visit(instruction: Return, context: Unit): String {
    return "RETU [${if (instruction.target) "Register #${instruction.index}" else "Stack"}]"
  }

  override fun visit(instruction: If, context: Unit): String {
    return "IF   " + when (instruction.condition) {
      If.IfCondition.JUMP_ABSOLUTE -> return "JUMP ABS ${instruction.location} (0x${
        instruction.location.toString(16)
      })${if (instruction.wide) " W" else ""}"
      If.IfCondition.MINUS_ONE -> "<"
      If.IfCondition.ZERO -> "="
      If.IfCondition.ZERO_MINUS_ONE -> "<="
      If.IfCondition.ONE -> ">"
      If.IfCondition.ONE_MINUS_ONE -> "!="
      If.IfCondition.ONE_ZERO -> ">="
      If.IfCondition.JUMP_RELATIVE -> return "JUMP REL $${(if (instruction.location.toShort() > 0) "+" else "")}${instruction.location.toShort()} (0x${
        instruction.location.toShort().toString(16)
      }) ${if (instruction.wide) "W" else ""}"
    } + " $${
      instruction.location.toShort().let { (if (it > 0) "+" else "") + it.toString() + " (0x${it.toString(16)})" }
    }${if (instruction.wide) " W" else ""} "
  }

  override fun visit(instruction: InvNegion, context: Unit): String {
    return "${if (instruction.mode) "INV  " else "NEG  "} ${instruction.dataType.name}"
  }

  override fun visit(instruction: Conversion, context: Unit): String {
    return "CONV [${instruction.fromType.name} -> ${instruction.toType.name}] To ${if (instruction.target) "Register #${instruction.index}" else "Stack"}"
  }

  override fun visit(instruction: Math, context: Unit): String {
    return "MATH ${instruction.type.name} ${instruction.operation.name} (${
      if (instruction.firstOperandRegister) "Register #${instruction.firstOperandIndex}" else "Stack"
    }, ${
      if (instruction.secondOperandRegister) "Register #${instruction.secondOperandIndex}" else "Stack"
    }) -> ${if (instruction.targetRegister) "Register #${instruction.targetIndex}" else "Stack"}"
  }

  override fun visit(instruction: LoadStore, context: Unit): String {
    return (if (instruction.mode) "STOR #" else "LOAD #") + instruction.index
  }

  override fun visit(instruction: GetPut, context: Unit): String {
    val fromTo = if (instruction.register) "Register #${instruction.registerIndex}" else "Stack"
    val refStr = pool.referencePool[instruction.referenceIndex.toInt()].stringify()
    val attr = if (instruction.wide && instruction.reversed) "WR" else if (instruction.wide) "W" else if (instruction.reversed) "R" else ""
    return (if (instruction.mode) "GET $fromTo <- $refStr" else "PUT  $fromTo <- $refStr") + (" [${attr}]")
  }

  override fun visit(instruction: Oop, context: Unit): String {
    return "OOP  ${instruction.oop.name} ${pool.referencePool[instruction.index.toInt()].stringify()}"
  }

  override fun visit(instruction: PopStack, context: Unit): String {
    return "POP  ${instruction.entries + 1u}"
  }

  override fun visit(instruction: DuplicateStack, context: Unit): String {
    return "DUP  ${instruction.entries + 1u}"
  }

  override fun visit(instruction: SwapStack, context: Unit): String {
    return "SWAP ${instruction.entries + 1u}"
  }

  override fun visit(instruction: Constant.DirectInteger, context: Unit): String {
    return "DIST ${instruction.value.stringify()}"
  }

  override fun visit(instruction: Constant.DirectFloat, context: Unit): String {
    return "DFST ${instruction.value.stringify()}"
  }

  override fun visit(instruction: Constant.Indirect, context: Unit): String {
    return "ICST " + pool.constantPool[instruction.index.toInt()].stringify()
  }

  private fun ReferenceInfo.stringify(): String {
    return stringify(pool.referencePool)
  }

  private fun ConstantInfo.stringify(): String {
    return "#$id " + when (this) {
      is ConstantInfo.ByteConstant -> value.toString() + "B"
      is ConstantInfo.IntConstant -> value.toString() + "I"
      is ConstantInfo.LongConstant -> value.toString() + "L"
      is ConstantInfo.ShortConstant -> value.toString() + "S"
      is ConstantInfo.FloatConstant -> value.toString() + "F"
      is ConstantInfo.DoubleConstant -> value.toString() + "D"
      is ConstantInfo.StringConstant -> "\"${Token.ValueToken.LiteralToken.StringToken(string).stringify()}\""
    }
  }

  override fun visit(label: Label, context: Unit) = "LABEL ${label.hashCode()}"
}
