package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.parser.Expression

sealed class Structure {

  data class UseStructure(val path: String, val all: Boolean, val asOther: String?) : Structure()

  data class FunctionStructure(
    val identifier: String,
    val parameters: List<Expression.Type>,
    val type: Expression.Type?,
    val declaration: Expression.Declaration.FunctionDeclaration
  ) : Structure()

  data class ClassStructure(
    val identifier: String,
    val superTypes: List<Expression.Type>,
    val structure: GlobalStructure
  ) : Structure()

  data class VarStructure(
    val identifier: String,
    val type: Expression.Type?,
    var expression: Expression?,
    var value: ReturnValue?,
  ) : Structure()

  data class GlobalStructure(val imports: List<UseStructure>, val structures: List<Structure>) : Structure()
}
