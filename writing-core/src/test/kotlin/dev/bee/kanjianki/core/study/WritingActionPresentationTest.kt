package dev.bee.kanjianki.core.study

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingActionPresentationTest {
    @Test
    fun emptyWritingStateShowsPrimaryPracticeActions() {
        val input = WritingActionPresentation.Input(null)
        input.canUndoStroke = true
        input.canRevealMoreHelp = true
        input.teachingTask = true
        input.currentPracticeLevel = 1

        val presentation = WritingActionPresentation.from(input)

        assertFalse(presentation.hasResult)
        assertTrue(presentation.checkVisible)
        assertTrue(presentation.checkEnabled)
        assertEquals("Check", presentation.checkText)
        assertTrue(presentation.undoEnabled)
        assertTrue(presentation.downloadVisible)
        assertFalse(presentation.nextVisible)
        assertFalse(presentation.manualOverrideVisible)
        assertFalse(presentation.practiceWithGuideVisible)
        assertFalse(presentation.replayVisible)
        assertTrue(presentation.hintVisible)
        assertEquals("More help", presentation.hintText)
        assertTrue(presentation.answerPanelVisible)
        assertFalse(presentation.resultStatusVisible)
    }

    @Test
    fun cleanPassCanSubmitWithoutRetryActions() {
        val input = WritingActionPresentation.Input(analysis(WritingAnalysis.Status.PASS, true))
        input.writingModelStatusKnown = true
        input.writingModelDownloaded = true
        input.currentPracticeLevel = 3

        val presentation = WritingActionPresentation.from(input)

        assertTrue(presentation.hasResult)
        assertTrue(presentation.passed)
        assertFalse(presentation.messyPass)
        assertFalse(presentation.checkVisible)
        assertFalse(presentation.downloadVisible)
        assertTrue(presentation.nextVisible)
        assertEquals("Pass", presentation.nextLabel)
        assertEquals(StudyRating.GOOD.code(), presentation.nextRating)
        assertFalse(presentation.manualOverrideVisible)
        assertFalse(presentation.practiceWithGuideVisible)
        assertFalse(presentation.hintVisible)
        assertEquals("Hint", presentation.hintText)
        assertTrue(presentation.answerPanelVisible)
        assertTrue(presentation.resultStatusVisible)
    }

    @Test
    fun closePassKeepsCleanerRetryAndHardSubmitVisible() {
        val presentation = WritingActionPresentation.from(
            WritingActionPresentation.Input(analysis(WritingAnalysis.Status.CLOSE, true)),
        )

        assertTrue(presentation.passed)
        assertTrue(presentation.messyPass)
        assertTrue(presentation.checkVisible)
        assertEquals("Try cleaner", presentation.checkText)
        assertTrue(presentation.nextVisible)
        assertEquals("Save hard", presentation.nextLabel)
        assertEquals(StudyRating.HARD.code(), presentation.nextRating)
        assertTrue(presentation.manualOverrideVisible)
        assertFalse(presentation.practiceWithGuideVisible)
    }

    @Test
    fun wrongRecallCanReplayOverridePracticeAndFail() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate("校", 0.8f)),
        )
        val input = WritingActionPresentation.Input(analysis)
        input.hasReplaySnapshot = true
        input.hasInk = true
        input.guide = guide()
        input.recallTask = true
        input.canRevealMoreHelp = true

        val presentation = WritingActionPresentation.from(input)

        assertFalse(presentation.passed)
        assertTrue(presentation.checkVisible)
        assertTrue(presentation.nextVisible)
        assertEquals("Fail", presentation.nextLabel)
        assertEquals(StudyRating.AGAIN.code(), presentation.nextRating)
        assertTrue(presentation.manualOverrideVisible)
        assertTrue(presentation.practiceWithGuideVisible)
        assertTrue(presentation.replayVisible)
        assertTrue(presentation.hintVisible)
        assertTrue(presentation.answerPanelVisible)
    }

    @Test
    fun checkingStateDisablesWritingButtons() {
        val input = WritingActionPresentation.Input(null)
        input.checkingWriting = true
        input.canUndoStroke = true

        val presentation = WritingActionPresentation.from(input)

        assertFalse(presentation.checkEnabled)
        assertEquals("Checking...", presentation.checkText)
        assertFalse(presentation.undoEnabled)
    }

    @Test
    fun japaneseLocaleTranslatesWritingPresentationLabels() {
        withLocale(Locale.JAPAN) {
            val input = WritingActionPresentation.Input(analysis(WritingAnalysis.Status.CLOSE, true))
            input.checkingWriting = true
            input.currentPracticeLevel = 1

            val presentation = WritingActionPresentation.from(input)

            assertTrue(presentation.checkVisible)
            assertEquals("確認中...", presentation.checkText)
            assertEquals("もっとヒント", presentation.hintText)
            assertTrue(presentation.nextVisible)
            assertEquals("しっかり保存", presentation.nextLabel)
        }
    }

    private fun analysis(status: WritingAnalysis.Status, passed: Boolean): WritingAnalysis {
        return WritingAnalysis(
            status,
            StudyRating.GOOD.code(),
            passed,
            "message",
            emptyList<RecognitionCandidate>(),
            null,
        )
    }

    private fun sample(): WritingSample {
        return WritingSample(
            listOf(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f),
            ),
            100f,
            100f,
        )
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                stroke(0.1f, 0.1f, 0.9f, 0.1f),
                stroke(0.1f, 0.3f, 0.9f, 0.3f),
            ),
        )
    }

    private fun stroke(startX: Float, startY: Float, endX: Float, endY: Float): InkStroke {
        return InkStroke(listOf(InkPoint(startX, startY, 0), InkPoint(endX, endY, 1)))
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
