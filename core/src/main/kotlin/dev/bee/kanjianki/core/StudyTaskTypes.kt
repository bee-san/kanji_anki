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
    /** Exact full-word kana entry used only as targeted reading repair. */
    const val TYPE_READING: String = "type_reading"
    const val KANJI_READING: String = "kanji_reading"
    const val READING_KANJI: String = "reading_kanji"
    const val SENTENCE_READING: String = "sentence_reading"

    // Legacy wire-format aliases retained for persisted task memory rows.
    const val TYPING_MEANING: String = "typing_meaning"
    const val WRITING_REMEDIATION: String = "writing_remediation"

    // Repair and diagnostic writing wire names used by session routing,
    // persisted task rows, and study copy.
    const val TARGETED_WRITING: String = "targeted_writing"
    const val REPAIR_WRITING: String = "repair_writing"
    const val CONTEXT_WRITING: String = "context_writing"
    const val GUIDED_WRITING: String = "guided_writing"
    const val BLIND_WRITING: String = "blind_writing"
    const val SAMPLED_HANDWRITING: String = "sampled_handwriting"

    @JvmStatic
    fun forRung(rung: RecordsBase.LadderRung): String {
        return rung.wireName()
    }
}
