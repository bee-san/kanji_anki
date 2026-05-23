package dev.bee.kanjianki.core.study

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class StrokeGuideGuard private constructor() {
    class Decision private constructor(
        @JvmField val allowed: Boolean,
        @JvmField val strokeNumber: Int,
        message: String?,
    ) {
        @JvmField val message: String = message ?: ""

        companion object {
            @JvmStatic
            fun allow(): Decision = Decision(true, 0, "")

            @JvmStatic
            fun rejected(strokeNumber: Int, message: String?): Decision {
                return Decision(false, max(0, strokeNumber), message)
            }
        }
    }

    companion object {
        const val DEFAULT_CORRIDOR_FRACTION: Float = 0.18f

        @JvmStatic
        @Suppress("SENSELESS_COMPARISON")
        fun evaluatePoint(
            guide: StrokeGuide?,
            committedStrokeCount: Int,
            width: Float,
            height: Float,
            x: Float,
            y: Float,
        ): Decision {
            return evaluatePoint(guide, committedStrokeCount, width, height, x, y, DEFAULT_CORRIDOR_FRACTION)
        }

        @JvmStatic
        @Suppress("SENSELESS_COMPARISON")
        fun evaluatePoint(
            guide: StrokeGuide?,
            committedStrokeCount: Int,
            width: Float,
            height: Float,
            x: Float,
            y: Float,
            corridorFraction: Float,
        ): Decision {
            if (guide == null || guide.isEmpty() || width <= 0f || height <= 0f) {
                return Decision.allow()
            }
            if (!finite(x) || !finite(y)) {
                return Decision.rejected(nextStrokeNumber(guide, committedStrokeCount), "Stay close to the guide.")
            }
            val strokeIndex = max(0, committedStrokeCount)
            if (strokeIndex >= guide.strokeCount()) {
                return Decision.rejected(guide.strokeCount(), "All guided strokes are already drawn.")
            }
            val expected = guide.strokes.getOrNull(strokeIndex) ?: return Decision.allow()
            if (expected.isEmpty()) {
                return Decision.allow()
            }
            val corridor = max(1f, min(width, height) * max(0f, corridorFraction))
            val distance = distanceToStroke(expected, width, height, x, y)
            if (distance <= corridor) {
                return Decision.allow()
            }
            return Decision.rejected(strokeIndex + 1, "Stay close to stroke ${strokeIndex + 1}.")
        }

        private fun nextStrokeNumber(guide: StrokeGuide?, committedStrokeCount: Int): Int {
            if (guide == null || guide.isEmpty()) {
                return 0
            }
            return min(guide.strokeCount(), max(0, committedStrokeCount) + 1)
        }

        @Suppress("SENSELESS_COMPARISON")
        private fun distanceToStroke(stroke: InkStroke, width: Float, height: Float, x: Float, y: Float): Float {
            var previous: InkPoint? = null
            var best = Float.MAX_VALUE
            for (point in stroke.points.filterNotNull()) {
                if (!finite(point.x) || !finite(point.y)) {
                    continue
                }
                val scaled = InkPoint(point.x * width, point.y * height, point.timestampMillis)
                best = min(best, distance(x, y, scaled.x, scaled.y))
                val last = previous
                if (last != null) {
                    best = min(best, distanceToSegment(x, y, last.x, last.y, scaled.x, scaled.y))
                }
                previous = scaled
            }
            return if (best == Float.MAX_VALUE) 0f else best
        }

        private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.0001f) {
                return distance(px, py, ax, ay)
            }
            var t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
            t = max(0f, min(1f, t))
            return distance(px, py, ax + t * dx, ay + t * dy)
        }

        private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = ax - bx
            val dy = ay - by
            return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        private fun finite(value: Float): Boolean = !value.isNaN() && !value.isInfinite()
    }
}
