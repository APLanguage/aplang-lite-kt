package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject


sealed class Expression {
  data class Program(
    val packageDeclaration: GriddedObject<PackageDeclaration>?,
    val uses: List<GriddedObject<Declaration.UseDeclaration>>,
    val vars: List<GriddedObject<Declaration.VarDeclaration>>,
    val functions: List<GriddedObject<Declaration.FunctionDeclaration>>,
    val classes: List<GriddedObject<Declaration.ClassDeclaration>>
  )

  data class Path(val identifiers: List<GriddedObject<Token.IdentifierToken>>) {
    fun asString() = identifiers.joinToString(".") { it.obj.identifier }
  }

  data class PackageDeclaration(val path: GriddedObject<Path>)

  data class Type(val path: Path)

  data class BrokenExpression(val area: Area) : Expression()
}
