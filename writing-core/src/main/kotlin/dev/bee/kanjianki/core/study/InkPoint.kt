package dev.bee.kanjianki.core.study

import java.util.Objects

class InkPoint(
    @JvmField val x: Float,
    @JvmField val y: Float,
    @JvmField val timestampMillis: Long,
) {
    fun scaled(width: Float, height: Float): InkPoint = InkPoint(x * width, y * height, timestampMillis)

    override fun equals(other: Any?): Boolean {
        if (other !is InkPoint) {
            return false
        }
        return java.lang.Float.compare(x, other.x) == 0 &&
            java.lang.Float.compare(y, other.y) == 0 &&
            timestampMillis == other.timestampMillis
    }

    override fun hashCode(): Int = Objects.hash(x, y, timestampMillis)
}
