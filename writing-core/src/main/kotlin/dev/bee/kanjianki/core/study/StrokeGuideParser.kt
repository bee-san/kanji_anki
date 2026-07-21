package dev.bee.kanjianki.core.study

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.util.LinkedHashMap
import java.util.regex.Pattern

class StrokeGuideParser private constructor() {
    companion object {
        private val TAB_SEPARATOR: Pattern = Pattern.compile("\\t")
        private val PIPE_SEPARATOR: Pattern = Pattern.compile("\\|")
        private val SEMICOLON_SEPARATOR: Pattern = Pattern.compile(";")
        private val COMMA_SEPARATOR: Pattern = Pattern.compile(",")

        @JvmStatic
        @Throws(IOException::class)
        fun parse(reader: Reader): Map<String, StrokeGuide> {
            val guides = LinkedHashMap<String, StrokeGuide>()
            val buffered = BufferedReader(reader)
            var lineNumber = 0
            while (true) {
                val line = buffered.readLine() ?: break
                lineNumber++
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue
                }
                val columns = TAB_SEPARATOR.split(trimmed, 2)
                if (columns.size != 2) {
                    throw IOException("Invalid stroke guide line $lineNumber: expected kanji<TAB>stroke data.")
                }
                guides[columns[0]] = StrokeGuide(columns[0], parseStrokes(columns[1], lineNumber))
            }
            return guides
        }

        @Throws(IOException::class)
        private fun parseStrokes(value: String, lineNumber: Int): List<InkStroke> {
            val strokes = ArrayList<InkStroke>()
            for (strokeValue in PIPE_SEPARATOR.split(value)) {
                val trimmed = strokeValue.trim()
                if (trimmed.isEmpty()) {
                    continue
                }
                val points = ArrayList<InkPoint>()
                for (pointValue in SEMICOLON_SEPARATOR.split(trimmed)) {
                    val xy = COMMA_SEPARATOR.split(pointValue.trim())
                    if (xy.size != 2) {
                        throw IOException("Invalid point on stroke guide line $lineNumber: $pointValue")
                    }
                    val x = parseCoordinate(xy[0], pointValue, lineNumber)
                    val y = parseCoordinate(xy[1], pointValue, lineNumber)
                    points.add(InkPoint(x, y, points.size.toLong()))
                }
                if (points.size >= 2) {
                    strokes.add(InkStroke(points))
                }
            }
            if (strokes.isEmpty()) {
                throw IOException("Stroke guide line $lineNumber has no usable strokes.")
            }
            return strokes
        }

        @Throws(IOException::class)
        private fun parseCoordinate(value: String, pointValue: String, lineNumber: Int): Float {
            val coordinate = value.toFloatOrNull()
            if (coordinate == null || !coordinate.isFinite()) {
                throw IOException("Invalid coordinate on stroke guide line $lineNumber: $pointValue")
            }
            return coordinate
        }
    }
}
