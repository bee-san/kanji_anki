package dev.bee.kanjianki.core.study

import java.util.Collections

class WritingAnalysisEngine private constructor() {
    private data class RecognitionMatch(val recognized: Boolean, val topCandidate: Boolean)

    companion object {
        private const val RATING_AGAIN = "again"
        private const val RATING_HARD = "hard"
        private const val RATING_GOOD = "good"
        private const val RATING_EASY = "easy"
        private const val TARGET_NOT_RECOGNIZED = "I could not read that as the target kanji yet."
        private const val RECOGNITION_ERROR_MESSAGE = "The handwriting checker could not read this attempt. Try once more."

        @JvmStatic
        fun noInk(): WritingAnalysis = noInk(HintLevel.BLIND, 0)

        @JvmStatic
        fun noInk(hintLevel: HintLevel?, hintsUsed: Int): WritingAnalysis {
            return WritingAnalysis(
                WritingAnalysis.Status.NO_INK,
                RATING_AGAIN,
                false,
                "Write in the square before checking.",
                Collections.emptyList(),
                null,
                hintLevel,
                hintsUsed
            )
        }

        @JvmStatic
        fun modelUnavailable(message: String?): WritingAnalysis = modelUnavailable(message, HintLevel.BLIND, 0)

        @JvmStatic
        fun modelUnavailable(message: String?, hintLevel: HintLevel?, hintsUsed: Int): WritingAnalysis {
            return WritingAnalysis(
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                RATING_AGAIN,
                false,
                message,
                Collections.emptyList(),
                null,
                hintLevel,
                hintsUsed
            )
        }

        @JvmStatic
        fun recognitionError(): WritingAnalysis = recognitionError(HintLevel.BLIND, 0)

        @JvmStatic
        fun recognitionError(hintLevel: HintLevel?, hintsUsed: Int): WritingAnalysis {
            return WritingAnalysis(
                WritingAnalysis.Status.RECOGNITION_ERROR,
                RATING_AGAIN,
                false,
                RECOGNITION_ERROR_MESSAGE,
                Collections.emptyList(),
                null,
                hintLevel,
                hintsUsed
            )
        }

        @JvmStatic
        fun analyze(
            target: String?,
            sample: WritingSample?,
            guide: StrokeGuide?,
            candidates: List<RecognitionCandidate>?,
        ): WritingAnalysis {
            return analyze(target, sample, guide, candidates, HintLevel.BLIND, 0)
        }

        @JvmStatic
        fun analyze(
            target: String?,
            sample: WritingSample?,
            guide: StrokeGuide?,
            candidates: List<RecognitionCandidate>?,
            hintLevel: HintLevel?,
            hintsUsed: Int,
        ): WritingAnalysis {
            if (sample == null || !sample.hasInk()) {
                return noInk(hintLevel, hintsUsed)
            }
            var order = StrokeOrderEvaluator.evaluate(guide, sample)
            if (order.missingGuide) {
                val match = match(target, candidates)
                if (match.recognized) {
                    val message = if (match.topCandidate) {
                        "Recognized as the target kanji. Stroke order could not be checked because no guide is bundled yet."
                    } else {
                        "Recognized as the target kanji, but stroke order could not be checked because no guide is bundled yet."
                    }
                    return WritingAnalysis(
                        WritingAnalysis.Status.CLOSE,
                        if (match.topCandidate) RATING_GOOD else RATING_HARD,
                        true,
                        message,
                        candidates,
                        order,
                        hintLevel,
                        hintsUsed
                    )
                }
                return WritingAnalysis(
                    WritingAnalysis.Status.NO_STROKE_DATA,
                    RATING_AGAIN,
                    false,
                    "${order.message} $TARGET_NOT_RECOGNIZED",
                    candidates,
                    order,
                    hintLevel,
                    hintsUsed
                )
            }
            val match = match(target, candidates)
            if (!match.recognized) {
                return WritingAnalysis(
                    WritingAnalysis.Status.WRONG,
                    RATING_AGAIN,
                    false,
                    TARGET_NOT_RECOGNIZED,
                    candidates,
                    order,
                    hintLevel,
                    hintsUsed
                )
            }
            if (!order.acceptable) {
                return WritingAnalysis(
                    WritingAnalysis.Status.WRONG,
                    RATING_AGAIN,
                    false,
                    order.message,
                    candidates,
                    order,
                    hintLevel,
                    hintsUsed
                )
            }
            if (!order.clean) {
                order = order.withDiagnosis(order.diagnosis.plus(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 0))
                return WritingAnalysis(
                    WritingAnalysis.Status.CLOSE,
                    RATING_HARD,
                    true,
                    "Readable, but the stroke path needs one more careful pass.",
                    candidates,
                    order,
                    hintLevel,
                    hintsUsed
                )
            }
            if (match.topCandidate) {
                return WritingAnalysis(
                    WritingAnalysis.Status.PASS,
                    RATING_EASY,
                    true,
                    "Clean match.",
                    candidates,
                    order,
                    hintLevel,
                    hintsUsed
                )
            }
            return WritingAnalysis(
                WritingAnalysis.Status.PASS,
                RATING_GOOD,
                true,
                "Matched the kanji. Keep tightening the stroke path.",
                candidates,
                order,
                hintLevel,
                hintsUsed
            )
        }

        private fun match(target: String?, candidates: List<RecognitionCandidate>?): RecognitionMatch {
            if (target == null || candidates == null || candidates.isEmpty()) {
                return RecognitionMatch(false, false)
            }
            for (i in candidates.indices) {
                val text = normalizedCandidate(candidates[i].text)
                if (target == text) {
                    return RecognitionMatch(true, i == 0)
                }
            }
            return RecognitionMatch(false, false)
        }

        private fun normalizedCandidate(text: String): String {
            return text.trim()
                .replace("\uFE0E", "")
                .replace("\uFE0F", "")
        }
    }
}
