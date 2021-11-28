package com.github.aplanguage.aplanglite.parser

import arrow.core.Either
import com.github.aplanguage.aplanglite.parser.Expression.Declaration.Companion.asStatement
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Keyword
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import com.github.aplanguage.aplanglite.utils.TokenScanner
import com.github.aplanguage.aplanglite.utils.Underliner
import com.github.aplanguage.aplanglite.utils.filterOfType
import com.github.aplanguage.aplanglite.utils.listOfUntilNull

class ParserException(msg: String) : RuntimeException(msg)

data class ParserError(val exception: ParserException, val area: Area, val message: String? = null)

class Parser(val scanner: TokenScanner, val underliner: Underliner?) {

  private val errors: MutableList<ParserError> = mutableListOf()

  fun clearErrors() = errors.clear()

  fun errors() = errors.toList()

  fun program(): GriddedObject<Expression.Program>? {
    val packageDeclaration = packageDeclaration()
    val uses = listOfUntilNull(this::use_decl)
    val declarations = listOfUntilNull(this::declaration)
    if (uses.isEmpty() && declarations.isEmpty()) return null
    return GriddedObject.of(
      uses.ifEmpty { declarations }[0].startCoords(),
      Expression.Program(
        packageDeclaration,
        uses.filterOfType(),
        declarations.filterOfType(),
        declarations.filterOfType(),
        declarations.filterOfType()
      ),
      uses.ifEmpty { declarations }.last().endCoords()
    )
  }

  fun packageDeclaration(): GriddedObject<Expression.PackageDeclaration>? {
    scanner.startSection()
    val packageTk = scanner.consumeMatchingKeywordToken(Keyword.PACKAGE)
    if (packageTk == null) {
      scanner.endSection(true)
      return null
    }
    val path = path() ?: throw ParserException("Expected path after package keyword")
    scanner.endSection()
    return GriddedObject.of(packageTk.startCoords(), Expression.PackageDeclaration(path), path.endCoords()).also {
      if (it.endCoords().y != scanner.peekNextCoords().startCoords().y) {
        errors.add(
          ParserError(
            ParserException("Expected end of line after package declaration"),
            it.endCoords().toArea(),
            "Expected end of line after package declaration"
          )
        )

      }
    }
  }

  fun declaration(): GriddedObject<Expression.Declaration> =
    class_decl() ?: fun_decl() ?: var_decl() ?: throw ParserException("Expected class, fun or var, got ${scanner.peek()}")

