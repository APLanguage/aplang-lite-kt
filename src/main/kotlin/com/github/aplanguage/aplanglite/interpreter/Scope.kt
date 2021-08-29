package com.github.aplanguage.aplanglite.interpreter

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
