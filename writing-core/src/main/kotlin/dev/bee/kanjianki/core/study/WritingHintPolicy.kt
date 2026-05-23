package dev.bee.kanjianki.core.study

class WritingHintPolicy private constructor() {
    companion object {
        @JvmStatic
        fun initialHintState(
            writingLevel: Int,
            totalReviews: Int,
            learningStep: Int,
            targetedWriting: Boolean,
        ): HintState {
            val stored = maxOf(HintLevel.TRACE.writingLevel(), minOf(HintLevel.BLIND.writingLevel(), writingLevel))
            if (targetedWriting || totalReviews == 0 || learningStep == 0) {
                return HintState.fromWritingLevel(minOf(stored, HintLevel.OUTLINE.writingLevel()))
            }
            return HintState.fromWritingLevel(stored)
        }
    }
}
