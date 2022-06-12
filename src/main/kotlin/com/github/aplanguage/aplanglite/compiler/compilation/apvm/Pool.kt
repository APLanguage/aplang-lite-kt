package com.github.aplanguage.aplanglite.compiler.compilation.apvm

import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.ConstantInfo
import com.github.aplanguage.aplanglite.compiler.compilation.apvm.bytecode.ReferenceInfo
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Class
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Field
import com.github.aplanguage.aplanglite.compiler.naming.namespace.Method

class Pool {
  val referencePool = mutableListOf<ReferenceInfo>()
  val constantPool = mutableListOf<ConstantInfo>()

  operator fun contains(clazz: Class) = referencePool.any { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo && it.clazz == clazz }
  operator fun contains(method: Method) = referencePool.any { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo && it.method == method }
  operator fun contains(field: Field) = referencePool.any { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo && it.field == field }
  operator fun get(clazz: Class) = pushOrGet(clazz)
  operator fun get(method: Method) = pushOrGet(method)
  operator fun get(field: Field) = pushOrGet(field)
  operator fun get(string: String) = pushOrGet(string)

  fun pushOrGet(field: Field): ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo {
    referencePool.firstOrNull { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo && it.field == field }?.run { return@pushOrGet this as ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo }
    val parent = field.parent.let { if (it is Class) pushOrGet(it) else null }?.id
    val type = pushOrGet(field.type()).classReference
    return ReferenceInfo.ResolvedReferenceInfo.ResolvedFieldReferenceInfo(
      field,
      ReferenceInfo.FieldReference(
        referencePool.size.toUShort(),
        field.name,
        parent,
        type
      )
    ).also(referencePool::add)
  }

  fun pushOrGet(method: Method): ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo {
    referencePool.firstOrNull { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo && it.method == method }
      ?.run { return@pushOrGet this as ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo }
    val parent = method.parent.let { if (it is Class) pushOrGet(it) else null }?.id
    val returnType = method.type()?.let { pushOrGet(it).classReference }
    val parameters = method.parameters.map { pushOrGet(it.type()).classReference }
    return ReferenceInfo.ResolvedReferenceInfo.ResolvedMethodReferenceInfo(
      method,
      ReferenceInfo.MethodReference(
        referencePool.size.toUShort(),
        method.name,
        parent,
        returnType,
        parameters
      )
    ).also(referencePool::add)
  }

  fun pushOrGet(clazz: Class): ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo {
    referencePool.firstOrNull { it is ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo && it.clazz == clazz }?.run { return@pushOrGet this as ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo }
    val parent = clazz.parent.let { if (it is Class) pushOrGet(it) else null }?.id
    return ReferenceInfo.ResolvedReferenceInfo.ResolvedClassReferenceInfo(
      clazz,
      ReferenceInfo.ClassReference(
        referencePool.size.toUShort(),
        clazz.name,
        parent
      )
    ).also(referencePool::add)
  }

  fun pushOrGet(constant: String): ConstantInfo.StringConstant {
    constantPool.firstOrNull { it is ConstantInfo.StringConstant && it.string == constant }?.run { return this as ConstantInfo.StringConstant }
    return ConstantInfo.StringConstant(constant, constantPool.size.toUShort()).also(constantPool::add)
  }
}
