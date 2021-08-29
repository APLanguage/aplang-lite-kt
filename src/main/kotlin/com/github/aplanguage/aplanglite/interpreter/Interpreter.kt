package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.interpreter.stdlib.StdLibFunctions
import com.github.aplanguage.aplanglite.parser.Expression
import java.lang.invoke.MethodHandles

class InterpreterException(message: String) : RuntimeException(message)

class Interpreter {

  data class Scope(
    val fields: MutableMap<String, ReturnValue.PropertiesNFunctionsValue.FieldValue> = mutableMapOf(),
    val functions: Map<String, ReturnValue.CallableValue.CallableFunctionValue> = mutableMapOf(),
    var scope: Scope? = null
  ) {
    fun findField(identifier: String): ReturnValue.PropertiesNFunctionsValue.FieldValue? =
      fields.getOrElse(identifier) { scope?.findField(identifier) }

    fun findCallable(identifier: String): ReturnValue.CallableValue.CallableFunctionValue? =
      functions.getOrElse(identifier) { scope?.findCallable(identifier) }
  }

  fun runExpression(scope: Scope, expression: Expression): ReturnValue {
    return when (expression) {
      is Expression.Block -> expression.run(this, scope)
      is Expression.DataExpression -> expression.run(this, scope)
      is Expression.Statement.BreakStatement -> ReturnValue.Unit
      is Expression.Statement -> expression.run(this, scope)
      else -> ReturnValue.Unit
    }
  }

  fun compileAndRun(program: Expression.Program) {
    val structure = forgeStructure(program)
    val globalScope =
      Scope(structure.structures.filterIsInstance(Structure.VarStructure::class.java).map { it.toFieldValue() }.associateBy { it.identifier }
        .toMutableMap(),
        StdLibFunctions.javaClass.declaredMethods.let { arrayOfMethods ->
          val lookup = MethodHandles.publicLookup().`in`(StdLibFunctions.javaClass);
          arrayOfMethods.map { method ->
            ReturnValue.CallableValue.CallableFunctionValue(method.name, lookup.unreflect(method))
          }.associateBy { it.identifier }
        }
      )
    val main = structure.structures.find { it is Structure.FunctionStructure && it.identifier == "main" }
    if (main == null) println("No Main-Method Found")
    else runFunction(main as Structure.FunctionStructure, arrayOf(), globalScope)
  }

  fun runFunction(functionStructure: Structure.FunctionStructure, arguments: Array<ReturnValue>, scope: Scope): ReturnValue {
    if (functionStructure.parameters.size < arguments.size) {
      throw InterpreterException(
        "Too many arguments provided to ${functionStructure.identifier}(${
          functionStructure.parameters.joinToString(
            ", "
          )
        })" + (if (functionStructure.type == null) "" else functionStructure.type.path.identifiers.joinToString(".")) + "."
      )
    } else if (functionStructure.parameters.size > arguments.size) {
      throw InterpreterException(
        "Too few arguments provided to ${functionStructure.identifier}(${
          functionStructure.parameters.joinToString(
            ", "
          )
        })" + (if (functionStructure.type == null) "" else functionStructure.type.path.identifiers.joinToString(".")) + "."
      )
    }
    return runExpression(
      Scope(functionStructure.declaration.parameters.zip(arguments).map {
        Structure.VarStructure(it.first.first.obj.identifier, it.first.second.obj, null, it.second).toFieldValue()
      }.associateBy { it.identifier }.toMutableMap(), mapOf(), scope),
      functionStructure.declaration.block.obj
    )
  }

  fun forgeStructure(program: Expression.Program): Structure.GlobalStructure = Structure.GlobalStructure(
    program.uses.mapNotNull { useDeclaration ->
      val obj = useDeclaration.obj
      if (obj is Expression.Declaration.UseDeclaration) Structure.UseStructure(
        obj.path.obj.identifiers.joinToString(".") { it.obj.identifier }, obj.all,
        obj.asOther?.obj?.identifier
      )
      else null
    },
    program.declarations.mapNotNull { declaration ->
      val obj = declaration.obj
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
