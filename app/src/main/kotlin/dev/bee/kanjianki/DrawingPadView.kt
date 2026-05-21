package dev.bee.kanjianki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import dev.bee.kanjianki.core.study.HintPolicy
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.StrokeGuideGuard
import dev.bee.kanjianki.core.study.WritingSample
import dev.bee.kanjianki.study.CapturedStroke
import dev.bee.kanjianki.study.CapturedWriting
import java.util.Objects
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DrawingPadView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val replayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths: MutableList<Path> = ArrayList()
    private val committedStrokes: MutableList<MutableList<CapturedStroke.Point>> = ArrayList()
    private val replayStrokes: MutableList<MutableList<CapturedStroke.Point>> = ArrayList()
    private val currentPoints: MutableList<CapturedStroke.Point> = ArrayList()
    private var current: Path? = null
    private var guide: StrokeGuide? = null
    private var guideLevel = 3
    private var guideState: HintState = HintState.fromWritingLevel(3)
    private var revealGuide = false
    private var replayOverlayVisible = false
    private var replayStartedAtMillis = 0L
    private var inkEditListener: Runnable? = null
    private var strokeBlockedListener: StrokeBlockedListener? = null
    private var target = ""
    private var activePointerId = -1
    private var blockingCurrentStroke = false

    init {
        setBackgroundColor(Color.WHITE)
        paint.color = DRAWING_INK
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        grid.color = Color.rgb(244, 199, 225)
        grid.strokeWidth = 2f
        guidePaint.style = Paint.Style.STROKE
        guidePaint.strokeCap = Paint.Cap.ROUND
        guidePaint.strokeJoin = Paint.Join.ROUND
        markerPaint.style = Paint.Style.FILL
        markerText.textAlign = Paint.Align.CENTER
        markerText.typeface = Typeface.DEFAULT_BOLD
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 5f
        outlinePaint.textAlign = Paint.Align.CENTER
        outlinePaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        replayPaint.color = DRAWING_BLUE
        replayPaint.strokeWidth = 13f
        replayPaint.style = Paint.Style.STROKE
        replayPaint.strokeCap = Paint.Cap.ROUND
        replayPaint.strokeJoin = Paint.Join.ROUND
    }

    fun hasInk(): Boolean {
        return committedStrokes.isNotEmpty()
    }

    fun canUndoStroke(): Boolean {
        return committedStrokes.isNotEmpty()
    }

    fun undoLastStroke(): Boolean {
        if (committedStrokes.isEmpty()) {
            return false
        }
        val last = committedStrokes.size - 1
        committedStrokes.removeAt(last)
        if (paths.isNotEmpty()) {
            paths.removeAt(min(last, paths.size - 1))
        }
        clearReplaySnapshot()
        invalidate()
        return true
    }

    fun clear() {
        paths.clear()
        committedStrokes.clear()
        replayStrokes.clear()
        currentPoints.clear()
        current = null
        activePointerId = -1
        blockingCurrentStroke = false
        stopReplay()
        invalidate()
    }

    fun setTarget(target: String?) {
        this.target = Objects.toString(target, "")
    }

    fun setInkEditListener(listener: Runnable?) {
        inkEditListener = listener
    }

    fun setStrokeBlockedListener(listener: StrokeBlockedListener?) {
        strokeBlockedListener = listener
    }

    fun setGuide(guide: StrokeGuide?, level: Int, revealGuide: Boolean) {
        setGuide(guide, HintState.fromWritingLevel(level), revealGuide)
    }

    fun setGuide(guide: StrokeGuide?, state: HintState?, revealGuide: Boolean) {
        this.guide = guide
        guideState = state ?: HintState.fromWritingLevel(3)
        guideLevel = guideState.level().writingLevel()
        this.revealGuide = revealGuide
        if (!revealGuide || guide == null || guide.isEmpty) {
            replayOverlayVisible = false
            replayStartedAtMillis = 0L
        }
        invalidate()
    }

    fun startReplay() {
        startReplayAt(SystemClock.uptimeMillis())
    }

    fun startReplayAt(startedAtMillis: Long) {
        if (replayStrokes.isEmpty()) {
            return
        }
        replayOverlayVisible = true
        replayStartedAtMillis = startedAtMillis
        postInvalidateOnAnimation()
    }

    fun stopReplay() {
        replayOverlayVisible = false
        replayStartedAtMillis = 0L
        invalidate()
    }

    fun captureReplaySnapshot() {
        replayStrokes.clear()
        for (stroke in committedStrokes) {
            replayStrokes.add(ArrayList(stroke))
        }
    }

    fun clearReplaySnapshot() {
        replayStrokes.clear()
        stopReplay()
    }

    fun hasReplaySnapshot(): Boolean {
        return replayStrokes.isNotEmpty()
    }

    fun isReplayOverlayVisibleForTests(): Boolean {
        return replayOverlayVisible
    }

    fun capturedWriting(): CapturedWriting {
        return CapturedWriting(capturedStrokes(), width.toFloat(), height.toFloat(), "")
    }

    fun writingSample(): WritingSample {
        return CapturedWriting.toWritingSample(capturedStrokes(), width.toFloat(), height.toFloat())
    }

    private fun capturedStrokes(): List<CapturedStroke> {
        val strokes: MutableList<CapturedStroke> = ArrayList()
        for (points in committedStrokes) {
            strokes.add(CapturedStroke(points))
        }
        return strokes
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        canvas.drawLine(width / 2f, 0f, width / 2f, height, grid)
        canvas.drawLine(0f, height / 2f, width, height / 2f, grid)
        canvas.drawLine(0f, height * 0.72f, width, height * 0.72f, grid)
        drawGuide(canvas, width, height)
        drawInk(canvas)
    }

    private fun drawInk(canvas: Canvas) {
        if (replayOverlayVisible) {
            val progress = replayProgress()
            drawReplayStrokes(canvas, progress)
            if (progress < 1f) {
                postInvalidateOnAnimation()
            }
        } else {
            for (path in paths) {
                canvas.drawPath(path, paint)
            }
        }
        current?.let { canvas.drawPath(it, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event)
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event)
                true
            }
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> {
                performClick()
                handleTouchEnd(event)
            }
            MotionEvent.ACTION_CANCEL -> handleTouchEnd(event)
            else -> true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTouchDown(event: MotionEvent): Boolean {
        stopReplay()
        activePointerId = event.getPointerId(0)
        val decision = guideDecision(event.getX(0), event.getY(0))
        if (!decision.allowed) {
            notifyStrokeBlocked(decision)
            current = null
            currentPoints.clear()
            blockingCurrentStroke = true
            requestParentIntercept(true)
            invalidate()
            return true
        }
        inkEditListener?.run()
        requestParentIntercept(false)
        blockingCurrentStroke = false
        current = Path().apply {
            moveTo(event.getX(0), event.getY(0))
        }
        currentPoints.clear()
        appendPoint(event.getX(0), event.getY(0), event.eventTime, false)
        invalidate()
        return true
    }

    private fun handleTouchMove(event: MotionEvent) {
        if (blockingCurrentStroke || current == null) {
            return
        }
        requestParentIntercept(false)
        val pointerIndex = activePointerIndex(event)
        if (pointerIndex < 0) {
            return
        }
        for (i in 0 until event.historySize) {
            if (!appendPointIfAllowed(
                    event.getHistoricalX(pointerIndex, i),
                    event.getHistoricalY(pointerIndex, i),
                    event.getHistoricalEventTime(i),
                    true
                )
            ) {
                invalidate()
                return
            }
        }
        appendPointIfAllowed(event.getX(pointerIndex), event.getY(pointerIndex), event.eventTime, true)
        invalidate()
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        if (current != null && event.getPointerId(event.actionIndex) == activePointerId) {
            if (blockingCurrentStroke) {
                finishBlockedStroke()
            } else {
                finishStroke(event, event.actionIndex)
            }
        }
        return true
    }

    private fun handleTouchEnd(event: MotionEvent): Boolean {
        if (current != null) {
            val pointerIndex = activePointerIndex(event)
            if (blockingCurrentStroke) {
                finishBlockedStroke()
            } else {
                finishStroke(event, if (pointerIndex < 0) 0 else pointerIndex)
            }
        }
        blockingCurrentStroke = false
        activePointerId = -1
        requestParentIntercept(true)
        return true
    }

    private fun requestParentIntercept(allow: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(!allow)
    }

    private fun activePointerIndex(event: MotionEvent): Int {
        return event.findPointerIndex(activePointerId)
    }

    private fun finishStroke(event: MotionEvent, pointerIndex: Int) {
        if (!appendPointIfAllowed(event.getX(pointerIndex), event.getY(pointerIndex), event.eventTime, true)) {
            finishBlockedStroke()
            return
        }
        paths.add(current!!)
        committedStrokes.add(ArrayList(currentPoints))
        currentPoints.clear()
        current = null
        activePointerId = -1
        notifyInkEdited()
        invalidate()
    }

    private fun finishBlockedStroke() {
        currentPoints.clear()
        current = null
        activePointerId = -1
        invalidate()
    }

    private fun appendPointIfAllowed(x: Float, y: Float, timestamp: Long, drawLine: Boolean): Boolean {
        val decision = guideDecision(x, y)
        if (!decision.allowed) {
            blockingCurrentStroke = true
            notifyStrokeBlocked(decision)
            return false
        }
        appendPoint(x, y, timestamp, drawLine)
        return true
    }

    private fun appendPoint(x: Float, y: Float, timestamp: Long, drawLine: Boolean) {
        val last = currentPoints.lastOrNull()
        if (last != null && abs(last.x - x) < 0.5f && abs(last.y - y) < 0.5f) {
            return
        }
        currentPoints.add(CapturedStroke.Point(x, y, timestamp))
        if (drawLine) {
            current!!.lineTo(x, y)
        }
    }

    private fun guideDecision(x: Float, y: Float): StrokeGuideGuard.Decision {
        return StrokeGuideGuard.evaluatePoint(guide, committedStrokes.size, width.toFloat(), height.toFloat(), x, y)
    }

    private fun notifyInkEdited() {
        inkEditListener?.run()
    }

    private fun notifyStrokeBlocked(decision: StrokeGuideGuard.Decision) {
        strokeBlockedListener?.onStrokeBlocked(decision)
    }

    private fun drawGuide(canvas: Canvas, width: Float, height: Float) {
        val guide = guide
        if (guide != null && !guide.isEmpty) {
            drawStrokeHints(canvas, width, height)
            return
        }
        if ((guideLevel < 3 || revealGuide) && target.isNotEmpty()) {
            drawFallbackOutline(canvas, width, height)
        }
    }

    private fun drawStrokeHints(canvas: Canvas, width: Float, height: Float) {
        val hints = HintPolicy.hintsFor(guide, guideState, committedStrokes.size, revealGuide)
        for (hint in hints) {
            drawStrokeHint(canvas, width, height, hint)
        }
    }

    private fun drawStrokeHint(canvas: Canvas, width: Float, height: Float, hint: HintPolicy.StrokeHint) {
        if (!hint.visible || hint.stroke.points.size < 2) {
            return
        }
        val path = Path()
        val first: InkPoint = hint.stroke.points[0]
        path.moveTo(first.x * width, first.y * height)
        for (i in 1 until hint.stroke.points.size) {
            val point: InkPoint = hint.stroke.points[i]
            path.lineTo(point.x * width, point.y * height)
        }
        guidePaint.color = if (hint.current) DRAWING_CORAL else Color.rgb(111, 74, 39)
        guidePaint.alpha = ((if (hint.current) 220 else 160) * hint.alpha).roundToInt()
        guidePaint.strokeWidth = if (hint.current) 14f else 9f
        canvas.drawPath(path, guidePaint)
        drawStartMarker(canvas, first.x * width, first.y * height, hint.strokeIndex + 1, hint.current, hint.numberVisible)
    }

    private fun drawFallbackOutline(canvas: Canvas, width: Float, height: Float) {
        outlinePaint.color = Color.argb(if (revealGuide) 120 else 72, 111, 74, 39)
        outlinePaint.textSize = min(width, height) * 0.62f
        val bounds = Rect()
        outlinePaint.getTextBounds(target, 0, target.length, bounds)
        val outline = Path()
        outlinePaint.getTextPath(target, 0, target.length, width / 2f - bounds.exactCenterX(), height * 0.68f, outline)
        canvas.drawPath(outline, outlinePaint)
    }

    private fun replayProgress(): Float {
        val elapsed = max(0L, SystemClock.uptimeMillis() - replayStartedAtMillis)
        if (elapsed >= REPLAY_DURATION_MILLIS) {
            return 1f
        }
        return max(0f, min(1f, elapsed / REPLAY_DURATION_MILLIS.toFloat()))
    }

    private fun drawReplayStrokes(canvas: Canvas, progress: Float) {
        val position = max(0f, min(1f, progress)) * replayStrokes.size
        val fullStrokeCount = min(replayStrokes.size, floor(position).toInt())
        for (i in replayStrokes.indices) {
            val strokeProgress = when {
                i < fullStrokeCount -> 1f
                i == fullStrokeCount -> position - fullStrokeCount
                else -> break
            }
            drawReplayStroke(canvas, replayStrokes[i], strokeProgress)
        }
    }

    private fun drawReplayStroke(canvas: Canvas, points: List<CapturedStroke.Point>, progress: Float) {
        val first = points[0]
        if (points.size == 1 || progress <= 0.001f) {
            canvas.drawCircle(first.x, first.y, replayPaint.strokeWidth / 2f, replayPaint)
            return
        }
        val segmentPosition = max(0f, min(1f, progress)) * (points.size - 1)
        val lastWholeSegment = min(points.size - 1, floor(segmentPosition).toInt())
        val path = Path()
        path.moveTo(first.x, first.y)
        for (i in 1..lastWholeSegment) {
            val point = points[i]
            path.lineTo(point.x, point.y)
        }
        if (lastWholeSegment < points.size - 1) {
            val from = points[lastWholeSegment]
            val to = points[lastWholeSegment + 1]
            val localProgress = segmentPosition - lastWholeSegment
            path.lineTo(
                from.x + (to.x - from.x) * localProgress,
                from.y + (to.y - from.y) * localProgress
            )
        }
        canvas.drawPath(path, replayPaint)
    }

    private fun drawStartMarker(canvas: Canvas, x: Float, y: Float, number: Int, active: Boolean, numberVisible: Boolean) {
        markerPaint.color = Color.argb(230, 255, 255, 255)
        canvas.drawCircle(x, y, 17f, markerPaint)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 3f
        markerPaint.color = if (active) DRAWING_CORAL else Color.rgb(111, 74, 39)
        canvas.drawCircle(x, y, 17f, markerPaint)
        markerPaint.style = Paint.Style.FILL
        if (!numberVisible) {
            canvas.drawCircle(x, y, if (active) 5f else 3.5f, markerPaint)
            return
        }
        markerText.textSize = 18f
        markerText.color = if (active) DRAWING_CORAL else Color.rgb(111, 74, 39)
        canvas.drawText(number.toString(), x, y - (markerText.descent() + markerText.ascent()) / 2f, markerText)
    }

    fun interface StrokeBlockedListener {
        fun onStrokeBlocked(decision: StrokeGuideGuard.Decision)
    }

    companion object {
        private val DRAWING_INK = Color.rgb(45, 22, 53)
        private val DRAWING_BLUE = Color.rgb(110, 92, 230)
        private val DRAWING_CORAL = Color.rgb(255, 76, 118)
        private const val REPLAY_DURATION_MILLIS = 950L
    }
}
