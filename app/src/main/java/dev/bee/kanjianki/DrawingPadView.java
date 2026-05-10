package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import dev.bee.kanjianki.core.study.HintPolicy;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;

import java.util.ArrayList;
import java.util.List;

final class DrawingPadView extends View {
    private static final int DRAWING_INK = Color.rgb(45, 22, 53);
    private static final int DRAWING_BLUE = Color.rgb(110, 92, 230);
    private static final int DRAWING_CORAL = Color.rgb(255, 76, 118);
    private static final long REPLAY_DURATION_MILLIS = 950L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint replayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> paths = new ArrayList<>();
    private final List<List<CapturedStroke.Point>> committedStrokes = new ArrayList<>();
    private final List<List<CapturedStroke.Point>> replayStrokes = new ArrayList<>();
    private final List<CapturedStroke.Point> currentPoints = new ArrayList<>();
    private Path current;
    private StrokeGuide guide;
    private int guideLevel = 3;
    private HintState guideState = HintState.fromWritingLevel(3);
    private boolean revealGuide;
    private boolean replayOverlayVisible;
    private long replayStartedAtMillis;
    private Runnable inkEditListener;
    private String target = "";
    private int activePointerId = -1;

    DrawingPadView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        paint.setColor(DRAWING_INK);
        paint.setStrokeWidth(12f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(Color.rgb(244, 199, 225));
        grid.setStrokeWidth(2f);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeCap(Paint.Cap.ROUND);
        guidePaint.setStrokeJoin(Paint.Join.ROUND);
        markerPaint.setStyle(Paint.Style.FILL);
        markerText.setTextAlign(Paint.Align.CENTER);
        markerText.setTypeface(Typeface.DEFAULT_BOLD);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(5f);
        outlinePaint.setTextAlign(Paint.Align.CENTER);
        outlinePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        replayPaint.setColor(DRAWING_BLUE);
        replayPaint.setStrokeWidth(13f);
        replayPaint.setStyle(Paint.Style.STROKE);
        replayPaint.setStrokeCap(Paint.Cap.ROUND);
        replayPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    boolean hasInk() {
        return !committedStrokes.isEmpty();
    }

    void clear() {
        paths.clear();
        committedStrokes.clear();
        replayStrokes.clear();
        currentPoints.clear();
        current = null;
        activePointerId = -1;
        stopReplay();
        invalidate();
    }

    void setTarget(String target) {
        this.target = target == null ? "" : target;
    }

    void setInkEditListener(Runnable listener) {
        this.inkEditListener = listener;
    }

    void setGuide(StrokeGuide guide, int level, boolean revealGuide) {
        setGuide(guide, HintState.fromWritingLevel(level), revealGuide);
    }

    void setGuide(StrokeGuide guide, HintState state, boolean revealGuide) {
        this.guide = guide;
        this.guideState = state == null ? HintState.fromWritingLevel(3) : state;
        this.guideLevel = guideState.level().writingLevel();
        this.revealGuide = revealGuide;
        if (!revealGuide || guide == null || guide.isEmpty()) {
            replayOverlayVisible = false;
            replayStartedAtMillis = 0L;
        }
        invalidate();
    }

    void startReplay() {
        if (replayStrokes.isEmpty()) {
            return;
        }
        replayOverlayVisible = true;
        replayStartedAtMillis = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void stopReplay() {
        replayOverlayVisible = false;
        replayStartedAtMillis = 0L;
        invalidate();
    }

    void captureReplaySnapshot() {
        replayStrokes.clear();
        for (List<CapturedStroke.Point> stroke : committedStrokes) {
            replayStrokes.add(new ArrayList<>(stroke));
        }
    }

    void clearReplaySnapshot() {
        replayStrokes.clear();
        stopReplay();
    }

    boolean hasReplaySnapshot() {
        return !replayStrokes.isEmpty();
    }

    boolean isReplayOverlayVisibleForTests() {
        return replayOverlayVisible;
    }

    CapturedWriting capturedWriting() {
        List<CapturedStroke> strokes = new ArrayList<>();
        for (List<CapturedStroke.Point> points : committedStrokes) {
            strokes.add(new CapturedStroke(points));
        }
        return new CapturedWriting(strokes, (float) getWidth(), (float) getHeight(), "");
    }

    WritingSample writingSample() {
        List<InkStroke> strokes = new ArrayList<>();
        for (List<CapturedStroke.Point> points : committedStrokes) {
            List<InkPoint> inkPoints = new ArrayList<>();
            for (CapturedStroke.Point point : points) {
                inkPoints.add(new InkPoint(point.x, point.y, point.timestampMillis == null ? 0L : point.timestampMillis));
            }
            strokes.add(new InkStroke(inkPoints));
        }
        return new WritingSample(strokes, (float) getWidth(), (float) getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        canvas.drawLine(width / 2f, 0, width / 2f, height, grid);
        canvas.drawLine(0, height / 2f, width, height / 2f, grid);
        canvas.drawLine(0, height * 0.72f, width, height * 0.72f, grid);
        drawGuide(canvas, width, height);
        drawInk(canvas);
    }

    private void drawInk(Canvas canvas) {
        if (replayOverlayVisible) {
            float progress = replayProgress();
            drawReplayStrokes(canvas, progress);
            if (progress < 1f) {
                postInvalidateOnAnimation();
            }
        } else {
            for (Path path : paths) {
                canvas.drawPath(path, paint);
            }
        }
        if (current != null) {
            canvas.drawPath(current, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(event);
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(event);
            case MotionEvent.ACTION_POINTER_UP:
                return handlePointerUp(event);
            case MotionEvent.ACTION_UP:
                performClick();
                return handleTouchEnd(event);
            case MotionEvent.ACTION_CANCEL:
                return handleTouchEnd(event);
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean handleTouchDown(MotionEvent event) {
        stopReplay();
        if (inkEditListener != null) {
            inkEditListener.run();
        }
        requestParentIntercept(false);
        activePointerId = event.getPointerId(0);
        current = new Path();
        current.moveTo(event.getX(0), event.getY(0));
        currentPoints.clear();
        appendPoint(event.getX(0), event.getY(0), event.getEventTime(), false);
        invalidate();
        return true;
    }

    private boolean handleTouchMove(MotionEvent event) {
        if (current == null) {
            return true;
        }
        requestParentIntercept(false);
        int pointerIndex = activePointerIndex(event);
        if (pointerIndex < 0) {
            return true;
        }
        for (int i = 0; i < event.getHistorySize(); i++) {
            appendPoint(event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), event.getHistoricalEventTime(i), true);
        }
        appendPoint(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), true);
        invalidate();
        return true;
    }

    private boolean handlePointerUp(MotionEvent event) {
        if (current != null && event.getPointerId(event.getActionIndex()) == activePointerId) {
            finishStroke(event, event.getActionIndex());
        }
        return true;
    }

    private boolean handleTouchEnd(MotionEvent event) {
        if (current != null) {
            int pointerIndex = activePointerIndex(event);
            finishStroke(event, pointerIndex < 0 ? 0 : pointerIndex);
        }
        requestParentIntercept(true);
        return true;
    }

    private void requestParentIntercept(boolean allow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allow);
        }
    }

    private int activePointerIndex(MotionEvent event) {
        if (activePointerId < 0) {
            return event.getPointerCount() == 0 ? -1 : 0;
        }
        return event.findPointerIndex(activePointerId);
    }

    private void finishStroke(MotionEvent event, int pointerIndex) {
        if (pointerIndex >= 0 && pointerIndex < event.getPointerCount()) {
            appendPoint(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), true);
        }
        if (!currentPoints.isEmpty()) {
            paths.add(current);
            committedStrokes.add(new ArrayList<>(currentPoints));
        }
        currentPoints.clear();
        current = null;
        activePointerId = -1;
        invalidate();
    }

    private void appendPoint(float x, float y, long timestamp, boolean drawLine) {
        CapturedStroke.Point last = currentPoints.isEmpty() ? null : currentPoints.get(currentPoints.size() - 1);
        if (last != null && Math.abs(last.x - x) < 0.5f && Math.abs(last.y - y) < 0.5f) {
            return;
        }
        currentPoints.add(new CapturedStroke.Point(x, y, timestamp));
        if (drawLine && current != null) {
            current.lineTo(x, y);
        }
    }

    private void drawGuide(Canvas canvas, float width, float height) {
        if (guide != null && !guide.isEmpty()) {
            drawStrokeHints(canvas, width, height);
            return;
        }
        if ((guideLevel < 3 || revealGuide) && !target.isEmpty()) {
            drawFallbackOutline(canvas, width, height);
        }
    }

    private void drawStrokeHints(Canvas canvas, float width, float height) {
        List<HintPolicy.StrokeHint> hints = HintPolicy.hintsFor(guide, guideState, committedStrokes.size(), revealGuide);
        for (HintPolicy.StrokeHint hint : hints) {
            drawStrokeHint(canvas, width, height, hint);
        }
    }

    private void drawStrokeHint(Canvas canvas, float width, float height, HintPolicy.StrokeHint hint) {
        if (!hint.visible || hint.stroke.points.size() < 2) {
            return;
        }
        Path path = new Path();
        InkPoint first = hint.stroke.points.get(0);
        path.moveTo(first.x * width, first.y * height);
        for (int i = 1; i < hint.stroke.points.size(); i++) {
            InkPoint point = hint.stroke.points.get(i);
            path.lineTo(point.x * width, point.y * height);
        }
        guidePaint.setColor(hint.current ? DRAWING_CORAL : Color.rgb(111, 74, 39));
        guidePaint.setAlpha(Math.round((hint.current ? 220 : 160) * hint.alpha));
        guidePaint.setStrokeWidth(hint.current ? 14f : 9f);
        canvas.drawPath(path, guidePaint);
        drawStartMarker(canvas, first.x * width, first.y * height, hint.strokeIndex + 1, hint.current, hint.numberVisible);
    }

    private void drawFallbackOutline(Canvas canvas, float width, float height) {
        outlinePaint.setColor(Color.argb(revealGuide ? 120 : 72, 111, 74, 39));
        outlinePaint.setTextSize(Math.min(width, height) * 0.62f);
        Rect bounds = new Rect();
        outlinePaint.getTextBounds(target, 0, target.length(), bounds);
        Path outline = new Path();
        outlinePaint.getTextPath(target, 0, target.length(), width / 2f - bounds.exactCenterX(), height * 0.68f, outline);
        canvas.drawPath(outline, outlinePaint);
    }

    private float replayProgress() {
        if (!replayOverlayVisible || replayStartedAtMillis <= 0L) {
            return 1f;
        }
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - replayStartedAtMillis);
        if (elapsed >= REPLAY_DURATION_MILLIS) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, elapsed / (float) REPLAY_DURATION_MILLIS));
    }

    private void drawReplayStrokes(Canvas canvas, float progress) {
        if (replayStrokes.isEmpty()) {
            return;
        }
        float position = Math.max(0f, Math.min(1f, progress)) * replayStrokes.size();
        int fullStrokeCount = Math.min(replayStrokes.size(), (int) Math.floor(position));
        for (int i = 0; i < replayStrokes.size(); i++) {
            float strokeProgress;
            if (i < fullStrokeCount) {
                strokeProgress = 1f;
            } else if (i == fullStrokeCount) {
                strokeProgress = position - fullStrokeCount;
            } else {
                break;
            }
            drawReplayStroke(canvas, replayStrokes.get(i), strokeProgress);
        }
    }

    private void drawReplayStroke(Canvas canvas, List<CapturedStroke.Point> points, float progress) {
        if (points.isEmpty()) {
            return;
        }
        CapturedStroke.Point first = points.get(0);
        if (points.size() == 1 || progress <= 0.001f) {
            canvas.drawCircle(first.x, first.y, replayPaint.getStrokeWidth() / 2f, replayPaint);
            return;
        }
        float segmentPosition = Math.max(0f, Math.min(1f, progress)) * (points.size() - 1);
        int lastWholeSegment = Math.min(points.size() - 1, (int) Math.floor(segmentPosition));
        Path path = new Path();
        path.moveTo(first.x, first.y);
        for (int i = 1; i <= lastWholeSegment; i++) {
            CapturedStroke.Point point = points.get(i);
            path.lineTo(point.x, point.y);
        }
        if (lastWholeSegment < points.size() - 1) {
            CapturedStroke.Point from = points.get(lastWholeSegment);
            CapturedStroke.Point to = points.get(lastWholeSegment + 1);
            float localProgress = segmentPosition - lastWholeSegment;
            path.lineTo(
                    from.x + ((to.x - from.x) * localProgress),
                    from.y + ((to.y - from.y) * localProgress)
            );
        }
        canvas.drawPath(path, replayPaint);
    }

    private void drawStartMarker(Canvas canvas, float x, float y, int number, boolean active, boolean numberVisible) {
        markerPaint.setColor(Color.argb(230, 255, 255, 255));
        canvas.drawCircle(x, y, 17f, markerPaint);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(3f);
        markerPaint.setColor(active ? DRAWING_CORAL : Color.rgb(111, 74, 39));
        canvas.drawCircle(x, y, 17f, markerPaint);
        markerPaint.setStyle(Paint.Style.FILL);
        if (!numberVisible) {
            canvas.drawCircle(x, y, active ? 5f : 3.5f, markerPaint);
            return;
        }
        markerText.setTextSize(18f);
        markerText.setColor(active ? DRAWING_CORAL : Color.rgb(111, 74, 39));
        canvas.drawText(Integer.toString(number), x, y - (markerText.descent() + markerText.ascent()) / 2f, markerText);
    }
}
