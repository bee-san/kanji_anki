package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRetirementRegressionTest {
    @Test
    fun absentReviewedItemIsRetiredWithSchedulerMemoryIntact() {
        val memory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            9_000L,
            12.5,
            6.25,
            8,
            2,
            0,
            "good",
            30,
            4,
            7_000L,
            8_000L,
        )
        val original = studyItem("痛")
            .copyBuilder()
            .typingMeaningMemory(memory)
            .meaningKanjiMemory(memory)
            .kanjiMeaningMemory(memory)
            .fontMeaningMemory(memory)
            .wordReadingMemory(memory)
            .writingRemediationMemory(memory)
            .similarKanjiMemory(memory)
            .kanjiReadingMemory(memory)
            .readingKanjiMemory(memory)
            .sentenceReadingMemory(memory)
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .realPassStreak(4)
            .realAgainStreak(2)
            .lastRealReviewDueAtMillis(7_000L)
            .schedulerRevision(7L)
            .build()

        val retired = BridgeScheduler().seedQueue(
            emptyList<RecordsImportModels.DashboardRow>(),
            listOf(original),
            RecordsSyncModels.Settings.kikuDefaults(),
            10_000L,
            0L,
        ).single()

        assertEquals(StudyLadderRules.STATE_RETIRED, retired.state)
        assertNull(retired.activeToken)
        assertEquals(original.stability, retired.stability, 0.0)
        assertEquals(original.difficulty, retired.difficulty, 0.0)
        assertEquals(original.totalReviews, retired.totalReviews)
        assertEquals(original.lapses, retired.lapses)
        assertEquals(original.rung, retired.rung)
        assertEquals(original.phase, retired.phase)
        assertEquals(original.realPassStreak, retired.realPassStreak)
        assertEquals(original.realAgainStreak, retired.realAgainStreak)
        assertEquals(original.lastRealReviewDueAtMillis, retired.lastRealReviewDueAtMillis)
        assertEquals(memoryEncodings(original), memoryEncodings(retired))
        assertEquals(8L, retired.schedulerRevision)
    }

    @Test
    fun alreadyRetiredAbsentItemSurvivesWithoutAnotherRevision() {
        val retired = studyItem("裂").copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .activeToken(null)
            .schedulerRevision(11L)
            .build()

        val reseeded = BridgeScheduler().seedQueue(
            emptyList(),
            listOf(retired),
            RecordsSyncModels.Settings.kikuDefaults(),
            10_000L,
            0L,
        ).single()

        assertEquals(StudyLadderRules.STATE_RETIRED, reseeded.state)
        assertEquals(11L, reseeded.schedulerRevision)
        assertEquals(retired.dueAtMillis, reseeded.dueAtMillis)
        assertEquals(memoryEncodings(retired), memoryEncodings(reseeded))
    }

    @Test
    fun locallySuspendedItemStillReachesExplicitRetirement() {
        val memory = RecordsStudyModels.TaskMemory.initial().withDueAtMillis(12_000L)
        val original = studyItem("痛").copyBuilder()
            .wordReadingMemory(memory)
            .schedulerRevision(3L)
            .build()
        val row = RecordsImportModels.DashboardRow(
            "痛",
            272,
            "pain",
            "いたい",
            "search",
            12,
            "suspended_archive",
            "reason",
            0,
            1,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
        val activeRows = SuspendedImportPolicy.activeRows(listOf(row), setOf("痛"))

        val retired = BridgeScheduler().seedQueue(
            activeRows,
            listOf(original),
            RecordsSyncModels.Settings.kikuDefaults(),
            10_000L,
            0L,
        ).single()

        assertEquals(emptyList<RecordsImportModels.DashboardRow>(), activeRows)
        assertEquals(StudyLadderRules.STATE_RETIRED, retired.state)
        assertEquals(original.totalReviews, retired.totalReviews)
        assertEquals(memory.encode(), retired.wordReadingMemory.encode())
        assertEquals(4L, retired.schedulerRevision)
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            StudyLadderRules.STATE_REVIEW,
            9_000L,
            12.5,
            6.25,
            8,
            2,
            0,
            3,
            "active-token",
            1_000L,
        )
    }

    private fun memoryEncodings(item: RecordsStudyModels.StudyItem): List<String> {
        return listOf(
            item.typingMeaningMemory,
            item.meaningKanjiMemory,
            item.kanjiMeaningMemory,
            item.fontMeaningMemory,
            item.wordReadingMemory,
            item.writingRemediationMemory,
            item.similarKanjiMemory,
            item.kanjiReadingMemory,
            item.readingKanjiMemory,
            item.sentenceReadingMemory,
        ).map { it.encode() }
    }
}
