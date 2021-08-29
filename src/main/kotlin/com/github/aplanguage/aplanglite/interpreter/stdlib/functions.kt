package com.github.aplanguage.aplanglite.interpreter.stdlib

import com.github.aplanguage.aplanglite.interpreter.ReturnValue

object StdLibFunctions {
  @JvmStatic
  fun println(arguments: Array<ReturnValue>) {
    println(arguments.joinToString(" ") { returnValue -> returnValue.asString() })
  }

  @JvmStatic
  fun range(leftBound: ReturnValue.Number.IntegerNumber, rightBound: ReturnValue.Number.IntegerNumber): ReturnValue.IterableValue {
    return ReturnValue.IterableValue((leftBound.number..rightBound.number).map { ReturnValue.Number.IntegerNumber(it) })
  }

  @JvmStatic
  fun rangeNatural(rightBound: ReturnValue.Number.IntegerNumber): ReturnValue.IterableValue {
    return ReturnValue.IterableValue((0..rightBound.number).map { ReturnValue.Number.IntegerNumber(it) })
  }
}
