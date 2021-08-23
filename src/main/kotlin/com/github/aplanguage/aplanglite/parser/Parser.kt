package com.github.aplanguage.aplanglite.parser

import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Keyword
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.*

class ParserException(msg: String) : RuntimeException(msg)

data class ParserError(val exception: ParserException, val area: Area, val message: String? = null)

class Parser(val scanner: TokenScanner, val underliner: Underliner?) {

  private val errors: MutableList<ParserError> = mutableListOf()

  fun clearErrors() = errors.clear()

  fun errors() = errors.toList()

  fun program(): GriddedObject<Expression.Program>? {
    val uses = listOfUntilNull(this::use_decl)
    val declarations = listOfUntilNull(this::declaration)
    if (uses.isEmpty() && declarations.isEmpty()) return null
    return GriddedObject.of(
      uses.ifEmpty { declarations }[0].startCoords(),
      Expression.Program(uses, declarations),
      uses.ifEmpty { declarations }.last().endCoords()
    )
  }

  fun declaration(): GriddedObject<Expression>? = class_decl() ?: fun_decl() ?: var_decl()

  fun class_decl(): GriddedObject<Expression.ClassDeclaration>? {
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
    var program: GriddedObject<Expression.Program>? = null
    if (scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) != null) {
      program = program()
      expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "class_decl, close brace")?.also { throw it }
    } else if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After an class statement (with no braces) there must be an new-line. ${scanner.positionPreviousCoords().endCoords()}")
    }
    return GriddedObject.of(
      classTk.startCoords(),
      Expression.ClassDeclaration(identifier, superTypes, program),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun fun_decl(): GriddedObject<Expression>? {
    scanner.startSection()
    val fnTk = scanner.consumeMatchingKeywordToken(Keyword.FN)
    if (fnTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "fun_decl, After fn")
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "fun_decl, opening paren")?.also { throw it }
    val parameters = mutableMapOf<GriddedObject<Token.IdentifierToken>, GriddedObject<Expression.Type>>()
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
      errors.add(ParserError(ParserException("Every Function has a body."), scanner.positionCoords()))
      GriddedObject.of(
        fnTk.startCoords(),
        Expression.BrokenExpression(fnTk.startCoords().expandTo(scanner.positionPreviousCoords().endCoords())),
        scanner.positionPreviousCoords().endCoords()
      )
    } else GriddedObject.of(fnTk.startCoords(), Expression.FunctionDeclaration(identifier, parameters, returnType, block), block.endCoords())
  }

  private fun functionParameterDeclaration(
    parameters: MutableMap<GriddedObject<Token.IdentifierToken>, GriddedObject<Expression.Type>>,
    whenToDeclare: String
  ) {
    try {
      scanner.startSection()
      val parameterIdentifier = expectIdentifier(scanner, "fun_decl, $whenToDeclare")
      expectCodeToken(scanner, CodeToken.COLON, "fun_decl, parameter colon")?.also { throw it }
      parameters[parameterIdentifier] =
        type() ?: throw ParserException("Each parameter must have a type.")
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


  fun var_decl(): GriddedObject<Expression.VarDeclaration>? {
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
    return GriddedObject.of(varTk.startCoords(), Expression.VarDeclaration(identifier, type, expr), scanner.positionPreviousCoords().endCoords())
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
    return GriddedObject.of(useTk.startCoords(), Expression.UseDeclaration(path, star != null, asOther), scanner.positionPreviousCoords().endCoords())
  }

  fun statement(): GriddedObject<Expression> =
    for_stmt() ?: return_stmt() ?: break_stmt() ?: while_stmt() ?: var_decl() ?: if_stmt() ?: block() ?: exp_stmt()

  fun for_stmt(): GriddedObject<Expression>? {
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
    return GriddedObject.of(forTk.startCoords(), Expression.ForStatement(identifier, expr, statement), statement.endCoords())
  }

  fun return_stmt(): GriddedObject<Expression>? {
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
    return GriddedObject.of(returnTk.startCoords(), Expression.ReturnStatement(expr), scanner.positionCoords().endCoords())
  }

  fun break_stmt(): GriddedObject<Expression>? {
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
    return GriddedObject.of(breakTk.startCoords(), Expression.BreakStatement(), scanner.positionCoords().endCoords())
  }

  fun while_stmt(): GriddedObject<Expression>? {
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
    return GriddedObject.of(whileTk.startCoords(), Expression.WhileStatement(expr, statement), scanner.positionCoords().endCoords())
  }

  fun if_stmt(): GriddedObject<Expression>? {
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
      Expression.IfStatement(condition, thenStatement, elseStatement),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun exp_stmt(): GriddedObject<Expression> {
    val expr = expression()
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After an expr there must be a new-line. ${expr.endCoords()}")
    }
    return expr
  }

  fun expression(): GriddedObject<Expression> = assignment()
  fun assignment(): GriddedObject<Expression> {
    scanner.startSection()
    val call = logic_or()
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
    if (call.obj !is Expression.Primary.IdentifierExpression && call.obj !is Expression.Call) {
      throw ParserException(
        "For the left-side of the assignment it only can be a Call or an Identifier. ${
          scanner.positionPreviousCoords().endCoords()
        }"
      )
    }
    return GriddedObject.of(
      call.startCoords(),
      Expression.Assignment(call, tk, assignment()),
      scanner.positionPreviousCoords().endCoords()
    ).also { scanner.endSection() }
  }

  fun if_expr(): GriddedObject<Expression> {
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
      Expression.IfExpression(condition, thenExpr, elseExpr),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  inline fun binaryOp(ops: Array<CodeToken>, next: () -> GriddedObject<Expression>): GriddedObject<Expression> {
    val nextExpr = next()
    scanner.startSection()
    val operations = listOfUntilNull {
      Pair(scanner.consumeMatchingCodeTokens(ops) ?: return@listOfUntilNull null, next())
    }
    scanner.endSection()
    return if (operations.isEmpty()) nextExpr
    else GriddedObject.of(
      nextExpr.startCoords(),
      Expression.BinaryOperation(nextExpr, operations),
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
    ), this::unary_left
  )

  fun unary_left(): GriddedObject<Expression> {
    scanner.startSection()
    val tk = scanner.consumeMatchingCodeTokens(arrayOf(CodeToken.BANG, CodeToken.TILDE)) ?: return call().also { scanner.endSection() }
    return GriddedObject.of(tk.startCoords(), Expression.UnaryOperation(tk, unary_left()), scanner.positionPreviousCoords().endCoords())
      .also { scanner.endSection() }
  }

  fun call(): GriddedObject<Expression> {
    val primary = primary()
    val invocations = listOfUntilNull(this::invocation)
    val calls = listOfUntilNull {
      scanner.consumeMatchingCodeToken(CodeToken.DOT) ?: return@listOfUntilNull null
      Pair(
        GriddedObject.of(
          scanner.positionPreviousCoords().startCoords(),
          expectIdentifier(scanner, "call, After Dot"),
          scanner.positionPreviousCoords().endCoords()
        ), listOfUntilNull(this::invocation)
      )
    }
    if (invocations.isEmpty() && calls.isEmpty()) return primary
    return GriddedObject.of(primary.startCoords(), Expression.Call(primary, invocations, calls), scanner.positionPreviousCoords().endCoords())
  }

  fun invocation(): GriddedObject<Expression.Invocation>? {
    val tk = scanner.consumeMatchingCodeTokens(arrayOf(CodeToken.LEFT_PAREN, CodeToken.LEFT_BRACKET)) ?: return null
    return GriddedObject.of(tk.startCoords(), when (tk.obj.codeToken) {
      CodeToken.LEFT_PAREN -> Expression.Invocation.FunctionCall(
        if (scanner.consumeMatchingCodeToken(CodeToken.RIGHT_PAREN) == null) {
          (listOf(expression()) + listOfUntilNull {
            scanner.consumeMatchingCodeToken(CodeToken.COMMA)?.let { return@listOfUntilNull expression() }
          }).also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "invocation, close paren")?.also { throw it } }
        } else listOf()
      )
      CodeToken.LEFT_BRACKET -> Expression.Invocation.ArrayCall(expression())
        .also { expectCodeToken(scanner, CodeToken.RIGHT_BRACKET, "invocation, closing bracket")?.also { throw it } }
      else -> throw IllegalStateException("should never happen")
    }, scanner.positionPreviousCoords().endCoords())
  }

  fun primary(): GriddedObject<Expression> {
    val tk = scanner.consume() ?: throw ParserException("No EOF expected!")
    return when (tk.obj) {
      is Token.IdentifierToken -> tk.repack(Expression.Primary.IdentifierExpression(tk.obj as Token.IdentifierToken))
      is Token.ValueToken -> tk.repack(Expression.Primary.DirectValue(tk.obj as Token.ValueToken))
      is Token.SignToken -> {
        if ((tk.obj as Token.SignToken).codeToken == CodeToken.LEFT_PAREN)
          expression().also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "primary, (expression) close paren")?.also { throw it } }
        else throw ParserException("Identifier, ValueToken or ( expected, got $tk")
      }
      else -> throw ParserException("Unexpected Token: $tk")
    }
  }

  fun block(): GriddedObject<Expression.Block>? {
    val openBrace = scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) ?: return null
    val stmts = mutableListOf<GriddedObject<Expression>>()
    while (!scanner.isPositionEOF() && scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null) {
      stmts.add(var_decl() ?: statement())
    }
    expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "block, closing brace")?.also { throw it }
    return GriddedObject.of(openBrace.startCoords(), Expression.Block(stmts), scanner.positionPreviousCoords().endCoords())
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
