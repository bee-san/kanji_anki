package dev.bee.kanjianki.study

import java.util.ArrayList
import java.util.Collections
import java.util.Objects

class CapturedStroke(points: List<Point>?) {
    @JvmField
    val points: List<Point>

    init {
        Objects.requireNonNull(points, "points")
        require(points!!.isNotEmpty()) { "A captured stroke must contain at least one point." }
        val copy = ArrayList<Point>()
        for (point in points) {
            copy.add(Objects.requireNonNull(point, "point"))
        }
        this.points = Collections.unmodifiableList(copy)
    }

    class Point @JvmOverloads constructor(
        @JvmField val x: Float,
        @JvmField val y: Float,
        @JvmField val timestampMillis: Long? = null,
    ) {
        init {
            requireFinite(x, "x")
            requireFinite(y, "y")
            if (timestampMillis != null && timestampMillis < 0) {
                throw IllegalArgumentException("timestampMillis must be non-negative.")
            }
        }

        companion object {
            private fun requireFinite(value: Float, name: String) {
                if (value.isNaN() || value.isInfinite()) {
                    throw IllegalArgumentException("$name must be finite.")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun of(points: List<Point>?): CapturedStroke = CapturedStroke(points)
    }
}
