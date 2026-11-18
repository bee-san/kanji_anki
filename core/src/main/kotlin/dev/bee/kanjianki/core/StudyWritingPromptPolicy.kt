package dev.bee.kanjianki.core

/**
 * Builds the recall cue for a `write_kanji` card: the learner writes the
 * missing kanji from memory inside real context, never from a visible answer.
 *
 * Preference order:
 * 1. [Kind.SENTENCE] — a mined sentence with the target kanji blanked, plus the
 *    blanked word, its reading and its meaning.
 * 2. [Kind.WORD] — an attested word with the target kanji blanked, plus the
 *    reading and meaning.
 * 3. [Kind.MEANING] — no usable example: the kanji meaning and reading only.
 *
 * Every occurrence of the target kanji is blanked so the cue can never show
 * the answer.
 */
object StudyWritingPromptPolicy {
    const val BLANK: String = ReadingKanjiChoicePlanner.BLANK
    private const val MAX_MEANING_CHARS = 72

    enum class Kind { SENTENCE, WORD, MEANING }

    data class Prompt(
        @JvmField val kind: Kind,
        /** Sentence with the target blanked; empty unless [Kind.SENTENCE]. */
        @JvmField val sentence: String,
        /** Word with the target blanked; empty for [Kind.MEANING]. */
        @JvmField val word: String,
        @JvmField val reading: String,
        @JvmField val meaning: String,
    ) {
        /** True when the cue contains a blank the learner fills in. */
        fun hasBlank(): Boolean = kind != Kind.MEANING
    }

    /**
     * @param kanjiMeaning the canonical meaning of the target kanji, used only
     *   for the [Kind.MEANING] fallback.
     */
    @JvmStatic
    fun build(session: RecordsSchedulerModels.StudySession?, kanjiMeaning: String?): Prompt {
        val row = session?.row
        val kanji = session?.item?.kanji.orEmpty()
        if (row == null || kanji.isEmpty()) {
            return meaningPrompt(kanjiMeaning, row?.reading.orEmpty())
        }
        val sentenceExample = sentenceExample(row, kanji)
        if (sentenceExample != null) {
            return Prompt(
                Kind.SENTENCE,
                blank(sentenceExample.sentence.trim(), kanji),
                blank(sentenceExample.expression, kanji),
                sentenceExample.reading,
                exampleMeaning(sentenceExample, row),
            )
        }
        val wordExample = wordExample(row, kanji)
        if (wordExample != null) {
            return Prompt(
                Kind.WORD,
                "",
                blank(wordExample.expression, kanji),
                wordExample.reading,
                exampleMeaning(wordExample, row),
            )
        }
        return meaningPrompt(kanjiMeaning, row.reading)
    }

    private fun meaningPrompt(kanjiMeaning: String?, reading: String): Prompt {
        return Prompt(Kind.MEANING, "", "", reading, kanjiMeaning.orEmpty())
    }

    /**
     * The sentence_reading selection when it is usable as a cloze, else any
     * other example whose sentence and word both contain the target.
     */
    private fun sentenceExample(
        row: RecordsImportModels.DashboardRow,
        kanji: String,
    ): RecordsImportModels.Example? {
        val preferred = StudyExampleSelector.sentenceReadingExample(row)
        if (preferred != null && isSentenceCloze(preferred, kanji)) {
            return preferred
        }
        return row.examples.firstOrNull { isSentenceCloze(it, kanji) }
    }

    private fun wordExample(
        row: RecordsImportModels.DashboardRow,
        kanji: String,
    ): RecordsImportModels.Example? {
        val preferred = StudyExampleSelector.wordReadingExample(row)
        if (preferred != null && isWordCloze(preferred, kanji)) {
            return preferred
        }
        return row.examples.firstOrNull { isWordCloze(it, kanji) }
    }

    private fun isSentenceCloze(example: RecordsImportModels.Example, kanji: String): Boolean {
        return isWordCloze(example, kanji) && example.sentence.contains(kanji)
    }

    /** A bare single-kanji word would blank to nothing but "〇": no context. */
    private fun isWordCloze(example: RecordsImportModels.Example, kanji: String): Boolean {
        return example.expression.contains(kanji) &&
            (example.expression != kanji || example.reading.isNotBlank())
    }

    private fun exampleMeaning(
        example: RecordsImportModels.Example,
        row: RecordsImportModels.DashboardRow,
    ): String {
        val raw = example.meaning.ifBlank { row.primaryMeaning }
        return StudyTextCopy.compact(raw, MAX_MEANING_CHARS)
    }

    private fun blank(text: String, kanji: String): String = text.replace(kanji, BLANK)
}
