package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWritingPromptPolicyTest {
    @Test
    fun sentenceExampleBlanksEveryTargetOccurrenceInSentenceAndWord() {
        val prompt = build(
            row(example("active", "脱出", "だっしゅつ", "escape", " 彼は脱出した後で脱いだ。 ")),
        )

        assertEquals(StudyWritingPromptPolicy.Kind.SENTENCE, prompt.kind)
        assertEquals("彼は〇出した後で〇いだ。", prompt.sentence)
        assertEquals("〇出", prompt.word)
        assertEquals("だっしゅつ", prompt.reading)
        assertEquals("escape", prompt.meaning)
        assertTrue(prompt.hasBlank())
    }

    @Test
    fun sentenceSelectionFollowsSentenceReadingPreferenceWhenUsable() {
        val active = example("active", "脱出", "だっしゅつ", "escape", "脱出する。")
        val suspended = example("suspended", "脱線", "だっせん", "derailment", "脱線した。")

        assertEquals("〇線した。", build(row(active, suspended)).sentence)
    }

    @Test
    fun sentenceWithoutTheTargetFallsThroughToAnotherSentenceOrTheWord() {
        val inflected = example("suspended", "脱出", "だっしゅつ", "escape", "だっしゅつした。")
        val usable = example("active", "脱線", "だっせん", "derailment", "脱線した。")

        assertEquals("〇線した。", build(row(inflected, usable)).sentence)

        val wordOnly = build(row(inflected))
        assertEquals(StudyWritingPromptPolicy.Kind.WORD, wordOnly.kind)
        assertEquals("", wordOnly.sentence)
        assertEquals("〇出", wordOnly.word)
        assertEquals("だっしゅつ", wordOnly.reading)
    }

    @Test
    fun wordExampleUsesRowMeaningWhenTheExampleHasNone() {
        val prompt = build(row(example("active", "脱出", "だっしゅつ", "", "")))

        assertEquals(StudyWritingPromptPolicy.Kind.WORD, prompt.kind)
        assertEquals("row meaning", prompt.meaning)
    }

    @Test
    fun bareKanjiWordNeedsAReadingToCountAsContext() {
        val withReading = build(row(example("active", "脱", "だつ", "undress", "")))
        assertEquals(StudyWritingPromptPolicy.Kind.WORD, withReading.kind)
        assertEquals("〇", withReading.word)

        val bare = build(row(example("active", "脱", "", "undress", "")))
        assertEquals(StudyWritingPromptPolicy.Kind.MEANING, bare.kind)
    }

    @Test
    fun noUsableExampleFallsBackToKanjiMeaningAndRowReading() {
        val unrelated = build(row(example("active", "出口", "でぐち", "exit", "出口はどこ。")))
        assertEquals(StudyWritingPromptPolicy.Kind.MEANING, unrelated.kind)
        assertEquals("undress, remove", unrelated.meaning)
        assertEquals("row reading", unrelated.reading)
        assertEquals("", unrelated.word)
        assertFalse(unrelated.hasBlank())

        val rowless = StudyWritingPromptPolicy.build(session(null), null)
        assertEquals(StudyWritingPromptPolicy.Kind.MEANING, rowless.kind)
        assertEquals("", rowless.meaning)
        assertEquals("", rowless.reading)

        assertEquals(StudyWritingPromptPolicy.Kind.MEANING, StudyWritingPromptPolicy.build(null, "x").kind)
    }

    private fun build(row: RecordsImportModels.DashboardRow): StudyWritingPromptPolicy.Prompt {
        return StudyWritingPromptPolicy.build(session(row), "undress, remove")
    }

    private fun session(row: RecordsImportModels.DashboardRow?): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem("脱", "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L),
            row,
            "token",
            StudyTaskTypes.WRITE_KANJI,
            true,
            "prompt",
        )
    }

    private fun row(vararg examples: RecordsImportModels.Example): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            "脱",
            900,
            "row meaning",
            "row reading",
            "search",
            1,
            "reason",
            "reason text",
            1,
            0,
            1,
            examples.toList(),
        )
    }

    private fun example(
        sourceType: String,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            sourceType, 1L, 2L, expression, reading, meaning, sentence, false, 0, 0, 0, null, null, null,
        )
    }
}
