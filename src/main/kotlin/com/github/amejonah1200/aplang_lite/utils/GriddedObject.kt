package com.github.amejonah1200.aplang_lite.utils

open class GriddedObject<T>(open val x: Int, open val y: Int, open val obj: T)

data class OneLineObject<T>(override val x: Int, override val y: Int, val length: Int, override val obj: T) : GriddedObject<T>(x, y, obj)

data class MultiLineObject<T>(val startX: Int, val startY: Int, val endX: Int, val endY: Int, override val obj: T) : GriddedObject<T>(startX, startY, obj)

data class Point(val x: Int, val y: Int)

data class OneLineArea(val x: Int, val y: Int, val length: Int)

data class MultiLineArea(val startX: Int, val startY: Int, val endX: Int, val endY: Int)