  fun class_decl(): GriddedObject<Expression.Declaration.ClassDeclaration>? {
    scanner.startSection()
    val classTk = scanner.consumeMatchingKeywordToken(Keyword.CLASS)
    if (classTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "After class")
    val superTypes = mutableListOf<GriddedObject<Expression.Type>>()
    if (scanner.consumeMatchingCodeToken(CodeToken.COLON) != null) {
      superTypes.add(type() ?: throw ParserException("After : a Type expected. ${scanner.positionPreviousCoords().endCoords()}"))
      while (scanner.consumeMatchingCodeToken(CodeToken.COMMA) != null) {
        superTypes.add(type() ?: throw ParserException("After , a Type expected. ${scanner.positionPreviousCoords().endCoords()}"))
      }
    }
    val program = buildList {
      if (scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) != null) {
        while (scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null) {
          add(declaration())
        }
        expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "class_decl, close brace")?.also { throw it }
      }
    }
    scanner.endSection()
    return GriddedObject.of(
      classTk.startCoords(),
      Expression.Declaration.ClassDeclaration(
        identifier,
        superTypes,
        program.filterOfType(),
        program.filterOfType(),
        program.filterOfType(),
        program.filterOfType()
      ),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun fun_decl(): GriddedObject<Expression.Declaration.FunctionDeclaration>? {
    scanner.startSection()
    val fnTk = scanner.consumeMatchingKeywordToken(Keyword.FN)
    if (fnTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "fun_decl, After fn")
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "fun_decl, opening paren")?.also { throw it }
    val parameters = mutableListOf<Pair<GriddedObject<String>, GriddedObject<Expression.Type>>>()
    if (scanner.peekMatchingCodeToken(CodeToken.RIGHT_PAREN) == null) {
      functionParameterDeclaration(parameters, "After (")
      while (scanner.consumeMatchingCodeToken(CodeToken.COMMA) != null) {
        functionParameterDeclaration(parameters, "After ,")
      }
    }
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "fun_decl, closing paren.")?.also {
      errors.add(ParserError(it, scanner.positionCoords()))
    }
    scanner.startSection()
    val returnType = scanner.consumeMatchingCodeToken(CodeToken.COLON)?.let { type() }
    scanner.endSection(returnType == null)
    val block = block()
    scanner.endSection()
    return if (block == null) {
      throw ParserException("Every Function has a body.")
    } else GriddedObject.of(
      fnTk.startCoords(),
      Expression.Declaration.FunctionDeclaration(identifier, parameters, returnType, listOfUntilNull { statement() }),
      block.endCoords()
    )
  }

  private fun functionParameterDeclaration(
    parameters: MutableList<Pair<GriddedObject<String>, GriddedObject<Expression.Type>>>,
    whenToDeclare: String
  ) {
    try {
      scanner.startSection()
      val parameterIdentifier = expectIdentifier(scanner, "fun_decl, $whenToDeclare")
      expectCodeToken(scanner, CodeToken.COLON, "fun_decl, parameter colon")?.also { throw it }
      parameters.add(
        Pair(
          parameterIdentifier.repack { it.identifier },
          type() ?: throw ParserException("Each parameter must have a type.")
        )
      )
      scanner.endSection()
    } catch (e: ParserException) {
      scanner.endSection(true)
      errors.add(ParserError(e, listOfUntilNull {
        scanner.consumeWithPredicate {
          if (it.obj is Token.SignToken) {
            (it.obj as Token.SignToken).codeToken !in arrayOf(CodeToken.RIGHT_PAREN, CodeToken.COMMA)
          } else true
        }
      }.let {
        if (it.isNotEmpty()) it.reduce { acc, griddedObject -> acc.expandTo(griddedObject.endCoords()) }
          .area() else scanner.positionPreviousCoords()
      }, "Syntax for a parameter is: <identifier> : <type>"))
    }
  }


  fun var_decl(): GriddedObject<Expression.Declaration.VarDeclaration>? {
    scanner.startSection()
    val varTk = scanner.consumeMatchingKeywordToken(Keyword.VAR)
    if (varTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "var_decl, After var")
    scanner.startSection()
    val type = scanner.consumeMatchingCodeToken(CodeToken.COLON)?.let { type() }
    scanner.endSection(type == null)
    scanner.startSection()
    val expr = scanner.consumeMatchingCodeToken(CodeToken.EQUAL)?.let { expression() }
    scanner.endSection(expr == null)
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a var declaration there must be an new-line. ${scanner.positionPreviousCoords().endCoords()}")
    }
    scanner.endSection()
    return GriddedObject.of(
      varTk.startCoords(),
      Expression.Declaration.VarDeclaration(identifier, type, expr),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun use_decl(): GriddedObject<Expression>? {
    scanner.startSection()
    val useTk = scanner.consumeMatchingKeywordToken(Keyword.USE)
    if (useTk == null) {
      scanner.endSection(true)
      return null
    }
    val path = path()
    if (path == null) {
      errors.add(ParserError(ParserException("No path specified for use_decl"), scanner.positionPreviousCoords()))
    }
    scanner.startSection()
    val star = path?.let { scanner.consumeMatchingCodeToken(CodeToken.DOT)?.let { scanner.consumeMatchingCodeToken(CodeToken.STAR) } }
    scanner.endSection(star == null)
    var asOther: GriddedObject<Token.IdentifierToken>? = null
    if (star == null) {
      scanner.startSection()
      asOther = scanner.consumeMatchingKeywordToken(Keyword.AS)?.let { scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) }
      scanner.endSection(asOther == null)
    }
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      val useY = scanner.positionPreviousCoords().endCoords().y
      val leftOver = scanner.consumeUntilEOFOrPredicate {
        it.startCoords().y == useY
      }.let { it.first().expandTo(it.last().endCoords()).area() }
      if (path != null) {
        errors.add(ParserError(ParserException("After a use statement there must be an new-line."), leftOver))
      } else {
        if (asOther != null || star != null) {
          errors.add(ParserError(ParserException("No path specified."), useTk.endCoords().toArea()))
        } else {
          errors.add(ParserError(ParserException("After a use statement there must be an new-line."), leftOver))
        }
        scanner.endSection()
        return GriddedObject.of(useTk.startCoords(), Expression.BrokenExpression(useTk.area().expandTo(leftOver.endCoords())), leftOver.endCoords())
      }
    }
    scanner.endSection()
    if (path == null) {
      return GriddedObject.of(
        useTk.startCoords(),
        Expression.BrokenExpression(useTk.area().expandTo(scanner.positionPreviousCoords().endCoords())),
        scanner.positionPreviousCoords().endCoords()
      )
    }
    return GriddedObject.of(
      useTk.startCoords(),
      Expression.Declaration.UseDeclaration(path, star != null, asOther?.repack { it.identifier }),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun statement(): GriddedObject<Expression.Statement> =
    for_stmt() ?: return_stmt() ?: break_stmt() ?: while_stmt() ?: if_stmt() ?: var_decl()?.repack { it.asStatement() } ?: block() ?: exp_stmt()

  fun for_stmt(): GriddedObject<Expression.Statement.ForStatement>? {
    scanner.startSection()
    val forTk = scanner.consumeMatchingKeywordToken(Keyword.FOR)
    if (forTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "for_stmt, open paren")?.also { throw it }
    val identifier = expectIdentifier(scanner, "for_stmt, After (")
    expectCodeToken(scanner, CodeToken.COLON, "for_stmt, separator")?.also { throw it }
    val expr = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "for_stmt, close paren")?.also { throw it }
    val statement = statement()
    scanner.endSection()
    return GriddedObject.of(forTk.startCoords(), Expression.Statement.ForStatement(identifier, expr, statement), statement.endCoords())
  }

  fun return_stmt(): GriddedObject<Expression.Statement.ReturnStatement>? {
    scanner.startSection()
    val returnTk = scanner.consumeMatchingKeywordToken(Keyword.RETURN)
    if (returnTk == null) {
      scanner.endSection(true)
      return null
    }
    var expr: GriddedObject<Expression>? = null
    if (!scanner.isPositionEOF() && returnTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      expr = expression()
      if (!scanner.isPositionEOF() && returnTk.endCoords().y == scanner.positionCoords().startCoords().y) {
        throw ParserException("After a return statement there must be an new-line. at ${returnTk.endCoords()}")
      }
    }
    scanner.endSection()
    return GriddedObject.of(returnTk.startCoords(), Expression.Statement.ReturnStatement(expr), scanner.positionCoords().endCoords())
  }

  fun break_stmt(): GriddedObject<Expression.Statement.BreakStatement>? {
    scanner.startSection()
    val breakTk = scanner.consumeMatchingKeywordToken(Keyword.BREAK)
    if (breakTk == null) {
      scanner.endSection(true)
      return null
    }
    if (!scanner.isPositionEOF() && breakTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a break statement there must be an new-line. at ${breakTk.endCoords()}")
    }
    scanner.endSection()
    return GriddedObject.of(breakTk.startCoords(), Expression.Statement.BreakStatement(), scanner.positionCoords().endCoords())
  }

  fun while_stmt(): GriddedObject<Expression.Statement.WhileStatement>? {
    scanner.startSection()
    val whileTk = scanner.consumeMatchingKeywordToken(Keyword.WHILE)
    if (whileTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "while_stmt, open paren")?.also { throw it }
    val expr = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "while_stmt, close paren")?.also { throw it }
    var statement: GriddedObject<Expression>? = null
    if (!scanner.isPositionEOF() && whileTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      statement = statement()
    }
    scanner.endSection()
    return GriddedObject.of(whileTk.startCoords(), Expression.Statement.WhileStatement(expr, statement), scanner.positionCoords().endCoords())
  }

  fun if_stmt(): GriddedObject<Expression.Statement.IfStatement>? {
    scanner.startSection()
    val ifTk = scanner.consumeMatchingKeywordToken(Keyword.IF)
    if (ifTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "if_stmt, open paren")?.also { throw it }
    val condition = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "if_stmt, close paren")?.also { throw it }
    val thenStatement = statement()
    var elseStatement: GriddedObject<Expression>? = null
    if (scanner.consumeMatchingKeywordToken(Keyword.ELSE) != null) {
      elseStatement = statement()
    }
    scanner.endSection()
    return GriddedObject.of(
      ifTk.startCoords(),
      Expression.Statement.IfStatement(condition, thenStatement, elseStatement),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun exp_stmt(): GriddedObject<Expression.Statement.ExpressionStatement> {
    val expr = expression()
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After an expr there must be a new-line. ${expr.endCoords()}")
    }
    return expr.repack { Expression.Statement.ExpressionStatement(it) }
  }

  fun expression(): GriddedObject<Expression.DataExpression> = assignment()
  fun assignment(): GriddedObject<Expression.DataExpression> {
    scanner.startSection()
    val left = logic_or()
    val tk = scanner.consumeMatchingCodeTokens(
      arrayOf(
        CodeToken.PLUS_EQUAL, CodeToken.MINUS_EQUAL, CodeToken.STAR_EQUAL, CodeToken.STAR_STAR_EQUAL, CodeToken.SLASH_EQUAL,
        CodeToken.PERCENTAGE_EQUAL,
        CodeToken.AMPERSAND_EQUAL, CodeToken.VERTICAL_BAR_EQUAL, CodeToken.CIRCUMFLEX_EQUAL, CodeToken.TILDE_EQUAL,
        CodeToken.LESS_LESS_EQUAL, CodeToken.GREATER_GREATER_EQUAL, CodeToken.GREATER_GREATER_GREATER_EQUAL,
        CodeToken.EQUAL
      )
    )
    if (tk == null) {
      scanner.endSection(true)
      return if_expr()
    }
    if (left.obj !is Expression.DataExpression.IdentifierExpression && left.obj !is Expression.DataExpression.Call) {
      throw ParserException(
        "For the left-side of the assignment it only can be a Call or an Identifier. ${
          scanner.positionPreviousCoords().endCoords()
        }"
      )
    }
    return GriddedObject.of(
      left.startCoords(),
      Expression.DataExpression.Assignment(left, tk, assignment()),
      scanner.positionPreviousCoords().endCoords()
    ).also { scanner.endSection() }
  }

  fun if_expr(): GriddedObject<Expression.DataExpression> {
    scanner.startSection()
    val ifTk = scanner.consumeMatchingKeywordToken(Keyword.IF)
    if (ifTk == null) {
      scanner.endSection(true)
      return logic_or()
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "if_expr, open paren")?.also { throw it }
    val condition = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "if_expr, close paren")?.also { throw it }
    val thenExpr = expression()
    expectKeywordToken(scanner, Keyword.ELSE, "if_expr, else expr")?.also { throw it }
    val elseExpr = expression()
    scanner.endSection()
    return GriddedObject.of(
      ifTk.startCoords(),
      Expression.DataExpression.IfExpression(condition, thenExpr, elseExpr),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  inline fun binaryOp(ops: Array<CodeToken>, next: () -> GriddedObject<Expression.DataExpression>): GriddedObject<Expression.DataExpression> {
    val nextExpr = next()
    scanner.startSection()
    val operations = listOfUntilNull {
      Pair(scanner.consumeMatchingCodeTokens(ops) ?: return@listOfUntilNull null, next())
    }
    scanner.endSection()
    return if (operations.isEmpty()) nextExpr
    else GriddedObject.of(
      nextExpr.startCoords(),
      Expression.DataExpression.BinaryOperation(nextExpr, operations),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun logic_or() = binaryOp(arrayOf(CodeToken.DOUBLE_VERTICAL_BAR), this::logic_and)
  fun logic_and() = binaryOp(arrayOf(CodeToken.AMPERSAND_AMPERSAND), this::equality)
  fun equality() = binaryOp(arrayOf(CodeToken.BANG_EQUAL, CodeToken.EQUAL_EQUAL), this::comparison)
  fun comparison() = binaryOp(arrayOf(CodeToken.GREATER, CodeToken.GREATER_EQUAL, CodeToken.LESS, CodeToken.LESS_EQUAL), this::term)
  fun term() = binaryOp(arrayOf(CodeToken.PLUS, CodeToken.MINUS), this::factor)
  fun factor() = binaryOp(arrayOf(CodeToken.SLASH, CodeToken.STAR, CodeToken.STAR_STAR, CodeToken.PERCENTAGE), this::bit_op)
  fun bit_op() = binaryOp(
    arrayOf(
      CodeToken.VERTICAL_BAR,
      CodeToken.CIRCUMFLEX,
      CodeToken.AMPERSAND,
      CodeToken.GREATER_GREATER,
      CodeToken.LESS_LESS,
      CodeToken.GREATER_GREATER_GREATER
    ), this::oop
  )

  fun oop(): GriddedObject<Expression.DataExpression> {
    val nextExpr = unary_left()
    scanner.startSection()
    val op = if (scanner.consumeMatchingCodeToken(CodeToken.BANG_IS) != null) Expression.DataExpression.OopExpression.OopOpType.IS_NOT
    else if (scanner.consumeMatchingKeywordToken(Keyword.IS) != null) Expression.DataExpression.OopExpression.OopOpType.IS
    else if (scanner.consumeMatchingKeywordToken(Keyword.AS) != null) Expression.DataExpression.OopExpression.OopOpType.AS
    else return nextExpr.also { scanner.endSection() }
    val type = type() ?: throw ParserException(
      "Type expected after $op. At ${scanner.positionPreviousCoords().endCoords()}"
    )
    scanner.endSection()
    return GriddedObject.of(
      nextExpr.startCoords(),
      Expression.DataExpression.OopExpression(nextExpr, op, type),
      type.endCoords()
    )
  }

  fun unary_left(): GriddedObject<Expression.DataExpression> {
    scanner.startSection()
    val tk = scanner.consumeMatchingCodeTokens(arrayOf(CodeToken.BANG, CodeToken.TILDE)) ?: return call().also { scanner.endSection() }
    return GriddedObject.of(
      tk.startCoords(),
      Expression.DataExpression.UnaryOperation(tk, unary_left()),
      scanner.positionPreviousCoords().endCoords()
    ).also { scanner.endSection() }
  }

  fun call(): GriddedObject<Expression.DataExpression> {
    val primaryObj = primary()
    val primary = when (primaryObj.obj) {
      is Expression.DataExpression.IdentifierExpression -> {
        funcArguments(primaryObj.repack((primaryObj.obj as Expression.DataExpression.IdentifierExpression).identifier)) ?: primaryObj
      }
      else -> primaryObj
    }
    val calls = listOfUntilNull {
      scanner.consumeMatchingCodeToken(CodeToken.DOT) ?: return@listOfUntilNull null
      val identifier = scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)?.repack { it.identifier } ?: throw ParserException(
        "Identifier expected after '.'. At ${scanner.positionPreviousCoords().endCoords()}"
      )
      funcArguments(identifier)?.let { Either.Left(it) } ?: Either.Right(identifier)
    }
    return if (calls.isEmpty()) primary
    else calls.drop(1).fold(
      GriddedObject.of(
        primaryObj.startCoords(),
        Expression.DataExpression.Call(primary, calls.first()),
        calls.first().fold({ it }, { it }).endCoords()
      )
    ) { call, func ->
      GriddedObject.of(call.startCoords(), Expression.DataExpression.Call(call, func), func.fold({ it }, { it }).endCoords())
    }
  }

  fun funcArguments(identifier: GriddedObject<String>): GriddedObject<Expression.DataExpression.FunctionCall>? {
    if (scanner.consumeMatchingCodeToken(CodeToken.LEFT_PAREN) == null) return null
    return GriddedObject.of(identifier.startCoords(), Expression.DataExpression.FunctionCall(identifier,
      if (scanner.consumeMatchingCodeToken(CodeToken.RIGHT_PAREN) == null)
        (listOf(expression()) + listOfUntilNull {
          scanner.consumeMatchingCodeToken(CodeToken.COMMA)?.let { return@listOfUntilNull expression() }
        }).also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "invocation, close paren")?.also { throw it } }
      else listOf()
    ), scanner.positionPreviousCoords().endCoords())
  }

  fun primary(): GriddedObject<Expression.DataExpression> {
    val tk = scanner.consume() ?: throw ParserException("No EOF expected!")
    return when (tk.obj) {
      is Token.IdentifierToken -> tk.repack(Expression.DataExpression.IdentifierExpression((tk.obj as Token.IdentifierToken).identifier))
      is Token.ValueToken -> tk.repack(Expression.DataExpression.DirectValue(tk.obj as Token.ValueToken))
      is Token.SignToken -> {
        if ((tk.obj as Token.SignToken).codeToken == CodeToken.LEFT_PAREN)
          expression().also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "primary, (expression) close paren")?.also { throw it } }
        else throw ParserException("Identifier, ValueToken or ( expected, got $tk")
      }
      else -> throw ParserException("Unexpected Token: $tk")
    }
  }

  fun block(): GriddedObject<Expression.Statement.Block>? {
    val openBrace = scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) ?: return null
    val stmts = mutableListOf<GriddedObject<Expression>>()
    while (!scanner.isPositionEOF() && scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null) {
      stmts.add(var_decl() ?: statement())
    }
    expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "block, closing brace")?.also { throw it }
    return GriddedObject.of(openBrace.startCoords(), Expression.Statement.Block(stmts), scanner.positionPreviousCoords().endCoords())
  }

  fun type(): GriddedObject<Expression.Type>? = path()?.let { it.repack(Expression.Type(it.obj)) }

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

  private fun expectCodeToken(scanner: TokenScanner, codeToken: CodeToken, message: String? = null): ParserException? {
    val tk = scanner.consumeMatchingInnerClass(Token.SignToken::class.java)
      ?: return ParserException(
        (if (message == null) "" else "$message. ") + "Expected Token.SignToken at ${
          scanner.positionCoords().startCoords()
        }, got " + (scanner.consume()?.obj ?: "EOF")
      )
    if (tk.obj.codeToken != codeToken) return ParserException(
      (if (message == null) "" else "$message. ") + "Expected $codeToken at ${
        scanner.positionCoords().startCoords()
      }, got ${tk.obj.codeToken}"
    )
    return null
  }

  private fun expectKeywordToken(scanner: TokenScanner, keyword: Keyword, message: String? = null): ParserException? {
    val tk = scanner.consumeMatchingInnerClass(Token.KeywordToken::class.java)
      ?: return ParserException(
        (if (message == null) "" else "$message. ") + "Expected Token.KeywordToken at ${
          scanner.positionCoords().startCoords()
        }, got " + (scanner.consume()?.obj ?: "EOF")
      )
    if (tk.obj.keyword != keyword) return ParserException(
      (if (message == null) "" else "$message. ") + "Expected $keyword at ${
        scanner.positionCoords().startCoords()
      }, got ${tk.obj.keyword}"
    )
    return null
  }

  private fun expectIdentifier(scanner: TokenScanner, message: String? = null): GriddedObject<Token.IdentifierToken> {
    return scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)
      ?: throw ParserException(
        (if (message == null) "" else "$message. ") + "Expected Token.IdentifierToken at ${
          scanner.positionCoords().startCoords()
        }, got " + (scanner.consume()?.obj ?: "EOF")
      )
  }
}
