package dev.bee.kanjianki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.core.study.HintLevel
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.InkStroke
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingSample
import dev.bee.kanjianki.study.CapturedStroke
import dev.bee.kanjianki.study.CapturedWriting
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val PAD_SIZE = 1000

@RunWith(AndroidJUnit4::class)
class DrawingPadViewInstrumentedTest {
    @Test
    fun drawingPadExposesAnswerSafeAccessibilityStateAsInkChanges() {
        val pad = laidOutPad()
        pad.setTarget("裂")

        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, pad.importantForAccessibility)
        assertEquals(StudyWritingCopy.drawingPadDescription(), pad.contentDescription)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(0), ViewCompat.getStateDescription(pad))
        assertFalse(pad.contentDescription.contains("裂"))

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 300f, 300f)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(1), ViewCompat.getStateDescription(pad))

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f)
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 700f, 700f)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(2), ViewCompat.getStateDescription(pad))

        assertTrue(pad.undoLastStroke())
        assertEquals(StudyWritingCopy.drawingPadStrokeState(1), ViewCompat.getStateDescription(pad))

        pad.clear()
        assertEquals(StudyWritingCopy.drawingPadStrokeState(0), ViewCompat.getStateDescription(pad))
        assertTrue(pad.contentDescription.isNotEmpty())
    }

    @Test
    fun drawingPadCapturesNoiseFilteredStrokeAndWritingSample() {
        val pad = laidOutPad()
        val downTime = 100L

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, downTime, 110L, MotionEvent.ACTION_MOVE, 100.2f, 100.1f)
        sendTouch(pad, downTime, 120L, MotionEvent.ACTION_MOVE, 260f, 320f)
        sendTouch(pad, downTime, 130L, MotionEvent.ACTION_UP, 420f, 480f)

        assertTrue(pad.hasInk())
        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertEquals(3, writing.strokes[0].points.size)
        assertEquals(1000f, writing.writingAreaWidth)
        assertEquals(1000f, writing.writingAreaHeight)
        val sample = pad.writingSample()
        assertEquals(1, sample.strokes.size)
        assertEquals(3, sample.strokes[0].points.size)
    }

    @Test
    fun drawingPadKeepsStrokePointWhenOnlyOneAxisLooksLikeNoise() {
        val pad = laidOutPad()
        val downTime = 100L

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, downTime, 110L, MotionEvent.ACTION_MOVE, 100.2f, 180f)
        sendTouch(pad, downTime, 120L, MotionEvent.ACTION_UP, 100.3f, 260f)

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertEquals(3, writing.strokes[0].points.size)
        assertStrokePoint(writing.strokes[0].points[1], 100.2f, 180f, 110L)
    }

    @Test
    fun drawingPadCapturesHistoricalMoveSamplesInOrder() {
        val pad = laidOutPad()
        val downTime = 100L

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendMoveWithHistory(pad, downTime)
        sendTouch(pad, downTime, 150L, MotionEvent.ACTION_UP, 480f, 520f)

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertStrokePoint(writing.strokes[0].points[0], 100f, 100f, 100L)
        assertStrokePoint(writing.strokes[0].points[1], 160f, 180f, 120L)
        assertStrokePoint(writing.strokes[0].points[2], 240f, 280f, 130L)
        assertStrokePoint(writing.strokes[0].points[3], 360f, 390f, 140L)
        assertStrokePoint(writing.strokes[0].points[4], 480f, 520f, 150L)
    }

    @Test
    fun drawingPadReplaySnapshotStartsStopsAndCancelsOnEdit() {
        val pad = laidOutPad()
        val edits = AtomicInteger()
        pad.setInkEditListener(Runnable { edits.incrementAndGet() })

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 200f, 200f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 300f, 300f)
        val editsAfterFirstStroke = edits.get()
        assertTrue(editsAfterFirstStroke > 0)

        pad.captureReplaySnapshot()
        pad.startReplay()
        assertTrue(pad.hasReplaySnapshot())
        assertTrue(pad.isReplayOverlayVisible())
        drawToBitmap(pad)

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f)
        assertFalse(pad.isReplayOverlayVisible())
        assertEquals(editsAfterFirstStroke + 1, edits.get())
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 560f, 560f)
    }

    @Test
    fun drawingPadUndoRemovesCommittedStrokesAndReplaySnapshot() {
        val pad = laidOutPad()

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 300f, 300f)
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 600f, 600f)
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 800f, 800f)
        pad.captureReplaySnapshot()
        pad.startReplay()

        assertTrue(pad.canUndoStroke())
        assertTrue(pad.hasReplaySnapshot())

        assertTrue(pad.undoLastStroke())
        assertFalse(pad.hasReplaySnapshot())
        assertTrue(pad.hasInk())
        assertEquals(1, pad.writingSample().strokes.size)

        assertTrue(pad.undoLastStroke())
        assertFalse(pad.hasInk())
        assertFalse(pad.canUndoStroke())
        assertFalse(pad.undoLastStroke())
    }

    @Test
    fun drawingPadBlocksFarStartEvenWhenGuideIsHidden() {
        val pad = laidOutPad()
        val blocked = AtomicInteger()
        pad.setStrokeBlockedListener { decision ->
            blocked.incrementAndGet()
            assertEquals("Stay close to stroke 1.", decision.message)
        }
        pad.setGuide(twoStrokeGuide(), HintState(HintLevel.BLIND, 0, 0), false)

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 950f, 950f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 960f, 960f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 970f, 970f)

        assertFalse(pad.hasInk())
        assertEquals(1, blocked.get())
    }

    @Test
    fun drawingPadBlocksFarMoveWithoutCommittingPartialStroke() {
        val pad = laidOutPad()
        val blocked = AtomicInteger()
        pad.setStrokeBlockedListener { blocked.incrementAndGet() }
        pad.setGuide(twoStrokeGuide(), HintState.initial(), false)

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 200f, 220f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 220f, 420f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_MOVE, 900f, 500f)
        sendTouch(pad, 100L, 160L, MotionEvent.ACTION_UP, 920f, 540f)

        assertFalse(pad.hasInk())
        assertFalse(pad.canUndoStroke())
        assertEquals(0, pad.writingSample().strokes.size)
        assertEquals(1, blocked.get())

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 200f, 220f)
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_MOVE, 200f, 420f)
        sendTouch(pad, 200L, 240L, MotionEvent.ACTION_UP, 200f, 720f)

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertStrokePoint(writing.strokes[0].points[0], 200f, 220f, 200L)
    }

    @Test
    fun drawingPadClearReplaySnapshotRemovesStoredReplayAndOverlay() {
        val pad = laidOutPad()

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 200f, 200f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 300f, 300f)
        pad.captureReplaySnapshot()
        pad.startReplay()

        assertTrue(pad.hasReplaySnapshot())
        assertTrue(pad.isReplayOverlayVisible())

        pad.clearReplaySnapshot()

        assertFalse(pad.hasReplaySnapshot())
        assertFalse(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadDrawsGuideAndFallbackWithoutStateRegression() {
        val pad = laidOutPad()
        pad.setTarget("拉")
        pad.setGuide(null, 2, true)
        drawToBitmap(pad)
        assertFalse(pad.hasInk())

        val guide = StrokeGuide(
            "拉",
            listOf(
                InkStroke(
                    listOf(
                        InkPoint(0.2f, 0.2f, 0L),
                        InkPoint(0.8f, 0.8f, 10L)
                    )
                )
            )
        )
        pad.setGuide(guide, 1, false)
        drawToBitmap(pad)
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 200f, 200f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 800f, 800f)
        drawToBitmap(pad)
        assertTrue(pad.hasInk())
        assertFalse(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadFallbackOutlineUsesStrongerInkWhenGuideIsRevealed() {
        val pad = laidOutPad()
        pad.setTarget("A")
        pad.setGuide(null, 2, false)
        val practiceHint = renderToBitmap(pad)

        pad.setGuide(null, 3, true)
        val revealedHint = renderToBitmap(pad)
        assertTrue(countDifferentPixels(practiceHint, revealedHint) > 20)
        assertFalse(pad.hasInk())
    }

    @Test
    fun drawingPadHandlesMoveBeforeDownCancelAndReplayNoOps() {
        val pad = laidOutPad()

        pad.startReplay()
        assertFalse(pad.isReplayOverlayVisible())
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_MOVE, 10f, 10f)
        assertFalse(pad.hasInk())

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        drawToBitmap(pad)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_CANCEL, 180f, 220f)
        assertTrue(pad.hasInk())

        pad.clear()
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f)
        sendTouch(pad, 200L, 210L, MotionEvent.ACTION_UP, 500f, 500f)
        pad.captureReplaySnapshot()
        pad.startReplay()
        drawToBitmap(pad)
        assertTrue(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadKeepsActiveStrokeWhenNonActivePointerLifts() {
        val pad = laidOutPad()
        val downTime = 100L
        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)

        val pointerDown = twoPointerEvent(downTime, 110L, MotionEvent.ACTION_POINTER_DOWN, 1, 0, 1)
        pad.onTouchEvent(pointerDown)
        val nonActivePointerUp = twoPointerEvent(downTime, 120L, MotionEvent.ACTION_POINTER_UP, 1, 0, 1)
        pad.onTouchEvent(nonActivePointerUp)
        assertFalse(pad.hasInk())

        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_MOVE, 220f, 240f)
        sendTouch(pad, downTime, 160L, MotionEvent.ACTION_UP, 320f, 360f)
        assertTrue(pad.hasInk())
        assertEquals(3, pad.capturedWriting().strokes[0].points.size)
    }

    @Test
    fun drawingPadCommitsStrokeWhenActivePointerLiftsDuringMultiTouch() {
        val pad = laidOutPad()
        val downTime = 100L
        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)

        val pointerDown = twoPointerEvent(
            downTime,
            110L,
            MotionEvent.ACTION_POINTER_DOWN,
            1,
            0,
            1,
            100f,
            100f,
            400f,
            400f
        )
        pad.onTouchEvent(pointerDown)
        val activePointerUp = twoPointerEvent(
            downTime,
            120L,
            MotionEvent.ACTION_POINTER_UP,
            0,
            0,
            1,
            260f,
            280f,
            420f,
            440f
        )
        pad.onTouchEvent(activePointerUp)

        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_MOVE, 700f, 720f)

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertEquals(2, writing.strokes[0].points.size)
        assertStrokePoint(writing.strokes[0].points[0], 100f, 100f, 100L)
        assertStrokePoint(writing.strokes[0].points[1], 260f, 280f, 120L)
    }

    @Test
    fun drawingPadIgnoresMoveFromMissingActivePointerAndSinglePointGuideHints() {
        val pad = laidOutPad()
        val downTime = 100L

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        val wrongPointerMove = onePointerEvent(downTime, 120L, MotionEvent.ACTION_MOVE, 9, 600f, 600f)
        assertTrue(pad.onTouchEvent(wrongPointerMove))
        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_UP, 200f, 200f)

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertEquals(2, writing.strokes[0].points.size)
        assertStrokePoint(writing.strokes[0].points[0], 100f, 100f, 100L)
        assertStrokePoint(writing.strokes[0].points[1], 200f, 200f, 140L)

        pad.clear()
        pad.setGuide(
            StrokeGuide(
                "点",
                listOf(
                    InkStroke(
                        listOf(
                            InkPoint(0.5f, 0.5f, 0L)
                        )
                    )
                )
            ),
            0,
            false
        )
        drawToBitmap(pad)
        assertFalse(pad.hasInk())

        pad.captureReplaySnapshot()
        drawToBitmap(pad)
        assertFalse(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadUsesVisiblePointerWhenActivePointerIsMissingOnLift() {
        val pad = laidOutPad()
        val downTime = 100L

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        val wrongPointerUp = onePointerEvent(downTime, 140L, MotionEvent.ACTION_UP, 9, 620f, 640f)
        assertTrue(pad.onTouchEvent(wrongPointerUp))

        val writing = pad.capturedWriting()
        assertEquals(1, writing.strokes.size)
        assertEquals(2, writing.strokes[0].points.size)
        assertStrokePoint(writing.strokes[0].points[0], 100f, 100f, 100L)
        assertStrokePoint(writing.strokes[0].points[1], 620f, 640f, 140L)
    }

    @Test
    fun drawingPadIgnoresPointerEndEventsWhenNoStrokeIsActive() {
        val pad = laidOutPad()
        val downTime = 100L

        val pointerUp = twoPointerEvent(downTime, 120L, MotionEvent.ACTION_POINTER_UP, 1, 0, 1)
        assertTrue(pad.onTouchEvent(pointerUp))
        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_CANCEL, 300f, 300f)

        assertFalse(pad.hasInk())
        assertEquals(0, pad.writingSample().strokes.size)
    }

    @Test
    fun drawingPadRequestsParentInterceptOnlyDuringActiveStroke() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = TrackingParent(context)
        val pad = DrawingPadView(context)
        parent.addView(pad, FrameLayout.LayoutParams(PAD_SIZE, PAD_SIZE))
        parent.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(PAD_SIZE, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(PAD_SIZE, android.view.View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, PAD_SIZE, PAD_SIZE)

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        assertTrue(parent.lastDisallowIntercept)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 200f, 200f)

        assertFalse(parent.lastDisallowIntercept)
        assertEquals(2, parent.interceptRequests)
    }

    @Test
    fun drawingPadRendersFallbackOutlineOnlyWhenTargetIsAvailable() {
        val pad = laidOutPad()
        pad.setTarget(null)
        pad.setGuide(null, 3, false)
        val blank = renderToBitmap(pad)

        pad.setTarget("A")
        pad.setGuide(StrokeGuide("A", emptyList()), 2, true)
        val outlined = renderToBitmap(pad)
        assertTrue(countDifferentPixels(blank, outlined) > 20)
        assertFalse(pad.hasInk())
    }

    @Test
    fun drawingPadSkipsFallbackOutlineWhenHintsAreDisabled() {
        val pad = laidOutPad()
        pad.setTarget(null)
        pad.setGuide(null, 3, false)
        val blank = renderToBitmap(pad)

        pad.setTarget("A")
        pad.setGuide(null, 3, false)
        val hidden = renderToBitmap(pad)
        assertEquals(0, countDifferentPixels(blank, hidden))

        pad.setTarget(null)
        pad.setGuide(null, 2, false)
        val hiddenWithoutTarget = renderToBitmap(pad)
        assertEquals(0, countDifferentPixels(blank, hiddenWithoutTarget))
        assertFalse(pad.hasInk())
    }

    @Test
    fun drawingPadRendersOnlyVisibleStrokeHints() {
        val pad = laidOutPad()
        val guide = twoStrokeGuide()

        pad.setGuide(guide, HintState(HintLevel.BLIND, 0, 0), false)
        val blind = renderToBitmap(pad)
        val blindPixels = countGuidePixels(blind)

        pad.setGuide(guide, HintState.initial(), true)
        val revealed = renderToBitmap(pad)
        assertTrue(countGuidePixels(revealed) > blindPixels)
        assertFalse(pad.hasInk())
    }

    @Test
    fun drawingPadKeepsReplayWhenRevealGuideHasStrokeHintsAndThenHidesForEmptyGuide() {
        val pad = laidOutPad()
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 120f, 140f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 560f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 820f, 860f)
        pad.captureReplaySnapshot()
        pad.startReplay()

        pad.setGuide(twoStrokeGuide(), null as HintState?, true)
        assertTrue(pad.isReplayOverlayVisible())
        val replaying = renderToBitmap(pad)
        assertTrue(countBluePixels(replaying) > 0)

        pad.setGuide(StrokeGuide("空", emptyList()), HintState.initial(), true)
        assertFalse(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadReplayRendersCompletedStrokeAfterAnimationDuration() {
        val pad = laidOutPad()
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 500f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 900f, 900f)
        pad.captureReplaySnapshot()
        pad.startReplayAt(SystemClock.uptimeMillis() - 10_000L)

        val finishedReplay = renderToBitmap(pad)
        assertTrue(countBluePixels(finishedReplay) > 400)
        assertTrue(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadReplayRendersCurrentStrokeAndStopsBeforeLaterStrokes() {
        val pad = laidOutPad()
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 180f, 180f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 260f, 260f)
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 700f, 700f)
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_MOVE, 760f, 760f)
        sendTouch(pad, 200L, 240L, MotionEvent.ACTION_UP, 820f, 820f)
        pad.captureReplaySnapshot()
        pad.startReplay()

        val partialReplay = renderToBitmap(pad)
        assertTrue(countBluePixels(partialReplay) > 0)
        assertTrue(pad.isReplayOverlayVisible())
    }

    @Test
    fun drawingPadReplayShowsStartDotBeforeAnyAnimationElapsed() {
        val pad = laidOutPad()
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 500f)
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 900f, 900f)
        pad.captureReplaySnapshot()
        pad.startReplayAt(SystemClock.uptimeMillis() + 10_000L)

        val firstFrame = renderToBitmap(pad)
        assertTrue(countBluePixels(firstFrame) > 0)
        assertTrue(pad.isReplayOverlayVisible())
    }
}

