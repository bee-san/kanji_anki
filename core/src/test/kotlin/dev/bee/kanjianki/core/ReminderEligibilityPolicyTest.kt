package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEligibilityPolicyTest {
    @Test
    fun excludesRetiredItems() {
        val rows = listOf(row("裂"), row("包"))
        val items = listOf(
            studyItem("裂", "review", 1_000L),
            studyItem("包", "retired", 2_000L),
        )

        val eligible = ReminderEligibilityPolicy.eligibleReminderItems(items, rows, null)

        assertEquals(setOf("裂"), eligible.map { it.kanji }.toSet())
    }

    @Test
    fun excludesItemsWithoutAnActiveDashboardRow() {
        // 包 is locally suspended / off the dashboard: no row exists for it.
        val rows = listOf(row("裂"))
        val items = listOf(
            studyItem("裂", "review", 1_000L),
            studyItem("包", "review", 2_000L),
        )

        val eligible = ReminderEligibilityPolicy.eligibleReminderItems(items, rows, null)

        assertEquals(listOf("裂"), eligible.map { it.kanji })
        assertFalse(eligible.any { it.kanji == "包" })
    }

    @Test
    fun eligibleDueTimesReturnsDueAtOfEligibleItemsOnly() {
        val rows = listOf(row("裂"), row("包"))
        val items = listOf(
            studyItem("裂", "review", 1_000L),
            studyItem("包", "retired", 2_000L),
            studyItem("風", "review", 3_000L), // off-dashboard
        )

        val dueTimes = ReminderEligibilityPolicy.eligibleDueTimes(items, rows, null)

        assertEquals(listOf(1_000L), dueTimes)
    }

    @Test
    fun eligibleCountMatchesStudySessionSelectorDueCount() {
        val now = 10_000L
        val rows = listOf(row("裂"), row("包"), row("風"))
        val items = listOf(
            studyItem("裂", "review", now - 1_000L),
            studyItem("包", "review", now - 500L),
            studyItem("風", "retired", now - 200L),
            studyItem("岩", "review", now - 100L), // off-dashboard
        )

        val eligibleDueNow = ReminderEligibilityPolicy.eligibleDueTimes(items, rows, null)
            .count { it <= now }
        val selectorDueCount = StudySessionSelector().dueCount(items, rows, now, 0L, null)

        assertEquals(selectorDueCount, eligibleDueNow)
        assertEquals(2, eligibleDueNow)
    }

    @Test
    fun emptyInputsProduceNothing() {
        assertTrue(ReminderEligibilityPolicy.eligibleReminderItems(emptyList(), emptyList(), null).isEmpty())
        assertTrue(ReminderEligibilityPolicy.eligibleDueTimes(emptyList(), emptyList(), null).isEmpty())
    }

    private fun studyItem(kanji: String, state: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        // 11-arg form: recognitionStage defaults to 0 (kanji_meaning rung), empty
        // answer signature so the item matches its dashboard row by kanji.
        return RecordsStudyModels.StudyItem(
            kanji,
            state,
            dueAtMillis,
            1.0,
            5.0,
            2,
            0,
            0,
            0,
            "token-$kanji",
            0L,
        )
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            5,
            "reason",
            "reason text",
            1,
            0,
            0,
            ArrayList<RecordsImportModels.Example>(),
        )
    }
}
