package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class DrawingPadViewInstrumentedTest {
    @Test
    public void drawingPadCapturesNoiseFilteredStrokeAndWritingSample() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, downTime, 110L, MotionEvent.ACTION_MOVE, 100.2f, 100.1f);
        sendTouch(pad, downTime, 120L, MotionEvent.ACTION_MOVE, 260f, 320f);
        sendTouch(pad, downTime, 130L, MotionEvent.ACTION_UP, 420f, 480f);

        assertTrue(pad.hasInk());
        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertEquals(3, writing.strokes.get(0).points.size());
        assertEquals(Float.valueOf(1000f), writing.writingAreaWidth);
        assertEquals(Float.valueOf(1000f), writing.writingAreaHeight);
        WritingSample sample = pad.writingSample();
        assertEquals(1, sample.strokes.size());
        assertEquals(3, sample.strokes.get(0).points.size());
    }

    @Test
    public void drawingPadKeepsStrokePointWhenOnlyOneAxisLooksLikeNoise() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, downTime, 110L, MotionEvent.ACTION_MOVE, 100.2f, 180f);
        sendTouch(pad, downTime, 120L, MotionEvent.ACTION_UP, 100.3f, 260f);

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertEquals(3, writing.strokes.get(0).points.size());
        assertStrokePoint(writing.strokes.get(0).points.get(1), 100.2f, 180f, 110L);
    }

    @Test
    public void drawingPadCapturesHistoricalMoveSamplesInOrder() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendMoveWithHistory(pad, downTime);
        sendTouch(pad, downTime, 150L, MotionEvent.ACTION_UP, 480f, 520f);

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertStrokePoint(writing.strokes.get(0).points.get(0), 100f, 100f, 100L);
        assertStrokePoint(writing.strokes.get(0).points.get(1), 160f, 180f, 120L);
        assertStrokePoint(writing.strokes.get(0).points.get(2), 240f, 280f, 130L);
        assertStrokePoint(writing.strokes.get(0).points.get(3), 360f, 390f, 140L);
        assertStrokePoint(writing.strokes.get(0).points.get(4), 480f, 520f, 150L);
    }

    @Test
    public void drawingPadReplaySnapshotStartsStopsAndCancelsOnEdit() {
        DrawingPadView pad = laidOutPad();
        AtomicInteger edits = new AtomicInteger();
        pad.setInkEditListener(edits::incrementAndGet);

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 200f, 200f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 300f, 300f);
        assertEquals(1, edits.get());

        pad.captureReplaySnapshot();
        pad.startReplay();
        assertTrue(pad.hasReplaySnapshot());
        assertTrue(pad.isReplayOverlayVisible());
        drawToBitmap(pad);

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f);
        assertFalse(pad.isReplayOverlayVisible());
        assertEquals(2, edits.get());
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 560f, 560f);
    }

    @Test
    public void drawingPadUndoRemovesCommittedStrokesAndReplaySnapshot() {
        DrawingPadView pad = laidOutPad();

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 300f, 300f);
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 600f, 600f);
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 800f, 800f);
        pad.captureReplaySnapshot();
        pad.startReplay();

        assertTrue(pad.canUndoStroke());
        assertTrue(pad.hasReplaySnapshot());

        assertTrue(pad.undoLastStroke());
        assertFalse(pad.hasReplaySnapshot());
        assertTrue(pad.hasInk());
        assertEquals(1, pad.writingSample().strokes.size());

        assertTrue(pad.undoLastStroke());
        assertFalse(pad.hasInk());
        assertFalse(pad.canUndoStroke());
        assertFalse(pad.undoLastStroke());
    }

    @Test
    public void drawingPadBlocksFarStartEvenWhenGuideIsHidden() {
        DrawingPadView pad = laidOutPad();
        AtomicInteger blocked = new AtomicInteger();
        pad.setStrokeBlockedListener(decision -> {
            blocked.incrementAndGet();
            assertEquals("Stay close to stroke 1.", decision.message);
        });
        pad.setGuide(twoStrokeGuide(), new HintState(HintLevel.BLIND, 0, 0), false);

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 950f, 950f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 960f, 960f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 970f, 970f);

        assertFalse(pad.hasInk());
        assertEquals(1, blocked.get());
    }

    @Test
    public void drawingPadBlocksFarMoveWithoutCommittingPartialStroke() {
        DrawingPadView pad = laidOutPad();
        AtomicInteger blocked = new AtomicInteger();
        pad.setStrokeBlockedListener(decision -> blocked.incrementAndGet());
        pad.setGuide(twoStrokeGuide(), HintState.initial(), false);

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 200f, 220f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 220f, 420f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_MOVE, 900f, 500f);
        sendTouch(pad, 100L, 160L, MotionEvent.ACTION_UP, 920f, 540f);

        assertFalse(pad.hasInk());
        assertFalse(pad.canUndoStroke());
        assertEquals(0, pad.writingSample().strokes.size());
        assertEquals(1, blocked.get());

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 200f, 220f);
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_MOVE, 200f, 420f);
        sendTouch(pad, 200L, 240L, MotionEvent.ACTION_UP, 200f, 720f);

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertStrokePoint(writing.strokes.get(0).points.get(0), 200f, 220f, 200L);
    }

    @Test
    public void drawingPadClearReplaySnapshotRemovesStoredReplayAndOverlay() {
        DrawingPadView pad = laidOutPad();

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 200f, 200f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 300f, 300f);
        pad.captureReplaySnapshot();
        pad.startReplay();

        assertTrue(pad.hasReplaySnapshot());
        assertTrue(pad.isReplayOverlayVisible());

        pad.clearReplaySnapshot();

        assertFalse(pad.hasReplaySnapshot());
        assertFalse(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadDrawsGuideAndFallbackWithoutStateRegression() {
        DrawingPadView pad = laidOutPad();
        pad.setTarget("拉");
        pad.setGuide(null, 2, true);
        drawToBitmap(pad);
        assertFalse(pad.hasInk());

        StrokeGuide guide = new StrokeGuide(
                "拉",
                Collections.singletonList(new InkStroke(Arrays.asList(
                        new InkPoint(0.2f, 0.2f, 0L),
                        new InkPoint(0.8f, 0.8f, 10L)
                )))
        );
        pad.setGuide(guide, 1, false);
        drawToBitmap(pad);
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 200f, 200f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 800f, 800f);
        drawToBitmap(pad);
        assertTrue(pad.hasInk());
        assertFalse(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadFallbackOutlineUsesStrongerInkWhenGuideIsRevealed() {
        DrawingPadView pad = laidOutPad();
        pad.setTarget("A");
        pad.setGuide(null, 2, false);
        Bitmap practiceHint = renderToBitmap(pad);

        pad.setGuide(null, 3, true);
        Bitmap revealedHint = renderToBitmap(pad);
        try {
            assertTrue(countDifferentPixels(practiceHint, revealedHint) > 20);
        } finally {
            practiceHint.recycle();
            revealedHint.recycle();
        }
        assertFalse(pad.hasInk());
    }

    @Test
    public void drawingPadHandlesMoveBeforeDownCancelAndReplayNoOps() {
        DrawingPadView pad = laidOutPad();

        pad.startReplay();
        assertFalse(pad.isReplayOverlayVisible());
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_MOVE, 10f, 10f);
        assertFalse(pad.hasInk());

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        drawToBitmap(pad);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_CANCEL, 180f, 220f);
        assertTrue(pad.hasInk());

        pad.clear();
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f);
        sendTouch(pad, 200L, 210L, MotionEvent.ACTION_UP, 500f, 500f);
        pad.captureReplaySnapshot();
        pad.startReplay();
        drawToBitmap(pad);
        assertTrue(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadKeepsActiveStrokeWhenNonActivePointerLifts() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;
        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);

        MotionEvent pointerDown = twoPointerEvent(downTime, 110L, MotionEvent.ACTION_POINTER_DOWN, 1, 0, 1);
        try {
            pad.onTouchEvent(pointerDown);
        } finally {
            pointerDown.recycle();
        }
        MotionEvent nonActivePointerUp = twoPointerEvent(downTime, 120L, MotionEvent.ACTION_POINTER_UP, 1, 0, 1);
        try {
            pad.onTouchEvent(nonActivePointerUp);
        } finally {
            nonActivePointerUp.recycle();
        }
        assertFalse(pad.hasInk());

        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_MOVE, 220f, 240f);
        sendTouch(pad, downTime, 160L, MotionEvent.ACTION_UP, 320f, 360f);
        assertTrue(pad.hasInk());
        assertEquals(3, pad.capturedWriting().strokes.get(0).points.size());
    }

    @Test
    public void drawingPadCommitsStrokeWhenActivePointerLiftsDuringMultiTouch() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;
        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);

        MotionEvent pointerDown = twoPointerEvent(
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
        );
        try {
            pad.onTouchEvent(pointerDown);
        } finally {
            pointerDown.recycle();
        }
        MotionEvent activePointerUp = twoPointerEvent(
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
        );
        try {
            pad.onTouchEvent(activePointerUp);
        } finally {
            activePointerUp.recycle();
        }

        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_MOVE, 700f, 720f);

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertEquals(2, writing.strokes.get(0).points.size());
        assertStrokePoint(writing.strokes.get(0).points.get(0), 100f, 100f, 100L);
        assertStrokePoint(writing.strokes.get(0).points.get(1), 260f, 280f, 120L);
    }

    @Test
    public void drawingPadIgnoresMoveFromMissingActivePointerAndSinglePointGuideHints() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        MotionEvent wrongPointerMove = onePointerEvent(downTime, 120L, MotionEvent.ACTION_MOVE, 9, 600f, 600f);
        try {
            assertTrue(pad.onTouchEvent(wrongPointerMove));
        } finally {
            wrongPointerMove.recycle();
        }
        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_UP, 200f, 200f);

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertEquals(2, writing.strokes.get(0).points.size());
        assertStrokePoint(writing.strokes.get(0).points.get(0), 100f, 100f, 100L);
        assertStrokePoint(writing.strokes.get(0).points.get(1), 200f, 200f, 140L);

        pad.clear();
        pad.setGuide(new StrokeGuide("点", Collections.singletonList(new InkStroke(Collections.singletonList(new InkPoint(0.5f, 0.5f, 0L))))), 0, false);
        drawToBitmap(pad);
        assertFalse(pad.hasInk());

        pad.captureReplaySnapshot();
        drawToBitmap(pad);
        assertFalse(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadUsesVisiblePointerWhenActivePointerIsMissingOnLift() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        sendTouch(pad, downTime, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        MotionEvent wrongPointerUp = onePointerEvent(downTime, 140L, MotionEvent.ACTION_UP, 9, 620f, 640f);
        try {
            assertTrue(pad.onTouchEvent(wrongPointerUp));
        } finally {
            wrongPointerUp.recycle();
        }

        CapturedWriting writing = pad.capturedWriting();
        assertEquals(1, writing.strokes.size());
        assertEquals(2, writing.strokes.get(0).points.size());
        assertStrokePoint(writing.strokes.get(0).points.get(0), 100f, 100f, 100L);
        assertStrokePoint(writing.strokes.get(0).points.get(1), 620f, 640f, 140L);
    }

    @Test
    public void drawingPadIgnoresPointerEndEventsWhenNoStrokeIsActive() {
        DrawingPadView pad = laidOutPad();
        long downTime = 100L;

        MotionEvent pointerUp = twoPointerEvent(downTime, 120L, MotionEvent.ACTION_POINTER_UP, 1, 0, 1);
        try {
            assertTrue(pad.onTouchEvent(pointerUp));
        } finally {
            pointerUp.recycle();
        }
        sendTouch(pad, downTime, 140L, MotionEvent.ACTION_CANCEL, 300f, 300f);

        assertFalse(pad.hasInk());
        assertEquals(0, pad.writingSample().strokes.size());
    }

    @Test
    public void drawingPadRequestsParentInterceptOnlyDuringActiveStroke() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TrackingParent parent = new TrackingParent(context);
        DrawingPadView pad = new DrawingPadView(context);
        parent.addView(pad, new FrameLayout.LayoutParams(1000, 1000));
        parent.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY)
        );
        parent.layout(0, 0, 1000, 1000);

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        assertTrue(parent.lastDisallowIntercept);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 200f, 200f);

        assertFalse(parent.lastDisallowIntercept);
        assertEquals(2, parent.interceptRequests);
    }

    @Test
    public void drawingPadRendersFallbackOutlineOnlyWhenTargetIsAvailable() {
        DrawingPadView pad = laidOutPad();
        pad.setTarget(null);
        pad.setGuide(null, 3, false);
        Bitmap blank = renderToBitmap(pad);

        pad.setTarget("A");
        pad.setGuide(new StrokeGuide("A", Collections.emptyList()), 2, true);
        Bitmap outlined = renderToBitmap(pad);
        try {
            assertTrue(countDifferentPixels(blank, outlined) > 20);
        } finally {
            blank.recycle();
            outlined.recycle();
        }
        assertFalse(pad.hasInk());
    }

    @Test
    public void drawingPadSkipsFallbackOutlineWhenHintsAreDisabled() {
        DrawingPadView pad = laidOutPad();
        pad.setTarget(null);
        pad.setGuide(null, 3, false);
        Bitmap blank = renderToBitmap(pad);

        pad.setTarget("A");
        pad.setGuide(null, 3, false);
        Bitmap hidden = renderToBitmap(pad);
        try {
            assertEquals(0, countDifferentPixels(blank, hidden));
        } finally {
            hidden.recycle();
        }

        pad.setTarget(null);
        pad.setGuide(null, 2, false);
        Bitmap hiddenWithoutTarget = renderToBitmap(pad);
        try {
            assertEquals(0, countDifferentPixels(blank, hiddenWithoutTarget));
        } finally {
            blank.recycle();
            hiddenWithoutTarget.recycle();
        }
        assertFalse(pad.hasInk());
    }

    @Test
    public void drawingPadRendersOnlyVisibleStrokeHints() {
        DrawingPadView pad = laidOutPad();
        StrokeGuide guide = twoStrokeGuide();

        pad.setGuide(guide, new HintState(HintLevel.BLIND, 0, 0), false);
        int blindPixels;
        Bitmap blind = renderToBitmap(pad);
        try {
            blindPixels = countGuidePixels(blind);
        } finally {
            blind.recycle();
        }

        pad.setGuide(guide, HintState.initial(), true);
        Bitmap revealed = renderToBitmap(pad);
        try {
            assertTrue(countGuidePixels(revealed) > blindPixels);
        } finally {
            revealed.recycle();
        }
        assertFalse(pad.hasInk());
    }

    @Test
    public void drawingPadKeepsReplayWhenRevealGuideHasStrokeHintsAndThenHidesForEmptyGuide() {
        DrawingPadView pad = laidOutPad();
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 120f, 140f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 560f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 820f, 860f);
        pad.captureReplaySnapshot();
        pad.startReplay();

        pad.setGuide(twoStrokeGuide(), (HintState) null, true);
        assertTrue(pad.isReplayOverlayVisible());
        Bitmap replaying = renderToBitmap(pad);
        try {
            assertTrue(countBluePixels(replaying) > 0);
        } finally {
            replaying.recycle();
        }

        pad.setGuide(new StrokeGuide("空", Collections.emptyList()), HintState.initial(), true);
        assertFalse(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadReplayRendersCompletedStrokeAfterAnimationDuration() {
        DrawingPadView pad = laidOutPad();
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 500f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 900f, 900f);
        pad.captureReplaySnapshot();
        pad.startReplay();
        android.os.SystemClock.sleep(1000L);

        Bitmap finishedReplay = renderToBitmap(pad);
        try {
            assertTrue(countBluePixels(finishedReplay) > 400);
        } finally {
            finishedReplay.recycle();
        }
        assertTrue(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadReplayRendersCurrentStrokeAndStopsBeforeLaterStrokes() {
        DrawingPadView pad = laidOutPad();
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 180f, 180f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 260f, 260f);
        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 700f, 700f);
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_MOVE, 760f, 760f);
        sendTouch(pad, 200L, 240L, MotionEvent.ACTION_UP, 820f, 820f);
        pad.captureReplaySnapshot();
        pad.startReplay();

        Bitmap partialReplay = renderToBitmap(pad);
        try {
            assertTrue(countBluePixels(partialReplay) > 0);
        } finally {
            partialReplay.recycle();
        }
        assertTrue(pad.isReplayOverlayVisible());
    }

    @Test
    public void drawingPadReplayShowsStartDotBeforeAnyAnimationElapsed() {
        DrawingPadView pad = laidOutPad();
        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f);
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_MOVE, 500f, 500f);
        sendTouch(pad, 100L, 140L, MotionEvent.ACTION_UP, 900f, 900f);
        pad.captureReplaySnapshot();
        pad.startReplayAt(android.os.SystemClock.uptimeMillis() + 10_000L);

        Bitmap firstFrame = renderToBitmap(pad);
        try {
            assertTrue(countBluePixels(firstFrame) > 0);
        } finally {
            firstFrame.recycle();
        }
        assertTrue(pad.isReplayOverlayVisible());
    }

    private static DrawingPadView laidOutPad() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DrawingPadView pad = new DrawingPadView(context);
        pad.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY)
        );
        pad.layout(0, 0, 1000, 1000);
        return pad;
    }

    private static void drawToBitmap(DrawingPadView pad) {
        Bitmap bitmap = renderToBitmap(pad);
        bitmap.recycle();
    }

    private static Bitmap renderToBitmap(DrawingPadView pad) {
        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        pad.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int countBluePixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 2) {
            for (int x = 0; x < bitmap.getWidth(); x += 2) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.blue(pixel) > Color.red(pixel) + 40 && Color.blue(pixel) > Color.green(pixel) + 40) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countGuidePixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 2) {
            for (int x = 0; x < bitmap.getWidth(); x += 2) {
                int pixel = bitmap.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                boolean brownGuide = red < 180 && green < 150 && blue < 130;
                boolean coralGuide = red > 220 && green < 120 && blue < 160;
                if (brownGuide || coralGuide) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDifferentPixels(Bitmap first, Bitmap second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y += 2) {
            for (int x = 0; x < first.getWidth(); x += 2) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static StrokeGuide twoStrokeGuide() {
        return new StrokeGuide(
                "川",
                Arrays.asList(
                        new InkStroke(Arrays.asList(
                                new InkPoint(0.20f, 0.20f, 0L),
                                new InkPoint(0.20f, 0.80f, 10L)
                        )),
                        new InkStroke(Arrays.asList(
                                new InkPoint(0.72f, 0.22f, 0L),
                                new InkPoint(0.72f, 0.82f, 10L)
                        ))
                )
        );
    }

    private static void sendTouch(DrawingPadView pad, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            pad.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void sendMoveWithHistory(DrawingPadView pad, long downTime) {
        MotionEvent event = MotionEvent.obtain(downTime, 120L, MotionEvent.ACTION_MOVE, 160f, 180f, 0);
        try {
            event.addBatch(130L, 240f, 280f, 1f, 1f, 0);
            event.addBatch(140L, 360f, 390f, 1f, 1f, 0);
            pad.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static MotionEvent twoPointerEvent(long downTime, long eventTime, int actionMasked, int actionIndex, int firstId, int secondId) {
        return twoPointerEvent(downTime, eventTime, actionMasked, actionIndex, firstId, secondId, 100f, 100f, 400f, 400f);
    }

    private static MotionEvent onePointerEvent(long downTime, long eventTime, int actionMasked, int pointerId, float x, float y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[]{pointer(pointerId)};
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[]{coords(x, y)};
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
        );
    }

    private static MotionEvent twoPointerEvent(
            long downTime,
            long eventTime,
            int actionMasked,
            int actionIndex,
            int firstId,
            int secondId,
            float firstX,
            float firstY,
            float secondX,
            float secondY
    ) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        properties[0] = pointer(firstId);
        properties[1] = pointer(secondId);
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        coords[0] = coords(firstX, firstY);
        coords[1] = coords(secondX, secondY);
        int action = actionMasked | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
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
        );
    }

    private static MotionEvent.PointerProperties pointer(int id) {
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = id;
        property.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return property;
    }

    private static MotionEvent.PointerCoords coords(float x, float y) {
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = 1f;
        coords.size = 1f;
        return coords;
    }

    private static void assertStrokePoint(CapturedStroke.Point point, float x, float y, long timestamp) {
        assertEquals(x, point.x, 0.001f);
        assertEquals(y, point.y, 0.001f);
        assertEquals(Long.valueOf(timestamp), point.timestampMillis);
    }

    private static final class TrackingParent extends FrameLayout {
        int interceptRequests;
        boolean lastDisallowIntercept;

        TrackingParent(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            interceptRequests++;
            lastDisallowIntercept = disallowIntercept;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }
}
