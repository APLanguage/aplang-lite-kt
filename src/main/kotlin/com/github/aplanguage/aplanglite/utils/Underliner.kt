package com.github.aplanguage.aplanglite.utils

class Underliner(val lines: List<String>) {

  fun underline(point: Point) = underline(point, point)

  fun underline(area: Area) {
    if (area != EOF_AREA)
      underline(area.startCoords(), area.endCoords())
    else underline(Point((lines.lastOrNull() ?: "").length, lines.size - 1))
  }

  fun underline(griddedObject: GriddedObject<*>) = underline(griddedObject.startCoords(), griddedObject.endCoords())

  fun underline(start: Point, end: Point) {
    for (y in start.y..end.y) {
      val line_opt = lines.getOrNull(y) ?: break
      val line = line_opt
      if (line.isEmpty()) {
        continue; }
      println(line)
      if (y == start.y) {
        for (blah in 0 until start.x) {
          print(" ")
        }
        print("↑")
        if (end.y == start.y) {
          when (end.x - start.x) {
            0 -> {
            }
            1 -> {
            }
            else -> {
              for (blah in (start.x + 1) until end.x - 1) {
                print("_")
              }
              print("↑")
            }
          }
        } else {
          for (blah in (start.x + 1) until line.length - 1) {
            print("_")
          }
        }
        println()
      } else if (y == end.y) {
        val range = Regex("^( *)").find(line)!!.groups[1]!!.range
        for (blah in range) {
          print(" "); }
        for (blah in range.last until end.x) {
          print("_")
        }
        println("↑")
      } else {
        if (Regex("[^\\s]").find(line) == null) {
          continue; }
        val range = Regex("^( *)").find(line)!!.groups[1]!!.range
        for (blah in range) {
          print(" "); }
        for (blah in range.last until line.length - 1) {
          print("_")
        }
        println()
      }
    }
  }
}
