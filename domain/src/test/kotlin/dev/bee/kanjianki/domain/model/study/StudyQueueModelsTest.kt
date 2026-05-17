package dev.bee.kanjianki.domain.model.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyQueueModelsTest {
    @Test
    fun studyItemStateWireNamesStayStable() {
        assertEquals(StudyItemState.NEW, StudyItemState.fromWireName("new"))
        assertEquals(StudyItemState.LEARNING, StudyItemState.fromWireName("learning"))
        assertEquals(StudyItemState.REVIEW, StudyItemState.fromWireName("review"))
        assertEquals(StudyItemState.RETIRED, StudyItemState.fromWireName("retired"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownStudyItemStateFailsExplicitly() {
        StudyItemState.fromWireName("buried")
    }

    @Test
    fun dashboardAnswerSignaturePrefersSuspendedThenActiveThenFirstExample() {
        val suspended = rowWithExamples(
            StudyExample("active", "破裂", "はれつ", "burst"),
            StudyExample("suspended", "裂ける", "さける", "split"),
        )
        val activeOnly = rowWithExamples(
            StudyExample("active", "first", "one", "meaning one"),
            StudyExample("active", "second", "two", "meaning two"),
        )
        val fallbackExample = rowWithExamples(
            StudyExample("other", "fallback", "read", "meaning"),
        )

        assertEquals("裂|裂ける|さける|split", suspended.answerSignature())
        assertEquals("裂|first|one|meaning one", activeOnly.answerSignature())
        assertEquals("裂|fallback|read|meaning", fallbackExample.answerSignature())
    }

    @Test
    fun dashboardAnswerSignatureFallsBackToRowFieldsAndNormalizesWhitespace() {
        val row = StudyDashboardRow(
            kanji = " 裂 ",
            jitenRank = 30,
            primaryMeaning = " split   apart ",
            reading = "  レツ  ",
            browserSearch = "search",
            weaknessScore = 5,
            reasonCode = "reason",
            reasonText = "reason text",
            activeExampleCount = 1,
            suspendedExampleCount = 0,
            matureSupportCount = 0,
        )

        assertEquals("裂||レツ|split apart", row.answerSignature())
    }

    @Test
    fun familyKeysUseKanjiAndAnswerSignature() {
        val row = rowWithExamples(StudyExample("suspended", "裂ける", "さける", "split"))
        val item = queueItem(answerSignature = "裂|裂ける|さける|split")

        assertEquals("裂\u0000裂|裂ける|さける|split", row.familyKey())
        assertEquals(row.familyKey(), item.familyKey)
        assertEquals("裂\u0000", StudyQueueFamilyKey.of("裂", null))
    }

    @Test
    fun queueItemTracksRetiredAndSuppressedFlags() {
        val active = queueItem()
        val retired = queueItem(state = StudyItemState.RETIRED)
        val suppressed = queueItem(suppressedByTaskType = StudyTaskWireNames.FONT_MEANING)

        assertFalse(active.isRetired)
        assertFalse(active.isSuppressed)
        assertTrue(retired.isRetired)
        assertTrue(suppressed.isSuppressed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun queueItemRequiresKanji() {
        queueItem(kanji = " ")
    }

    private fun rowWithExamples(
        vararg examples: StudyExample,
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = "裂",
        jitenRank = 30,
        primaryMeaning = "main meaning",
        reading = "レツ",
        browserSearch = "search",
        weaknessScore = 5,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 0,
        examples = examples.toList(),
    )

    private fun queueItem(
        kanji: String = "裂",
        state: StudyItemState = StudyItemState.REVIEW,
        answerSignature: String = "",
        suppressedByTaskType: String = "",
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = 100L,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = 1,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = answerSignature,
        suppressedByTaskType = suppressedByTaskType,
    )
}
