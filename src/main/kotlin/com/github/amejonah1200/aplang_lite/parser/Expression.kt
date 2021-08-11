package com.github.amejonah1200.aplang_lite.parser

import com.github.amejonah1200.aplang_lite.tokenizer.Token
import com.github.amejonah1200.aplang_lite.utils.GriddedObject

sealed class Expression {
  data class Program(val uses: List<GriddedObject<Expression>>, val declarations: List<GriddedObject<Expression>>) : Expression()

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>) : Expression()

  data class UseDeclaration(val path: GriddedObject<Expression>?, val all: Boolean, val asOther: GriddedObject<Token.IdentifierToken>?) : Expression()

  data class VarDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val type: GriddedObject<Type>?,
    val expr: GriddedObject<Expression>?
  ) :
    Expression()

  data class Type(val path: Path)

  data class FunctionDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val parameters: Map<GriddedObject<Token.IdentifierToken>, GriddedObject<Type>>,
    val type: GriddedObject<Type>?,
    val block: GriddedObject<List<GriddedObject<Expression>>>
  ) : Expression()

}
