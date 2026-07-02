package dev.bee.kanjianki.core

data class HomeDeckOverview(
    val dueCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val relearningCount: Int,
    val suspendedCount: Int,
) {
    fun rows(): List<String> {
        return HomeDeckOverviewPolicy.summaryRows(this)
    }
}

object HomeDeckOverviewPolicy {
    @JvmStatic
    fun from(
        studyItems: List<RecordsStudyModels.StudyItem>,
        dashboardRows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        locallySuspendedKanji: Set<String>,
    ): HomeDeckOverview {
        val activeFamilyKeys = HashSet<String>(dashboardRows.size)
        val activeRows = HashSet<String>(dashboardRows.size)
        for (row in dashboardRows) {
            activeFamilyKeys.add(StudyQueueSeeder.rowFamilyKey(row))
            activeRows.add(row.kanji)
        }

        var dueCount = 0
        var newCount = 0
        var learningCount = 0
        var relearningCount = 0

        for (item in studyItems) {
            if (!isActiveStudyItem(item, activeFamilyKeys, activeRows)) {
                continue
            }
            when {
                item.state == StudyLadderRules.STATE_REVIEW && item.dueAtMillis <= nowMillis -> dueCount++
                item.state == StudyLadderRules.STATE_NEW -> newCount++
                item.state == StudyLadderRules.STATE_LEARNING && item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING -> learningCount++
                item.state == StudyLadderRules.STATE_LEARNING && item.phase == RecordsBase.SchedulerPhase.RELEARNING -> relearningCount++
            }
        }

        val suspendedCount = locallySuspendedKanji.count { it in activeRows }

        return HomeDeckOverview(
            dueCount = dueCount,
            newCount = newCount,
            learningCount = learningCount,
            relearningCount = relearningCount,
            suspendedCount = suspendedCount,
        )
    }

    @JvmStatic
    fun summaryRows(overview: HomeDeckOverview): List<String> {
        val rows = ArrayList<String>(5)
        appendCount(rows, HomeTextCopy.deckOverviewDueLabel(), overview.dueCount)
        appendCount(rows, HomeTextCopy.deckOverviewNewLabel(), overview.newCount)
        appendCount(rows, HomeTextCopy.deckOverviewLearningLabel(), overview.learningCount)
        appendCount(rows, HomeTextCopy.deckOverviewRelearningLabel(), overview.relearningCount)
        appendCount(rows, HomeTextCopy.deckOverviewSuspendedLabel(), overview.suspendedCount)
        return rows
    }

    private fun appendCount(rows: MutableList<String>, label: String, count: Int) {
        if (count > 0) {
            rows.add("$label $count")
        }
    }

    private fun isActiveStudyItem(
        item: RecordsStudyModels.StudyItem,
        activeFamilyKeys: Set<String>,
        activeRows: Set<String>,
    ): Boolean {
        if (item.answerSignature.isEmpty() && item.kanji in activeRows) {
            return true
        }
        return StudyQueueSeeder.familyKey(item) in activeFamilyKeys
    }
}
