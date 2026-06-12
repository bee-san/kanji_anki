package dev.bee.kanjianki.core.study

import java.util.Locale

class WritingFeedbackCopy private constructor() {
    companion object {
        private const val JAPANESE_LANGUAGE = "ja"
        private val STAY_CLOSE_TO_STROKE_REGEX = Regex("Stay close to stroke (\\d+)\\.")
        private val STROKE_DIAGNOSIS_REGEX = Regex("Stroke (\\d+): (.+)")
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
                    return localizedText(
                        "Write from memory, then check. No stroke guide is bundled yet.",
                        "記憶で書いてから確認しましょう。筆順ガイドはまだ含まれていません。"
                    )
                }
                return localizedText(
                    "Draw it, then check. Stroke-order feedback will be limited.",
                    "書いてから確認しましょう。筆順フィードバックは限定されます。"
                )
            }
            return when (level) {
                HintLevel.TRACE -> localizedText(
                    "Trace the strokes, then check.",
                    "なぞってから確認しましょう。"
                )
                HintLevel.OUTLINE -> localizedText(
                    "Copy the faint outline; the current stroke is emphasized.",
                    "薄い輪郭を写しましょう。現在の一画が強調されています。"
                )
                HintLevel.MINIMAL -> localizedText(
                    "Write with only the current stroke hinted, then check.",
                    "現在の一画だけをヒントに書いてから確認しましょう。"
                )
                HintLevel.BLIND -> localizedText(
                    "Write from memory, then check. Use Hint if you are stuck.",
                    "記憶で書いてから確認しましょう。迷ったらヒントを使ってください。"
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
                        "\n次の書き取り復習はヒントが減ります: ${stageLabel(next.level())}。"
                    )
                }
            }
            if (analysis.status == WritingAnalysis.Status.CLOSE) {
                return localizedText(
                    "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.",
                    "\nもっと丁寧に書くと合格しやすいです。Hardで保存するとこのヒント段階を保てます。"
                )
            }
            if (increaseSupportAfterAnalysis && activeWritingLevel != null) {
                val next = HINT_PROGRESSION.afterWriting(HintState.fromWritingLevel(activeWritingLevel), analysis)
                if (next.level() != HintLevel.fromWritingLevel(activeWritingLevel)) {
                    return localizedText(
                        "\nNext try will use more support: ${stageLabel(next.level())}.",
                        "\n次の挑戦はヒントを増やします: ${stageLabel(next.level())}。"
                    )
                }
            }
            return ""
        }

        @JvmStatic
        fun stageLabel(level: HintLevel): String {
            return when (level) {
                HintLevel.TRACE -> localizedText("Trace", "なぞる")
                HintLevel.OUTLINE -> localizedText("Outline", "輪郭")
                HintLevel.MINIMAL -> localizedText("Minimal", "最小ヒント")
                HintLevel.BLIND -> localizedText("Blind", "暗記")
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
                -> localizedText("\nTarget: $targetKanji", "\nお題: $targetKanji")
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
            var message = localizedAnalysisMessage(analysis.message) +
                attemptProgressText(analysis, activeWritingLevel, increaseSupportAfterAnalysis) +
                targetRevealText(analysis, targetKanji) +
                if (candidates.isEmpty()) "" else localizedText("\nIt saw: $candidates", "\n認識候補: $candidates")
            val safeDiagnosis = diagnosis ?: ""
            if (safeDiagnosis.isNotEmpty()) {
                message += "\n${localizedDiagnosisText(safeDiagnosis)}"
            }
            return message
        }

        @JvmStatic
        fun checkWritingButtonText(checkingWriting: Boolean, messyPass: Boolean): String {
            if (checkingWriting) {
                return localizedText("Checking...", "確認中...")
            }
            return if (messyPass) localizedText("Try cleaner", "もっと丁寧に") else localizedText("Check", "確認")
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
                    "この端末では自動手書き判定を使えません。"
                )
            )
        }

        @JvmStatic
        fun modelStatusMessage(guidePrefix: String?, statusPresent: Boolean, downloaded: Boolean, hasError: Boolean): String {
            if (hasError || !statusPresent) {
                return appendStatus(
                    guidePrefix,
                    localizedText(
                        "Unable to read handwriting checker status.",
                        "手書き判定の状態を読み取れません。"
                    )
                )
            }
            if (!downloaded) {
                return appendStatus(
                    guidePrefix,
                    localizedText(
                        "Download the handwriting checker before automatic checks.",
                        "自動判定の前に手書き判定をダウンロードしてください。"
                    )
                )
            }
            return appendStatus(
                guidePrefix,
                localizedText("Handwriting checker ready.", "手書き判定の準備ができています。")
            )
        }

        @JvmStatic
        fun hintUsedStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Hint used. One current stroke hinted; your ink stayed on the canvas.",
                    "ヒントを使いました。現在の一画だけを表示し、書いた線はそのまま残しました。"
                )
            )
        }

        @JvmStatic
        fun freshGuidedTryStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Fresh guided try. Draw it again, then check.",
                    "ガイド付きでやり直します。もう一度書いてから確認しましょう。"
                )
            )
        }

        @JvmStatic
        fun cleanerRetryStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText(
                    "Try cleaner. Keep the same help level and draw it carefully once more.",
                    "もっと丁寧に。ヒント段階はそのままで、もう一度慎重に書きましょう。"
                )
            )
        }

        @JvmStatic
        fun undoStrokeStatus(guidePrefix: String?): String {
            return appendStatus(guidePrefix, localizedText("Undid the last stroke.", "最後の一画を取り消しました。"))
        }

        @JvmStatic
        fun updatedInkStatus(guidePrefix: String?): String {
            return appendStatus(
                guidePrefix,
                localizedText("Updated ink. Check again when ready.", "書き直しました。準備できたらもう一度確認してください。")
            )
        }

        @JvmStatic
        fun blockedStrokeStatus(guidePrefix: String?, decision: StrokeGuideGuard.Decision?): String {
            return appendStatus(guidePrefix, localizedBlockedStrokeMessage(decision))
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

        private fun localizedText(english: String, japanese: String): String =
            if (isJapaneseLocale()) japanese else english

        private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

        private fun localizedBlockedStrokeMessage(decision: StrokeGuideGuard.Decision?): String {
            val message = if (decision == null || decision.message.isEmpty()) {
                "Stay close to the guide."
            } else {
                decision.message
            }
            if (!isJapaneseLocale()) {
                return message
            }
            val strokeMatch = STAY_CLOSE_TO_STROKE_REGEX.matchEntire(message)
            if (strokeMatch != null) {
                return "${strokeMatch.groupValues[1]}画目に近づけて書いてください。"
            }
            return when (message) {
                "Stay close to the guide." -> "手本に沿って書いてください。"
                "All guided strokes are already drawn." -> "ガイドの全ての画はすでに書かれています。"
                else -> message
            }
        }

        private fun localizedAnalysisMessage(message: String): String {
            if (!isJapaneseLocale()) {
                return message
            }
            return when (message) {
                "Write in the square before checking." -> "確認する前に枠の中に書いてください。"
                "The handwriting checker is unavailable on this device." ->
                    "この端末では手書き判定を使えません。"
                "Automatic handwriting checks are unavailable on this device." ->
                    "この端末では自動手書き判定を使えません。"
                "Download the handwriting checker before automatic checks." ->
                    "自動判定の前に手書き判定をダウンロードしてください。"
                "I could not read that as the target kanji yet." -> "まだお題の漢字として読み取れません。"
                "The handwriting checker could not read this attempt. Try once more." ->
                    "手書き判定がこの入力を読み取れませんでした。もう一度試してください。"
                "Readable, but the stroke path needs one more careful pass." ->
                    "読めますが、線をもう少し丁寧に書くとよくなります。"
                "Clean match." -> "きれいに一致しました。"
                "Matched the kanji. Keep tightening the stroke path." ->
                    "漢字は一致しました。線をさらに整えていきましょう。"
                "Recognized as the target kanji. Stroke order could not be checked because no guide is bundled yet." ->
                    "お題の漢字として認識しました。筆順ガイドがまだ含まれていないため、筆順は確認できませんでした。"
                "Recognized as the target kanji, but stroke order could not be checked because no guide is bundled yet." ->
                    "お題の漢字として認識しましたが、筆順ガイドがまだ含まれていないため筆順は確認できませんでした。"
                "No stroke-order guide is available for this kanji." -> "この漢字の筆順ガイドはまだありません。"
                "No stroke-order guide is available for this kanji. I could not read that as the target kanji yet." ->
                    "この漢字の筆順ガイドはまだありません。まだお題の漢字として読み取れません。"
                "No ink was drawn." -> "まだ何も書かれていません。"
                "Stroke path looks clean." -> "線はきれいです。"
                "Readable path, but some strokes look shaky." -> "読める線ですが、一部の画が不安定です。"
                "The stroke count or order does not match the guide yet." ->
                    "画数または筆順がまだガイドと一致していません。"
                else -> message
            }
        }

        private fun localizedDiagnosisText(diagnosis: String): String {
            if (!isJapaneseLocale() || diagnosis.isEmpty()) {
                return diagnosis
            }
            return diagnosis.lines().joinToString("\n") { localizedDiagnosisLine(it) }
        }

        private fun localizedDiagnosisLine(line: String): String {
            val strokeMatch = STROKE_DIAGNOSIS_REGEX.matchEntire(line)
            if (strokeMatch != null) {
                val stroke = strokeMatch.groupValues[1]
                return when (strokeMatch.groupValues[2]) {
                    "likely wrong order" -> "${stroke}画目: 順番が違うかもしれません"
                    "likely wrong direction" -> "${stroke}画目: 向きが違うかもしれません"
                    "may be missing" -> "${stroke}画目: 抜けているかもしれません"
                    "may be extra" -> "${stroke}画目: 余分な一画かもしれません"
                    "shape looks rough" -> "${stroke}画目: 形が崩れているようです"
                    "component proportion or shape looks rough" -> "${stroke}画目: 部品のバランスや形を見直しましょう"
                    "too far from the guide" -> "${stroke}画目: 手本から離れすぎています"
                    else -> line
                }
            }
            return when (line) {
                "It looked like a different kanji; compare the similar parts" ->
                    "別の漢字に見えます。似ている部分を比べましょう"
                "Recognized, but the stroke path was messy" -> "認識できましたが、線が乱れています"
                "Good enough, but the stroke path was messy" -> "合格範囲ですが、線が乱れています"
                else -> line
            }
        }
    }
}
