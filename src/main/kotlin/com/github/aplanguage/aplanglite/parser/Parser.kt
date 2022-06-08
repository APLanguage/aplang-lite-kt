package com.github.aplanguage.aplanglite.parser

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.DataExpression.BinaryOperation.BinaryOpType
import com.github.aplanguage.aplanglite.parser.expression.Declaration
import com.github.aplanguage.aplanglite.parser.expression.Declaration.Companion.asStatement
import com.github.aplanguage.aplanglite.parser.expression.Expression
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.tokenizer.CodeToken
import com.github.aplanguage.aplanglite.tokenizer.Keyword
import com.github.aplanguage.aplanglite.tokenizer.Token
import com.github.aplanguage.aplanglite.utils.Area
import com.github.aplanguage.aplanglite.utils.GriddedObject
import com.github.aplanguage.aplanglite.utils.TokenScanner
import com.github.aplanguage.aplanglite.utils.Underliner
import com.github.aplanguage.aplanglite.utils.filterOfType
import com.github.aplanguage.aplanglite.utils.listOfUntilNull
import com.github.aplanguage.aplanglite.utils.toNonEmptyList

class ParserException(msg: String, val area: Area? = null) : RuntimeException(msg)

data class ParserError(val exception: ParserException, val area: Area, val message: String? = null)

class Parser(val scanner: TokenScanner, val underliner: Underliner?) {

  private val errors: MutableList<ParserError> = mutableListOf()

  fun clearErrors() = errors.clear()

  fun errors() = errors.toList()

  fun program(): GriddedObject<Expression.Program>? {
    val packageDeclaration = packageDeclaration()
    val uses = listOfUntilNull(this::use_decl)
    val declarations = listOfUntilNull {
      if (scanner.isPositionEOF()) null
      else declaration()
    }
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
    val path = path() ?: throw ParserException("Expected path after package keyword", packageTk.endCoords().toArea())
    scanner.endSection()
    return GriddedObject.of(packageTk.startCoords(), Expression.PackageDeclaration(path), path.endCoords()).also {
      if (it.endCoords().y != scanner.peekNextCoords().startCoords().y) {
        errors.add(
          ParserError(
            ParserException("Expected end of line after package declaration", it.endCoords().toArea()),
            it.endCoords().toArea(),
            "Expected end of line after package declaration"
          )
        )

      }
    }
  }

  fun declaration(): GriddedObject<Declaration> =
    class_decl() ?: fun_decl() ?: var_decl()
    ?: throw ParserException("Expected class, fun or var, got ${scanner.peek()}", scanner.peekPreviousCoords())

