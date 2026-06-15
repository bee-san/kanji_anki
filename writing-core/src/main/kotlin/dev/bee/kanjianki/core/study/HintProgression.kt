package dev.bee.kanjianki.core.study

class HintProgression {
    fun visibility(state: HintState?, guide: StrokeGuide?): HintVisibility {
        val safeState = state ?: HintState.initial()
        val strokeCount = guide?.strokeCount() ?: 0
        val revealed = minOf(safeState.revealedStrokeCount(), strokeCount)
        return when (safeState.level()) {
            HintLevel.BLIND -> HintVisibility(HintLevel.BLIND, false, false, false, false, false, revealed)
            HintLevel.MINIMAL -> HintVisibility(
                HintLevel.MINIMAL,
                false,
                false,
                false,
                true,
                true,
                minOf(strokeCount, 1 + revealed),
            )

            HintLevel.OUTLINE -> HintVisibility(HintLevel.OUTLINE, false, true, false, true, true, strokeCount)
            HintLevel.TRACE -> HintVisibility(HintLevel.TRACE, true, true, true, true, true, strokeCount)
        }
    }

    fun revealNext(state: HintState?, guide: StrokeGuide?): HintState {
        val safeState = state ?: HintState.initial()
        val strokeCount = guide?.strokeCount() ?: 0
        if (safeState.level() == HintLevel.TRACE) {
            return HintState(safeState.level(), strokeCount, 0)
        }
        if (safeState.level() == HintLevel.OUTLINE || strokeCount <= 0) {
            return HintState(safeState.level().previous(), 0, 0)
        }
        if (safeState.revealedStrokeCount() >= strokeCount) {
            return HintState(safeState.level().previous(), 0, 0)
        }
        return HintState(
            safeState.level(),
            minOf(strokeCount, safeState.revealedStrokeCount() + 1),
            0,
        )
    }

    fun canRevealMoreHelp(state: HintState?, guide: StrokeGuide?): Boolean {
        if (state == null || state.level() == HintLevel.TRACE) {
            return false
        }
        return guide == null ||
            guide.isEmpty() ||
            state.level() == HintLevel.OUTLINE ||
            state.revealedStrokeCount() < guide.strokeCount()
    }

    fun afterReview(state: HintState?, writingPassed: Boolean, hintsUsed: Int): HintState {
        val safeState = state ?: HintState.initial()
        if (!writingPassed) {
            return HintState(safeState.level().previous(), 0, 0)
        }
        if (hintsUsed > 0 || safeState.revealedStrokeCount() > 0) {
            return HintState(safeState.level(), 0, 0)
        }
        return HintState(safeState.level().next(), 0, safeState.consecutivePasses() + 1)
    }

    fun afterWriting(state: HintState?, analysis: WritingAnalysis?): HintState {
        if (analysis == null) {
            return afterReview(state, false, 0)
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE ||
            analysis.status == WritingAnalysis.Status.MODEL_UNAVAILABLE ||
            analysis.status == WritingAnalysis.Status.RECOGNITION_ERROR
        ) {
            val safeState = state ?: HintState.initial()
            return HintState(safeState.level(), 0, 0)
        }
        return afterReview(state, analysis.status == WritingAnalysis.Status.PASS, analysis.hintsUsed())
    }
}
