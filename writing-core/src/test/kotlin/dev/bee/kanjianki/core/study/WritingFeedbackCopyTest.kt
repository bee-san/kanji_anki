package dev.bee.kanjianki.core.study

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingFeedbackCopyTest {
    @Test
    fun guideLabelPreservesHintStageCopy() {
        val emptyGuide = StrokeGuide("裂", emptyList())
        val guide = guide()

        assertTrue(WritingFeedbackCopy.guideLabel(3, emptyGuide).startsWith("Write from memory"))
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), emptyGuide).startsWith("Write from memory"))
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), emptyGuide).startsWith("Draw it"))
        assertEquals("Trace the strokes, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide))
        assertEquals("Copy the faint outline; the current stroke is emphasized.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(1), guide))
        assertEquals("Write with only the current stroke hinted, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(2), guide))
        assertEquals("Write from memory, then check. Use Hint if you are stuck.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), guide))
        assertEquals("Trace the strokes, then check.", WritingFeedbackCopy.guideLabel(null, guide))
    }

    @Test
    fun stageLabelPreservesShortNames() {
        assertEquals("Trace", WritingFeedbackCopy.stageLabel(HintLevel.TRACE))
        assertEquals("Outline", WritingFeedbackCopy.stageLabel(HintLevel.OUTLINE))
        assertEquals("Minimal", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL))
        assertEquals("Blind", WritingFeedbackCopy.stageLabel(HintLevel.BLIND))
    }

    @Test
    fun attemptProgressTextPreservesHintProgressMessages() {
        assertEquals("", WritingFeedbackCopy.attemptProgressText(null, 3, true))
        assertEquals(
            "\nNext writing review will have less help: Minimal.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 0), null, false)
        )
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 1), null, false))
        assertEquals(
            "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), 3, false)
        )
        assertEquals(
            "\nNext try will use more support: Minimal.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, true)
        )
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, false))
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), null, true))
    }

    @Test
    fun targetRevealTextPreservesTerminalStatusCopy() {
        assertEquals("", WritingFeedbackCopy.targetRevealText(null, "裂"))
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), null))
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0), "裂"))
    }

    @Test
    fun candidateTextShowsTopThreeRecognizedValues() {
        assertEquals("", WritingFeedbackCopy.candidateText(null))
        assertEquals("", WritingFeedbackCopy.candidateText(emptyList()))
        assertEquals(
            "拉, 拡, 抽",
            WritingFeedbackCopy.candidateText(
                listOf(
                    RecognitionCandidate("拉", 0.9f),
                    RecognitionCandidate("拡", 0.7f),
                    RecognitionCandidate("抽", 0.5f),
                    RecognitionCandidate("扌", 0.3f),
                )
            )
        )
    }

    @Test
    fun resultMessageCombinesProgressTargetCandidatesAndDiagnosis() {
        val analysis = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "Try again.",
            listOf(RecognitionCandidate("拉", 0.9f), RecognitionCandidate("拡", 0.7f)),
            null,
            HintLevel.BLIND,
            0,
        )

        assertEquals(
            "Try again.\nNext try will use more support: Minimal.\nTarget: 裂\nIt saw: 拉, 拡\nStroke 1: too short",
            WritingFeedbackCopy.resultMessage(analysis, "裂", 3, true, "Stroke 1: too short")
        )
    }

    @Test
    fun resultMessageHandlesNullAndEmptyOptionalText() {
        assertEquals("", WritingFeedbackCopy.resultMessage(null, "裂", 3, true, "diagnosis"))
        assertEquals(
            "NO_INK",
            WritingFeedbackCopy.resultMessage(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), "裂", null, false, null)
        )
    }

    @Test
    fun writingActionCopyPreservesButtonAndRatingChoices() {
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, false))
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, true))
        assertEquals("Check", WritingFeedbackCopy.checkWritingButtonText(false, false))
        assertEquals("Try cleaner", WritingFeedbackCopy.checkWritingButtonText(false, true))

        assertEquals("Fail", WritingFeedbackCopy.submitLabel(null))
        assertEquals("Fail", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertEquals("Save hard", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertEquals("Pass", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))

        assertEquals("again", WritingFeedbackCopy.submitRating(null))
        assertEquals("again", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertEquals("hard", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertEquals("good", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
    }

    @Test
    fun modelStatusCopyPreservesGuidePrefixedMessages() {
        assertEquals(
            "Guide\nAutomatic handwriting checks are unavailable on this device.",
            WritingFeedbackCopy.unavailableModelStatusMessage("Guide")
        )
        assertEquals(
            "Guide\nUnable to read handwriting checker status.",
            WritingFeedbackCopy.modelStatusMessage("Guide", false, false, false)
        )
        assertEquals(
            "Guide\nUnable to read handwriting checker status.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, true, true)
        )
        assertEquals(
            "Guide\nDownload the handwriting checker before automatic checks.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, false, false)
        )
        assertEquals(
            "Guide\nHandwriting checker ready.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, true, false)
        )
        assertEquals(
            "Handwriting checker ready.",
            WritingFeedbackCopy.modelStatusMessage("", true, true, false)
        )
    }

    @Test
    fun writingStatusCopyPreservesGuidePrefixedActionMessages() {
        assertEquals(
            "Guide\nHint used. One current stroke hinted; your ink stayed on the canvas.",
            WritingFeedbackCopy.hintUsedStatus("Guide")
        )
        assertEquals(
            "Guide\nFresh guided try. Draw it again, then check.",
            WritingFeedbackCopy.freshGuidedTryStatus("Guide")
        )
        assertEquals(
            "Guide\nTry cleaner. Keep the same help level and draw it carefully once more.",
            WritingFeedbackCopy.cleanerRetryStatus("Guide")
        )
        assertEquals(
            "Guide\nUndid the last stroke.",
            WritingFeedbackCopy.undoStrokeStatus("Guide")
        )
        assertEquals(
            "Guide\nUpdated ink. Check again when ready.",
            WritingFeedbackCopy.updatedInkStatus("Guide")
        )
        assertEquals(
            "Updated ink. Check again when ready.",
            WritingFeedbackCopy.updatedInkStatus("")
        )
    }

    @Test
    fun blockedStrokeStatusUsesDecisionMessageOrFallback() {
        assertEquals(
            "Guide\nStay close to the guide.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", null)
        )
        assertEquals(
            "Guide\nStay close to the guide.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", StrokeGuideGuard.Decision.rejected(1, ""))
        )
        assertEquals(
            "Guide\nStay close to stroke 1.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", StrokeGuideGuard.Decision.rejected(1, "Stay close to stroke 1."))
        )
    }

    @Test
    fun writingActionPolicyPreservesSubmittableAndFallbackStatuses() {
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(null))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))

        assertFalse(WritingFeedbackCopy.canManualOverride(null))
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))

        assertFalse(WritingFeedbackCopy.canPracticeAfterAnalysis(null))
        assertTrue(WritingFeedbackCopy.canPracticeAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(null))
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))
    }

    @Test
    fun replayPolicyRequiresInkGuideAndReplayableAnalysis() {
        val order = strokeOrder()

        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, guide()))
        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.CLOSE, true, order), true, guide()))
        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.WRONG, false, order), true, guide()))

        assertFalse(WritingFeedbackCopy.canReplayAnalysis(null, true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), false, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, null))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, emptyGuide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, missingGuideOrder()), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.NO_INK, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.NO_STROKE_DATA, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.RECOGNITION_ERROR, false, order), true, guide()))
    }

    @Test
    fun learningPanelVisibilityPreservesRecallAndTeachingRules() {
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, true, false, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), true, false, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), true, false, 1))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), true, false, 1))

        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, false, 1))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 3))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), false, false, 3))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), false, false, 3))
    }

    @Test
    fun japaneseLocaleTranslatesWritingFeedbackCopy() {
        withLocale(Locale.JAPAN) {
            val guide = guide()
            val noGuide = emptyGuide()

            assertEquals("ストロークをなぞってから確認してください。", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide))
            assertEquals("記憶で書いてから確認してください。まだストロークガイドはありません。", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), noGuide))
            assertEquals("なぞり", WritingFeedbackCopy.stageLabel(HintLevel.TRACE))
            assertEquals("輪郭", WritingFeedbackCopy.stageLabel(HintLevel.OUTLINE))
            assertEquals("最小", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL))
            assertEquals("記憶", WritingFeedbackCopy.stageLabel(HintLevel.BLIND))
            assertEquals("確認中...", WritingFeedbackCopy.checkWritingButtonText(true, false))
            assertEquals("もっときれいに", WritingFeedbackCopy.checkWritingButtonText(false, true))
            assertEquals("チェック", WritingFeedbackCopy.checkWritingButtonText(false, false))
            assertEquals("チェッカーをダウンロード", WritingFeedbackCopy.downloadCheckerLabel())
            assertEquals("ヒント", WritingFeedbackCopy.hintButtonText(3))
            assertEquals("もっとヒント", WritingFeedbackCopy.hintButtonText(1))
            assertEquals("\n次の書き取りでは助けが少なくなります: 最小.", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 0), null, false))
            assertEquals("\nもっときれいに書くと合格しやすくなります。今の助けを保つなら「しっかり保存」を選んでください。", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), null, false))
            assertEquals("\n対象: 拉", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), "拉"))
            assertEquals("不合格", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
            assertEquals("しっかり保存", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
            assertEquals("合格", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
            assertEquals("Guide\nこの端末では自動手書き判定は使えません。", WritingFeedbackCopy.unavailableModelStatusMessage("Guide"))
            assertEquals("Guide\n手書き判定器の状態を読み取れません。", WritingFeedbackCopy.modelStatusMessage("Guide", false, false, false))
            assertEquals("Guide\n自動判定を使う前に手書き判定器をダウンロードしてください。", WritingFeedbackCopy.modelStatusMessage("Guide", true, false, false))
            assertEquals("Guide\n手書き判定器の準備ができました。", WritingFeedbackCopy.modelStatusMessage("Guide", true, true, false))
        }
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide("裂", listOf(stroke()))
    }

    private fun emptyGuide(): StrokeGuide {
        return StrokeGuide("裂", emptyList())
    }

    private fun strokeOrder(): StrokeOrderEvaluator.StrokeOrderResult {
        return StrokeOrderEvaluator.evaluate(guide(), sample())
    }

    private fun missingGuideOrder(): StrokeOrderEvaluator.StrokeOrderResult {
        return StrokeOrderEvaluator.evaluate(emptyGuide(), sample())
    }

    private fun sample(): WritingSample {
        return WritingSample(listOf(stroke()), 100f, 100f)
    }

    private fun stroke(): InkStroke {
        return InkStroke(
            listOf(
                InkPoint(0.1f, 0.2f, 0L),
                InkPoint(0.3f, 0.4f, 1L),
            )
        )
    }

    private fun analysis(
        status: WritingAnalysis.Status,
        passed: Boolean,
        hintLevel: HintLevel,
        hintsUsed: Int,
    ): WritingAnalysis {
        return WritingAnalysis(status, if (passed) "good" else "again", passed, status.name, emptyList(), null, hintLevel, hintsUsed)
    }

    private fun analysisWithOrder(
        status: WritingAnalysis.Status,
        passed: Boolean,
        order: StrokeOrderEvaluator.StrokeOrderResult,
    ): WritingAnalysis {
        return WritingAnalysis(status, if (passed) "good" else "again", passed, status.name, emptyList(), order, HintLevel.BLIND, 0)
    }

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
