package com.github.aplanguage.aplanglite.compiler

sealed class Signature {
  data class TypeSignature(
    val path: String,
    val parent: TypeSignature?,
    val fields: Map<String, TypeSignature>,
    val methods: Set<MethodSignature>
  ) : Signature()

  data class FieldSignature(val name: String, val type: TypeSignature) : Signature()

  data class MethodSignature(
    val name: String,
    val parameters: List<TypeSignature>,
    val returnType: TypeSignature?
  ) : Signature()
}
