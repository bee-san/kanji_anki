package dev.bee.kanjianki.core

internal class SiblingSuppressionPolicy {
    fun apply(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem> {
        val byKanji = HashMap<String, MutableList<RecordsStudyModels.StudyItem>>()
        for (item in items) {
            byKanji.computeIfAbsent(item.kanji) { ArrayList() }.add(item)
        }
        val result = ArrayList<RecordsStudyModels.StudyItem>(items.size)
        for (item in items) {
            val siblings = byKanji[item.kanji].orEmpty()
            result.add(evaluateSuppression(item, siblings))
        }
        return result
    }

    private fun evaluateSuppression(
        item: RecordsStudyModels.StudyItem,
        siblings: List<RecordsStudyModels.StudyItem>,
    ): RecordsStudyModels.StudyItem {
        if (StudyLadderRules.STATE_RETIRED == item.state) {
            return item
        }
        val dominator = findDominatingMatureSibling(item, siblings)
        val currentlySuppressed = item.suppressedByTaskType.isNotEmpty()
        if (dominator != null && !currentlySuppressed) {
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

    private fun findDominatingMatureSibling(
        item: RecordsStudyModels.StudyItem,
        siblings: List<RecordsStudyModels.StudyItem>,
    ): String? {
        val itemRung = item.rung
        for (sibling in siblings) {
            val skip = sibling === item ||
                StudyLadderRules.STATE_RETIRED == sibling.state ||
                !dominates(sibling.rung, itemRung)
            if (!skip && isMature(sibling)) {
                return sibling.rung.wireName()
            }
        }
        return null
    }

    private fun dominates(higher: RecordsBase.LadderRung, lower: RecordsBase.LadderRung): Boolean {
        if (higher == RecordsBase.LadderRung.WORD_READING) {
            return lower == RecordsBase.LadderRung.FONT_MEANING || lower == RecordsBase.LadderRung.KANJI_MEANING
        }
        if (higher == RecordsBase.LadderRung.FONT_MEANING) {
            return lower == RecordsBase.LadderRung.KANJI_MEANING
        }
        return false
    }

    private fun isMature(item: RecordsStudyModels.StudyItem): Boolean {
        return item.matureIntervalDays >= MATURE_DAYS_THRESHOLD &&
            item.totalReviews > 0 &&
            item.phase == RecordsBase.SchedulerPhase.REVIEW
    }

    private companion object {
        const val MATURE_DAYS_THRESHOLD = 21
    }
}
