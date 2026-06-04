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
        val due = currentDueCount(rows, safeItems(safeRequest), safeRequest.nowMillis())
        val studiedToday = safeRequest.studiedToday?.isNotEmpty() == true
        return when {
            !studiedToday -> streakCopy(maxOf(plan.remaining, due), safeRequest.currentStreakDays)
            due > 0 -> reviewCopy(due)
            plan.remaining > 0 -> focusCopy(plan.remaining)
            else -> caughtUpCopy()
        }
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

    private fun focusCopy(focusRemaining: Int): ReminderCopy {
        return forCounts(focusRemaining, 0)
    }

    private fun reviewCopy(due: Int): ReminderCopy {
        return ReminderCopy(
            "You have more Kanji to review",
            String.format(
                Locale.ROOT,
                "%d kanji %s ready now. Open Kani to review %s.",
                due,
                if (due == 1) "is" else "are",
                if (due == 1) "it" else "them",
            ),
        )
    }

    private fun streakCopy(waiting: Int, currentStreakDays: Int): ReminderCopy {
        val streakLabel = if (currentStreakDays > 0) {
            "your ${currentStreakDays}-day streak"
        } else {
            "your streak"
        }
        val waitingMessage = if (waiting > 0) {
            String.format(
                Locale.ROOT,
                "%d kanji %s waiting. ",
                waiting,
                if (waiting == 1) "is" else "are",
            )
        } else {
            ""
        }
        return ReminderCopy(
            "Kani streak reminder",
            waitingMessage + "Open Kani to keep $streakLabel alive.",
        )
    }

    private fun caughtUpCopy(): ReminderCopy {
        return ReminderCopy("Kani is caught up", "You've already studied today. Open Kani later when more kanji come back.")
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
