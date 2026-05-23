package dev.bee.kanjianki.core

import java.util.Objects

object StudyPlanSelectionPolicy {
    @JvmStatic
    fun select(
        extraNewCardKanji: List<String>?,
        continueAllKanjiSession: Boolean,
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        studiedToday: Set<String>?,
        nowMillis: Long,
        adaptivePlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val safeExtra = extraNewCardKanji ?: emptyList()
        if (safeExtra.isNotEmpty()) {
            return FocusedStudyPlanPolicy.studyMoreNewCardsPlan(safeExtra, rows, items, nowMillis)
        }
        if (continueAllKanjiSession) {
            return FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(rows, items, studiedToday, nowMillis)
        }
        return Objects.requireNonNull(adaptivePlan, "adaptivePlan")!!
    }
}
