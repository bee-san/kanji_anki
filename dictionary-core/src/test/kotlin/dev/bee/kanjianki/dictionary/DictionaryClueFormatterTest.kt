package dev.bee.kanjianki.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DictionaryClueFormatterTest {
    @Test
    fun answerLinesPutMeaningThenReadingThenFrom() {
        val lines = DictionaryClueFormatter.answerLines(
            DictionaryCue(
                meaning = "sorrow",
                reading = "かなしい",
                fromExpression = "悲しみ",
                source = "KANJIDIC2",
            ),
        )

        assertEquals(listOf("sorrow", "Reading: かなしい", "From: 悲しみ"), lines)
    }

    @Test
    fun answerLinesDoNotAddMeaningLabelOrBlankLines() {
        val lines = DictionaryClueFormatter.answerLines(
            DictionaryCue(
                meaning = "movement",
                reading = "",
                fromExpression = "",
                source = "KANJIDIC2",
            ),
        )

        assertEquals(listOf("movement"), lines)
        assertFalse(lines.any { it.startsWith("Meaning:") })
    }
}
