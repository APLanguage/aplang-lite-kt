package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ExpressionToBytecodeVisitor
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ResultTarget
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.naming.Frame
import com.github.aplanguage.aplanglite.compiler.naming.NameResolver
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeChecker
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.utils.GriddedObject

class Field(
  val name: String,
  var type: Either<GriddedObject<String>, Class>?,
  var expr: Either<GriddedObject<DataExpression>, List<Instruction>>?
) : Typeable, VirtualTypeable, Settable {
  lateinit var parent: Namespace

  fun asDescriptorString() = "${parent.path().let { if (it.isEmpty()) "" else "$it%" }}$name:${type?.orNull()?.path() ?: "?"}"

  override fun type(): Class = type?.orNull() ?: throw TypeResolveException("The type of ${asDescriptorString()} was not resolved!")
  fun typeCheck(namespaces: Set<Namespace>) {
    expr?.mapLeft { TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()))) }
  }

  override fun virtualType() = parent as? Class
  override fun isStatic() = parent !is Class
  fun compile(pool: Pool) {
    expr = expr?.handleError {
      it.obj.visit(ExpressionToBytecodeVisitor(com.github.aplanguage.aplanglite.compiler.compilation.apvm.Frame(pool)), ResultTarget.Stack)
        .instructions()
    }
  }
}
