package dev.bee.kanjianki.core

/** Public scheduler rating wire names. */
class StudyRatings private constructor() {
    companion object {
        const val AGAIN: String = "again"
        const val HARD: String = "hard"
        const val GOOD: String = "good"
        const val EASY: String = "easy"

        @JvmStatic
        fun normalize(rating: String?): String {
            return when (rating) {
                AGAIN, HARD, GOOD, EASY -> rating
                else -> AGAIN
            }
        }
    }
}
