package com.github.aplanguage.aplanglite.interpreter

import com.github.aplanguage.aplanglite.interpreter.stdlib.StdLibFunctions
import com.github.aplanguage.aplanglite.parser.Expression
import java.lang.invoke.MethodHandles

class InterpreterException(message: String) : RuntimeException(message)

class Interpreter {

  fun runExpression(scope: Scope, expression: Expression): ReturnValue {
    return when (expression) {
      is Expression.Block -> expression.run(this, scope)
      is Expression.DataExpression -> expression.run(this, scope)
      is Expression.Statement.BreakStatement -> ReturnValue.Unit
      is Expression.Declaration.VarDeclaration -> {
        val fieldVal = expression.toFieldValue().also { it.value(this, scope) }
        scope.fields[expression.identifier.obj.identifier] = fieldVal
        fieldVal
      }
      is Expression.Statement -> expression.run(this, scope)
      else -> ReturnValue.Unit
    }
  }

  fun compileAndRun(program: Expression.Program) {
    val structure = forgeStructure(program)
    val globalScope =
      Scope(structure.vars.map { it.toFieldValue() }.associateBy { it.identifier }
        .toMutableMap(),
        StdLibFunctions.javaClass.declaredMethods.let { arrayOfMethods ->
          val lookup = MethodHandles.publicLookup().`in`(StdLibFunctions.javaClass);
          arrayOfMethods.map { method ->
            ReturnValue.CallableValue.CallableFunctionValue.NativeMethodCallable(method.name, lookup.unreflect(method))
          }.associateBy { it.identifier } + structure.classes.map {
            ReturnValue.CallableValue.CallableFunctionValue.ClassCallValue(it)
          }.associateBy { it.identifier } + structure.functions.map {
            it.toCallableClassFunction()
          }.associateBy { it.identifier }
        }
      )
    val main = structure.functions.find { it.identifier == "main" }
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
    ).let {
      if (it is ReturnValue.TriggeredReturn) it.returnValue ?: ReturnValue.Unit
      else it
    }
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
    program.vars.map { Structure.VarStructure(it.obj.identifier.obj.identifier, it.obj.type?.obj, it.obj.expr?.obj, null) },
    program.functions.map {
      Structure.FunctionStructure(
        it.obj.identifier.obj.identifier,
        it.obj.parameters.map { it.second.obj },
        it.obj.type?.obj,
        it.obj
      )
    },
    program.classes.map {
      Structure.ClassStructure(
        it.obj.identifier.obj.identifier, it.obj.superTypes.map { it.obj }, forgeStructure(
          it.obj.content?.obj ?: Expression.Program(listOf(), listOf(), listOf(), listOf())
        )
      )
    }
  )
}
