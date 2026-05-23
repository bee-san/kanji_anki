package dev.bee.kanjianki.core

object StudyReviewRequestPolicy {
    @JvmStatic
    fun from(
        session: RecordsSchedulerModels.StudySession,
        writingOutcome: WritingOutcome?,
        hintsUsed: Int,
        rating: String?,
        override: Boolean,
    ): MappedReview {
        val item = session.item ?: throw NullPointerException("session.item")
        val mappedRating = applyRequestedRating(rating, session.writingRequired, writingOutcome, override)
        val passed = !session.writingRequired || (writingOutcome != null && writingOutcome.writingPassed)
        val cleanWriting = writingOutcome != null && writingOutcome.cleanWriting
        val request = RecordsSchedulerModels.ReviewRequest(
            item.kanji,
            session.token,
            mappedRating,
            session.writingRequired,
            passed,
            cleanWriting,
            override,
            hintsUsed,
            session.taskType,
            item.answerSignature,
            session.prompt,
        )
        return MappedReview.create(request, mappedRating)
    }

    @JvmStatic
    fun writingOutcome(writingPassed: Boolean, cleanWriting: Boolean, maxAllowedRating: String?): WritingOutcome {
        return WritingOutcome.create(writingPassed, cleanWriting, StudyRatings.normalize(maxAllowedRating))
    }

    private fun applyRequestedRating(
        requestedRating: String?,
        writingRequired: Boolean,
        writingOutcome: WritingOutcome?,
        manualOverride: Boolean,
    ): String {
        val requested = StudyRatings.normalize(requestedRating)
        if (!writingRequired || manualOverride) {
            return requested
        }
        if (writingOutcome == null || !writingOutcome.writingPassed) {
            return StudyRatings.AGAIN
        }
        return capAt(requested, writingOutcome.maxAllowedRating)
    }

    private fun capAt(requested: String, ceiling: String): String {
        return if (strength(requested) > strength(ceiling)) ceiling else requested
    }

    private fun strength(rating: String?): Int {
        return when (StudyRatings.normalize(rating)) {
            StudyRatings.HARD -> 1
            StudyRatings.GOOD -> 2
            StudyRatings.EASY -> 3
            else -> 0
        }
    }

    class WritingOutcome private constructor(
        val writingPassed: Boolean,
        val cleanWriting: Boolean,
        val maxAllowedRating: String,
    ) {
        companion object {
            @JvmSynthetic
            internal fun create(
                writingPassed: Boolean,
                cleanWriting: Boolean,
                maxAllowedRating: String,
            ): WritingOutcome = WritingOutcome(writingPassed, cleanWriting, maxAllowedRating)
        }
    }

    class MappedReview private constructor(
        private val request: RecordsSchedulerModels.ReviewRequest,
        private val ratingCode: String,
    ) {
        fun request(): RecordsSchedulerModels.ReviewRequest = request

        fun ratingCode(): String = ratingCode

        companion object {
            @JvmSynthetic
            internal fun create(
                request: RecordsSchedulerModels.ReviewRequest,
                ratingCode: String,
            ): MappedReview = MappedReview(request, ratingCode)
        }
    }
}
