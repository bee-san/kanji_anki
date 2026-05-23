package dev.bee.kanjianki.core

import java.util.Locale

object ReminderCopyPolicy {
    @JvmStatic
    fun forPlan(request: AdaptiveLoadPlanner.PlanRequest?): ReminderCopy {
        val safeRequest = request ?: return ReminderCopy(
            "Sync Kani",
            "Sync AnkiDroid to find the kanji your reviews keep exposing.",
        )
        val rows = safeRows(safeRequest)
        if (rows.isEmpty()) {
            return ReminderCopy("Sync Kani", "Sync AnkiDroid to find the kanji your reviews keep exposing.")
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
                    "%d focus kanji %s left today. Draw one now.",
                    focusRemaining,
                    if (focusRemaining == 1) "is" else "are",
                ),
            )
        }
        if (due > 0) {
            return ReminderCopy(
                "Kani recovery is due",
                String.format(
                    Locale.ROOT,
                    "%d problem kanji %s ready. Draw one now.",
                    due,
                    if (due == 1) "is" else "are",
                ),
            )
        }
        return ReminderCopy("Check Kani", "Your queue can rest today. Open Kani if you want an extra problem kanji rep.")
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
