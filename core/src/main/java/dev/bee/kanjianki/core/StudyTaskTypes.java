package dev.bee.kanjianki.core;

/**
 * Public wire-format task names used by scheduler state, review logs, and UI
 * routing.
 */
public final class StudyTaskTypes {
    public static final String WRITE_KANJI = "write_kanji";
    public static final String TYPE_MEANING = "type_meaning";
    public static final String SIMILAR_KANJI = "similar_kanji";
    public static final String MEANING_KANJI = "meaning_kanji";
    public static final String KANJI_MEANING = "kanji_meaning";
    public static final String FONT_MEANING = "font_meaning";
    public static final String WORD_READING = "word_reading";

    // Legacy wire-format aliases retained for persisted task memory rows.
    public static final String TYPING_MEANING = "typing_meaning";
    public static final String WRITING_REMEDIATION = "writing_remediation";

    private StudyTaskTypes() {
    }

    public static String forRung(RecordsBase.LadderRung rung) {
        return rung.wireName();
    }
}
