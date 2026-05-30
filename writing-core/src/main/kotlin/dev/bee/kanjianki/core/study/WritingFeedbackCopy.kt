package dev.bee.kanjianki.core.study

class WritingFeedbackCopy private constructor() {
    companion object {
        private val HINT_PROGRESSION = HintProgression()

        @JvmStatic
        fun guideLabel(level: Int, guide: StrokeGuide?): String {
            return guideLabel(HintState.fromWritingLevel(level), guide)
        }

        @JvmStatic
        fun guideLabel(state: HintState?, guide: StrokeGuide?): String {
            val level = state?.level() ?: HintLevel.TRACE
            val hasGuide = guide != null && !guide.isEmpty()
            if (!hasGuide) {
                if (level == HintLevel.BLIND) {
                    return "Write from memory, then check. No stroke guide is bundled yet."
                }
                return "Draw it, then check. Stroke-order feedback will be limited."
            }
            return when (level) {
                HintLevel.TRACE -> "Trace the strokes, then check."
                HintLevel.OUTLINE -> "Copy the faint outline; the current stroke is emphasized."
                HintLevel.MINIMAL -> "Write with only the current stroke hinted, then check."
                HintLevel.BLIND -> "Write from memory, then check. Use Hint if you are stuck."
            }
        }

        @JvmStatic
        fun attemptProgressText(
            analysis: WritingAnalysis?,
            activeWritingLevel: Int?,
            increaseSupportAfterAnalysis: Boolean,
        ): String {
            if (analysis == null) {
                return ""
            }
            if (analysis.status == WritingAnalysis.Status.PASS && analysis.hintsUsed() == 0) {
                val next = HINT_PROGRESSION.afterWriting(
                    HintState.fromWritingLevel(analysis.hintLevel().writingLevel()),
                    analysis
                )
                if (next.level() != analysis.hintLevel()) {
                    return "\nNext writing review will have less help: ${stageLabel(next.level())}."
                }
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return "\nTry cleaner for a cleaner pass, or Save hard to keep this help level."
            }
            if (increaseSupportAfterAnalysis && activeWritingLevel != null) {
                val next = HINT_PROGRESSION.afterWriting(HintState.fromWritingLevel(activeWritingLevel), analysis)
                if (next.level() != HintLevel.fromWritingLevel(activeWritingLevel)) {
                    return "\nNext try will use more support: ${stageLabel(next.level())}."
                }
            }
            return ""
        }

        @JvmStatic
        fun stageLabel(level: HintLevel): String {
            return when (level) {
                HintLevel.TRACE -> "Trace"
                HintLevel.OUTLINE -> "Outline"
                HintLevel.MINIMAL -> "Minimal"
                HintLevel.BLIND -> "Blind"
            }
        }

        @JvmStatic
        fun targetRevealText(analysis: WritingAnalysis?, targetKanji: String?): String {
            if (targetKanji == null || analysis == null) {
                return ""
            }
            return when (analysis.status) {
                WritingAnalysis.Status.PASS,
                WritingAnalysis.Status.CLOSE,
                WritingAnalysis.Status.WRONG,
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR,
                -> "\nTarget: $targetKanji"
                else -> ""
            }
        }

        @JvmStatic
        fun candidateText(candidates: List<RecognitionCandidate>?): String {
            if (candidates.isNullOrEmpty()) {
                return ""
            }
            return candidates.take(3).joinToString(", ") { it.text }
        }

        @JvmStatic
        fun resultMessage(
            analysis: WritingAnalysis?,
            targetKanji: String?,
            activeWritingLevel: Int?,
            increaseSupportAfterAnalysis: Boolean,
            diagnosis: String?,
        ): String {
            if (analysis == null) {
                return ""
            }
            val candidates = candidateText(analysis.candidates)
            var message = analysis.message +
                attemptProgressText(analysis, activeWritingLevel, increaseSupportAfterAnalysis) +
                targetRevealText(analysis, targetKanji) +
                if (candidates.isEmpty()) "" else "\nIt saw: $candidates"
            val safeDiagnosis = diagnosis ?: ""
            if (safeDiagnosis.isNotEmpty()) {
                message += "\n$safeDiagnosis"
            }
            return message
        }

        @JvmStatic
        fun checkWritingButtonText(checkingWriting: Boolean, messyPass: Boolean): String {
            if (checkingWriting) {
                return "Checking..."
            }
            return if (messyPass) "Try cleaner" else "Check"
        }

        @JvmStatic
        fun unavailableModelStatusMessage(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Automatic handwriting checks are unavailable on this device.")
        }

        @JvmStatic
        fun modelStatusMessage(guidePrefix: String?, statusPresent: Boolean, downloaded: Boolean, hasError: Boolean): String {
            if (hasError || !statusPresent) {
                return appendStatus(guidePrefix, "Unable to read handwriting checker status.")
            }
            if (!downloaded) {
                return appendStatus(guidePrefix, "Download the handwriting checker before automatic checks.")
            }
            return appendStatus(guidePrefix, "Handwriting checker ready.")
        }

        @JvmStatic
        fun hintUsedStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Hint used. One current stroke hinted; your ink stayed on the canvas.")
        }

