package dev.bee.kanjianki.core.study

import dev.bee.kanjianki.core.StudyRatings

enum class StudyRating(private val code: String, private val strength: Int) {
    AGAIN(StudyRatings.AGAIN, 0),
    HARD(StudyRatings.HARD, 1),
    GOOD(StudyRatings.GOOD, 2),
    EASY(StudyRatings.EASY, 3),
    ;

    fun code(): String = code

    fun strongerThan(other: StudyRating): Boolean = strength > other.strength

    fun cappedAt(ceiling: StudyRating): StudyRating = if (strongerThan(ceiling)) ceiling else this

    companion object {
        @JvmStatic
        fun fromCode(code: String?): StudyRating {
            if (code == null) {
                return AGAIN
            }
            for (rating in entries) {
                if (rating.code == code) {
                    return rating
                }
            }
            return AGAIN
        }
    }
}
