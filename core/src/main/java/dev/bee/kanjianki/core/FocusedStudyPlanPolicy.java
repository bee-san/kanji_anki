package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class FocusedStudyPlanPolicy {
    private FocusedStudyPlanPolicy() {
    }

    public static RecordsSchedulerModels.AdaptiveLoadPlan studyMoreNewCardsPlan(
            List<String> requestedKanji,
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            long nowMillis
    ) {
        List<String> focus = new ArrayList<>();
        List<RecordsImportModels.DashboardRow> safeRows = safeRows(rows);
        for (String kanji : safeRequestedKanji(requestedKanji)) {
            if (StudyCollectionLookup.dashboardRowByKanji(safeRows, kanji) != null) {
                focus.add(kanji);
            }
        }
        int remaining = 0;
        List<RecordsStudyModels.StudyItem> safeItems = safeItems(items);
        for (String kanji : focus) {
            RecordsStudyModels.StudyItem item = StudyCollectionLookup.studyItemByKanji(safeItems, kanji);
            if (itemDueForFocus(item, nowMillis)) {
                remaining++;
            }
        }
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                false,
                100,
                focus.size(),
                remaining,
                focus,
                0,
                false,
                "Custom study: " + StudyTextCopy.countText(focus.size(), "extra new card", "extra new cards") + "."
        );
    }

    public static RecordsSchedulerModels.AdaptiveLoadPlan allCurrentProblemKanjiPlan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            Set<String> studiedToday,
            long nowMillis
    ) {
        List<String> focus = new ArrayList<>();
        for (RecordsImportModels.DashboardRow row : safeRows(rows)) {
            focus.add(row.kanji);
        }
        int remaining = 0;
        List<RecordsStudyModels.StudyItem> safeItems = safeItems(items);
        Set<String> safeStudied = studiedToday == null ? Collections.emptySet() : studiedToday;
        for (String kanji : focus) {
            RecordsStudyModels.StudyItem item = StudyCollectionLookup.studyItemByKanji(safeItems, kanji);
            if (!safeStudied.contains(kanji) || itemDueForFocus(item, nowMillis)) {
                remaining++;
            }
        }
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                false,
                100,
                focus.size(),
                remaining,
                focus,
                focus.size(),
                true,
                "All current problem kanji are available today."
        );
    }

    public static boolean itemDueForFocus(RecordsStudyModels.StudyItem item, long nowMillis) {
        if (item == null || StudyLadderRules.STATE_RETIRED.equals(item.state)) {
            return false;
        }
        if (StudyLadderRules.STATE_LEARNING.equals(item.state)) {
            return item.dueAtMillis <= nowMillis;
        }
        return item.totalReviews > 0 && item.dueAtMillis <= nowMillis;
    }

    private static List<String> safeRequestedKanji(List<String> requestedKanji) {
        return requestedKanji == null ? Collections.emptyList() : requestedKanji;
    }

    private static List<RecordsImportModels.DashboardRow> safeRows(List<RecordsImportModels.DashboardRow> rows) {
        return rows == null ? Collections.emptyList() : rows;
    }

    private static List<RecordsStudyModels.StudyItem> safeItems(List<RecordsStudyModels.StudyItem> items) {
        return items == null ? Collections.emptyList() : items;
    }
}
