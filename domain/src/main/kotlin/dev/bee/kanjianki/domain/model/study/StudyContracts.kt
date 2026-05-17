package dev.bee.kanjianki.domain.model.study

enum class StudyRung(
    val wireName: String,
    val alwaysAvailable: Boolean,
) {
    WRITE_KANJI("write_kanji", true),
    SIMILAR_KANJI("similar_kanji", false),
    TYPE_MEANING("type_meaning", true),
    MEANING_KANJI("meaning_kanji", true),
    KANJI_MEANING("kanji_meaning", true),
    FONT_MEANING("font_meaning", true),
    WORD_READING("word_reading", true);

    companion object {
        val defaultOrder: List<StudyRung> = listOf(
            WRITE_KANJI,
            SIMILAR_KANJI,
            TYPE_MEANING,
            MEANING_KANJI,
            KANJI_MEANING,
            FONT_MEANING,
            WORD_READING,
        )

        val defaultEnabled: Set<StudyRung> = defaultOrder.toSet() - MEANING_KANJI

        fun fromWireName(wireName: String): StudyRung =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown study rung: $wireName")
    }
}

enum class StudyPhase(val wireName: String) {
    NEW_LEARNING("new_learning"),
    REVIEW("review"),
    RELEARNING("relearning");

    companion object {
        fun fromWireName(wireName: String): StudyPhase =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown study phase: $wireName")
    }
}

enum class StudyRating(val wireName: String) {
    AGAIN("again"),
    HARD("hard"),
    GOOD("good"),
    EASY("easy");

    val countsAsLadderPass: Boolean
        get() = this != AGAIN

    companion object {
        fun fromWireName(wireName: String): StudyRating =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown study rating: $wireName")
    }
}
