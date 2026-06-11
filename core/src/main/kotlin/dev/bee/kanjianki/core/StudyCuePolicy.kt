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
    fun meaningChoiceAnswerLines(
        dictionaryLookup: DictionaryLookup?,
        session: RecordsSchedulerModels.StudySession?,
        example: RecordsImportModels.Example?,
    ): List<String> {
        if (!usesCompoundMeaningPrompt(session, example)) {
            return answerLines(dictionaryLookup, session, example, false)
        }
        val row = session?.row ?: return answerLines(dictionaryLookup, session, example, false)
        val item = session.item ?: return answerLines(dictionaryLookup, session, example, false)
        val sourceExpression = DictionaryLookup.normalize(example?.expression)
        val compoundMeaning = StudyCueFormatter.cleanFallbackMeaning(
            if (example != null && !example.meaning.isNullOrEmpty()) example.meaning else row.primaryMeaning,
            row.primaryMeaning,
            96,
        )
        val lines = ArrayList(
            StudyCueFormatter.answerLines(
                StudyCue(
                    compoundMeaning,
                    firstNonEmpty(example?.reading, row.reading),
                    sourceExpression,
                    DictionaryLookup.SOURCE_ANKI,
                ),
            ),
        )
        val individualMeanings = StudyCueFormatter.displayGlosses(
            dictionaryLookup?.lookupKanji(DictionaryLookup.normalize(item.kanji))?.meanings,
            2,
        )
        if (individualMeanings.isNotEmpty() && !individualMeanings.equals(compoundMeaning, ignoreCase = true)) {
            lines.add(StudyCueFormatter.individualKanjiMeaningsLine(individualMeanings))
        }
        return lines
    }

    @JvmStatic
    fun isReadingLine(line: String?): Boolean {
        return StudyCueFormatter.isReadingLine(line)
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
        val item = session.item ?: return StudyCue("", "", "", "")
        val sourceExpression = example?.expression ?: ""
        val sourceReading = example?.reading ?: row.reading
        val ankiMeaning = if (example != null && !example.meaning.isNullOrEmpty()) {
            example.meaning
        } else {
            row.primaryMeaning
        }
        return Objects.requireNonNull(dictionaryLookup, "dictionaryLookup")!!.studyCue(
            item.kanji,
            ankiMeaning,
            row.reading,
            sourceExpression,
            sourceReading,
        )
    }

    private fun usesCompoundMeaningPrompt(
        session: RecordsSchedulerModels.StudySession?,
        example: RecordsImportModels.Example?,
    ): Boolean {
        if (session?.taskType != BridgeScheduler.TASK_MEANING_KANJI) {
            return false
        }
        val kanji = DictionaryLookup.normalize(session.item?.kanji)
        val expression = DictionaryLookup.normalize(example?.expression)
        return kanji.isNotEmpty() &&
            expression.contains(kanji) &&
            expression.codePointCount(0, expression.length) > kanji.codePointCount(0, kanji.length)
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