        @JvmStatic
        fun freshGuidedTryStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Fresh guided try. Draw it again, then check.")
        }

        @JvmStatic
        fun cleanerRetryStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Try cleaner. Keep the same help level and draw it carefully once more.")
        }

        @JvmStatic
        fun undoStrokeStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Undid the last stroke.")
        }

        @JvmStatic
        fun updatedInkStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, "Updated ink. Check again when ready.")
        }

        @JvmStatic
        fun blockedStrokeStatus(guidePrefix: String?, decision: StrokeGuideGuard.Decision?): String {
            val message = if (decision == null || decision.message.isEmpty()) {
                "Stay close to the guide."
            } else {
                decision.message
            }
            return appendStatus(guidePrefix, message)
        }

        @JvmStatic
        fun submitLabel(analysis: WritingAnalysis?): String {
            if (analysis == null || !analysis.writingPassed) {
                return "Fail"
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return "Save hard"
            }
            return "Pass"
        }

        @JvmStatic
        fun submitRating(analysis: WritingAnalysis?): String {
            if (analysis == null || !analysis.writingPassed) {
                return StudyRating.AGAIN.code()
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return StudyRating.HARD.code()
            }
            return StudyRating.GOOD.code()
        }

        @JvmStatic
        fun canSubmitAnalysis(analysis: WritingAnalysis?): Boolean {
            if (analysis == null) {
                return false
            }
            return when (analysis.status) {
                WritingAnalysis.Status.PASS,
                WritingAnalysis.Status.CLOSE,
                WritingAnalysis.Status.WRONG,
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR,
                -> true
                else -> false
            }
        }

        @JvmStatic
        fun canManualOverride(analysis: WritingAnalysis?): Boolean {
            if (analysis == null) {
                return false
            }
            return when (analysis.status) {
                WritingAnalysis.Status.CLOSE,
                WritingAnalysis.Status.WRONG,
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR,
                -> true
                else -> false
            }
        }

        @JvmStatic
        fun canPracticeAfterAnalysis(analysis: WritingAnalysis?): Boolean = canManualOverride(analysis)

        @JvmStatic
        fun canReplayAnalysis(analysis: WritingAnalysis?, hasInk: Boolean, guide: StrokeGuide?): Boolean {
            if (analysis == null ||
                !hasInk ||
                guide == null ||
                guide.isEmpty() ||
                analysis.strokeOrder == null ||
                analysis.strokeOrder.missingGuide
            ) {
                return false
            }
            return when (analysis.status) {
                WritingAnalysis.Status.NO_INK,
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR,
                -> false
                else -> true
            }
        }

        @JvmStatic
        fun shouldIncreaseSupportAfterAnalysis(analysis: WritingAnalysis?): Boolean {
            if (analysis == null) {
                return false
            }
            return when (analysis.status) {
                WritingAnalysis.Status.WRONG,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR,
                -> true
                else -> false
            }
        }

        @JvmStatic
        fun shouldShowLearningPanel(
            analysis: WritingAnalysis?,
            recallTask: Boolean,
            teachingTask: Boolean,
            currentPracticeLevel: Int,
        ): Boolean {
            if (recallTask) {
                return analysis != null && analysis.status != WritingAnalysis.Status.NO_INK && !analysis.writingPassed
            }
            if (analysis == null || analysis.status == WritingAnalysis.Status.NO_INK) {
                return teachingTask && currentPracticeLevel < 3
            }
            return true
        }

        private fun appendStatus(guidePrefix: String?, status: String): String {
            val prefix = guidePrefix ?: ""
            if (prefix.isEmpty()) {
                return status
            }
            return "$prefix\n$status"
        }
    }
}
