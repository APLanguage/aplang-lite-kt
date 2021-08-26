package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.parser.Expression

class InterpreterException(message: String) : RuntimeException(message)

class Interpreter {

  data class Scope(val fields: MutableMap<String, Structure.VarStructure>, var scope: Scope? = null) {
    fun findField(identifier: String): Structure.VarStructure? = fields.getOrDefault(identifier, scope?.findField(identifier))
  }

  fun runStructure(globalStructure: Structure, scope: Scope, currentStructure: Structure): ReturnValue {
    return when (currentStructure) {
      is Structure.FunctionStructure -> TODO()
      is Structure.VarStructure -> TODO()
      else -> ReturnValue.Unit
    }
  }

  fun runExpression(scope: Scope, expression: Expression): ReturnValue {
    return when (expression) {
      is Expression.Block -> {
        var returnValue: ReturnValue = ReturnValue.Unit
        val blockScope = Scope(mutableMapOf(), scope)
        for (statement in expression.statements) {
          returnValue = runExpression(blockScope, statement.obj)
        }
        returnValue
      }
      else -> TODO()
    }
  }

  fun compileAndRun(program: Expression.Program) {
    val structure = forgeStructure(program)
    val globalScope =
      Scope(structure.structures.filterIsInstance(Structure.VarStructure::class.java).associateBy { it.identifier }.toMutableMap())
    val main = structure.structures.find { it is Structure.FunctionStructure && it.identifier == "main" }
    if (main == null) println("No Main-Method Found")
    else runStructure(structure, globalScope, main)
  }

  fun forgeStructure(program: Expression.Program): Structure.GlobalStructure = Structure.GlobalStructure(
    program.uses.mapNotNull {
      val obj = it.obj
      if (obj is Expression.Declaration.UseDeclaration) Structure.UseStructure(
        obj.path.obj.identifiers.map { it.obj.identifier }.joinToString("."), obj.all,
        obj.asOther?.obj?.identifier
      )
      else null
    },
    program.declarations.mapNotNull {
      val obj = it.obj
      if (obj !is Expression.Declaration) null
      else {
        when (obj) {
          is Expression.Declaration.FunctionDeclaration -> {
            Structure.FunctionStructure(obj.identifier.obj.identifier, obj.parameters.map { it.second.obj }, obj.type?.obj, obj)
          }
          is Expression.Declaration.ClassDeclaration -> {
            Structure.ClassStructure(
              obj.identifier.obj.identifier, obj.superTypes.map { it.obj }, forgeStructure(
                obj.content?.obj ?: Expression.Program(listOf(), listOf())
              )
            )
          }
          is Expression.Declaration.VarDeclaration -> Structure.VarStructure(obj.identifier.obj.identifier, obj.type?.obj, obj.expr?.obj, null)
          is Expression.Declaration.UseDeclaration -> null
        }
      }
    }
  )
}
