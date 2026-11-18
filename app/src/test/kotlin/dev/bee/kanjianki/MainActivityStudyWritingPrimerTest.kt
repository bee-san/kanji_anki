package dev.bee.kanjianki

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.core.study.HintLevel
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.InkStroke
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingCompareModel
import dev.bee.kanjianki.core.study.WritingPrimerStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyWritingPrimerTest {
    @Test
    fun primerWalksWatchWriteCompareThenHandsBackAnUntouchedCard() {
        val activity = primedActivity(HintState.initial())
        val pad = requireNotNull(activity.drawingPad)

        assertEquals(WritingPrimerStage.WATCH, activity.writingPrimerStage)
        assertTrue(pad.isGuideAnimationVisible())
        assertEquals(StudyWritingCopy.primerWatchStatus(), activity.studyStatus?.getText().toString())
        assertFalse(requireNotNull(activity.writingAnswerPanelState).visible)
        assertEquals(StudyWritingCopy.primerWriteLabel(), primary(activity).checkText)
        assertTrue(primary(activity).skipVisible)
        assertFalse(primary(activity).nextVisible)
        assertFalse(primary(activity).downloadVisible)
        assertFalse(tools(activity).hintVisible)
        // Watching is not writing: the pad ignores ink.
        drawStroke(pad, 100f, 100f, 900f, 100f)
        assertFalse(pad.hasInk())

        primary(activity).onCheck.run()

        assertEquals(WritingPrimerStage.WRITE, activity.writingPrimerStage)
        assertFalse(pad.isGuideAnimationVisible())
        assertFalse(requireNotNull(activity.writingAnswerPanelState).visible)
        assertEquals(StudyWritingCopy.primerCompareLabel(), primary(activity).checkText)

        // Comparing an empty pad asks for ink and stays put.
        primary(activity).onCheck.run()
        assertEquals(WritingPrimerStage.WRITE, activity.writingPrimerStage)
        assertEquals(StudyWritingCopy.primerNeedsInkStatus(), activity.studyStatus?.getText().toString())

        // Free recall: far from the guide, in the wrong order, never blocked.
        drawStroke(pad, 100f, 500f, 900f, 500f)
        drawStroke(pad, 100f, 100f, 900f, 100f)
        assertEquals(2, pad.writingSample().strokeCount())
        assertTrue(tools(activity).undoEnabled)

        primary(activity).onCheck.run()

        assertEquals(WritingPrimerStage.COMPARE, activity.writingPrimerStage)
        val compare = requireNotNull(activity.writingCompareState?.model)
        assertEquals(2, compare.yours.size)
        assertEquals(2, compare.model.size)
        assertTrue(compare.problems().contains(WritingCompareModel.Verdict.WRONG_ORDER))
        assertTrue(requireNotNull(activity.writingAnswerPanelState).visible)
        assertEquals(StudyWritingCopy.primerTryAgainLabel(), primary(activity).checkText)
        assertTrue(primary(activity).nextVisible)
        assertEquals(StudyWritingCopy.primerStartLabel(), primary(activity).nextText)
        assertFalse(primary(activity).skipVisible)

        primary(activity).onNext.run()

        assertNull(activity.writingPrimerStage)
        assertNull(activity.writingCompareState?.model)
        assertFalse(pad.hasInk())
        assertNull(activity.activeAnalysis)
        assertEquals(0, activity.hintsUsed)
        assertEquals(HintLevel.TRACE, activity.currentHintState.level())
        assertEquals("Check", primary(activity).checkText)
        assertFalse(primary(activity).skipVisible)
        // The graded attempt is guided again: off-guide ink is blocked.
        drawStroke(pad, 100f, 900f, 900f, 900f)
        assertFalse(pad.hasInk())
    }

    @Test
    fun newInkWhileComparingReturnsToWritingAndSkipEndsThePrimer() {
        val activity = primedActivity(HintState.initial())
        val pad = requireNotNull(activity.drawingPad)
        primary(activity).onCheck.run()
        drawStroke(pad, 100f, 100f, 900f, 100f)
        primary(activity).onCheck.run()
        assertEquals(WritingPrimerStage.COMPARE, activity.writingPrimerStage)

        drawStroke(pad, 100f, 500f, 900f, 500f)

        assertEquals(WritingPrimerStage.WRITE, activity.writingPrimerStage)
        assertNull(activity.writingCompareState?.model)
        assertEquals(2, pad.writingSample().strokeCount())

        // Replay goes back to watching with a clean pad.
        fallback(activity).onReplay.run()
        assertEquals(WritingPrimerStage.WATCH, activity.writingPrimerStage)
        assertFalse(pad.hasInk())
        assertTrue(pad.isGuideAnimationVisible())

        primary(activity).onSkip.run()

        assertNull(activity.writingPrimerStage)
        assertEquals(0, activity.hintsUsed)
        assertEquals("Check", primary(activity).checkText)
    }

    @Test
    fun aKanjiAlreadyWrittenStartsWithoutAPrimerEvenAtTrace() {
        val activity = primedActivity(HintState.initial(), totalReviews = 2)

        assertNull(activity.writingPrimerStage)
        assertEquals("Check", primary(activity).checkText)
    }

    @Test
    fun cardsPastTheTraceLevelStartWithoutAPrimer() {
        val activity = primedActivity(HintState(HintLevel.OUTLINE, 0, 0))

        assertNull(activity.writingPrimerStage)
        assertFalse(requireNotNull(activity.drawingPad).isGuideAnimationVisible())
        assertNotNull(primary(activity))
        assertEquals("Check", primary(activity).checkText)
    }

    private fun primedActivity(initial: HintState, totalReviews: Int = 0): MainActivity {
        val activity = createActivity()
        val session = session(totalReviews)
        activity.strokeGuides = mapOf(KANJI to guide())
        activity.activeSession = session
        activity.activeSimilarWritingRepair = null
        activity.setHintState(initial)
        activity.writingToolActionsView = WritingToolActionsView(activity)
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity)
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity)
        activity.writingAnswerPanelState = WritingAnswerPanelState(false)
        activity.writingCompareState = WritingCompareState()
        activity.writingResultStatus = WritingResultStatusHandle()
        activity.studyStatus = WritingStatusState()
        val pad = DrawingPadView(activity)
        pad.measure(
            View.MeasureSpec.makeMeasureSpec(PAD_SIZE, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(PAD_SIZE, View.MeasureSpec.EXACTLY),
        )
        pad.layout(0, 0, PAD_SIZE, PAD_SIZE)
        pad.setInkEditListener(activity::handleDrawingEdited)
        pad.setGuide(guide(), activity.currentHintState, false)
        activity.drawingPad = pad
        activity.beginWritingPrimer(session)
        activity.updateResultActions()
        return activity
    }

    private fun primary(activity: MainActivity) = requireNotNull(activity.writingPrimaryActionsView).currentModel()

    private fun tools(activity: MainActivity) = requireNotNull(activity.writingToolActionsView).currentModel()

    private fun fallback(activity: MainActivity) = requireNotNull(activity.writingFallbackActionsView).currentModel()

    private fun drawStroke(pad: DrawingPadView, x1: Float, y1: Float, x2: Float, y2: Float) {
        touch(pad, MotionEvent.ACTION_DOWN, 100L, x1, y1)
        touch(pad, MotionEvent.ACTION_MOVE, 120L, (x1 + x2) / 2f, (y1 + y2) / 2f)
        touch(pad, MotionEvent.ACTION_UP, 140L, x2, y2)
    }

    private fun touch(pad: DrawingPadView, action: Int, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(100L, eventTime, action, x, y, 0)
        pad.onTouchEvent(event)
        event.recycle()
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            KANJI,
            listOf(
                InkStroke(listOf(InkPoint(0.1f, 0.1f, 0L), InkPoint(0.9f, 0.1f, 1L))),
                InkStroke(listOf(InkPoint(0.1f, 0.5f, 0L), InkPoint(0.9f, 0.5f, 1L))),
            ),
        )
    }

    private fun session(totalReviews: Int = 0): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(KANJI, "new", 1_000L, 1.0, 2.0, totalReviews, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WRITE_KANJI)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .activeToken("token-primer")
            .build()
        val row = RecordsImportModels.DashboardRow(
            KANJI, 900, "undress", "だつ", "search", 1, "reason", "reason text", 1, 0, 1, emptyList<RecordsImportModels.Example>(),
        )
        return RecordsSchedulerModels.StudySession(
            item,
            row,
            "token-primer",
            StudyTaskTypes.WRITE_KANJI,
            true,
            "prompt",
        )
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private companion object {
        const val KANJI = "脱"
        const val PAD_SIZE = 1000
    }
}