private fun laidOutPad(): DrawingPadView {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pad = DrawingPadView(context).apply { setNightMode(false) }
    pad.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(PAD_SIZE, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(PAD_SIZE, android.view.View.MeasureSpec.EXACTLY)
    )
    pad.layout(0, 0, PAD_SIZE, PAD_SIZE)
    return pad
}

private fun drawToBitmap(pad: DrawingPadView) {
    renderToBitmap(pad)
}

private fun renderToBitmap(pad: DrawingPadView): Bitmap {
    val bitmap = Bitmap.createBitmap(PAD_SIZE, PAD_SIZE, Bitmap.Config.ARGB_8888)
    pad.draw(Canvas(bitmap))
    return bitmap
}

private fun countBluePixels(bitmap: Bitmap): Int {
    var count = 0
    for (y in 0 until bitmap.height step 2) {
        for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.blue(pixel) > Color.red(pixel) + 40 && Color.blue(pixel) > Color.green(pixel) + 40) {
                count++
            }
        }
    }
    return count
}

private fun countGuidePixels(bitmap: Bitmap): Int {
    var count = 0
    for (y in 0 until bitmap.height step 2) {
        for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val brownGuide = red < 180 && green < 150 && blue < 130
            val coralGuide = red > 220 && green < 120 && blue < 160
            if (brownGuide || coralGuide) {
                count++
            }
        }
    }
    return count
}

