package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusQueuePolicyTest {
    @Test
    fun queuedEntriesFollowFocusOrderBeforeDuePriority() {
        val now = 5_000L
        val entries = FocusQueuePolicy.queuedEntries(
            listOf(row("先", 10), row("後", 90)),
            listOf(review("先", now - 1_000L), review("後", now + 1_000L)),
            now,
            0L,
            plan("後", "先"),
            RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals(listOf("後", "先"), kanji(entries))
    }

    @Test
    fun queuedEntriesSortByDueStateDueTimeWeaknessAndKanji() {
        val now = 5_000L
        val entries = FocusQueuePolicy.queuedEntries(
            listOf(row("新", 20), row("学", 20), row("弱", 80), row("低", 10), row("後", 20)),
            listOf(
                item("後", StudyLadderRules.STATE_REVIEW, now + 1_000L, 1),
                item("新", StudyLadderRules.STATE_NEW, now - 1_000L, 0),
                item("低", StudyLadderRules.STATE_REVIEW, now - 1_000L, 1),
                item("弱", StudyLadderRules.STATE_REVIEW, now - 1_000L, 1),
                item("学", StudyLadderRules.STATE_LEARNING, now - 1_000L, 1)
            ),
            now,
            0L,
            null,
            RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals(listOf("学", "弱", "低", "新", "後"), kanji(entries))
    }

    @Test
    fun queuedEntriesKeepFutureQueueItemsButDropMissingRows() {
        val now = 5_000L
        val entries = FocusQueuePolicy.queuedEntries(
            listOf(row("待", 10)),
            listOf(review("待", now + 60_000L), review("無", now - 1L)),
            now,
            0L,
            null,
            RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals(listOf("待"), kanji(entries))
    }

    @Test
    fun queuedEntriesTreatMissingInputsAsEmpty() {
        val entries = FocusQueuePolicy.queuedEntries(
            null,
            null,
            1L,
            0L,
            null,
            RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals(emptyList<FocusQueuePolicy.QueueEntry>(), entries)
    }

    @Test
    fun stateRankOrdersLearningReviewNewThenUnknownStates() {
        assertEquals(0, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_LEARNING))
        assertEquals(1, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_REVIEW))
        assertEquals(2, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_NEW))
        assertEquals(3, FocusQueuePolicy.stateRank(StudyLadderRules.STATE_RETIRED))
        assertEquals(3, FocusQueuePolicy.stateRank(""))
    }

    @Test
    fun rowToneSeparatesDueLearningAndRestingQueueItems() {
        val now = 5_000L

        assertEquals(FocusQueuePolicy.QueueTone.DUE, FocusQueuePolicy.rowTone(review("裂", now), now))
        assertEquals(FocusQueuePolicy.QueueTone.LEARNING, FocusQueuePolicy.rowTone(item("学", StudyLadderRules.STATE_LEARNING, now + 1_000L, 1), now))
        assertEquals(FocusQueuePolicy.QueueTone.RESTING, FocusQueuePolicy.rowTone(review("待", now + 1_000L), now))
        assertEquals(FocusQueuePolicy.QueueTone.RESTING, FocusQueuePolicy.rowTone(null, now))
    }

    private fun kanji(entries: List<FocusQueuePolicy.QueueEntry>): List<String> {
        return entries.map { it.row.kanji }
    }

    private fun plan(vararg focusKanji: String): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            100,
            focusKanji.size,
            focusKanji.size,
            focusKanji.toList(),
            focusKanji.size,
            false,
            "test"
        )
    }

    private fun row(kanji: String, weaknessScore: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            weaknessScore,
            "reason",
            "reason text",
            1,
            1,
            0,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun review(kanji: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return item(kanji, StudyLadderRules.STATE_REVIEW, dueAtMillis, 1)
    }

    private fun item(kanji: String, state: String, dueAtMillis: Long, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)
    }
}
