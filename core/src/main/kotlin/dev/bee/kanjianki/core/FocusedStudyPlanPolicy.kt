package dev.bee.kanjianki.core

object FocusedStudyPlanPolicy {
    @JvmStatic
    fun studyMoreNewCardsPlan(
        requestedKanji: List<String>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val focus = ArrayList<String>()
        val rowsByKanji = StudyCollectionLookup.dashboardRowsByKanji(rows.orEmpty())
        for (kanji in requestedKanji.orEmpty()) {
            if (rowsByKanji.containsKey(kanji)) {
                focus.add(kanji)
            }
        }
        var remaining = 0
        val itemsByKanji = StudyCollectionLookup.studyItemsByKanji(items.orEmpty())
        for (kanji in focus) {
            val item = itemsByKanji[kanji]
            if (itemDueForFocus(item, nowMillis)) {
                remaining++
            }
        }
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            100,
            focus.size,
            remaining,
            focus,
            0,
            false,
            "Custom study: " + StudyTextCopy.countText(focus.size, "extra new card", "extra new cards") + ".",
        )
    }

    @JvmStatic
    fun allCurrentProblemKanjiPlan(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        studiedToday: Set<String>?,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val focus = ArrayList<String>()
        for (row in rows.orEmpty()) {
            focus.add(row.kanji)
        }
        var remaining = 0
        val itemsByKanji = StudyCollectionLookup.studyItemsByKanji(items.orEmpty())
        val safeStudied = studiedToday.orEmpty()
        for (kanji in focus) {
            val item = itemsByKanji[kanji]
            if (!safeStudied.contains(kanji) || itemDueForFocus(item, nowMillis)) {
                remaining++
            }
        }
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            100,
            focus.size,
            remaining,
            focus,
            focus.size,
            true,
            "All current problem kanji are available today.",
        )
    }

    @JvmStatic
    fun itemDueForFocus(item: RecordsStudyModels.StudyItem?, nowMillis: Long): Boolean {
        if (item == null || StudyLadderRules.STATE_RETIRED == item.state) {
            return false
        }
        if (StudyLadderRules.STATE_LEARNING == item.state) {
            return item.dueAtMillis <= nowMillis
        }
        return item.totalReviews > 0 && item.dueAtMillis <= nowMillis
    }
}
