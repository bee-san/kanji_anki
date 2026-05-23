package dev.bee.kanjianki.core

import java.util.Objects

object StudyCuePolicy {
    @JvmStatic
    fun answerLines(
        dictionaryLookup: DictionaryLookup?,
        session: RecordsSchedulerModels.StudySession?,
        example: RecordsImportModels.Example?,
        wordReadingTask: Boolean,
    ): List<String> {
        return StudyCueFormatter.answerLines(studyCue(dictionaryLookup, session, example, wordReadingTask))
    }

    @JvmStatic
    fun displayGlosses(meanings: List<String>?, maxMeanings: Int): String {
        return StudyCueFormatter.displayGlosses(meanings, maxMeanings)
    }

    @JvmStatic
    fun cleanFallbackMeaning(raw: String?, fallback: String?, maxChars: Int): String {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars)
    }

    @JvmStatic
    fun studyCue(
        dictionaryLookup: DictionaryLookup?,
        session: RecordsSchedulerModels.StudySession?,
        example: RecordsImportModels.Example?,
        wordReadingTask: Boolean,
    ): StudyCue {
        val row = session?.row ?: return StudyCue("", "", "", "")
        if (wordReadingTask) {
            return wordReadingCue(session, example)
        }
        val sourceExpression = example?.expression ?: ""
        val sourceReading = example?.reading ?: row.reading
        val ankiMeaning = if (example != null && !example.meaning.isNullOrEmpty()) {
            example.meaning
        } else {
            row.primaryMeaning
        }
        return Objects.requireNonNull(dictionaryLookup, "dictionaryLookup")!!.studyCue(
            session.item.kanji,
            ankiMeaning,
            row.reading,
            sourceExpression,
            sourceReading,
        )
    }

    private fun wordReadingCue(
        session: RecordsSchedulerModels.StudySession,
        example: RecordsImportModels.Example?,
    ): StudyCue {
        val row = session.row ?: return StudyCue("", "", "", "")
        val sourceExpression = example?.expression ?: ""
        val sourceReading = example?.reading ?: row.reading
        val cueReading = firstNonEmpty(sourceReading, row.reading)
        return StudyCue("", cueReading, firstNonEmpty(sourceExpression), DictionaryLookup.SOURCE_ANKI)
    }

    private fun firstNonEmpty(vararg values: String?): String {
        for (value in values) {
            if (!value.isNullOrBlank()) {
                return value.trim()
            }
        }
        return ""
    }
}
