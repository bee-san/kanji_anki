package dev.bee.kanjianki.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StudyPlanSelectionPolicy {
    private StudyPlanSelectionPolicy() {
    }

    public static RecordsSchedulerModels.AdaptiveLoadPlan select(
            List<String> extraNewCardKanji,
            boolean continueAllKanjiSession,
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            Set<String> studiedToday,
            long nowMillis,
            RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan
    ) {
        List<String> safeExtra = extraNewCardKanji == null ? Collections.emptyList() : extraNewCardKanji;
        if (!safeExtra.isEmpty()) {
            return FocusedStudyPlanPolicy.studyMoreNewCardsPlan(safeExtra, rows, items, nowMillis);
        }
        if (continueAllKanjiSession) {
            return FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(rows, items, studiedToday, nowMillis);
        }
        return Objects.requireNonNull(adaptivePlan, "adaptivePlan");
    }
}