private fun countDifferentPixels(first: Bitmap, second: Bitmap): Int {
    var count = 0
    for (y in 0 until first.height step 2) {
        for (x in 0 until first.width step 2) {
            if (first.getPixel(x, y) != second.getPixel(x, y)) {
                count++
            }
        }
    }
    return count
}

private fun twoStrokeGuide(): StrokeGuide {
    return StrokeGuide(
        "川",
        listOf(
            InkStroke(
                listOf(
                    InkPoint(0.20f, 0.20f, 0L),
                    InkPoint(0.20f, 0.80f, 10L)
                )
            ),
            InkStroke(
                listOf(
                    InkPoint(0.72f, 0.22f, 0L),
                    InkPoint(0.72f, 0.82f, 10L)
                )
            )
        )
    )
}

private fun sendTouch(pad: DrawingPadView, downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    pad.onTouchEvent(event)
}

private fun sendMoveWithHistory(pad: DrawingPadView, downTime: Long) {
    val event = MotionEvent.obtain(downTime, 120L, MotionEvent.ACTION_MOVE, 160f, 180f, 0)
    event.addBatch(130L, 240f, 280f, 1f, 1f, 0)
    event.addBatch(140L, 360f, 390f, 1f, 1f, 0)
    pad.onTouchEvent(event)
}

