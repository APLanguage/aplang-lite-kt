package com.github.aplanguage.aplanglite.parser.expression

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.Namespace
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.GriddedObject

sealed class Declaration : Expression() {
  data class UseDeclaration(val path: GriddedObject<Path>, val all: Boolean, val asOther: GriddedObject<String>?) : Declaration()

  data class VarDeclaration(
    val identifier: GriddedObject<String>,
    var type: Either<GriddedObject<Type>?, Namespace.Class>,
    val expr: GriddedObject<DataExpression>?
  ) : Declaration()

  data class FunctionDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val parameters: List<Pair<GriddedObject<String>, GriddedObject<Type>>>,
    val type: GriddedObject<Type>?,
    val code: List<GriddedObject<Statement>>
  ) : Declaration()

  data class ClassDeclaration(
    val identifier: GriddedObject<Token.IdentifierToken>,
    val superTypes: List<GriddedObject<Type>>,
    val uses: List<GriddedObject<UseDeclaration>>,
    val vars: List<GriddedObject<VarDeclaration>>,
    val functions: List<GriddedObject<FunctionDeclaration>>,
    val classes: List<GriddedObject<ClassDeclaration>>
  ) : Declaration() {
    fun asProgram() = Program(null, uses, vars, functions, classes)
  }

  companion object {
    fun Declaration.asStatement() = Statement.DeclarationStatement(this)
  }
}
