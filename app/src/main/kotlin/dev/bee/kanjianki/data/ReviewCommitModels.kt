package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsImportModels

/** Outcome of the single transactional review commit boundary. */
enum class ReviewCommitDisposition {
    APPLIED,
    DUPLICATE,
    STALE,
}

/** Aborts the owning review transaction and maps the result to STALE. */
internal class StaleReviewCommitException : RuntimeException()

class ReviewCommitResult(
    @JvmField val disposition: ReviewCommitDisposition,
    @JvmField val item: RecordsStudyModels.StudyItem?,
) {
    fun applied(): Boolean = disposition == ReviewCommitDisposition.APPLIED

    companion object {
        fun applied(item: RecordsStudyModels.StudyItem): ReviewCommitResult =
            ReviewCommitResult(ReviewCommitDisposition.APPLIED, item)

        fun duplicate(): ReviewCommitResult = ReviewCommitResult(ReviewCommitDisposition.DUPLICATE, null)

        fun stale(): ReviewCommitResult = ReviewCommitResult(ReviewCommitDisposition.STALE, null)
    }
}

/** Immutable timing snapshot prepared by the study session tracker. */
class ReviewTaskTiming(
    @JvmField val taskKey: String,
    @JvmField val kanji: String,
    @JvmField val taskType: String,
    @JvmField val startedAtMillis: Long,
    @JvmField val answeredAtMillis: Long,
    @JvmField val activeElapsedMillis: Long,
    @JvmField val outcome: String,
)

/**
 * Compatibility write for the legacy confusion log. New adaptive evidence is
 * stored on review_log, but the existing miner continues to consume this row.
 */
class ReviewChoiceLog(
    @JvmField val targetKanji: String,
    @JvmField val choiceSignature: String,
    @JvmField val selectedAnswer: String,
    @JvmField val correct: Boolean,
    @JvmField val rung: String,
    @JvmField val reviewedAtMillis: Long,
)

class SimilarChoiceCommit(
    @JvmField val submitted: RecordsImportModels.SimilarKanjiChoiceCard,
    @JvmField val selectedAnswer: String,
    @JvmField val reviewedAtMillis: Long,
)

/**
 * Complete input to one review transaction. [beforeReview.schedulerRevision]
 * is the compare-and-swap expectation; callers cannot provide a separate,
 * contradictory revision.
 */
class ReviewCommitCommand(
    @JvmField val afterReview: RecordsStudyModels.StudyItem,
    @JvmField val request: RecordsSchedulerModels.ReviewRequest,
    @JvmField val appliedRating: String?,
    @JvmField val reviewedAtMillis: Long,
    @JvmField val beforeReview: RecordsStudyModels.StudyItem,
    @JvmField val taskTiming: ReviewTaskTiming? = null,
    @JvmField val choiceLog: ReviewChoiceLog? = null,
    @JvmField val similarChoice: SimilarChoiceCommit? = null,
) {
    @JvmField
    val expectedRevision: Long = beforeReview.schedulerRevision

    init {
        require(request.token.isNotEmpty()) { "A review commit requires a non-empty token" }
        require(beforeReview.kanji == afterReview.kanji) { "Review item identity changed" }
        require(beforeReview.answerSignature == afterReview.answerSignature) { "Review answer signature changed" }
    }

    fun persistedItem(): RecordsStudyModels.StudyItem = afterReview.copyBuilder()
        .schedulerRevision(Math.addExact(expectedRevision, 1L))
        .build()
}
