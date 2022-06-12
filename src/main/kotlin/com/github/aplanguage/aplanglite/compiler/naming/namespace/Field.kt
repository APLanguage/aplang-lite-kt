package com.github.aplanguage.aplanglite.compiler.naming.namespace

import arrow.core.Either
import arrow.core.handleError
import com.github.aplanguage.aplanglite.compiler.compilation.FieldCompilationContext
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ExpressionToBytecodeVisitor
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.Pool
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.ResultTarget
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.Instruction
import com.github.aplanguage.aplanglite.compiler.naming.Frame
import com.github.aplanguage.aplanglite.compiler.naming.NameResolver
import com.github.aplanguage.aplanglite.compiler.typechecking.TypeChecker
import com.github.aplanguage.aplanglite.parser.expression.DataExpression
import com.github.aplanguage.aplanglite.parser.expression.visit
import com.github.aplanguage.aplanglite.utils.GriddedObject

class Field(
  val name: String,
  var type: Either<GriddedObject<String>, Class>?,
  var expr: GriddedObject<DataExpression>?
) : Typeable, VirtualTypeable, Settable {
  lateinit var parent: Namespace
  val compilationContexts = mutableListOf<FieldCompilationContext>()

  fun asDescriptorString() = "${parent.path().let { if (it.isEmpty()) "" else "$it%" }}$name:${type?.orNull()?.path() ?: "?"}"

  override fun type(): Class = type?.orNull() ?: throw TypeResolveException("The type of ${asDescriptorString()} was not resolved!")
  fun typeCheck(namespaces: Set<Namespace>) {
    expr?.visit(TypeChecker(NameResolver(parent, namespaces.toList(), Frame(type()))), Unit)
  }

  override fun virtualType() = parent as? Class
  override fun isStatic() = parent !is Class
}
