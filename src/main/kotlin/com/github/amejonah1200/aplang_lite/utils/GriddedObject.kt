package com.github.amejonah1200.aplang_lite.utils

sealed class GriddedObject<out T>(open val obj: T) {

  abstract fun startCoords(): Point

  abstract fun endCoords(): Point

  abstract fun expandTo(end: Point): GriddedObject<T>

  fun expandTo(endX: Int, endY: Int) = expandTo(Point(endX, endY))

  abstract fun area(): Area

  abstract fun <T2> asGriddedObjectOfType(clazz: Class<T2>): GriddedObject<T2>?

  inline fun <reified T2> asGriddedObjectOfType() : GriddedObject<T2>?{
    return if(obj is T2) asGriddedObjectOfType((obj as T2)!!::class.java)
    else null
  }

  companion object {
    fun <T> of(start: Point, obj: T, end: Point): GriddedObject<T> {
      return if (start.y == end.y) OneLineObject(start, obj, end.x - start.x)
      else MultiLineObject(start, obj, end)
    }

    fun <T> of(start: Point, obj: T, length: Int): GriddedObject<T> {
      return OneLineObject(start, obj, length)
    }
  }

}

data class OneLineObject<T>(val start: Point, override val obj: T, val length: Int) : GriddedObject<T>(obj) {

  constructor(x: Int, y: Int, obj: T, length: Int) : this(Point(x, y), obj, length)

  override fun startCoords() = start

  override fun endCoords() = Point(start.x + length, start.y)

  override fun expandTo(end: Point): GriddedObject<T> {
    return if (start.y == end.y) OneLineObject(start, obj, end.x - start.x)
    else MultiLineObject(start, obj, end)
  }

  override fun area(): Area = OneLineArea(start, length)

  override fun <T2> asGriddedObjectOfType(clazz: Class<T2>): GriddedObject<T2>? {
    @Suppress("UNCHECKED_CAST")
    return if(clazz.isInstance(obj)) this as OneLineObject<T2>
    else null
  }
}

data class MultiLineObject<T>(val start: Point, override val obj: T, val end: Point) : GriddedObject<T>(obj) {

  constructor(startX: Int, startY: Int, obj: T, endX: Int, endY: Int) : this(Point(startX, startY), obj, Point(endX, endY))

  override fun startCoords() = start

  override fun endCoords() = end

  override fun expandTo(end: Point): GriddedObject<T> {
    return if (start.y == end.y) OneLineObject(start, obj, end.x - start.x)
    else MultiLineObject(start, obj, end)
  }

  override fun area(): Area = MultiLineArea(start, end)

  override fun <T2> asGriddedObjectOfType(clazz: Class<T2>): GriddedObject<T2>? {
    @Suppress("UNCHECKED_CAST")
    return if(clazz.isInstance(obj)) this as MultiLineObject<T2>
    else null
  }
}

data class Point(val x: Int, val y: Int)

sealed class Area {
  fun expandTo(endX: Int, endY: Int) = expandTo(Point(endX, endY))

  abstract fun expandTo(end: Point): Area

  abstract fun <T> toObject(obj: T): GriddedObject<T>

  abstract fun startCoords(): Point

  abstract fun endCoords(): Point
}

data class OneLineArea(val start: Point, val length: Int) : Area() {
  constructor(x: Int, y: Int, length: Int) : this(Point(x, y), length)

  override fun expandTo(end: Point): Area {
    return if (start.y == end.y) OneLineArea(start, end.x - start.x)
    else MultiLineArea(start, end)
  }

  override fun startCoords() = start

  override fun endCoords() = Point(start.x + length, start.y)

  override fun <T> toObject(obj: T) = OneLineObject(start, obj, length)
}

data class MultiLineArea(val start: Point, val end: Point) : Area() {
  constructor(startX: Int, startY: Int, endX: Int, endY: Int) : this(Point(startX, startY), Point(endX, endY))

  override fun expandTo(end: Point): Area {
    return if (start.y == end.y) OneLineArea(start, end.x - start.x)
    else MultiLineArea(start, end)
  }

  override fun <T> toObject(obj: T) = MultiLineObject(start, obj, end)

  override fun startCoords() = start

  override fun endCoords() = end
}
