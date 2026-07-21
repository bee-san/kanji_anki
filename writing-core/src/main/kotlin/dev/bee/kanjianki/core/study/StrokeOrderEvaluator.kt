package dev.bee.kanjianki.core.study

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class StrokeOrderEvaluator private constructor() {
    class StrokeOrderResult private constructor(
        @JvmField val acceptable: Boolean,
        @JvmField val clean: Boolean,
        @JvmField val score: Float,
        @JvmField val message: String,
        @JvmField val missingGuide: Boolean,
        diagnosis: StrokeDiagnosis?,
    ) {
        @JvmField val diagnosis: StrokeDiagnosis = diagnosis ?: StrokeDiagnosis.empty()

        fun withDiagnosis(diagnosis: StrokeDiagnosis?): StrokeOrderResult {
            return StrokeOrderResult(acceptable, clean, score, message, missingGuide, diagnosis)
        }

        companion object {
            internal fun create(
                acceptable: Boolean,
                clean: Boolean,
                score: Float,
                message: String,
                missingGuide: Boolean,
                diagnosis: StrokeDiagnosis?,
            ): StrokeOrderResult {
                return StrokeOrderResult(acceptable, clean, score, message, missingGuide, diagnosis)
            }

            internal fun missing(): StrokeOrderResult {
                return StrokeOrderResult(
                    false,
                    false,
                    0f,
                    "No stroke-order guide is available for this kanji.",
                    true,
                    StrokeDiagnosis.empty()
                )
            }
        }
    }

    private data class StrokeComparison(
        val directScore: Float,
        val reversedScore: Float,
        val score: Float,
    ) {
        fun directionlessScore(): Float = max(directScore, reversedScore)
    }

    private data class StrokeComparisonSummary(
        val shapeScore: Float,
        val weakestStrokeScore: Float,
        val matchedGuideStrokes: BooleanArray,
        val diagnosis: StrokeDiagnosis.Builder,
    )

    private data class BestStrokeMatch(val index: Int, val directionlessScore: Float)

    private data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        fun width(): Float = max(0.001f, maxX - minX)

        fun height(): Float = max(0.001f, maxY - minY)
    }

    private class MutableBounds {
        private var minX = Float.MAX_VALUE
        private var minY = Float.MAX_VALUE
        private var maxX = -Float.MAX_VALUE
        private var maxY = -Float.MAX_VALUE

        fun include(point: InkPoint?) {
            if (point == null) {
                return
            }
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }

        fun toBounds(): Bounds {
            if (minX == Float.MAX_VALUE) {
                return Bounds(0f, 0f, 1f, 1f)
            }
            return Bounds(minX, minY, maxX, maxY)
        }
    }

    companion object {
        @JvmStatic
        fun evaluate(guide: StrokeGuide?, sample: WritingSample?): StrokeOrderResult {
            if (guide == null || guide.isEmpty()) {
                return StrokeOrderResult.missing()
            }
            if (sample == null || !sample.hasInk()) {
                return StrokeOrderResult.create(false, false, 0f, "No ink was drawn.", false, StrokeDiagnosis.empty())
            }
            val expected = guide.strokeCount()
            val actual = sample.strokeCount()
            val countDelta = abs(expected - actual)
            val countScore = max(0f, 1f - (countDelta / max(1, expected).toFloat()))
            val compared = min(expected, actual)
            val guideBounds = boundsForGuide(guide)
            val sampleBounds = boundsForSample(sample)
            val summary = compareStrokes(guide, sample, sampleBounds, guideBounds, compared)
            val shapeScore = summary.shapeScore / compared
            val weakestStrokeScore = summary.weakestStrokeScore
            if (actual < expected) {
                addMissingStrokes(summary.matchedGuideStrokes, summary.diagnosis)
            } else if (actual > expected) {
                addExtraStrokes(expected, actual, summary.diagnosis)
            }
            val score = clamp((countScore * 0.45f) + (shapeScore * 0.55f))
            val acceptable = countDelta <= max(1, expected / 4) && score >= 0.45f
            val clean = countDelta == 0 && score >= 0.68f && weakestStrokeScore >= 0.55f
            val message = resultMessage(acceptable, clean)
            return StrokeOrderResult.create(acceptable, clean, score, message, false, summary.diagnosis.build())
        }

        private fun compareStrokes(
            guide: StrokeGuide,
            sample: WritingSample,
            sampleBounds: Bounds,
            guideBounds: Bounds,
            compared: Int,
        ): StrokeComparisonSummary {
            var shapeScore = 0f
            var weakestStrokeScore = 1f
            val diagnosis = StrokeDiagnosis.builder()
            val matchedGuideStrokes = BooleanArray(guide.strokeCount())
            for (i in 0 until compared) {
                val expectedComparison = compare(guide.strokes[i], sample.strokes[i], sampleBounds, guideBounds)
                shapeScore += expectedComparison.score
                weakestStrokeScore = min(weakestStrokeScore, expectedComparison.score)
                val best = bestGuideMatch(guide, sample.strokes[i], sampleBounds, guideBounds)
                if (best.index >= 0 && best.directionlessScore >= 0.65f) {
                    matchedGuideStrokes[best.index] = true
                }
                diagnoseComparedStroke(i, expectedComparison, best, diagnosis)
            }
            return StrokeComparisonSummary(shapeScore, weakestStrokeScore, matchedGuideStrokes, diagnosis)
        }

        private fun addMissingStrokes(matchedGuideStrokes: BooleanArray, diagnosis: StrokeDiagnosis.Builder) {
            for (i in matchedGuideStrokes.indices) {
                if (!matchedGuideStrokes[i]) {
                    diagnosis.add(StrokeDiagnosis.Label.MISSING_STROKE, i + 1)
                }
            }
        }

        private fun addExtraStrokes(expected: Int, actual: Int, diagnosis: StrokeDiagnosis.Builder) {
            for (i in expected until actual) {
                diagnosis.add(StrokeDiagnosis.Label.EXTRA_STROKE, i + 1)
            }
        }

        private fun resultMessage(acceptable: Boolean, clean: Boolean): String {
            if (clean) {
                return "Stroke path looks clean."
            }
            if (acceptable) {
                return "Readable path, but some strokes look shaky."
            }
            return "The stroke count or order does not match the guide yet."
        }

        private fun diagnoseComparedStroke(
            expectedIndex: Int,
            expectedComparison: StrokeComparison,
            best: BestStrokeMatch,
            diagnosis: StrokeDiagnosis.Builder,
        ) {
            val wrongOrder = best.index >= 0 &&
                best.index != expectedIndex &&
                best.directionlessScore >= 0.72f &&
                best.directionlessScore - expectedComparison.directionlessScore() >= 0.18f
            if (wrongOrder) {
                diagnosis.add(StrokeDiagnosis.Label.WRONG_ORDER, expectedIndex + 1)
                return
            }
            val wrongDirection = expectedComparison.reversedScore >= 0.70f &&
                expectedComparison.reversedScore - expectedComparison.directScore >= 0.25f
            if (wrongDirection) {
                diagnosis.add(StrokeDiagnosis.Label.WRONG_DIRECTION, expectedIndex + 1)
                return
            }
            if (expectedComparison.directionlessScore() < 0.65f) {
                diagnosis.add(StrokeDiagnosis.Label.FAR_FROM_GUIDE, expectedIndex + 1)
            }
            if (expectedComparison.score < 0.50f && expectedComparison.directionlessScore() < 0.65f) {
                diagnosis.add(StrokeDiagnosis.Label.ROUGH_SHAPE, expectedIndex + 1)
            }
        }

        private fun bestGuideMatch(
            guide: StrokeGuide,
            sampleStroke: InkStroke,
            sampleBounds: Bounds,
            guideBounds: Bounds,
        ): BestStrokeMatch {
            var bestIndex = -1
            var bestScore = 0f
            for (i in guide.strokes.indices) {
                val comparison = compare(guide.strokes[i], sampleStroke, sampleBounds, guideBounds)
                val score = comparison.directionlessScore()
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = i
                }
            }
            return BestStrokeMatch(bestIndex, bestScore)
        }

        private fun compare(
            guideStroke: InkStroke,
            sampleStroke: InkStroke,
            sampleBounds: Bounds,
            guideBounds: Bounds,
        ): StrokeComparison {
            val guideStart = guideStroke.start()
            val guideEnd = guideStroke.end()
            val sampleStart = normalized(sampleStroke.start(), sampleBounds, guideBounds)
            val sampleEnd = normalized(sampleStroke.end(), sampleBounds, guideBounds)
            if (guideStart == null || guideEnd == null || sampleStart == null || sampleEnd == null) {
                return StrokeComparison(0f, 0f, 0f)
            }
            val startDistance = distance(guideStart, sampleStart)
            val endDistance = distance(guideEnd, sampleEnd)
            val direct = max(0f, 1f - ((startDistance + endDistance) / 1.2f))
            val reverseStartDistance = distance(guideStart, sampleEnd)
            val reverseEndDistance = distance(guideEnd, sampleStart)
            val reversed = max(0f, 1f - ((reverseStartDistance + reverseEndDistance) / 1.2f))
            val score = if (reversed > direct) direct * 0.55f else direct
            return StrokeComparison(direct, reversed, score)
        }

        private fun normalized(point: InkPoint?, source: Bounds, target: Bounds): InkPoint? {
            if (point == null) {
                return null
            }
            val x = target.minX + ((point.x - source.minX) / source.width()) * target.width()
            val y = target.minY + ((point.y - source.minY) / source.height()) * target.height()
            return InkPoint(clamp(x), clamp(y), point.timestampMillis)
        }

        private fun distance(a: InkPoint, b: InkPoint): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        private fun clamp(value: Float): Float {
            return if (value.isFinite()) max(0f, min(1f, value)) else 0f
        }

        private fun boundsForGuide(guide: StrokeGuide): Bounds {
            val bounds = MutableBounds()
            for (stroke in guide.strokes) {
                for (point in stroke.points) {
                    bounds.include(point)
                }
            }
            return bounds.toBounds()
        }

        private fun boundsForSample(sample: WritingSample): Bounds {
            val bounds = MutableBounds()
            for (stroke in sample.strokes) {
                for (point in stroke.points) {
                    bounds.include(point)
                }
            }
            return bounds.toBounds()
        }
    }
}