  fun class_decl(): GriddedObject<Declaration.ClassDeclaration>? {
    scanner.startSection()
    val classTk = scanner.consumeMatchingKeywordToken(Keyword.CLASS)
    if (classTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "After class")
    val superTypes = mutableListOf<GriddedObject<Expression.Type>>()
    if (scanner.consumeMatchingCodeToken(CodeToken.COLON) != null) {
      superTypes.add(type() ?: throw ParserException("After : a Type expected.", scanner.positionPreviousCoords().endCoords().toArea()))
      while (scanner.consumeMatchingCodeToken(CodeToken.COMMA) != null) {
        superTypes.add(type() ?: throw ParserException("After , a Type expected.", scanner.positionPreviousCoords().endCoords().toArea()))
      }
    }
    val program = buildList {
      if (scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) != null) {
        while (scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null) {
          add(use_decl() ?: declaration())
        }
        expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "class_decl, close brace")?.also { throw it }
      }
    }
    scanner.endSection()
    return GriddedObject.of(
      classTk.startCoords(),
      Declaration.ClassDeclaration(
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

  fun fun_decl(): GriddedObject<Declaration.FunctionDeclaration>? {
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
      throw ParserException("Every Function has a body.", fnTk.endCoords().toArea())
    } else GriddedObject.of(
      fnTk.startCoords(),
      Declaration.FunctionDeclaration(identifier, parameters, returnType, block.obj.statements),
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
          type() ?: throw ParserException("Each parameter must have a type.", parameterIdentifier.endCoords().toArea())
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


  fun var_decl(): GriddedObject<Declaration.VarDeclaration>? {
    scanner.startSection()
    val varTk = scanner.consumeMatchingKeywordToken(Keyword.VAR)
    if (varTk == null) {
      scanner.endSection(true)
      return null
    }
    val identifier = expectIdentifier(scanner, "var_decl, After var").repack { it.identifier }
    scanner.startSection()
    val type = scanner.consumeMatchingCodeToken(CodeToken.COLON)?.let { type() }
    scanner.endSection(type == null)
    scanner.startSection()
    val expr = scanner.consumeMatchingCodeToken(CodeToken.EQUAL)?.let { expression() }
    scanner.endSection(expr == null)
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a var declaration there must be an new-line.", scanner.positionPreviousCoords().endCoords().toArea())
    }
    scanner.endSection()
    return GriddedObject.of(
      varTk.startCoords(),
      Declaration.VarDeclaration(identifier, Either.Left(type), expr),
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
      errors.add(ParserError(ParserException("No path specified for use_decl", scanner.positionPreviousCoords()), scanner.positionPreviousCoords()))
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
        errors.add(ParserError(ParserException("After a use statement there must be an new-line.", leftOver), leftOver))
      } else {
        if (asOther != null || star != null) {
          errors.add(ParserError(ParserException("No path specified.", useTk.endCoords().toArea()), useTk.endCoords().toArea()))
        } else {
          errors.add(ParserError(ParserException("After a use statement there must be an new-line.", leftOver), leftOver))
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
      Declaration.UseDeclaration(path, star != null, asOther?.repack { it.identifier }),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun statement(): GriddedObject<Statement> =
    for_stmt() ?: return_stmt() ?: break_stmt() ?: while_stmt() ?: if_stmt() ?: var_decl()?.repack { it.asStatement() } ?: block() ?: exp_stmt()

  fun for_stmt(): GriddedObject<Statement.ForStatement>? {
    scanner.startSection()
    val forTk = scanner.consumeMatchingKeywordToken(Keyword.FOR)
    if (forTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "for_stmt, open paren")?.also { throw it }
    val identifier = expectIdentifier(scanner, "for_stmt, After (").repack { it.identifier }
    expectCodeToken(scanner, CodeToken.COLON, "for_stmt, separator")?.also { throw it }
    val type = type()?.repack { it.path.asString() } ?: throw ParserException("for_stmt, type", scanner.positionPreviousCoords())
    expectCodeToken(scanner, CodeToken.COLON, "for_stmt, separator")?.also { throw it }
    val expr = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "for_stmt, close paren")?.also { throw it }
    val statement = statement()
    scanner.endSection()
    return GriddedObject.of(forTk.startCoords(), Statement.ForStatement(identifier, Either.Left(type), expr, statement), statement.endCoords())
  }

  fun return_stmt(): GriddedObject<Statement.ReturnStatement>? {
    scanner.startSection()
    val returnTk = scanner.consumeMatchingKeywordToken(Keyword.RETURN)
    if (returnTk == null) {
      scanner.endSection(true)
      return null
    }
    var expr: GriddedObject<DataExpression>? = null
    if (!scanner.isPositionEOF() && returnTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      expr = expression()
      if (!scanner.isPositionEOF() && returnTk.endCoords().y == scanner.positionCoords().startCoords().y) {
        throw ParserException("After a return statement there must be an new-line.", returnTk.area())
      }
    }
    scanner.endSection()
    return GriddedObject.of(returnTk.startCoords(), Statement.ReturnStatement(expr), scanner.positionCoords().endCoords())
  }

  fun break_stmt(): GriddedObject<Statement.BreakStatement>? {
    scanner.startSection()
    val breakTk = scanner.consumeMatchingKeywordToken(Keyword.BREAK)
    if (breakTk == null) {
      scanner.endSection(true)
      return null
    }
    if (!scanner.isPositionEOF() && breakTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      throw ParserException("After a break statement there must be an new-line.", breakTk.endCoords().toArea())
    }
    scanner.endSection()
    return GriddedObject.of(breakTk.startCoords(), Statement.BreakStatement, scanner.positionCoords().endCoords())
  }

  fun while_stmt(): GriddedObject<Statement.WhileStatement>? {
    scanner.startSection()
    val whileTk = scanner.consumeMatchingKeywordToken(Keyword.WHILE)
    if (whileTk == null) {
      scanner.endSection(true)
      return null
    }
    expectCodeToken(scanner, CodeToken.LEFT_PAREN, "while_stmt, open paren")?.also { throw it }
    val expr = expression()
    expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "while_stmt, close paren")?.also { throw it }
    var statement: GriddedObject<Statement>? = null
    if (!scanner.isPositionEOF() && whileTk.endCoords().y == scanner.positionCoords().startCoords().y) {
      statement = statement()
    }
    scanner.endSection()
    return GriddedObject.of(whileTk.startCoords(), Statement.WhileStatement(expr, statement), scanner.positionCoords().endCoords())
  }

  fun if_stmt(): GriddedObject<Statement.IfStatement>? {
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
    var elseStatement: GriddedObject<Statement>? = null
    if (scanner.consumeMatchingKeywordToken(Keyword.ELSE) != null) {
      elseStatement = statement()
    }
    scanner.endSection()
    return GriddedObject.of(
      ifTk.startCoords(),
      Statement.IfStatement(condition, thenStatement, elseStatement),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun exp_stmt(): GriddedObject<Statement.ExpressionStatement> {
    val expr = expression()
    if (!scanner.isPositionEOF() && scanner.positionPreviousCoords().endCoords().y == scanner.positionCoords().startCoords().y) {
      if (scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null)
        throw ParserException("After an expr there must be a new-line or } of a closing scope.", expr.endCoords().toArea())
      else scanner.rewindPeek(1)
    }
    return expr.repack { Statement.ExpressionStatement(it) }
  }

  fun expression(): GriddedObject<DataExpression> = assignment()
  fun assignment(): GriddedObject<DataExpression> {
    scanner.startSection()
    val left = logic_or()
    val tk = scanner.consumeMatchingCodeTokens(
      arrayOf(
        CodeToken.PLUS_EQUAL, CodeToken.MINUS_EQUAL, CodeToken.STAR_EQUAL, CodeToken.STAR_STAR_EQUAL, CodeToken.SLASH_EQUAL,
        CodeToken.PERCENTAGE_EQUAL,
        CodeToken.AMPERSAND_EQUAL, CodeToken.VERTICAL_BAR_EQUAL, CodeToken.CIRCUMFLEX_EQUAL,
        CodeToken.LESS_LESS_EQUAL, CodeToken.GREATER_GREATER_EQUAL, CodeToken.GREATER_GREATER_GREATER_EQUAL,
        CodeToken.EQUAL
      )
    )
    if (tk == null) {
      scanner.endSection(true)
      return if_expr()
    }
    if (left.obj !is DataExpression.IdentifierExpression && left.obj !is DataExpression.Call) {
      throw ParserException(
        "For the left-side of the assignment it only can be a Call or an Identifier.", scanner.positionPreviousCoords()
      )
    }
    return GriddedObject.of(
      left.startCoords(),
      DataExpression.Assignment(left, tk, assignment()),
      scanner.positionPreviousCoords().endCoords()
    ).also { scanner.endSection() }
  }

  fun if_expr(): GriddedObject<DataExpression> {
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
      DataExpression.IfExpression(condition, thenExpr, elseExpr),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  inline fun binaryOp(ops: Array<CodeToken>, next: () -> GriddedObject<DataExpression>, binaryOpType: BinaryOpType): GriddedObject<DataExpression> {
    val nextExpr = next()
    scanner.startSection()
    val operations = listOfUntilNull {
      Pair(scanner.consumeMatchingCodeTokens(ops) ?: return@listOfUntilNull null, next())
    }
    scanner.endSection()
    return if (operations.isEmpty()) nextExpr
    else GriddedObject.of(
      nextExpr.startCoords(),
      DataExpression.BinaryOperation(binaryOpType, nextExpr, operations.toNonEmptyList()),
      scanner.positionPreviousCoords().endCoords()
    )
  }

  fun logic_or() = binaryOp(arrayOf(CodeToken.DOUBLE_VERTICAL_BAR), this::logic_and, BinaryOpType.LOGIC_OR)
  fun logic_and() = binaryOp(arrayOf(CodeToken.AMPERSAND_AMPERSAND), this::equality, BinaryOpType.LOGIC_AND)
  fun equality() = binaryOp(arrayOf(CodeToken.BANG_EQUAL, CodeToken.EQUAL_EQUAL), this::comparison, BinaryOpType.EQUALITY)
  fun comparison() = binaryOp(
    arrayOf(CodeToken.GREATER, CodeToken.GREATER_EQUAL, CodeToken.LESS, CodeToken.LESS_EQUAL),
    this::term,
    BinaryOpType.COMPARISON
  )

  fun term() = binaryOp(arrayOf(CodeToken.PLUS, CodeToken.MINUS), this::factor, BinaryOpType.TERM)
  fun factor() = binaryOp(arrayOf(CodeToken.SLASH, CodeToken.STAR, CodeToken.STAR_STAR, CodeToken.PERCENTAGE), this::bit_op, BinaryOpType.FACTOR)
  fun bit_op() = binaryOp(
    arrayOf(
      CodeToken.VERTICAL_BAR,
      CodeToken.CIRCUMFLEX,
      CodeToken.AMPERSAND,
      CodeToken.GREATER_GREATER,
      CodeToken.LESS_LESS,
      CodeToken.GREATER_GREATER_GREATER
    ), this::oop, BinaryOpType.BIT_OP
  )

  fun oop(): GriddedObject<DataExpression> {
    val nextExpr = unary_left()
    scanner.startSection()
    val op = if (scanner.consumeMatchingCodeToken(CodeToken.BANG_IS) != null) DataExpression.OopExpression.OopOpType.IS_NOT
    else if (scanner.consumeMatchingKeywordToken(Keyword.IS) != null) DataExpression.OopExpression.OopOpType.IS
    else if (scanner.consumeMatchingKeywordToken(Keyword.AS) != null) DataExpression.OopExpression.OopOpType.AS
    else return nextExpr.also { scanner.endSection() }
    val type = type() ?: throw ParserException(
      "Type expected after $op.", scanner.positionPreviousCoords().endCoords().toArea()
    )
    scanner.endSection()
    return GriddedObject.of(
      nextExpr.startCoords(),
      DataExpression.OopExpression(nextExpr, op, Either.Left(type)),
      type.endCoords()
    )
  }

  fun unary_left(): GriddedObject<DataExpression> {
    scanner.startSection()
    val tk =
      scanner.consumeMatchingCodeTokens(arrayOf(CodeToken.BANG, CodeToken.TILDE, CodeToken.MINUS)) ?: return call().also { scanner.endSection() }
    return GriddedObject.of(
      tk.startCoords(),
      DataExpression.UnaryOperation(tk, unary_left()),
      scanner.positionPreviousCoords().endCoords()
    ).also { scanner.endSection() }
  }

  fun call(): GriddedObject<DataExpression> {
    val primaryObj = primary()
    val primary = when (primaryObj.obj) {
      is DataExpression.IdentifierExpression -> {
        funcArguments((primaryObj.obj as DataExpression.IdentifierExpression).identifier) ?: primaryObj
      }
      else -> primaryObj
    }
    val calls = listOfUntilNull {
      scanner.consumeMatchingCodeToken(CodeToken.DOT) ?: return@listOfUntilNull null
      val identifier = scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)?.repack { it.identifier } ?: throw ParserException(
        "Identifier expected after '.'.", scanner.positionPreviousCoords().endCoords().toArea()
      )
      funcArguments(identifier)?.left() ?: identifier.left().right()
    }
    return if (calls.isEmpty()) primary
    else calls.drop(1).fold(
      GriddedObject.of(
        primaryObj.startCoords(),
        DataExpression.Call(primary, calls.first()),
        calls.first().fold({ it }, { (it as Either.Left).value }).endCoords()
      )
    ) { call, func ->
      GriddedObject.of(call.startCoords(), DataExpression.Call(call, func), func.fold({ it }, { (it as Either.Left).value }).endCoords())
    }
  }

  fun funcArguments(identifier: GriddedObject<String>): GriddedObject<DataExpression.FunctionCall>? {
    if (scanner.consumeMatchingCodeToken(CodeToken.LEFT_PAREN) == null) return null
    return GriddedObject.of(identifier.startCoords(), DataExpression.FunctionCall(identifier,
      if (scanner.consumeMatchingCodeToken(CodeToken.RIGHT_PAREN) == null)
        (listOf(expression()) + listOfUntilNull {
          scanner.consumeMatchingCodeToken(CodeToken.COMMA)?.let { return@listOfUntilNull expression() }
        }).also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "invocation, close paren")?.also { throw it } }
      else listOf()
    ), scanner.positionPreviousCoords().endCoords())
  }

  fun primary(): GriddedObject<DataExpression> {
    val tk = scanner.consume() ?: throw ParserException("No EOF expected!")
    return when (val tkObj = tk.obj) {
      is Token.IdentifierToken -> tk.repack(DataExpression.IdentifierExpression(tk.repack { (it as Token.IdentifierToken).identifier }))
      is Token.ValueToken -> tk.repack(DataExpression.DirectValue(tk.obj as Token.ValueToken))
      is Token.SignToken -> {
        if (tkObj.codeToken == CodeToken.LEFT_PAREN)
          expression().also { expectCodeToken(scanner, CodeToken.RIGHT_PAREN, "primary, (expression) close paren")?.also { throw it } }
        else throw ParserException("Identifier, ValueToken or ( expected, got ${tk.obj}", tk.area())
      }
      is Token.KeywordToken -> if (tkObj.keyword == Keyword.IF) if_expr()
      else throw ParserException("Unexpected Token: ${tk.obj}", tk.area())
    }
  }

  fun block(): GriddedObject<Statement.Block>? {
    val openBrace = scanner.consumeMatchingCodeToken(CodeToken.LEFT_BRACE) ?: return null
    val stmts = mutableListOf<GriddedObject<Statement>>()
    while (!scanner.isPositionEOF() && scanner.peekMatchingCodeToken(CodeToken.RIGHT_BRACE) == null) {
      stmts.add(var_decl()?.repack { Statement.DeclarationStatement(it) } ?: statement())
    }
    expectCodeToken(scanner, CodeToken.RIGHT_BRACE, "block, closing brace")?.also { throw it }
    return GriddedObject.of(openBrace.startCoords(), Statement.Block(stmts), scanner.positionPreviousCoords().endCoords())
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
        (if (message == null) "" else "$message. ") + "Expected Token.SignToken, got " + (scanner.consume()?.obj ?: "EOF"),
        scanner.positionCoords().startCoords().toArea()
      )
    if (tk.obj.codeToken != codeToken) return ParserException(
      (if (message == null) "" else "$message. ") + "Expected $codeToken, got ${tk.obj.codeToken}", scanner.positionCoords().startCoords().toArea()
    )
    return null
  }

  private fun expectKeywordToken(scanner: TokenScanner, keyword: Keyword, message: String? = null): ParserException? {
    val tk = scanner.consumeMatchingInnerClass(Token.KeywordToken::class.java)
      ?: return ParserException(
        (if (message == null) "" else "$message. ") + "Expected Token.KeywordToken, got " + (scanner.consume()?.obj ?: "EOF"),
        scanner.positionCoords().startCoords().toArea()
      )
    if (tk.obj.keyword != keyword) return ParserException(
      (if (message == null) "" else "$message. ") + "Expected $keyword at ${
        scanner.positionCoords().startCoords()
      }, got ${tk.obj.keyword}",
      scanner.positionCoords().startCoords().toArea()
    )
    return null
  }

  private fun expectIdentifier(scanner: TokenScanner, message: String? = null): GriddedObject<Token.IdentifierToken> {
    return scanner.consumeMatchingInnerClass(Token.IdentifierToken::class.java)
      ?: throw ParserException(
        (if (message == null) "" else "$message. ") + "Expected Token.IdentifierToken, got " + (scanner.consume()?.obj ?: "EOF"),
        scanner.positionCoords().startCoords().toArea()
      )
  }
}
