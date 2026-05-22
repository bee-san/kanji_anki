package dev.bee.kanjianki.core

object FocusQueuePolicy {
    @JvmStatic
    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<QueueEntry> {
        val safeRows = rows.orEmpty()
        val rowByKanji = HashMap<String, RecordsImportModels.DashboardRow>()
        for (row in safeRows) {
            rowByKanji[row.kanji] = row
        }
        val focusOrder = focusOrder(plan)
        val entries = ArrayList<QueueEntry>()
        val activeItems = BridgeScheduler().activeQueueItems(
            items.orEmpty(),
            safeRows,
            nowMillis,
            studyAheadMillis,
            null,
            ladder,
        )
        for (item in activeItems) {
            val row = rowByKanji[item.kanji]
            if (row != null) {
                entries.add(QueueEntry(row, item))
            }
        }
        entries.sortWith(
            compareBy<QueueEntry> { focusOrder.getOrDefault(it.row.kanji, Int.MAX_VALUE) }
                .thenBy { if (it.item.dueAtMillis <= nowMillis) 0 else 1 }
                .thenBy { stateRank(it.item.state) }
                .thenBy { it.item.dueAtMillis }
                .thenBy { -it.row.weaknessScore }
                .thenBy { it.row.kanji },
        )
        return entries
    }

    private fun focusOrder(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): Map<String, Int> {
        val focusOrder = HashMap<String, Int>()
        if (plan != null) {
            for (i in plan.focusKanji.indices) {
                focusOrder[plan.focusKanji[i]] = i
            }
        }
        return focusOrder
    }

    @JvmStatic
    fun stateRank(state: String?): Int {
        if (StudyLadderRules.STATE_LEARNING == state) {
            return 0
        }
        if (StudyLadderRules.STATE_REVIEW == state) {
            return 1
        }
        if (StudyLadderRules.STATE_NEW == state) {
            return 2
        }
        return 3
    }

    @JvmStatic
    fun rowTone(item: RecordsStudyModels.StudyItem?, nowMillis: Long): QueueTone {
        if (item != null && item.dueAtMillis <= nowMillis) {
            return QueueTone.DUE
        }
        if (item != null && StudyLadderRules.STATE_LEARNING == item.state) {
            return QueueTone.LEARNING
        }
        return QueueTone.RESTING
    }

    enum class QueueTone {
        DUE,
        LEARNING,
        RESTING,
    }

    open class QueueEntry(
        @JvmField val row: RecordsImportModels.DashboardRow,
        @JvmField val item: RecordsStudyModels.StudyItem,
    )
}
