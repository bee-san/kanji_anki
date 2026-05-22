package dev.bee.kanjianki.core

object RecentMistakePolicy {
    @JvmStatic
    fun boundedLimit(limit: Int): Int {
        return maxOf(1, limit)
    }

    @JvmStatic
    fun mistakeRatings(): Array<String> {
        return arrayOf(StudyRatings.AGAIN, StudyRatings.HARD)
    }

    @JvmStatic
    fun mistake(kanji: String?, rating: String?, reviewedAtMillis: Long): RecentMistake {
        return RecentMistake(kanji.orEmpty(), rating.orEmpty(), reviewedAtMillis)
    }

    @JvmRecord
    data class RecentMistake(
        val kanji: String,
        val rating: String,
        val reviewedAtMillis: Long,
    ) {
        override fun toString(): String {
            return "RecentMistake[kanji=$kanji, rating=$rating, reviewedAtMillis=$reviewedAtMillis]"
        }
    }
}
