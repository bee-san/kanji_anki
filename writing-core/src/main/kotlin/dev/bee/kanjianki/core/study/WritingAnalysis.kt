package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class WritingAnalysis(
    @JvmField val status: Status,
    @JvmField val rating: String?,
    @JvmField val writingPassed: Boolean,
    message: String?,
    candidates: List<RecognitionCandidate>?,
    @JvmField val strokeOrder: StrokeOrderEvaluator.StrokeOrderResult?,
    hintOptions: Array<out Any?>?,
) {
    constructor(
        status: Status,
        rating: String?,
        writingPassed: Boolean,
        message: String?,
        candidates: List<RecognitionCandidate>?,
        strokeOrder: StrokeOrderEvaluator.StrokeOrderResult?,
    ) : this(status, rating, writingPassed, message, candidates, strokeOrder, emptyArray())

    constructor(
        status: Status,
        rating: String?,
        writingPassed: Boolean,
        message: String?,
        candidates: List<RecognitionCandidate>?,
        strokeOrder: StrokeOrderEvaluator.StrokeOrderResult?,
        hintLevel: Any?,
        hintsUsed: Any?,
    ) : this(status, rating, writingPassed, message, candidates, strokeOrder, arrayOf(hintLevel, hintsUsed))

    enum class Status {
        PASS,
        CLOSE,
        WRONG,
        NO_INK,
        MODEL_UNAVAILABLE,
        NO_STROKE_DATA,
        RECOGNITION_ERROR,
    }

    @JvmField val message: String = message ?: ""
    @JvmField val candidates: List<RecognitionCandidate> =
        Collections.unmodifiableList(ArrayList(candidates ?: emptyList()))
    private val hintLevel: HintLevel = hintLevelFrom(hintOptions)
    private val hintsUsed: Int = hintsUsedFrom(hintOptions)

    fun failed(): Boolean = status != Status.PASS && status != Status.CLOSE

    fun passed(): Boolean = writingPassed

    fun confidenceScore(): Double {
        val recognitionScore = if (candidates.isNotEmpty() && candidates[0].score != null) {
            candidates[0].score!!.toDouble()
        } else if (candidates.isNotEmpty()) {
            if (writingPassed) 0.78 else 0.0
        } else {
            0.0
        }
        val orderScore = orderConfidenceScore()
        return maxOf(0.0, minOf(1.0, (recognitionScore * 0.55) + (orderScore * 0.45)))
    }

    private fun orderConfidenceScore(): Double {
        if (strokeOrder != null) {
            return strokeOrder.score.toDouble()
        }
        return if (writingPassed) 0.7 else 0.0
    }

    fun hintLevel(): HintLevel = hintLevel

    fun hintsUsed(): Int = hintsUsed

    companion object {
        private fun hintLevelFrom(hintOptions: Array<out Any?>?): HintLevel {
            return if (hintOptions != null && hintOptions.isNotEmpty() && hintOptions[0] is HintLevel) {
                hintOptions[0] as HintLevel
            } else {
                HintLevel.BLIND
            }
        }

        private fun hintsUsedFrom(hintOptions: Array<out Any?>?): Int {
            return if (hintOptions != null && hintOptions.size > 1 && hintOptions[1] is Int) {
                maxOf(0, hintOptions[1] as Int)
            } else {
                0
            }
        }
    }
}
