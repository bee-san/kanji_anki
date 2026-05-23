package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class StrokeGuide(
    @JvmField val kanji: String,
    strokes: List<InkStroke>,
) {
    @JvmField val strokes: List<InkStroke> = Collections.unmodifiableList(ArrayList(strokes))

    fun isEmpty(): Boolean = strokes.isEmpty()

    fun strokeCount(): Int = strokes.size
}
