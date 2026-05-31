package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Collections

class ReminderCopyPolicyTest {
    @Test
    fun forPlanTreatsMissingRowsAsSyncWork() {
        val missingRequest = ReminderCopyPolicy.forPlan(null)
        val missingRows = ReminderCopyPolicy.forPlan(
            planRequest(
                null,
                Collections.emptyList(),
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                utc(2026, Calendar.MAY, 15, 8, 0)
            )
        )

        assertEquals("Sync Kani", missingRequest.title)
        assertEquals("Sync Kani", missingRows.title)
    }

    @Test
    fun forPlanAsksForSyncBeforeAnyActiveKanjiExist() {
        val copy = ReminderCopyPolicy.forPlan(
            planRequest(
                Collections.emptyList(),
                Collections.emptyList(),
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                utc(2026, Calendar.MAY, 15, 8, 0)
            )
        )

        assertEquals("Sync Kani", copy.title)
        assertEquals("Open Kani and tap Sync.", copy.message)
    }

    @Test
    fun forPlanPlansActiveRowsBeforeFormattingMessage() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(
            planRequest(
                Collections.singletonList(row("裂", 80)),
                Collections.singletonList(
                    RecordsStudyModels.StudyItem("裂", "review", now - 1L, 1.0, 5.0, 2, 0, 2, 1, null, now)
                ),
                1,
                now
            )
        )

        assertEquals("Kani focus is ready", copy.title)
        assertEquals("1 focus kanji is waiting. Open Kani to review it.", copy.message)
    }

    @Test
    fun forPlanTreatsMissingStudyItemsAsEmptyQueue() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(
            planRequest(
                Collections.singletonList(row("裂", 80)),
                null,
                1,
                now
            )
        )

        assertEquals("Kani focus is ready", copy.title)
        assertEquals("1 focus kanji is waiting. Open Kani to review it.", copy.message)
    }

    @Test
    fun forCountsFormatsFocusRecoveryAndRestMessages() {
        val oneFocus = ReminderCopyPolicy.forCounts(1, 4)
        val manyFocus = ReminderCopyPolicy.forCounts(3, 4)
        val oneDue = ReminderCopyPolicy.forCounts(0, 1)
        val manyDue = ReminderCopyPolicy.forCounts(0, 2)
        val rest = ReminderCopyPolicy.forCounts(0, 0)

        assertEquals("Kani focus is ready", oneFocus.title)
        assertEquals("1 focus kanji is waiting. Open Kani to review it.", oneFocus.message)
        assertEquals("3 focus kanji are waiting. Open Kani to review them.", manyFocus.message)
        assertEquals("Kani recovery is due", oneDue.title)
        assertEquals("1 problem kanji is due. Open Kani to review it now.", oneDue.message)
        assertEquals("2 problem kanji are due. Open Kani to review them now.", manyDue.message)
        assertEquals("Kani is caught up", rest.title)
        assertEquals("No problem kanji are due. Open Kani for extra practice if you want.", rest.message)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun planRequest(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        maxItems: Int,
        now: Long,
    ): AdaptiveLoadPlanner.PlanRequest {
        return AdaptiveLoadPlanner.PlanRequest.builder(
            rows,
            items,
            RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
            0,
            Collections.emptySet(),
            AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                maxItems
            ),
            now,
        ).build()
    }

    private fun row(kanji: String, score: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            score,
            "reason",
            "reason text",
            1,
            if (score > 15) 1 else 0,
            0,
            Collections.emptyList<RecordsImportModels.Example>()
        )
    }
}
