package dev.bee.kanjianki.core.study

import java.util.Collections

class WritingAnalysisEngine private constructor() {
    private data class RecognitionMatch(val recognized: Boolean, val topCandidate: Boolean)

    companion object {
        private const val TARGET_NOT_RECOGNIZED = "I could not read that as the target kanji yet."
        private const val RECOGNITION_ERROR_MESSAGE = "The handwriting checker could not read this attempt. Try once more."

        @JvmStatic
        fun noInk(): WritingAnalysis = noInk(HintLevel.BLIND, 0)

        @JvmStatic
        fun noInk(hintLevel: HintLevel?, hintsUsed: Int): WritingAnalysis {
            return WritingAnalysis(
                WritingAnalysis.Status.NO_INK,
                StudyRating.AGAIN.code(),
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
                StudyRating.AGAIN.code(),
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
                StudyRating.AGAIN.code(),
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
                return analyzeWithoutGuide(target, candidates, order, hintLevel, hintsUsed)
            }
            val match = match(target, candidates)
            if (!match.recognized) {
                order = order.withDiagnosis(confusionDiagnosis(target, candidates, order.diagnosis))
                return WritingAnalysis(
                    WritingAnalysis.Status.WRONG,
                    StudyRating.AGAIN.code(),
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
                    StudyRating.AGAIN.code(),
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
                    StudyRating.HARD.code(),
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
                    StudyRating.EASY.code(),
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
                StudyRating.GOOD.code(),
                true,
                "Matched the kanji. Keep tightening the stroke path.",
                candidates,
                order,
                hintLevel,
                hintsUsed
            )
        }

        private fun analyzeWithoutGuide(
            target: String?,
            candidates: List<RecognitionCandidate>?,
            order: StrokeOrderEvaluator.StrokeOrderResult,
            hintLevel: HintLevel?,
            hintsUsed: Int,
        ): WritingAnalysis {
            val match = match(target, candidates)
            if (match.recognized) {
                val message = if (match.topCandidate) {
                    "Recognized as the target kanji. Stroke order could not be checked because no guide is bundled yet."
                } else {
                    "Recognized as the target kanji, but stroke order could not be checked because no guide is bundled yet."
                }
                return WritingAnalysis(
                    WritingAnalysis.Status.CLOSE,
                    if (match.topCandidate) StudyRating.GOOD.code() else StudyRating.HARD.code(),
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
                StudyRating.AGAIN.code(),
                false,
                "${order.message} $TARGET_NOT_RECOGNIZED",
                candidates,
                order,
                hintLevel,
                hintsUsed
            )
        }

        private fun match(target: String?, candidates: List<RecognitionCandidate>?): RecognitionMatch {
            if (target == null || candidates.isNullOrEmpty()) {
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

        private fun confusionDiagnosis(
            target: String?,
            candidates: List<RecognitionCandidate>?,
            diagnosis: StrokeDiagnosis,
        ): StrokeDiagnosis {
            val top = candidates?.firstOrNull()?.text?.let(::normalizedCandidate) ?: ""
            if (target.isNullOrEmpty() || top.isEmpty() || top == target || top.length != 1) {
                return diagnosis
            }
            return diagnosis.plus(StrokeDiagnosis.Label.CONFUSED_WITH_SIMILAR_KANJI, 0)
        }

        private fun normalizedCandidate(text: String): String {
            return text.trim()
                .replace("\uFE0E", "")
                .replace("\uFE0F", "")
        }
    }
}
