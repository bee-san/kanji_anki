package dev.bee.kanjianki.core

object AdaptiveFocusCopy {
    @JvmStatic
    fun adaptiveFocusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return "Adaptive focus is waiting for sync"
        }
        if (plan.allKanjiMode) {
            return "Adaptive focus covers all current problem kanji"
        }
        return "Today's adaptive focus: ${plan.remaining} of ${plan.target} left"
    }
}
