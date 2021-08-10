package com.github.amejonah1200.aplang_lite.utils

sealed class GriddedObject<T>(open val x: Int, open val y: Int, open val obj: T) {
  fun startCoords() = Pair(x, y)
  abstract fun endCoords(): Pair<Int, Int>
}

data class OneLineObject<T>(override val x: Int, override val y: Int, override val obj: T, val length: Int) : GriddedObject<T>(x, y, obj) {
  override fun endCoords() = Pair(x + length, y)
}

data class MultiLineObject<T>(val startX: Int, val startY: Int, override val obj: T, val endX: Int, val endY: Int) :
  GriddedObject<T>(startX, startY, obj) {
  override fun endCoords() = Pair(endX, endY)
}

data class Point(val x: Int, val y: Int)

data class OneLineArea(val x: Int, val y: Int, val length: Int)

data class MultiLineArea(val startX: Int, val startY: Int, val endX: Int, val endY: Int)
