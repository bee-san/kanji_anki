package dev.bee.kanjianki.core

object StudyCollectionLookup {
    @JvmStatic
    fun dashboardRowByKanji(
        rows: List<RecordsImportModels.DashboardRow?>?,
        kanji: String?,
    ): RecordsImportModels.DashboardRow? {
        if (rows == null || kanji == null) {
            return null
        }
        for (row in rows) {
            if (row != null && kanji == row.kanji) {
                return row
            }
        }
        return null
    }

    @JvmStatic
    fun dashboardRowsByKanji(
        rows: List<RecordsImportModels.DashboardRow?>?,
    ): Map<String, RecordsImportModels.DashboardRow> {
        val rowsByKanji = linkedMapOf<String, RecordsImportModels.DashboardRow>()
        if (rows == null) {
            return rowsByKanji
        }
        for (row in rows) {
            if (row != null && !rowsByKanji.containsKey(row.kanji)) {
                rowsByKanji[row.kanji] = row
            }
        }
        return rowsByKanji
    }

    @JvmStatic
    fun studyItemByKanji(
        items: List<RecordsStudyModels.StudyItem?>?,
        kanji: String?,
    ): RecordsStudyModels.StudyItem? {
        if (items == null || kanji == null) {
            return null
        }
        for (item in items) {
            if (item != null && kanji == item.kanji) {
                return item
            }
        }
        return null
    }

    @JvmStatic
    fun studyItemsByKanji(
        items: List<RecordsStudyModels.StudyItem?>?,
    ): Map<String, RecordsStudyModels.StudyItem> {
        val itemsByKanji = linkedMapOf<String, RecordsStudyModels.StudyItem>()
        if (items == null) {
            return itemsByKanji
        }
        for (item in items) {
            if (item != null && !itemsByKanji.containsKey(item.kanji)) {
                itemsByKanji[item.kanji] = item
            }
        }
        return itemsByKanji
    }
}
