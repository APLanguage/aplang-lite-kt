package com.github.amejonah1200.aplang_lite.parser

import com.github.amejonah1200.aplang_lite.tokenizer.CodeToken
import com.github.amejonah1200.aplang_lite.tokenizer.Keyword
import com.github.amejonah1200.aplang_lite.tokenizer.ParserException
import com.github.amejonah1200.aplang_lite.tokenizer.Token
import com.github.amejonah1200.aplang_lite.utils.GriddedObject
import com.github.amejonah1200.aplang_lite.utils.TokenScanner

class ParseException(msg: String) : RuntimeException(msg)

class Parser(val scanner: TokenScanner) {

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
    val classTk = scanner.consumeMatchingKeywordToken(Keyword.FN)
    if (classTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier =
      scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)
        ?: throw ParserException("After class, there should be an identifier.") // TODO better exceptions
    val superTypes = mutableListOf<GriddedObject<Expression.Type>>()
    if (scanner.peekMatchingCodeToken(CodeToken.COLON) != null) {
      superTypes.add(type() ?: throw ParserException("After : a Type expected."))
      while (scanner.consumeMatchingCodeToken(CodeToken.COMMA) != null) {
        superTypes.add(type() ?: throw ParserException("After , a Type expected."))
      }
    }
    var program: GriddedObject<Expression.Program>? = null
    if (scanner.peekMatchingCodeToken(CodeToken.LEFT_BRACE) != null) {
      program = program()
      expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "class_decl, close brace")
    } else if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After an class statement (with no braces) there must be an new-line.")
    }
    return GriddedObject.of(
      classTk.startCoords(),
      Expression.ClassDeclaration(identifier, superTypes, program),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun fun_decl(): GriddedObject<Expression.FunctionDeclaration>? {
    scanner.startSection()
    val fnTk = scanner.consumeMatchingKeywordToken(Keyword.FN)
    if (fnTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier =
      scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)
        ?: throw ParserException("After fn, there should be an identifier.") // TODO better exceptions
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "fun_decl, opening paren")
    val parameters = mutableMapOf<GriddedObject<Token.IdentifierToken>, GriddedObject<Expression.Type>>()
    if (scanner.peekMatchingCodeToken(CodeToken.RIGHT_PAREN) == null) {
      scanner.startSection()
      val parameterIdentifier =
        scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) ?: throw ParserException("After ( no )? So identifier expected.")
      expectCodeToken(scanner, CodeToken.COLON, "fun_decl, parameter colon")
      parameters[parameterIdentifier] = type() ?: throw ParserException("Each parameter must have a type.")
      while (scanner.consumeMatchingCodeToken(CodeToken.COMMA) != null) {
        val otherParameterIdentifier =
          scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) ?: throw ParserException("After comma identifier expected.")
        expectCodeToken(scanner, CodeToken.COLON, "fun_decl, parameter colon")
        parameters[otherParameterIdentifier] = type() ?: throw ParserException("Each parameter must have a type.")
      }
      scanner.endSection()
    }
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "fun_decl, closing paren.")
    scanner.startSection()
    val returnType = scanner.consumeMatchingCodeToken(CodeToken.COLON)?.let { type() }
    scanner.endSection(returnType == null)
    val block = block() ?: throw ParserException("Every Function has a body.")
    scanner.endSection()
    return GriddedObject.of(fnTk.startCoords(), Expression.FunctionDeclaration(identifier, parameters, returnType, block), block.endCoords())
  }

  fun var_decl(): GriddedObject<Expression.VarDeclaration>? {
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
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a var declaration there must be an new-line.")
    }
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
      throw ParserException("After a use statement there must be an new-line.")
    }
    scanner.endSection()
    return GriddedObject.of(useTk.startCoords(), Expression.UseDeclaration(path, star != null, asOther), path.obj.identifiers.last().endCoords())
  }

  fun statement(): GriddedObject<Expression>? =
    for_stmt() ?: return_stmt() ?: break_stmt() ?: while_stmt() ?: var_decl() ?: if_stmt() ?: block() ?: exp_stmt()

  fun for_stmt(): GriddedObject<Expression>? {
    scanner.startSection()
    val forTk = scanner.consumeMatchingKeywordToken(Keyword.FOR)
    if (forTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "for_stmt, open paren")
    val identifier =
      scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java) ?: throw ParserException("After ( identifier expected.")
    expectCodeToken(scanner, CodeToken.COLON, "for_stmt, separator")
    val expr = expression() ?: throw ParserException("No expression to iterate through provided.")
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "for_stmt, close paren")
    val statement = statement() ?: throw ParserException("No statement for the fo stmt provided!")
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
      if (expr == null || (!scanner.isPositionEOF() && returnTk.endCoords().y == scanner.positionCoords().startCoords().y)) {
        throw ParserException("After a return statement there must be an new-line.")
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
      throw ParserException("After a break statement there must be an new-line.")
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
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "while_stmt, open paren")
    val expr = expression() ?: throw ParserException("No expression to have as condition for while loop provided.")
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "while_stmt, close paren")
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
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "if_stmt, open paren")
    val condition = expression() ?: throw ParserException("condition not provided.")
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "if_stmt, close paren")
    val thenStatement = statement() ?: throw ParserException("then at if not provided.")

    var elseStatement: GriddedObject<Expression>? = null
    if (scanner.consumeMatchingKeywordToken(Keyword.ELSE) != null) {
      elseStatement = statement() ?: throw ParserException("No statement after else provided.")
    }
    scanner.endSection()
    return GriddedObject.of(
      ifTk.startCoords(),
      Expression.IfStatement(condition, thenStatement, elseStatement),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun exp_stmt(): GriddedObject<Expression>? {
    val expr = expression() ?: return null
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After an expr there must be a new-line.")
    }
    return expr
  }

  fun expression(): GriddedObject<Expression> = assignment()
  fun assignment(): GriddedObject<Expression> {
    val call = call() ?: return if_expr()
    val tk = scanner.consumeMatchingCodeTokens(
      arrayOf(
        CodeToken.PLUS_EQUAL, CodeToken.MINUS_EQUAL, CodeToken.STAR_EQUAL, CodeToken.STAR_STAR_EQUAL, CodeToken.SLASH_EQUAL,
        CodeToken.PERCENTAGE_EQUAL,
        CodeToken.AMPERSAND_EQUAL, CodeToken.VERTICAL_BAR_EQUAL, CodeToken.CIRCUMFLEX_EQUAL, CodeToken.TILDE_EQUAL,
        CodeToken.LESS_LESS_EQUAL, CodeToken.GREATER_GREATER_EQUAL, CodeToken.GREATER_GREATER_GREATER_EQUAL,
        CodeToken.EQUAL
      )
    ) ?: return if_expr()
    return GriddedObject.of(
      call.startCoords(),
      Expression.Assignment(call, tk, assignment()),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun if_expr(): GriddedObject<Expression> {
    scanner.startSection()
    val ifTk = scanner.consumeMatchingKeywordToken(Keyword.IF)
    if (ifTk == null) {
      scanner.endSection(true)
      return logic_or()
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "if_expr, open paren")
    val condition = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "if_expr, close paren")
    val thenStatement = statement() ?: throw ParserException("then at if not provided.")
    expectKeywordToken(scanner, Keyword.ELSE, "if_expr, else expr")
    val elseStatement = statement() ?: throw ParserException("No statement after else provided.")
    scanner.endSection()
    return GriddedObject.of(
      ifTk.startCoords(),
      Expression.IfExpression(condition, thenStatement, elseStatement),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun binaryOp(ops: Array<CodeToken>, next: () -> GriddedObject<Expression>): GriddedObject<Expression> {
    val nextExpr = next()
    scanner.startSection()
    val operations = listOfUntilNull {
      Pair(scanner.consumeMatchingCodeTokens(ops) ?: return@listOfUntilNull null, next())
    }
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

  fun unary_left(): GriddedObject<Expression> = throw NotImplementedError("unary_left")
  fun call(): GriddedObject<Expression> = throw NotImplementedError("call")
  fun primary(): GriddedObject<Expression> = throw NotImplementedError("primary")

  fun arguments(): GriddedObject<Expression>? = null
  fun block(): GriddedObject<Expression.Block>? = null

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

private fun expectCodeToken(scanner: TokenScanner, codeToken: CodeToken, message: String? = null) {
  val tk = scanner.consumeMatchingInnerClass(Token.SignToken::class.java)
    ?: throw ParserException(
      (if (message == null) "" else "$message. ") + "Expected Token.SignToken at ${
        scanner.positionCoords().startCoords()
      }, got " + (scanner.consume()?.obj ?: "EOF")
    )

  if (tk.obj.codeToken != codeToken) throw ParserException(
    (if (message == null) "" else "$message. ") + "Expected $codeToken at ${
      scanner.positionCoords().startCoords()
    }, got ${tk.obj.codeToken}"
  )
}

private fun expectKeywordToken(scanner: TokenScanner, keyword: Keyword, message: String? = null) {
  val tk = scanner.consumeMatchingInnerClass(Token.KeywordToken::class.java)
    ?: throw ParserException(
      (if (message == null) "" else "$message. ") + "Expected Token.KeywordToken at ${
        scanner.positionCoords().startCoords()
      }, got " + (scanner.consume()?.obj ?: "EOF")
    )

  if (tk.obj.keyword != keyword) throw ParserException(
    (if (message == null) "" else "$message. ") + "Expected $keyword at ${
      scanner.positionCoords().startCoords()
    }, got ${tk.obj.keyword}"
  )
}
