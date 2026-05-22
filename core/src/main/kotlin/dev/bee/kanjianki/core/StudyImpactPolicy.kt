package dev.bee.kanjianki.core

object StudyImpactPolicy {
    @JvmStatic
    fun summarize(
        totalReviews: Int,
        distinctReviewedKanji: Int,
        writingRequired: Int,
        writingPassed: Int,
        writingFailed: Int,
        manualOverrides: Int,
    ): Impact {
        return Impact(
            totalReviews,
            distinctReviewedKanji,
            writingRequired,
            writingPassed,
            writingFailed,
            manualOverrides,
        )
    }

    class Impact(
        private val totalReviews: Int,
        private val distinctReviewedKanji: Int,
        private val writingRequired: Int,
        private val writingPassed: Int,
        private val writingFailed: Int,
        private val manualOverrides: Int,
    ) {
        fun totalReviews(): Int = totalReviews

        fun distinctReviewedKanji(): Int = distinctReviewedKanji

        fun writingRequired(): Int = writingRequired

        fun writingPassed(): Int = writingPassed

        fun writingFailed(): Int = writingFailed

        fun manualOverrides(): Int = manualOverrides
    }
}
