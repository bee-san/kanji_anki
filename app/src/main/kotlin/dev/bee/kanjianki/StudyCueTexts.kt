package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyCuePolicy

internal object StudyCueTexts {
    @JvmStatic
    fun answerLines(
        dictionaryLookup: DictionaryLookup,
        session: RecordsSchedulerModels.StudySession?,
        example: RecordsImportModels.Example?,
        wordReadingTask: Boolean,
    ): List<String> {
        return StudyCuePolicy.answerLines(dictionaryLookup, session, example, wordReadingTask)
    }

    @JvmStatic
    fun displayGlosses(meanings: List<String>?, maxMeanings: Int): String {
        return StudyCuePolicy.displayGlosses(meanings, maxMeanings)
    }

    @JvmStatic
    fun cleanFallbackMeaning(raw: String?, fallback: String?, maxChars: Int): String {
        return StudyCuePolicy.cleanFallbackMeaning(raw, fallback, maxChars)
    }
}
