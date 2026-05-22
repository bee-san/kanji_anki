package dev.bee.kanjianki.core

class StudySessionSelector {
    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsSchedulerModels.StudySession? {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        val rowByKanji = HashMap<String, RecordsImportModels.DashboardRow>()
        for (row in rows) {
            rowByKanji[row.kanji] = row
        }
        var best: RecordsStudyModels.StudyItem? = null
        for (item in activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)) {
            if (item.dueAtMillis > horizon) {
                continue
            }
            if (best == null || compareDueItems(item, best, rowByKanji, settings) < 0) {
                best = item
            }
        }
        if (best == null) {
            return null
        }
        val row = rowByKanji[best.kanji]
        val token = StudyTokenPolicy.studyItem(best.kanji, best.activeToken)
        val taskType = StudyTaskTypes.forRung(best.rung)
        val writingRequired = best.rung == RecordsBase.LadderRung.WRITE_KANJI
        val prompt = row!!.reasonText
        return RecordsSchedulerModels.StudySession(best.withToken(token), row, token, taskType, writingRequired, prompt)
    }

    fun dueCount(
        items: List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
        studyAheadMillis: Long,
    ): Int {
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        var count = 0
        for (item in items) {
            if (StudyLadderRules.STATE_RETIRED != item.state && item.dueAtMillis <= horizon) {
                count++
            }
        }
        return count
    }

    fun dueCount(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Int {
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        var count = 0
        for (item in activeQueueItems(items, rows, nowMillis, studyAheadMillis, null, ladder)) {
            if (item.dueAtMillis <= horizon) {
                count++
            }
        }
        return count
    }

    fun activeQueueItems(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        val currentRows = HashSet<String>()
        val currentFamilies = HashSet<String>()
        for (row in rows) {
            currentRows.add(row.kanji)
            currentFamilies.add(StudyQueueSeeder.rowFamilyKey(row))
        }
        val byFamily = HashMap<String, MutableList<RecordsStudyModels.StudyItem>>()
        for (item in items) {
            val effective = StudyLadderRules.alignRungToLadder(item, safeLadder)
            if (isActiveQueueCandidate(effective, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, effective)
            }
        }
        val out = ArrayList<RecordsStudyModels.StudyItem>()
        for (family in byFamily.values) {
            out.add(activeFamilyItem(family, horizon, safeLadder))
        }
        return out
    }

    private fun isActiveQueueCandidate(
        item: RecordsStudyModels.StudyItem,
        currentRows: Set<String>,
        currentFamilies: Set<String>,
        allowedKanji: Set<String>?,
    ): Boolean {
        return StudyLadderRules.STATE_RETIRED != item.state &&
            item.suppressedByTaskType.isEmpty() &&
            (allowedKanji == null || allowedKanji.contains(item.kanji)) &&
            hasCurrentQueueRow(item, currentRows, currentFamilies)
    }

    private fun hasCurrentQueueRow(
        item: RecordsStudyModels.StudyItem,
        currentRows: Set<String>,
        currentFamilies: Set<String>,
    ): Boolean {
        return currentFamilies.contains(StudyQueueSeeder.familyKey(item)) ||
            (item.answerSignature.isEmpty() && currentRows.contains(item.kanji))
    }

    private fun addFamilyItem(
        byFamily: MutableMap<String, MutableList<RecordsStudyModels.StudyItem>>,
        item: RecordsStudyModels.StudyItem,
    ) {
        val itemFamilyKey = StudyQueueSeeder.familyKey(item)
        byFamily.computeIfAbsent(itemFamilyKey) { ArrayList() }.add(item)
    }

    private fun activeFamilyItem(
        family: List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        var best: RecordsStudyModels.StudyItem? = null
        for (item in family) {
            if (best == null || compareFamilyActivity(item, best, nowMillis, ladder) < 0) {
                best = item
            }
        }
        return best!!
    }

    private companion object {
        fun compareDueItems(
            left: RecordsStudyModels.StudyItem,
            right: RecordsStudyModels.StudyItem,
            rowByKanji: Map<String, RecordsImportModels.DashboardRow>,
            settings: RecordsSyncModels.Settings,
        ): Int {
            val priority = duePriority(left).compareTo(duePriority(right))
            if (priority != 0) {
                return priority
            }
            val due = left.dueAtMillis.compareTo(right.dueAtMillis)
            if (due != 0) {
                return due
            }
            if (isUnseenNewItem(left) && isUnseenNewItem(right)) {
                val newCardSort = StudyQueueSeeder.compareRowsForNewCardSort(
                    rowByKanji[left.kanji],
                    rowByKanji[right.kanji],
                    settings,
                )
                if (newCardSort != 0) {
                    return newCardSort
                }
            }
            val weakness = rowWeakness(right, rowByKanji).compareTo(rowWeakness(left, rowByKanji))
            if (weakness != 0) {
                return weakness
            }
            return left.kanji.compareTo(right.kanji)
        }

        fun isUnseenNewItem(item: RecordsStudyModels.StudyItem): Boolean {
            return item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews == 0
        }

        fun duePriority(item: RecordsStudyModels.StudyItem): Int {
            if (item.rung == RecordsBase.LadderRung.WRITE_KANJI || item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                return 0
            }
            if (item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING) {
                return if (item.totalReviews > 0) 0 else 2
            }
            return 1
        }

        fun rowWeakness(
            item: RecordsStudyModels.StudyItem,
            rowByKanji: Map<String, RecordsImportModels.DashboardRow>,
        ): Int {
            return rowByKanji[item.kanji]?.weaknessScore ?: 0
        }

        fun compareFamilyActivity(
            left: RecordsStudyModels.StudyItem,
            right: RecordsStudyModels.StudyItem,
            nowMillis: Long,
            ladder: RecordsBase.StudyLadderSettings?,
        ): Int {
            val safeLadder = StudyLadderRules.safeLadder(ladder)
            val rank = (-safeLadder.rankForRung(left.rung)).compareTo(-safeLadder.rankForRung(right.rung))
            if (rank != 0) {
                return rank
            }
            val due = (if (left.dueAtMillis <= nowMillis) 0 else 1)
                .compareTo(if (right.dueAtMillis <= nowMillis) 0 else 1)
            if (due != 0) {
                return due
            }
            return left.dueAtMillis.compareTo(right.dueAtMillis)
        }
    }
}
