package dev.bee.kanjianki.core

import java.security.SecureRandom
import java.util.Collections

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
        for (item in dueQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)) {
            if (item.dueAtMillis > horizon) {
                continue
            }
            // Skip an item with no dashboard row (e.g. its kanji was removed from the
            // collection after the study queue was built) rather than let it win and
            // then abort the whole session — a healthy due item behind it should still
            // be selectable.
            if (rowByKanji[item.kanji] == null) {
                continue
            }
            if (best == null || compareDueItems(item, best, rowByKanji, settings) < 0) {
                best = item
            }
        }
        if (best == null) {
            return null
        }
        return sessionForItem(best, rowByKanji, safeLadder)
    }

    fun debugTraceNextSession(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings?,
    ): SchedulerDecisionTrace {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        val rowByKanji = rowByKanji(rows)
        val activeByFamily = activeCandidatesByFamily(items, rows, allowedKanji, safeLadder)
        val activeItems = ArrayList<RecordsStudyModels.StudyItem>()
        val skipped = ArrayList<SchedulerDecisionTraceCandidate>()
        for (family in activeByFamily.values) {
            val active = activeFamilyItem(family, nowMillis, horizon, safeLadder, true)
            activeItems.add(active)
            for (item in family) {
                if (sameTraceItem(item, active)) {
                    continue
                }
                val reasons = queueReasonCodes(item, nowMillis, horizon)
                reasons.add("same_family_hidden")
                if (compareFamilyActivity(item, active, nowMillis, horizon, safeLadder, true) > 0) {
                    reasons.add("same_family_lower_priority")
                }
                skipped.add(traceCandidate(item, rowByKanji, reasons, nowMillis))
            }
        }
        skipped.sortWith(compareTraceCandidates())
        val dueCandidates = activeItems
            .filter { it.dueAtMillis <= horizon }
            .sortedWith { left, right -> compareDueItems(left, right, rowByKanji, settings) }
        val best = dueCandidates.firstOrNull()
        val candidates = ArrayList<SchedulerDecisionTraceCandidate>()
        for (item in dueCandidates) {
            val reasons = queueReasonCodes(item, nowMillis, horizon)
            if (best != null && sameTraceItem(item, best)) {
                reasons.add("selected_best_candidate")
            }
            candidates.add(traceCandidate(item, rowByKanji, reasons, nowMillis))
        }
        val selected = best?.let {
            val reasons = queueReasonCodes(it, nowMillis, horizon)
            reasons.add("selected_best_candidate")
            traceCandidate(it, rowByKanji, reasons, nowMillis)
        }
        return SchedulerDecisionTrace("next_session", nowMillis, selected, candidates, skipped, null, emptyList())
    }

    @Suppress("java:S2245")
    fun randomizedTaskKeys(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings?,
        randomSeed: Long?,
    ): List<String> {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        val rowByKanji = rowByKanji(rows)
        val dueItems = dueQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)
            .filter { it.dueAtMillis <= horizon }
            .sortedWith { left, right -> compareDueItems(left, right, rowByKanji, settings) }
            .toMutableList()
        shuffleDuePriorityBuckets(dueItems, randomSeed)
        return dueItems.map { sessionTaskKeyForItem(it, safeLadder) }
    }

    fun nextSessionForTaskKeys(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings?,
        taskKeys: List<String>,
    ): RecordsSchedulerModels.StudySession? {
        val dueItems = dueItemByTaskKey(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, ladder)
        for (taskKey in taskKeys) {
            val item = dueItems[taskKey]
            if (item != null) {
                return sessionForItem(item, rowByKanji(rows), StudyLadderRules.safeLadder(ladder))
            }
        }
        return null
    }

    fun sessionTaskKeyForItem(item: RecordsStudyModels.StudyItem?): String {
        return sessionTaskKeyForItem(item, RecordsBase.StudyLadderSettings.defaults())
    }

    private fun sessionTaskKeyForItem(
        item: RecordsStudyModels.StudyItem?,
        ladder: RecordsBase.StudyLadderSettings,
    ): String {
        if (item == null) {
            return ""
        }
        return AdaptiveStudyItemPolicy.taskTypeFor(item, ladder) + ":" + item.kanji
    }

    private fun dueItemByTaskKey(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Map<String, RecordsStudyModels.StudyItem> {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis)
        val rowByKanji = rowByKanji(rows)
        val out = LinkedHashMap<String, RecordsStudyModels.StudyItem>()
        for (item in dueQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)
            .filter { it.dueAtMillis <= horizon }
            .sortedWith { left, right -> compareDueItems(left, right, rowByKanji, settings) }) {
            out.putIfAbsent(sessionTaskKeyForItem(item, safeLadder), item)
        }
        return out
    }

    private fun sessionForItem(
        item: RecordsStudyModels.StudyItem,
        rowByKanji: Map<String, RecordsImportModels.DashboardRow>,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsSchedulerModels.StudySession? {
        val routedItem = AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
        val row = rowByKanji[routedItem.kanji] ?: return null
        val token = StudyTokenPolicy.studyItem(routedItem.kanji, routedItem.activeToken)
        val taskType = AdaptiveStudyItemPolicy.taskTypeFor(routedItem, ladder)
        val writingRequired = taskType == StudyTaskTypes.WRITE_KANJI
        return RecordsSchedulerModels.StudySession(routedItem.withToken(token), row, token, taskType, writingRequired, row.reasonText)
    }

    private fun rowByKanji(rows: List<RecordsImportModels.DashboardRow>): Map<String, RecordsImportModels.DashboardRow> {
        val out = HashMap<String, RecordsImportModels.DashboardRow>()
        for (row in rows) {
            out[row.kanji] = row
        }
        return out
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
        for (item in dueQueueItems(items, rows, nowMillis, studyAheadMillis, null, ladder)) {
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
        return familyQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, ladder, false)
    }

    fun focusQueueItems(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        return familyQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, ladder, true)
    }

    private fun dueQueueItems(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        return familyQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, ladder, true)
    }

    private fun familyQueueItems(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings?,
        preferDueEligible: Boolean,
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
            val effective = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
                AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
            } else {
                StudyLadderRules.alignRungToLadder(item, safeLadder)
            }
            if (isActiveQueueCandidate(effective, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, effective)
            }
        }
        val out = ArrayList<RecordsStudyModels.StudyItem>()
        for (family in byFamily.values) {
            out.add(activeFamilyItem(family, nowMillis, horizon, safeLadder, preferDueEligible))
        }
        return out
    }

    private fun isActiveQueueCandidate(
        item: RecordsStudyModels.StudyItem,
        currentRows: Set<String>,
        currentFamilies: Set<String>,
        allowedKanji: Set<String>?,
    ): Boolean {
        return isQueueVisible(item) &&
            (allowedKanji == null || allowedKanji.contains(item.kanji)) &&
            hasCurrentQueueRow(item, currentRows, currentFamilies)
    }

    private fun isQueueVisible(item: RecordsStudyModels.StudyItem): Boolean {
        return StudyLadderRules.STATE_RETIRED != item.state
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

    private fun activeCandidatesByFamily(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings,
    ): Map<String, MutableList<RecordsStudyModels.StudyItem>> {
        val currentRows = HashSet<String>()
        val currentFamilies = HashSet<String>()
        for (row in rows) {
            currentRows.add(row.kanji)
            currentFamilies.add(StudyQueueSeeder.rowFamilyKey(row))
        }
        val byFamily = HashMap<String, MutableList<RecordsStudyModels.StudyItem>>()
        for (item in items) {
            val effective = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
                AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
            } else {
                StudyLadderRules.alignRungToLadder(item, ladder)
            }
            if (isActiveQueueCandidate(effective, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, effective)
            }
        }
        return byFamily
    }

    private fun traceCandidate(
        item: RecordsStudyModels.StudyItem,
        rowByKanji: Map<String, RecordsImportModels.DashboardRow>,
        reasonCodes: List<String>,
        nowMillis: Long,
    ): SchedulerDecisionTraceCandidate {
        return SchedulerDecisionTraceCandidate(
            item.kanji,
            StudyTaskTypes.forRung(item.rung),
            item.rung,
            item.phase,
            traceDueAtMillis(item, nowMillis),
            reasonCodes,
            StudyQueueSeeder.familyKey(item),
            rowWeakness(item, rowByKanji),
        )
    }

    private fun traceDueAtMillis(item: RecordsStudyModels.StudyItem, nowMillis: Long): Long {
        return if (isUnseenNewItem(item)) nowMillis else item.dueAtMillis
    }

    private fun compareTraceCandidates(): Comparator<SchedulerDecisionTraceCandidate> {
        return compareBy<SchedulerDecisionTraceCandidate>(
            { it.kanji },
            { it.taskType },
            { it.dueAtMillis },
            { it.familyKey },
            { it.phase.wireName() },
            { it.rung.wireName() },
            { it.weaknessScore },
            { it.reasonCodes.joinToString("|") },
        )
    }

    private fun queueReasonCodes(
        item: RecordsStudyModels.StudyItem,
        nowMillis: Long,
        horizonMillis: Long,
    ): MutableList<String> {
        val reasons = ArrayList<String>()
        if (item.dueAtMillis > horizonMillis) {
            reasons.add("outside_study_ahead")
        } else if (item.dueAtMillis <= nowMillis) {
            reasons.add("due_now")
        } else {
            reasons.add("inside_study_ahead")
        }
        when (item.phase) {
            RecordsBase.SchedulerPhase.NEW_LEARNING -> {
                if (item.totalReviews == 0) {
                    reasons.add("new_learning_unseen")
                } else {
                    reasons.add("new_learning_step")
                }
            }
            RecordsBase.SchedulerPhase.RELEARNING -> reasons.add("relearning_due")
            RecordsBase.SchedulerPhase.REVIEW -> reasons.add("review_due")
        }
        return reasons
    }

    private fun sameTraceItem(
        left: RecordsStudyModels.StudyItem,
        right: RecordsStudyModels.StudyItem,
    ): Boolean {
        return left.kanji == right.kanji &&
            left.rung == right.rung &&
            left.phase == right.phase &&
            left.answerSignature == right.answerSignature &&
            left.dueAtMillis == right.dueAtMillis
    }

    private fun activeFamilyItem(
        family: List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
        horizonMillis: Long,
        ladder: RecordsBase.StudyLadderSettings,
        preferDueEligible: Boolean,
    ): RecordsStudyModels.StudyItem {
        var best: RecordsStudyModels.StudyItem? = null
        for (item in family) {
            if (best == null || compareFamilyActivity(item, best, nowMillis, horizonMillis, ladder, preferDueEligible) < 0) {
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
                val newCardSort = NewCardSortPlanner.compareRowsForSettings(
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

        fun shuffleDuePriorityBuckets(items: MutableList<RecordsStudyModels.StudyItem>, randomSeed: Long?) {
            val seed = randomSeed
            // Cosmetic queue ordering only. Deterministic (seeded) ordering uses the
            // seeded sort below; the null-seed path uses SecureRandom, which the Sonar
            // gate accepts for this non-security shuffle.
            val secureRandom: SecureRandom? = if (seed == null) SecureRandom() else null
            var start = 0
            while (start < items.size) {
                val priority = duePriority(items[start])
                var end = start + 1
                while (end < items.size && duePriority(items[end]) == priority) {
                    end++
                }
                if (end - start > 1) {
                    val bucket = items.subList(start, end)
                    if (secureRandom != null) {
                        Collections.shuffle(bucket, secureRandom)
                    } else {
                        val nonNullSeed = seed!!
                        bucket.sortWith(
                            compareBy<RecordsStudyModels.StudyItem> { seededShuffleRank(nonNullSeed, it) }
                                .thenBy { taskKeyForSeededShuffle(it) }
                        )
                    }
                }
                start = end
            }
        }

        fun seededShuffleRank(seed: Long, item: RecordsStudyModels.StudyItem): Long {
            var hash = seed xor -7046029254386353131L
            val key = taskKeyForSeededShuffle(item)
            for (index in key.indices) {
                hash = java.lang.Long.rotateLeft(hash xor key[index].code.toLong(), 27) * 1099511628211L
            }
            return hash
        }

        fun taskKeyForSeededShuffle(item: RecordsStudyModels.StudyItem): String {
            return AdaptiveStudyItemPolicy.taskTypeFor(item, RecordsBase.StudyLadderSettings.defaults()) + ":" + item.kanji
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
            horizonMillis: Long,
            ladder: RecordsBase.StudyLadderSettings?,
            preferDueEligible: Boolean,
        ): Int {
            val safeLadder = StudyLadderRules.safeLadder(ladder)
            if (preferDueEligible) {
                val eligible = (if (left.dueAtMillis <= horizonMillis) 0 else 1)
                    .compareTo(if (right.dueAtMillis <= horizonMillis) 0 else 1)
                if (eligible != 0) {
                    return eligible
                }
                val ankiGatherOrder = duePriority(left).compareTo(duePriority(right))
                if (ankiGatherOrder != 0) {
                    return ankiGatherOrder
                }
                val due = (if (left.dueAtMillis <= nowMillis) 0 else 1)
                    .compareTo(if (right.dueAtMillis <= nowMillis) 0 else 1)
                if (due != 0) {
                    return due
                }
            }
            val rank = (-safeLadder.rankForRung(left.rung)).compareTo(-safeLadder.rankForRung(right.rung))
            if (rank != 0) {
                return rank
            }
            if (!preferDueEligible) {
                val due = (if (left.dueAtMillis <= horizonMillis) 0 else 1)
                    .compareTo(if (right.dueAtMillis <= horizonMillis) 0 else 1)
                if (due != 0) {
                    return due
                }
            }
            return left.dueAtMillis.compareTo(right.dueAtMillis)
        }
    }
}
