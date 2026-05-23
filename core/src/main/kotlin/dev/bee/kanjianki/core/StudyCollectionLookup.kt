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
}
