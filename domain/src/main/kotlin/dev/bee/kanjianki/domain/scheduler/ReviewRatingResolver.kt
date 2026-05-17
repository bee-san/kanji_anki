package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung

class ReviewRatingResolver {
    fun resolve(
        request: StudyReviewRequest,
        rung: StudyRung,
    ): ResolvedReviewRating {
        val rating = when {
            rung == StudyRung.WRITE_KANJI && request.manualOverride -> StudyRating.HARD
            request.writingRequired && !request.writingPassed && !request.manualOverride -> StudyRating.AGAIN
            else -> request.rating
        }
        return ResolvedReviewRating(
            rating = rating,
            cleanWritingPass = request.cleanWritingPassFor(rung),
            failedWriting = request.failedWritingFor(rung),
        )
    }

    private fun StudyReviewRequest.cleanWritingPassFor(rung: StudyRung): Boolean =
        rung == StudyRung.WRITE_KANJI &&
            writingRequired &&
            !manualOverride &&
            writingPassed &&
            writingClean &&
            hintsUsed <= 0

    private fun StudyReviewRequest.failedWritingFor(rung: StudyRung): Boolean =
        rung == StudyRung.WRITE_KANJI &&
            writingRequired &&
            !manualOverride &&
            !writingPassed
}

data class StudyReviewRequest(
    val kanji: String,
    val rating: StudyRating,
    val token: String = "",
    val writingRequired: Boolean = false,
    val writingPassed: Boolean = true,
    val writingClean: Boolean = false,
    val hintsUsed: Int = 0,
    val manualOverride: Boolean = false,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(hintsUsed >= 0) { "hintsUsed must not be negative" }
    }
}

data class ResolvedReviewRating(
    val rating: StudyRating,
    val cleanWritingPass: Boolean,
    val failedWriting: Boolean,
)
