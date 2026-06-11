package dev.bee.kanjianki.core.study

import java.util.Locale

class WritingFeedbackCopy private constructor() {
    companion object {
        private const val JAPANESE_LANGUAGE = "ja"
        private val HINT_PROGRESSION = HintProgression()

        private fun localizedText(english: String, japanese: String): String {
            return if (isJapaneseLocale()) japanese else english
        }

        private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

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
                    return localizedText(
                        "Write from memory, then check. No stroke guide is bundled yet.",
                        "記憶で書いてから確認してください。まだストロークガイドはありません。",
                    )
                }
                return localizedText(
                    "Draw it, then check. Stroke-order feedback will be limited.",
                    "描いてから確認してください。ストローク順のフィードバックは少なめです。",
                )
            }
            return when (level) {
                HintLevel.TRACE -> localizedText(
                    "Trace the strokes, then check.",
                    "ストロークをなぞってから確認してください。",
                )
                HintLevel.OUTLINE -> localizedText(
                    "Copy the faint outline; the current stroke is emphasized.",
                    "薄い輪郭を写し、現在のストロークが強調されます。",
                )
                HintLevel.MINIMAL -> localizedText(
                    "Write with only the current stroke hinted, then check.",
                    "現在のストロークだけをヒントにして書いてから確認してください。",
                )
                HintLevel.BLIND -> localizedText(
                    "Write from memory, then check. Use Hint if you are stuck.",
                    "記憶で書いてから確認してください。ヒントが必要なら使ってください。",
                )
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
                    return localizedText(
                        "\nNext writing review will have less help: ${stageLabel(next.level())}.",
                        "\n次の書き取りでは助けが少なくなります: ${stageLabel(next.level())}.",
                    )
                }
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return localizedText(
                    "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.",
                    "\nもっときれいに書くと合格しやすくなります。今の助けを保つなら「しっかり保存」を選んでください。",
                )
            }
            if (increaseSupportAfterAnalysis && activeWritingLevel != null) {
                val next = HINT_PROGRESSION.afterWriting(HintState.fromWritingLevel(activeWritingLevel), analysis)
                if (next.level() != HintLevel.fromWritingLevel(activeWritingLevel)) {
                    return localizedText(
                        "\nNext try will use more support: ${stageLabel(next.level())}.",
                        "\n次回はより多くの支援を使います: ${stageLabel(next.level())}.",
                    )
                }
            }
            return ""
        }

        @JvmStatic
        fun stageLabel(level: HintLevel): String {
            return when (level) {
                HintLevel.TRACE -> localizedText("Trace", "なぞり")
                HintLevel.OUTLINE -> localizedText("Outline", "輪郭")
                HintLevel.MINIMAL -> localizedText("Minimal", "最小")
                HintLevel.BLIND -> localizedText("Blind", "記憶")
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
                -> localizedText("\nTarget: $targetKanji", "\n対象: $targetKanji")
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
                if (candidates.isEmpty()) "" else localizedText("\nIt saw: $candidates", "\n見えた: $candidates")
            val safeDiagnosis = diagnosis ?: ""
            if (safeDiagnosis.isNotEmpty()) {
                message += "\n$safeDiagnosis"
            }
            return message
        }

        @JvmStatic
        fun checkWritingButtonText(checkingWriting: Boolean, messyPass: Boolean): String {
            if (checkingWriting) {
                return localizedText("Checking...", "確認中...")
            }
            return if (messyPass) localizedText("Try cleaner", "もっときれいに") else localizedText("Check", "チェック")
        }

        @JvmStatic
        fun downloadCheckerLabel(): String {
            return localizedText("Download checker", "チェッカーをダウンロード")
        }

        @JvmStatic
        fun hintButtonText(currentPracticeLevel: Int): String {
            return if (currentPracticeLevel == 3) {
                localizedText("Hint", "ヒント")
            } else {
                localizedText("More help", "もっとヒント")
            }
        }

        @JvmStatic
        fun unavailableModelStatusMessage(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Automatic handwriting checks are unavailable on this device.",
                    "この端末では自動手書き判定は使えません。",
                ),
            )
        }

        @JvmStatic
        fun modelStatusMessage(guidePrefix: String?, statusPresent: Boolean, downloaded: Boolean, hasError: Boolean): String {
            if (hasError || !statusPresent) {
                return appendStatus(
                    guidePrefix,
                    localizedText(
                        "Unable to read handwriting checker status.",
                        "手書き判定器の状態を読み取れません。",
                    ),
                )
            }
            if (!downloaded) {
                return appendStatus(
                    guidePrefix,
                    localizedText(
                        "Download the handwriting checker before automatic checks.",
                        "自動判定を使う前に手書き判定器をダウンロードしてください。",
                    ),
                )
            }
            return appendStatus(
                guidePrefix,
                localizedText("Handwriting checker ready.", "手書き判定器の準備ができました。"),
            )
        }

        @JvmStatic
        fun hintUsedStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Hint used. One current stroke hinted; your ink stayed on the canvas.",
                    "ヒントを使いました。現在のストロークだけがヒントになり、インクはキャンバスに残っています。",
                ),
            )
        }

        @JvmStatic
        fun freshGuidedTryStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Fresh guided try. Draw it again, then check.",
                    "新しいガイド付きの再挑戦です。もう一度描いてから確認してください。",
                ),
            )
        }

        @JvmStatic
        fun cleanerRetryStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Try cleaner. Keep the same help level and draw it carefully once more.",
                    "もっときれいに再挑戦してください。同じ助けのレベルで、もう一度ていねいに描きます。",
                ),
            )
        }

        @JvmStatic
        fun undoStrokeStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Undid the last stroke.",
                    "最後のストロークを元に戻しました。",
                ),
            )
        }

        @JvmStatic
        fun updatedInkStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Updated ink. Check again when ready.",
                    "インクを更新しました。準備ができたらもう一度確認してください。",
                ),
            )
        }

        @JvmStatic
        fun blockedStrokeStatus(guidePrefix: String?, decision: StrokeGuideGuard.Decision?): String {
            val message = if (decision == null || decision.message.isEmpty()) {
                localizedText("Stay close to the guide.", "ガイドから離れすぎないでください。")
            } else {
                decision.message
            }
            return appendStatus(guidePrefix, message)
        }

        @JvmStatic
        fun submitLabel(analysis: WritingAnalysis?): String {
            if (analysis == null || !analysis.writingPassed) {
                return localizedText("Fail", "不合格")
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return localizedText("Save hard", "しっかり保存")
            }
            return localizedText("Pass", "合格")
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
