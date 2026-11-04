package dev.bee.kanjianki.presentation

/**
 * Handwriting captured by the shared ink canvas, as portable data.
 *
 * `writing-core`'s `InkStroke`/`InkPoint` stay the evaluation input, but they live in
 * a JVM module a leaf feature cannot depend on, and they carry pixel coordinates tied
 * to one canvas size. This is the presentation-side shape ADR 0005 calls for: strokes
 * of normalized points a host adapts to `writing-core` for recognition (on Android)
 * or renders as a guide/practice trace (on desktop, which has no recognizer).
 *
 * Coordinates are normalized to 0..1 over the canvas, so the same captured ink means
 * the same shape at any size or DPI — the host multiplies by the live canvas extent
 * when drawing and when handing strokes to the recognizer.
 */
data class CapturedInk(
    val strokes: List<InkStroke> = emptyList(),
) {
    val isEmpty: Boolean
        get() = strokes.all { it.points.isEmpty() }

    /** The captured ink with [stroke] appended, for committing a finished stroke. */
    fun withStroke(stroke: InkStroke): CapturedInk =
        if (stroke.points.isEmpty()) this else copy(strokes = strokes + stroke)

    /** The captured ink with its last stroke removed, for undo. */
    fun withoutLastStroke(): CapturedInk =
        if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1))

    companion object {
        val EMPTY: CapturedInk = CapturedInk()
    }
}

/** One pen-down-to-pen-up stroke, its points in capture order. */
data class InkStroke(
    val points: List<InkPoint> = emptyList(),
)

/**
 * One sampled point, normalized to 0..1 over the canvas, with a capture timestamp.
 *
 * The timestamp is relative to the stroke's start rather than a wall clock — the
 * recognizer wants stroke dynamics, not the time of day, and a relative offset is the
 * same whether captured now or replayed from a restored attempt.
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
) {
    init {
        require(timestampMillis >= 0L) { "an ink timestamp is a non-negative offset" }
    }
}
