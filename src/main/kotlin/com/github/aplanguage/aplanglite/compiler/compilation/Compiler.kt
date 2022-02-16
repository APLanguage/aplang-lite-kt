package com.github.aplanguage.aplanglite.compiler.compilation

import arrow.core.Either
import com.github.aplanguage.aplanglite.compiler.bytecode.ConstantInfo
import com.github.aplanguage.aplanglite.compiler.bytecode.ReferenceInfo
import com.github.aplanguage.aplanglite.compiler.bytecode.ReferenceInfo.ResolvedReferenceInfo.*
import com.github.aplanguage.aplanglite.compiler.naming.LocalVariable
import com.github.aplanguage.aplanglite.compiler.naming.Namespace
import com.github.aplanguage.aplanglite.utils.lastOrPut

class Pool {
  val referencePool = mutableListOf<ReferenceInfo>()
  val constantPool = mutableListOf<ConstantInfo>()

  operator fun contains(clazz: Namespace.Class) = referencePool.any { it is ResolvedClassReferenceInfo && it.clazz == clazz }
  operator fun contains(method: Namespace.Method) = referencePool.any { it is ResolvedMethodReferenceInfo && it.method == method }
  operator fun contains(field: Namespace.Field) = referencePool.any { it is ResolvedFieldReferenceInfo && it.field == field }
  operator fun get(clazz: Namespace.Class) = pushOrGet(clazz)
  operator fun get(method: Namespace.Method) = pushOrGet(method)
  operator fun get(field: Namespace.Field) = pushOrGet(field)
  operator fun get(string: String) = pushOrGet(string)

  fun pushOrGet(field: Namespace.Field): ResolvedFieldReferenceInfo {
    referencePool.firstOrNull { it is ResolvedFieldReferenceInfo && it.field == field }?.run { return@pushOrGet this as ResolvedFieldReferenceInfo }
    val parent = field.parent.let { if (it is Namespace.Class) pushOrGet(it) else null }?.id
    val type = pushOrGet(field.type()).classReference
    return ResolvedFieldReferenceInfo(
      field,
      ReferenceInfo.FieldReference(
        referencePool.size.toUShort(),
        field.name,
        parent,
        type
      )
    ).also(referencePool::add)
  }

  fun pushOrGet(method: Namespace.Method): ResolvedMethodReferenceInfo {
    referencePool.firstOrNull { it is ResolvedMethodReferenceInfo && it.method == method }
      ?.run { return@pushOrGet this as ResolvedMethodReferenceInfo }
    val parent = method.parent.let { if (it is Namespace.Class) pushOrGet(it) else null }?.id
    val returnType = method.type()?.let { pushOrGet(it).classReference }
    val parameters = method.parameters.map { pushOrGet(it.type()).classReference }
    return ResolvedMethodReferenceInfo(
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

  fun pushOrGet(clazz: Namespace.Class): ResolvedClassReferenceInfo {
    referencePool.firstOrNull { it is ResolvedClassReferenceInfo && it.clazz == clazz }?.run { return@pushOrGet this as ResolvedClassReferenceInfo }
    val parent = clazz.parent.let { if (it is Namespace.Class) pushOrGet(it) else null }?.id
    return ResolvedClassReferenceInfo(
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

class RegisterAllocator {
  enum class Type {

    BIT_8,
    BIT_16,
    BIT_32,
    BIT_64,
    REFERENCE;

    val byteSize: Byte
      get() = when (this) {
        BIT_8 -> 1
        BIT_16 -> 2
        BIT_32 -> 4
        BIT_64 -> 8
        REFERENCE -> 0
      }
  }

  inner class Register(val id: Int, val type: Type) {
    var used: Boolean = false

    fun use() {
      if (used) throw IllegalStateException("Register $id is already in use!")
      used = true
      scopes.lastOrPut { mutableListOf() }.add(this)
    }
  }

  val registers = mutableListOf<Register>()
  private val scopes = mutableListOf<MutableList<Register>>()

  fun register(type: Type): Register {
    val register = registers.firstOrNull { !it.used && it.type == type } ?: Register(registers.size, type).also(registers::add)
    register.use()
    return register
  }

  fun register(type: Namespace.Class): Register = register(type.primitiveType().registerType)
  fun register(type: Namespace.Typeable) = register(type.type()!!)

  operator fun get(index: Int) = registers[index]

  fun enterScope() {
    scopes.add(mutableListOf())
  }

  fun leaveScope() {
    scopes.removeLastOrNull()?.forEach { it.used = false }
  }

  companion object {
    inline fun Either<Register, Int>.registerIndex(): Int = when (this) {
      is Either.Left -> this.value.id
      is Either.Right -> this.value
    }
  }
}

class Frame {
  val code = mutableListOf<BytecodeChunk>()
  val pool: Pool
  val registerAllocator: RegisterAllocator
  val arguments: List<LocalVariable>
  val localVariables = mutableListOf<LocalVariable>()
  private val sections = mutableListOf<Int>()

  fun enterScope() {
    sections.add(localVariables.size)
    registerAllocator.enterScope()
  }

  fun register(name: String, type: Namespace.Class): LocalVariable {
    val variable = LocalVariable(name, type)
    variable.register = registerAllocator.register(type)
    localVariables.add(variable)
    return variable
  }

  fun variable(name: String): LocalVariable? = localVariables.firstOrNull { it.name == name } ?: arguments.firstOrNull { it.name == name }

  fun leaveScope() {
    registerAllocator.leaveScope()
    repeat(localVariables.size - (sections.removeLastOrNull() ?: return)) { localVariables.removeLast() }
  }

  constructor(pool: Pool, arguments: List<LocalVariable>, self: Namespace.Class? = null) {
    this.pool = pool
    this.arguments = if (self != null) listOf(LocalVariable("this", self)) + arguments else arguments
    registerAllocator = RegisterAllocator()
    this.arguments.forEach { it.register = registerAllocator.register(it) }
  }

  constructor(pool: Pool, self: Namespace.Class? = null) : this(pool, emptyList(), self)

  fun push(bytecode: BytecodeChunk) {
    code.add(bytecode)
  }
}
