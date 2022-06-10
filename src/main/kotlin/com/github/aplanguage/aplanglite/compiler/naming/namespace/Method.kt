package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ExpressionToBytecodeVisitor
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.RegisterAllocator
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.naming.Frame
import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.NameResolver
import com.github.aplanguage.aplanglite.compiler.typechecking.StatementTypeChecker
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeChecker
import com.github.aplanguage.aplanglite.parser.expression.Statement
import com.github.aplanguage.aplanglite.utils.GriddedObject

class Method(
  val name: String,
  var returnType: Either<GriddedObject<String>, Class>?,
  var exprs: Either<List<GriddedObject<Statement>>, List<Instruction>>
) : Typeable, VirtualTypeable {
  var resolvedRegisters: List<RegisterAllocator.Type> = listOf()
  val parameters = mutableListOf<MethodParameter>()
  lateinit var parent: Namespace

  fun asDescriptorString(): String {
    return parent.path().let { if (it.isEmpty()) "" else "$it#" } + name + "(${
      parameters.joinToString(", ") { (_, type) ->
        type.fold({ it.obj }, { it.path() })
      }
    })" + (returnType?.fold({ ": $it" }, { ": ${it.path()}" }) ?: "")
  }

  override fun type(): Class? = returnType?.let {
    it.orNull() ?: throw TypeResolveException(
      "The return type ${(it as Either.Left).value} of ${asDescriptorString()} was not resolved!",
      it.value.area()
    )
  }

  fun typeCheck(namespaces: Set<Namespace>) {
    val checker = StatementTypeChecker(TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()).apply {
      this@Method.parameters.forEach { parameter ->
        parameter.clazz.fold({ throw IllegalStateException("Parameters not resolved!") }, { register(parameter.name, it) })
      }
    })))
    exprs.mapLeft { it.forEach { it.obj.visit(checker, Unit) } }
  }

  fun addParameter(name: String, clazz: Either<GriddedObject<String>, Class>) {
    parameters.add(MethodParameter(name, clazz))
  }

  override fun virtualType() = parent as? Class
  fun compile(pool: Pool) {
    val frame =
      com.github.aplanguage.aplanglite.compiler.compilation.apvm.Frame(
        pool,
        parameters.map { it.localVariable ?: LocalVariable(it.name, it.type()) })
    exprs = exprs.handleError {
      frame.enterScope()
      val ins = it.flatMap { it.obj.visit(ExpressionToBytecodeVisitor(frame), null).instructions() }
      frame.leaveScope()
      ins
    }
    resolvedRegisters = frame.registerAllocator.registers.map { it.type }
  }

  fun isStatic() = parent !is Class

  inner class MethodParameter(val name: String, var clazz: Either<GriddedObject<String>, Class>) : Typeable {
    var localVariable: LocalVariable? = null
    operator fun component1() = name
    operator fun component2() = clazz
    override fun type(): Class = clazz.orNull() ?: throw IllegalStateException("The method parameter $name was not resolved!")
  }
}