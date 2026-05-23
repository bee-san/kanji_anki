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

    class RecentMistake(kanji: String?, rating: String?, reviewedAtMillis: Long) {
        private val kanji = kanji.orEmpty()
        private val rating = rating.orEmpty()
        private val reviewedAtMillis = reviewedAtMillis

        fun kanji(): String = kanji

        fun rating(): String = rating

        fun reviewedAtMillis(): Long = reviewedAtMillis

        override fun equals(other: Any?): Boolean {
            return other is RecentMistake &&
                kanji == other.kanji &&
                rating == other.rating &&
                reviewedAtMillis == other.reviewedAtMillis
        }

        override fun hashCode(): Int {
            var result = kanji.hashCode()
            result = 31 * result + rating.hashCode()
            result = 31 * result + reviewedAtMillis.hashCode()
            return result
        }

        override fun toString(): String {
            return "RecentMistake[kanji=$kanji, rating=$rating, reviewedAtMillis=$reviewedAtMillis]"
        }
    }
}
