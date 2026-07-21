package dev.bee.kanjianki.core

object SimilarKanjiChoiceReviewPolicy {
    @JvmStatic
    fun reviewUpdate(
        card: RecordsImportModels.SimilarKanjiChoiceCard?,
        result: RecordsImportModels.SimilarKanjiChoiceResult?,
        nowMillis: Long,
    ): ReviewUpdate {
        val correct = result != null && result.correct
        val correctCount = card?.correctCount ?: 0
        val wrongCount = card?.wrongCount ?: 0
        return if (correct) {
            ReviewUpdate(nowMillis, nowMillis, null, saturatingAddNonNegative(correctCount, 1), null)
        } else {
            ReviewUpdate(nowMillis, 0L, nowMillis, null, saturatingAddNonNegative(wrongCount, 1))
        }
    }

    class ReviewUpdate(
        private val lastReviewedAtMillis: Long,
        private val passedAtMillis: Long,
        private val dueAtMillis: Long?,
        private val correctCount: Int?,
        private val wrongCount: Int?,
    ) {
        fun lastReviewedAtMillis(): Long = lastReviewedAtMillis

        fun passedAtMillis(): Long = passedAtMillis

        fun dueAtMillis(): Long? = dueAtMillis

        fun correctCount(): Int? = correctCount

        fun wrongCount(): Int? = wrongCount

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is ReviewUpdate) {
                return false
            }
            return lastReviewedAtMillis == other.lastReviewedAtMillis &&
                passedAtMillis == other.passedAtMillis &&
                dueAtMillis == other.dueAtMillis &&
                correctCount == other.correctCount &&
                wrongCount == other.wrongCount
        }

        override fun hashCode(): Int {
            var result = lastReviewedAtMillis.hashCode()
            result = 31 * result + passedAtMillis.hashCode()
            result = 31 * result + (dueAtMillis?.hashCode() ?: 0)
            result = 31 * result + (correctCount ?: 0)
            result = 31 * result + (wrongCount ?: 0)
            return result
        }

        override fun toString(): String {
            return "ReviewUpdate[" +
                "lastReviewedAtMillis=$lastReviewedAtMillis, " +
                "passedAtMillis=$passedAtMillis, " +
                "dueAtMillis=$dueAtMillis, " +
                "correctCount=$correctCount, " +
                "wrongCount=$wrongCount]"
        }
    }
}
