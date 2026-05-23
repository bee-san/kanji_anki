package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class HintPolicy private constructor() {
    class StrokeHint {
        @JvmField val strokeIndex: Int
        @JvmField val visible: Boolean
        @JvmField val alpha: Float
        @JvmField val numberVisible: Boolean
        @JvmField val current: Boolean
        @JvmField val stroke: InkStroke?

        constructor(strokeIndex: Int, visible: Boolean, alpha: Float, current: Boolean, stroke: InkStroke?) :
            this(strokeIndex, visible, alpha, true, current, stroke)

        constructor(
            strokeIndex: Int,
            visible: Boolean,
            alpha: Float,
            numberVisible: Boolean,
            current: Boolean,
            stroke: InkStroke?,
        ) {
            this.strokeIndex = strokeIndex
            this.visible = visible
            this.alpha = alpha
            this.numberVisible = numberVisible
            this.current = current
            this.stroke = stroke
        }
    }

    private class StrokeHintVisibility(
        val visible: Boolean,
        val alpha: Float,
        val numberVisible: Boolean,
    )

    companion object {
        @JvmStatic
        fun hintsFor(guide: StrokeGuide?, writingLevel: Int, completedStrokes: Int, reveal: Boolean): List<StrokeHint> {
            return hintsFor(guide, HintState.fromWritingLevel(writingLevel), completedStrokes, reveal)
        }

        @JvmStatic
        fun hintsFor(guide: StrokeGuide?, state: HintState?, completedStrokes: Int, reveal: Boolean): List<StrokeHint> {
            if (guide == null || guide.strokes.isEmpty()) {
                return Collections.emptyList()
            }
            val hints = ArrayList<StrokeHint>()
            val safeState = state ?: HintState.initial()
            val current = maxOf(0, minOf(completedStrokes, guide.strokeCount() - 1))
            for (index in 0 until guide.strokeCount()) {
                val visibility = visibilityFor(safeState, index, current, guide.strokeCount(), reveal)
                hints.add(
                    StrokeHint(
                        index,
                        visibility.visible,
                        visibility.alpha,
                        visibility.numberVisible,
                        index == current,
                        guide.strokes[index],
                    ),
                )
            }
            return hints
        }

        private fun visibilityFor(
            state: HintState,
            index: Int,
            current: Int,
            strokeCount: Int,
            reveal: Boolean,
        ): StrokeHintVisibility {
            if (reveal) {
                return StrokeHintVisibility(true, if (index == current) 0.95f else 0.42f, state.level() == HintLevel.TRACE)
            }
            return when (state.level()) {
                HintLevel.TRACE -> StrokeHintVisibility(true, if (index == current) 0.95f else 0.62f, true)
                HintLevel.OUTLINE -> StrokeHintVisibility(true, if (index == current) 0.9f else 0.20f, false)
                HintLevel.MINIMAL -> minimalVisibility(state, index, current, strokeCount)
                HintLevel.BLIND -> blindVisibility(state, index, current, strokeCount)
            }
        }

        private fun minimalVisibility(
            state: HintState,
            index: Int,
            current: Int,
            strokeCount: Int,
        ): StrokeHintVisibility {
            if (index == current) {
                return StrokeHintVisibility(true, 0.86f, false)
            }
            val revealed = index > current && index <= minOf(strokeCount - 1, current + state.revealedStrokeCount())
            return if (revealed) StrokeHintVisibility(true, 0.58f, false) else StrokeHintVisibility(false, 0f, false)
        }

        private fun blindVisibility(
            state: HintState,
            index: Int,
            current: Int,
            strokeCount: Int,
        ): StrokeHintVisibility {
            val revealed = index >= current && index < minOf(strokeCount, current + state.revealedStrokeCount())
            val alpha = if (index == current) 0.86f else 0.58f
            return if (revealed) StrokeHintVisibility(true, alpha, false) else StrokeHintVisibility(false, 0f, false)
        }
    }
}
