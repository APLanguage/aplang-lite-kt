package com.github.aplanguage.aplanglite.compiler.compilation

import com.github.aplanguage.aplanglite.compiler.naming.namespace.Field
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Method

interface CompilationContext<MCC : MethodCompilationContext, FCC : FieldCompilationContext> {
  fun compile(method: Method, context: MCC) = context.compile(method)
  fun compile(field: Field, context: FCC) = context.compile(field)

  fun fieldCompilationContext(field: Field): FCC?
  fun methodCompilationContext(method: Method): MCC?
}

interface MethodCompilationContext {
  fun compile(method: Method)
}

interface FieldCompilationContext {
  fun compile(field: Field)
}