private fun twoPointerEvent(
    downTime: Long,
    eventTime: Long,
    actionMasked: Int,
    actionIndex: Int,
    firstId: Int,
    secondId: Int,
    firstX: Float = 100f,
    firstY: Float = 100f,
    secondX: Float = 400f,
    secondY: Float = 400f
): MotionEvent {
    val properties = arrayOf(pointer(firstId), pointer(secondId))
    val coords = arrayOf(coords(firstX, firstY), coords(secondX, secondY))
    val action = actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    return MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        2,
        properties,
        coords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0
    )
}

private fun onePointerEvent(
    downTime: Long,
    eventTime: Long,
    actionMasked: Int,
    pointerId: Int,
    x: Float,
    y: Float
): MotionEvent {
    val properties = arrayOf(pointer(pointerId))
    val coords = arrayOf(coords(x, y))
    return MotionEvent.obtain(
        downTime,
        eventTime,
        actionMasked,
        1,
        properties,
        coords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0
    )
}

private fun pointer(id: Int): MotionEvent.PointerProperties {
    return MotionEvent.PointerProperties().apply {
        this.id = id
        toolType = MotionEvent.TOOL_TYPE_FINGER
    }
}

private fun coords(x: Float, y: Float): MotionEvent.PointerCoords {
    return MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        pressure = 1f
        size = 1f
    }
}

private fun assertStrokePoint(point: CapturedStroke.Point, x: Float, y: Float, timestamp: Long) {
    assertEquals(x, point.x, 0.001f)
    assertEquals(y, point.y, 0.001f)
    assertEquals(java.lang.Long.valueOf(timestamp), point.timestampMillis)
}

private class TrackingParent(context: Context) : FrameLayout(context) {
    var interceptRequests = 0
    var lastDisallowIntercept = false

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        interceptRequests++
        lastDisallowIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}
