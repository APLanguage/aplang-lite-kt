package com.github.amejonah1200.aplang_lite.parser

import com.github.amejonah1200.aplang_lite.tokenizer.CodeToken
import com.github.amejonah1200.aplang_lite.tokenizer.Keyword
import com.github.amejonah1200.aplang_lite.tokenizer.ParserException
import com.github.amejonah1200.aplang_lite.tokenizer.Token
import com.github.amejonah1200.aplang_lite.utils.GriddedObject
import com.github.amejonah1200.aplang_lite.utils.MultiLineObject
import com.github.amejonah1200.aplang_lite.utils.TokenScanner

class ParseException(msg: String) : RuntimeException(msg)

class Parser(val scanner: TokenScanner) {

  fun program(): GriddedObject<Expression>? {
    val uses = listOfUntilNull(this::use_decl)
    val declarations = listOfUntilNull(this::declaration)
    if (uses.isEmpty() && declarations.isEmpty()) return null
    val startCoords = uses.ifEmpty { declarations }[0].startCoords()
    val endCoords = uses.ifEmpty { declarations }.last().endCoords()
    return MultiLineObject(
      startCoords.x,
      startCoords.y,
      Expression.Program(uses, declarations),
      endCoords.x,
      endCoords.y
    )
  }

  fun declaration(): GriddedObject<Expression>? = class_decl()?.let { fun_decl() }?.let { var_decl() }

  fun class_decl(): GriddedObject<Expression>? = null
  fun fun_decl(): GriddedObject<Expression>? = null
  fun var_decl(): GriddedObject<Expression>? {
    scanner.startSection()
    val varTk = scanner.consumeMatchingKeywordToken(Keyword.VAR)
    if (varTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier =
      scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) ?: throw ParserException("After var, there should be an identifier.")
    scanner.startSection()
    val type = scanner.consumeMatchingCodeToken(CodeToken.COLON)?.let { type() }
    scanner.endSection(type == null)
    scanner.startSection()
    val expr = scanner.consumeMatchingCodeToken(CodeToken.EQUAL)?.let { expression() }
    scanner.endSection(expr == null)
    scanner.endSection()
    return GriddedObject.of(varTk.startCoords(), Expression.VarDeclaration(identifier, type, expr), scanner.positionPreviousCoords().endCoords())
  }

  fun use_decl(): GriddedObject<Expression.UseDeclaration>? {
    scanner.startSection()
    val useTk = scanner.consumeMatchingKeywordToken(Keyword.USE)
    if (useTk == null) {
      scanner.endSection(true)
      return null
    }
    val path = path() ?: throw ParseException("No path specified for use_decl at ${useTk.endCoords()}")
    scanner.startSection()
    val star = scanner.consumeMatchingCodeToken(CodeToken.DOT)?.let { scanner.consumeMatchingCodeToken(CodeToken.STAR) }
    scanner.endSection(star == null)
    var asOther: GriddedObject<Token.IdentifierToken>? = null
    if (star == null) {
      scanner.startSection()
      asOther = scanner.consumeMatchingKeywordToken(Keyword.AS)?.let { scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) }
      scanner.endSection(asOther == null)
    }
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a use statement there should be an new-line.")
    }
    scanner.endSection()
    return GriddedObject.of(useTk.startCoords(), Expression.UseDeclaration(path, star != null, asOther), path.obj.identifiers.last().endCoords())
  }

  fun statement(): GriddedObject<Expression>? = null
  fun for_stmt(): GriddedObject<Expression>? = null
  fun return_stmt(): GriddedObject<Expression>? = null
  fun break_stmt(): GriddedObject<Expression>? = null
  fun while_stmt(): GriddedObject<Expression>? = null
  fun var_stmt(): GriddedObject<Expression>? = null
  fun exp_stmt(): GriddedObject<Expression>? = null

  fun expression(): GriddedObject<Expression>? = null
  fun assignment(): GriddedObject<Expression>? = null
  fun if_expr(): GriddedObject<Expression>? = null
  fun logic_or(): GriddedObject<Expression>? = null
  fun logic_and(): GriddedObject<Expression>? = null
  fun equality(): GriddedObject<Expression>? = null
  fun comparison(): GriddedObject<Expression>? = null
  fun term(): GriddedObject<Expression>? = null
  fun factor(): GriddedObject<Expression>? = null
  fun bit_op(): GriddedObject<Expression>? = null
  fun unary_left(): GriddedObject<Expression>? = null
  fun call(): GriddedObject<Expression>? = null
  fun primary(): GriddedObject<Expression>? = null

  fun parameters(): GriddedObject<Expression>? = null
  fun arguments(): GriddedObject<Expression>? = null
  fun block(): GriddedObject<Expression>? = null

  fun type(): GriddedObject<Expression.Type>? = null
  fun array_type(): GriddedObject<Expression>? = null

  fun path(): GriddedObject<Expression.Path>? {
    scanner.startSection()
    val tk = scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)
    if (tk == null) {
      scanner.endSection(true)
      return null
    }
    val others = listOfUntilNull {
      scanner.startSection()
      val identifier = scanner.consumeMatchingCodeToken(CodeToken.DOT)?.let { scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) }
      if (identifier == null) {
        scanner.endSection(true)
        null
      } else identifier
    }
    scanner.endSection()
    return GriddedObject.of(tk.startCoords(), Expression.Path(listOf(tk) + others), (others.lastOrNull() ?: tk).endCoords())
  }

  fun array_clause(): GriddedObject<Expression>? = null

}

private fun <T> listOfUntilNull(generator: () -> T?): List<T> {
  var temp: T? = generator()
  val list = mutableListOf<T>()
  while (temp != null) {
    list.add(temp)
    temp = generator()
  }
  return list.toList()
}
