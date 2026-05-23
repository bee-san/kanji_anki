package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class WritingSample(
    strokes: List<InkStroke>,
    @JvmField val width: Float,
    @JvmField val height: Float,
) {
    @JvmField val strokes: List<InkStroke> = Collections.unmodifiableList(ArrayList(strokes))

    fun hasInk(): Boolean {
        for (stroke in strokes) {
            if (!stroke.isEmpty()) {
                return true
            }
        }
        return false
    }

    fun strokeCount(): Int {
        var count = 0
        for (stroke in strokes) {
            if (!stroke.isEmpty()) {
                count++
            }
        }
        return count
    }

    companion object {
        @JvmStatic
        fun empty(): WritingSample = WritingSample(emptyList(), 0f, 0f)
    }
}
