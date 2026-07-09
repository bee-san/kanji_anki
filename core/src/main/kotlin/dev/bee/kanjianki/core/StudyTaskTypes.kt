package dev.bee.kanjianki.core

/**
 * Public wire-format task names used by scheduler state, review logs, and UI
 * routing.
 */
object StudyTaskTypes {
    const val WRITE_KANJI: String = "write_kanji"
    const val TYPE_MEANING: String = "type_meaning"
    const val SIMILAR_KANJI: String = "similar_kanji"
    const val MEANING_KANJI: String = "meaning_kanji"
    const val KANJI_MEANING: String = "kanji_meaning"
    const val FONT_MEANING: String = "font_meaning"
    const val WORD_READING: String = "word_reading"
    const val KANJI_READING: String = "kanji_reading"

    // Legacy wire-format aliases retained for persisted task memory rows.
    const val TYPING_MEANING: String = "typing_meaning"
    const val WRITING_REMEDIATION: String = "writing_remediation"

    @JvmStatic
    fun forRung(rung: RecordsBase.LadderRung): String {
        return rung.wireName()
    }
}
