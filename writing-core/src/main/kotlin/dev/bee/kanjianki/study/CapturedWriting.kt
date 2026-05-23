package dev.bee.kanjianki.study

import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.InkStroke
import dev.bee.kanjianki.core.study.WritingSample
import java.util.ArrayList
import java.util.Collections
import java.util.Objects
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class CapturedWriting {
    @JvmField
    val strokes: List<CapturedStroke>

    @JvmField
    val writingAreaWidth: Float?

    @JvmField
    val writingAreaHeight: Float?

    @JvmField
    val preContext: String

    constructor(strokes: List<CapturedStroke>?) : this(strokes, null, null, "")

    constructor(
        strokes: List<CapturedStroke>?,
        writingAreaWidth: Float?,
        writingAreaHeight: Float?,
        preContext: String?,
    ) {
        Objects.requireNonNull(strokes, "strokes")
        require(strokes!!.isNotEmpty()) { "Captured writing must contain at least one stroke." }
        val copy = ArrayList<CapturedStroke>()
        for (stroke in strokes) {
            copy.add(Objects.requireNonNull(stroke, "stroke"))
        }
        if ((writingAreaWidth == null) != (writingAreaHeight == null)) {
            throw IllegalArgumentException("writingAreaWidth and writingAreaHeight must both be set or both be null.")
        }
        if (writingAreaWidth != null) {
            requirePositiveFinite(writingAreaWidth, "writingAreaWidth")
            requirePositiveFinite(writingAreaHeight!!, "writingAreaHeight")
        }
        this.strokes = Collections.unmodifiableList(copy)
        this.writingAreaWidth = writingAreaWidth
        this.writingAreaHeight = writingAreaHeight
        this.preContext = preContext ?: ""
    }

    fun hasWritingArea(): Boolean = writingAreaWidth != null

    fun hasRecognitionContext(): Boolean = hasWritingArea() || preContext.isNotEmpty()

    fun toWritingSample(): WritingSample {
        val width = writingAreaWidth ?: 0f
        val height = writingAreaHeight ?: 0f
        return toWritingSample(strokes, width, height)
    }

    companion object {
        @JvmStatic
        fun of(strokes: List<CapturedStroke>?): CapturedWriting = CapturedWriting(strokes)

        @JvmStatic
        fun prepareForRecognition(strokes: List<CapturedStroke>?, width: Float, height: Float): CapturedWriting {
            Objects.requireNonNull(strokes, "strokes")
            require(strokes!!.isNotEmpty()) { "Captured writing must contain at least one stroke." }
            val simplified = ArrayList<CapturedStroke>()
            for (stroke in strokes) {
                simplified.add(simplify(stroke))
            }
            requirePositiveFinite(width, "width")
            requirePositiveFinite(height, "height")

            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (stroke in simplified) {
                for (point in stroke.points) {
                    minX = min(minX, point.x)
                    maxX = max(maxX, point.x)
                    minY = min(minY, point.y)
                    maxY = max(maxY, point.y)
                }
            }
            val sourceWidth = max(maxX - minX, 1f)
            val sourceHeight = max(maxY - minY, 1f)
            val targetWidth = 1000f
            val targetHeight = 1000f
            val margin = 140f
            val scale = min((targetWidth - margin * 2f) / sourceWidth, (targetHeight - margin * 2f) / sourceHeight)
            val scaledWidth = sourceWidth * scale
            val scaledHeight = sourceHeight * scale
            val offsetX = (targetWidth - scaledWidth) / 2f
            val offsetY = (targetHeight - scaledHeight) / 2f

            val normalized = ArrayList<CapturedStroke>()
            for (stroke in simplified) {
                val points = ArrayList<CapturedStroke.Point>()
                for (point in stroke.points) {
                    points.add(
                        CapturedStroke.Point(
                            ((point.x - minX) * scale) + offsetX,
                            ((point.y - minY) * scale) + offsetY,
                            point.timestampMillis
                        )
                    )
                }
                normalized.add(CapturedStroke(points))
            }
            return CapturedWriting(normalized, targetWidth, targetHeight, "")
        }

        @JvmStatic
        fun toWritingSample(strokes: List<CapturedStroke>?, width: Float, height: Float): WritingSample {
            Objects.requireNonNull(strokes, "strokes")
            val inkStrokes = ArrayList<InkStroke>()
            for (stroke in strokes!!) {
                Objects.requireNonNull(stroke, "stroke")
                val inkPoints = ArrayList<InkPoint>()
                for (point in stroke.points) {
                    val timestamp = point.timestampMillis ?: 0L
                    inkPoints.add(InkPoint(point.x, point.y, timestamp))
                }
                inkStrokes.add(InkStroke(inkPoints))
            }
            return WritingSample(inkStrokes, width, height)
        }

        private fun requirePositiveFinite(value: Float, name: String) {
            if (value.isNaN() || value.isInfinite() || value <= 0.0f) {
                throw IllegalArgumentException("$name must be positive and finite.")
            }
        }

        private fun simplify(stroke: CapturedStroke): CapturedStroke {
            if (stroke.points.size <= 2) {
                return stroke
            }
            val simplified = ArrayList<CapturedStroke.Point>()
            simplified.add(stroke.points[0])
            for (i in 1 until stroke.points.size - 1) {
                val point = stroke.points[i]
                val last = simplified[simplified.size - 1]
                if (distance(last, point) >= 2.5f) {
                    simplified.add(point)
                }
            }
            val lastPoint = stroke.points[stroke.points.size - 1]
            val currentLast = simplified[simplified.size - 1]
            if (currentLast.x != lastPoint.x || currentLast.y != lastPoint.y) {
                simplified.add(lastPoint)
            }
            return CapturedStroke(simplified)
        }

        private fun distance(a: CapturedStroke.Point, b: CapturedStroke.Point): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
    }
}
