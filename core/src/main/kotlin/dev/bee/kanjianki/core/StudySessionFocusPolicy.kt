package dev.bee.kanjianki.core

import java.util.Objects

object StudySessionFocusPolicy {
    @JvmStatic
    fun allowedKanji(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        continueAllKanjiSession: Boolean,
    ): Set<String>? {
        val activePlan = Objects.requireNonNull(plan, "plan")!!
        if (continueAllKanjiSession || activePlan.allKanjiMode) {
            return null
        }
        return HashSet(activePlan.focusKanji)
    }
}
