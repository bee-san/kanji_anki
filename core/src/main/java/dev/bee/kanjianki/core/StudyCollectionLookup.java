package dev.bee.kanjianki.core;

import java.util.List;

public final class StudyCollectionLookup {
    private StudyCollectionLookup() {
    }

    public static RecordsImportModels.DashboardRow dashboardRowByKanji(
            List<RecordsImportModels.DashboardRow> rows,
            String kanji
    ) {
        if (rows == null || kanji == null) {
            return null;
        }
        for (RecordsImportModels.DashboardRow row : rows) {
            if (row != null && kanji.equals(row.kanji)) {
                return row;
            }
        }
        return null;
    }

    public static RecordsStudyModels.StudyItem studyItemByKanji(
            List<RecordsStudyModels.StudyItem> items,
            String kanji
    ) {
        if (items == null || kanji == null) {
            return null;
        }
        for (RecordsStudyModels.StudyItem item : items) {
            if (item != null && kanji.equals(item.kanji)) {
                return item;
            }
        }
        return null;
    }
}
