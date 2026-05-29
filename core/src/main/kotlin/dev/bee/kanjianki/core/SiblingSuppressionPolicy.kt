package dev.bee.kanjianki.core

internal class SiblingSuppressionPolicy {
    fun apply(
        items: List<RecordsStudyModels.StudyItem>,
        matureDays: Int = RecordsSyncModels.Settings.kikuDefaults().matureDays,
    ): List<RecordsStudyModels.StudyItem> {
        val byFamily = HashMap<String, MutableList<RecordsStudyModels.StudyItem>>()
        for (item in items) {
            byFamily.computeIfAbsent(familyKey(item)) { ArrayList() }.add(item)
        }
        val result = ArrayList<RecordsStudyModels.StudyItem>(items.size)
        for (item in items) {
            val siblings = byFamily[familyKey(item)].orEmpty()
            result.add(evaluateSuppression(item, siblings, matureDays))
        }
        return result
    }

    private fun evaluateSuppression(
        item: RecordsStudyModels.StudyItem,
        siblings: List<RecordsStudyModels.StudyItem>,
        matureDays: Int,
    ): RecordsStudyModels.StudyItem {
        if (StudyLadderRules.STATE_RETIRED == item.state) {
            return item
        }
        val dominator = findDominatingSibling(item, siblings, matureDays)
        val currentlySuppressed = item.suppressedByTaskType.isNotEmpty()
        if (dominator != null && item.suppressedByTaskType != dominator) {
            return item.copyBuilder()
                .suppressedByTaskType(dominator)
                .suppressedAtMillis(System.currentTimeMillis())
                .build()
        }
        if (dominator == null && currentlySuppressed) {
            return item.copyBuilder()
                .suppressedByTaskType(null)
                .suppressedAtMillis(0L)
                .build()
        }
        return item
    }

    private fun findDominatingSibling(
        item: RecordsStudyModels.StudyItem,
        siblings: List<RecordsStudyModels.StudyItem>,
        matureDays: Int,
    ): String? {
        val itemRung = item.rung
        for (sibling in siblings) {
            val skip = sibling === item ||
                StudyLadderRules.STATE_RETIRED == sibling.state ||
                !dominates(sibling, itemRung)
            if (!skip && (isActiveWritingRemediation(sibling) || isMature(sibling, matureDays))) {
                return sibling.rung.wireName()
            }
        }
        return null
    }

    private fun dominates(sibling: RecordsStudyModels.StudyItem, lower: RecordsBase.LadderRung): Boolean {
        val higher = sibling.rung
        if (higher == RecordsBase.LadderRung.WRITE_KANJI) {
            return lower != RecordsBase.LadderRung.WRITE_KANJI
        }
        if (higher == RecordsBase.LadderRung.WORD_READING) {
            return lower == RecordsBase.LadderRung.FONT_MEANING || lower == RecordsBase.LadderRung.KANJI_MEANING
        }
        if (higher == RecordsBase.LadderRung.FONT_MEANING) {
            return lower == RecordsBase.LadderRung.KANJI_MEANING
        }
        return false
    }

    private fun isActiveWritingRemediation(item: RecordsStudyModels.StudyItem): Boolean {
        return item.rung == RecordsBase.LadderRung.WRITE_KANJI &&
            item.writingRemediationPending &&
            StudyLadderRules.STATE_RETIRED != item.state
    }

    private fun isMature(item: RecordsStudyModels.StudyItem, matureDays: Int): Boolean {
        val memory = item.memoryForRung(item.rung)
        val intervalDays = if (memory.totalReviews > 0) memory.matureIntervalDays else item.matureIntervalDays
        val totalReviews = if (memory.totalReviews > 0) memory.totalReviews else item.totalReviews
        if (intervalDays < matureDays || totalReviews <= 0 || item.phase != RecordsBase.SchedulerPhase.REVIEW) {
            return false
        }
        if (StudyRatings.AGAIN == memory.lastRating) {
            return false
        }
        if (memory.lastRating.isNotEmpty()) {
            return StudyRatings.AGAIN != memory.lastRating
        }
        return true
    }

    private fun familyKey(item: RecordsStudyModels.StudyItem): String {
        return StudyQueueSeeder.familyKey(item)
    }
}
