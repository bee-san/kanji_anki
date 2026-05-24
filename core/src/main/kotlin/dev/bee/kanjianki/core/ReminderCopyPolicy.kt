package dev.bee.kanjianki.core

import java.util.Locale

object ReminderCopyPolicy {
    @JvmStatic
    fun forPlan(request: AdaptiveLoadPlanner.PlanRequest?): ReminderCopy {
        val safeRequest = request ?: return syncCopy()
        val rows = safeRows(safeRequest)
        if (rows.isEmpty()) {
            return syncCopy()
        }
        val plan = AdaptiveLoadPlanner().plan(safeRequest)
        return forCounts(plan.remaining, currentDueCount(rows, safeItems(safeRequest), safeRequest.nowMillis()))
    }

    @JvmStatic
    fun forCounts(focusRemaining: Int, due: Int): ReminderCopy {
        if (focusRemaining > 0) {
            return ReminderCopy(
                "Kani focus is ready",
                String.format(
                    Locale.ROOT,
                    "%d focus kanji %s waiting. Open Kani to review %s.",
                    focusRemaining,
                    if (focusRemaining == 1) "is" else "are",
                    if (focusRemaining == 1) "it" else "them",
                ),
            )
        }
        if (due > 0) {
            return ReminderCopy(
                "Kani recovery is due",
                String.format(
                    Locale.ROOT,
                    "%d problem kanji %s due. Open Kani to review %s now.",
                    due,
                    if (due == 1) "is" else "are",
                    if (due == 1) "it" else "them",
                ),
            )
        }
        return ReminderCopy("Kani is caught up", "No problem kanji are due. Open Kani for extra practice if you want.")
    }

    private fun syncCopy(): ReminderCopy {
        return ReminderCopy("Sync Kani", "Open Kani and tap Sync.")
    }

    private fun currentDueCount(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): Int {
        return BridgeScheduler().dueCount(items, rows, now)
    }

    private fun safeRows(request: AdaptiveLoadPlanner.PlanRequest): List<RecordsImportModels.DashboardRow> {
        return request.rows() ?: emptyList()
    }

    private fun safeItems(request: AdaptiveLoadPlanner.PlanRequest): List<RecordsStudyModels.StudyItem> {
        return request.items() ?: emptyList()
    }

    class ReminderCopy(
        @JvmField val title: String?,
        @JvmField val message: String?,
    )
}
