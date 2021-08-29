package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.parser.Expression

sealed class Structure {

  data class UseStructure(val path: String, val all: Boolean, val asOther: String?) : Structure()

  data class FunctionStructure(
    val identifier: String,
    val parameters: List<Expression.Type>,
    val type: Expression.Type?,
    val declaration: Expression.Declaration.FunctionDeclaration
  ) : Structure() {
    fun toCallableClassFunction() = ReturnValue.CallableValue.CallableFunctionValue.ClassMethodValue(identifier, this)
  }

  data class ClassStructure(
    val identifier: String,
    val superTypes: List<Expression.Type>,
    val structure: GlobalStructure
  ) : Structure() {
    fun buildScope() = Scope(
      structure.structures.filterIsInstance<VarStructure>().map {
        it.toFieldValue()
      }.associateBy { it.identifier }.toMutableMap(),
      structure.structures.filterIsInstance<FunctionStructure>().map {
        it.toCallableClassFunction()
      }.associateBy { it.identifier }
    )

  }

  data class VarStructure(
    val identifier: String,
    val type: Expression.Type?,
    var expression: Expression?,
    var value: ReturnValue?,
  ) : Structure() {
    fun evaluateValue(interpreter: Interpreter, scope: Scope): ReturnValue {
      return (value ?: expression?.let { interpreter.runExpression(scope, it); } ?: ReturnValue.Null).also { value = it }
    }

    fun toFieldValue() = ReturnValue.PropertiesNFunctionsValue.FieldValue(this)
  }

  data class GlobalStructure(val imports: List<UseStructure>, val structures: List<Structure>) : Structure()
}
