package dev.bee.kanjianki.domain.model.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TaskMemoryTest {
    @Test
    fun initialAndStudyFieldFactoriesMatchLegacyDefaults() {
        val initial = TaskMemory.initial()
        assertEquals("new", initial.state)
        assertEquals(0L, initial.dueAtMillis)
        assertEquals(0.4, initial.stability, 0.0)
        assertEquals(5.0, initial.difficulty, 0.0)
        assertEquals(0, initial.totalReviews)
        assertEquals(0, initial.lapses)

        val fromFields = TaskMemory.fromStudyFields(
            state = "",
            dueAtMillis = -1L,
            stability = 2.0,
            difficulty = 3.0,
            totalReviews = -4,
            lapses = -5,
            learningStep = -6,
            matureIntervalDays = -7,
        )
        assertEquals("new", fromFields.state)
        assertEquals(0L, fromFields.dueAtMillis)
        assertEquals(0, fromFields.totalReviews)
        assertEquals(0, fromFields.lapses)
        assertEquals(0, fromFields.learningStep)
        assertEquals(0, fromFields.matureIntervalDays)
    }

    @Test
    fun encodeDecodeRoundTripKeepsModernPassFields() {
        val memory = TaskMemory.from(
            state = "review",
            dueAtMillis = 9L,
            stability = 1.2,
            difficulty = 4.3,
            totalReviews = 5,
            lapses = 1,
            learningStep = 2,
            lastRating = "hard",
            matureIntervalDays = 7,
            consecutivePasses = 3,
            lastPassedDueAtMillis = 10L,
        )

        assertEquals(memory, TaskMemory.decode(memory.encode()))
    }

    @Test
    fun decodeFallsBackForMissingOrMalformedValuesAndSupportsLegacyParts() {
        val fallback = TaskMemory.from(
            state = "fallback",
            dueAtMillis = 1L,
            stability = 2.0,
            difficulty = 3.0,
            totalReviews = 4,
            lapses = 5,
            learningStep = 1,
            lastRating = "good",
            matureIntervalDays = 6,
        )

        assertSame(fallback, TaskMemory.decode(null, fallback))
        assertSame(fallback, TaskMemory.decode("", fallback))
        assertSame(fallback, TaskMemory.decode("too\tshort", fallback))
        assertSame(fallback, TaskMemory.decode("new\tbad\t0.4\t5.0\t0\t0\t0\t\t0", fallback))

        val legacyNinePart = TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7")
        assertEquals(0, legacyNinePart.consecutivePasses)
        assertEquals(0L, legacyNinePart.lastPassedDueAtMillis)

        val legacyTenPart = TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7\t3")
        assertEquals(3, legacyTenPart.consecutivePasses)
        assertEquals(0L, legacyTenPart.lastPassedDueAtMillis)
    }

    @Test
    fun withDueAtMillisPreservesPassStateAndClampsNegativeDue() {
        val memory = TaskMemory.from(
            state = "review",
            dueAtMillis = 10L,
            stability = 1.0,
            difficulty = 2.0,
            totalReviews = 3,
            lapses = 0,
            learningStep = 1,
            lastRating = "good",
            matureIntervalDays = 2,
            consecutivePasses = 4,
            lastPassedDueAtMillis = 5L,
        )

        val updated = memory.withDueAtMillis(-50L)
        assertEquals(0L, updated.dueAtMillis)
        assertEquals(4, updated.consecutivePasses)
        assertEquals(5L, updated.lastPassedDueAtMillis)
    }

    @Test
    fun memoryBankReadsCorrectMemoryPerTaskTypeAndRung() {
        val typed = memory("typed")
        val meaning = memory("meaning")
        val kanji = memory("kanji")
        val font = memory("font")
        val word = memory("word")
        val writing = memory("writing")
        val similar = memory("similar")
        val bank = TaskMemoryBank(
            typingMeaningMemory = typed,
            meaningKanjiMemory = meaning,
            kanjiMeaningMemory = kanji,
            fontMeaningMemory = font,
            wordReadingMemory = word,
            writingRemediationMemory = writing,
            similarKanjiMemory = similar,
        )

        assertEquals(writing, bank.memoryForTaskType(StudyTaskWireNames.WRITE_KANJI))
        assertEquals(writing, bank.memoryForTaskType(StudyTaskWireNames.WRITING_REMEDIATION))
        assertEquals(typed, bank.memoryForTaskType(StudyTaskWireNames.TYPE_MEANING))
        assertEquals(typed, bank.memoryForTaskType(StudyTaskWireNames.TYPING_MEANING))
        assertEquals(similar, bank.memoryForTaskType(StudyTaskWireNames.SIMILAR_KANJI))
        assertEquals(meaning, bank.memoryForTaskType(StudyTaskWireNames.MEANING_KANJI))
        assertEquals(word, bank.memoryForTaskType(StudyTaskWireNames.WORD_READING))
        assertEquals(font, bank.memoryForTaskType(StudyTaskWireNames.FONT_MEANING))
        assertEquals(kanji, bank.memoryForTaskType(null))
        assertEquals(kanji, bank.memoryForTaskType("unknown"))

        assertEquals(writing, bank.memoryForRung(StudyRung.WRITE_KANJI))
        assertEquals(typed, bank.memoryForRung(StudyRung.TYPE_MEANING))
        assertEquals(similar, bank.memoryForRung(StudyRung.SIMILAR_KANJI))
        assertEquals(meaning, bank.memoryForRung(StudyRung.MEANING_KANJI))
        assertEquals(kanji, bank.memoryForRung(StudyRung.KANJI_MEANING))
        assertEquals(font, bank.memoryForRung(StudyRung.FONT_MEANING))
        assertEquals(word, bank.memoryForRung(StudyRung.WORD_READING))
        assertEquals(kanji, bank.memoryForRung(null))
    }

    @Test
    fun memoryBankWritesCorrectMemoryPerTaskType() {
        val custom = memory("custom")
        val bank = TaskMemoryBank()

        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.WRITE_KANJI, custom).writingRemediationMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.WRITING_REMEDIATION, custom).writingRemediationMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.TYPE_MEANING, custom).typingMeaningMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.TYPING_MEANING, custom).typingMeaningMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.SIMILAR_KANJI, custom).similarKanjiMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.MEANING_KANJI, custom).meaningKanjiMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.WORD_READING, custom).wordReadingMemory)
        assertEquals(custom, bank.withTaskMemory(StudyTaskWireNames.FONT_MEANING, custom).fontMeaningMemory)
        assertEquals(custom, bank.withTaskMemory(null, custom).kanjiMeaningMemory)
        assertEquals(custom, bank.withTaskMemory("unknown", custom).kanjiMeaningMemory)
    }

    private fun memory(label: String): TaskMemory = TaskMemory.from(
        state = label,
        dueAtMillis = 1L,
        stability = 2.0,
        difficulty = 3.0,
        totalReviews = 4,
        lapses = 5,
        learningStep = 1,
        lastRating = "good",
        matureIntervalDays = 6,
    )
}
