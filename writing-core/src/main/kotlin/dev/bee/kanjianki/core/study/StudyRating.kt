package dev.bee.kanjianki.core.study

enum class StudyRating(private val code: String, private val strength: Int) {
    AGAIN("again", 0),
    HARD("hard", 1),
    GOOD("good", 2),
    EASY("easy", 3),
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
