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
        val safeRows = rows.orEmpty()
        if (safeRows.isEmpty()) {
            return emptyMap()
        }
        val rowsByKanji = java.util.LinkedHashMap<String, RecordsImportModels.DashboardRow>(safeRows.size)
        for (row in safeRows) {
            if (row != null) {
                rowsByKanji.putIfAbsent(row.kanji, row)
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
        val safeItems = items.orEmpty()
        if (safeItems.isEmpty()) {
            return emptyMap()
        }
        val itemsByKanji = java.util.LinkedHashMap<String, RecordsStudyModels.StudyItem>(safeItems.size)
        for (item in safeItems) {
            if (item != null) {
                itemsByKanji.putIfAbsent(item.kanji, item)
            }
        }
        return itemsByKanji
    }
}
