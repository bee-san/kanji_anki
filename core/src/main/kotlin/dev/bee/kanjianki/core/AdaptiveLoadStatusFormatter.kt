package dev.bee.kanjianki.core

/**
 * Renders the one-line plan status shown on the sync summary, home focus
 * panel, and study-done screens.
 *
 * The highest-priority message in both modes is backlog honesty: when more
 * kanji are due than today's cap can hold, the status says how many are
 * waiting and how to catch up, instead of hiding the overflow behind a
 * generic recovery sentence.
 */
internal object AdaptiveLoadStatusFormatter {
    fun manualStatus(
        workloadPercent: Int,
        target: Int,
        ceiling: Int,
        stats: RecordsSchedulerModels.ReviewStats,
        recoveryDue: Int,
        overflowDue: Int,
    ): String {
        if (overflowDue > 0) {
            return overflowStatus(overflowDue)
        }
        if (workloadPercent <= 0) {
            return "Very little work today: one focused kanji unless recovery is already due."
        }
        if (stats.total == 0) {
            return "Pareto focus starts small until Kani has review history."
        }
        if (recoveryDue >= target) {
            return "Due recovery fills today's focus, so new kanji wait."
        }
        if (target >= ceiling) {
            return "Recent reviews are steady, so Kani can use the full focus range."
        }
        return "Adaptive focus is set from recent misses, hard ratings, and writing results."
    }

    fun autoStatus(
        target: Int,
        autoTarget: AdaptiveLoadFocusPolicy.AutoTarget,
        stats: RecordsSchedulerModels.ReviewStats,
        recoveryDue: Int,
        overflowDue: Int,
    ): String {
        if (overflowDue > 0) {
            return overflowStatus(overflowDue)
        }
        if (recoveryDue >= target) {
            return "Due recovery fills today's auto Pareto focus, so new kanji wait."
        }
        if (stats.total == 0) {
            return if (autoTarget.concentrated) {
                "Auto Pareto found a concentrated focus, then starts small until Kani has review history."
            } else {
                "Auto Pareto starts small until Kani has review history."
            }
        }
        if (!autoTarget.concentrated) {
            return spreadStatus(target, autoTarget)
        }
        return concentratedStatus(target, autoTarget)
    }

    private fun spreadStatus(target: Int, autoTarget: AdaptiveLoadFocusPolicy.AutoTarget): String {
        if (target > autoTarget.target) {
            return "Today's priority is spread evenly, and your steady streak allows one extra kanji."
        }
        if (target < autoTarget.target) {
            return "Today's priority is spread evenly, then recent review strain lowered the focus."
        }
        return "Today's priority is spread evenly, so Kani uses the small Pareto focus."
    }

    private fun concentratedStatus(target: Int, autoTarget: AdaptiveLoadFocusPolicy.AutoTarget): String {
        if (target < autoTarget.target) {
            return "Auto Pareto found a concentrated focus, then recent review strain lowered it."
        }
        if (target > autoTarget.target) {
            return "Auto Pareto found a concentrated focus, and your steady streak allows one extra kanji."
        }
        return "Auto Pareto found a concentrated focus: a few kanji carry most of today's priority."
    }

    private fun overflowStatus(overflowDue: Int): String {
        val waiting = if (overflowDue == 1) "1 due kanji waits" else "$overflowDue due kanji wait"
        return "$waiting beyond today's cap. Continue all kanji or raise Max items to catch up."
    }
}
