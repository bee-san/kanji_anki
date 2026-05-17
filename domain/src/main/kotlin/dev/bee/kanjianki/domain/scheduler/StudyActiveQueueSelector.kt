package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung

class StudyActiveQueueSelector(
    private val studyAheadPolicy: StudyAheadPolicy = StudyAheadPolicy(),
) {
    fun activeQueueItems(input: ActiveQueueInput): List<StudyQueueItem> {
        val horizon = studyAheadPolicy.horizon(input.nowMillis, input.studyAheadMillis)
        val currentRows = input.rows.mapTo(mutableSetOf()) { it.kanji }
        val currentFamilies = input.rows.mapTo(mutableSetOf()) { it.familyKey() }
        val byFamily = linkedMapOf<String, MutableList<StudyQueueItem>>()

        for (item in input.items) {
            val effective = item.alignTo(input.ladderSettings)
            if (effective.isActiveQueueCandidate(currentRows, currentFamilies, input.allowedKanji)) {
                byFamily.getOrPut(effective.familyKey) { mutableListOf() }.add(effective)
            }
        }

        return byFamily.values.map { family ->
            activeFamilyItem(family, horizon, input.ladderSettings)
        }
    }

    fun dueCount(input: ActiveQueueInput): Int {
        val horizon = studyAheadPolicy.horizon(input.nowMillis, input.studyAheadMillis)
        return activeQueueItems(input).count { it.dueAtMillis <= horizon }
    }

    private fun StudyQueueItem.alignTo(settings: StudyLadderSettings): StudyQueueItem {
        val effectiveRung = settings.effectiveRung(rung, hasSimilarKanji)
        return if (effectiveRung == rung) this else copy(rung = effectiveRung)
    }

    private fun StudyQueueItem.isActiveQueueCandidate(
        currentRows: Set<String>,
        currentFamilies: Set<String>,
        allowedKanji: Set<String>?,
    ): Boolean = state != StudyItemState.RETIRED &&
        !isSuppressed &&
        (allowedKanji == null || allowedKanji.contains(kanji)) &&
        hasCurrentQueueRow(currentRows, currentFamilies)

    private fun StudyQueueItem.hasCurrentQueueRow(
        currentRows: Set<String>,
        currentFamilies: Set<String>,
    ): Boolean = currentFamilies.contains(familyKey) ||
        (answerSignature.isEmpty() && currentRows.contains(kanji))

    private fun activeFamilyItem(
        family: List<StudyQueueItem>,
        horizonMillis: Long,
        settings: StudyLadderSettings,
    ): StudyQueueItem = family.minWith { left, right ->
        compareFamilyActivity(left, right, horizonMillis, settings)
    }

    private fun compareFamilyActivity(
        left: StudyQueueItem,
        right: StudyQueueItem,
        horizonMillis: Long,
        settings: StudyLadderSettings,
    ): Int {
        val rung = rankForRung(right.rung, settings).compareTo(rankForRung(left.rung, settings))
        if (rung != 0) {
            return rung
        }
        val due = dueBucket(left, horizonMillis).compareTo(dueBucket(right, horizonMillis))
        if (due != 0) {
            return due
        }
        return left.dueAtMillis.compareTo(right.dueAtMillis)
    }

    private fun rankForRung(
        rung: StudyRung,
        settings: StudyLadderSettings,
    ): Int = settings.orderedRungs.indexOf(rung).takeIf { it >= 0 }
        ?: StudyRung.defaultOrder.indexOf(rung)

    private fun dueBucket(
        item: StudyQueueItem,
        horizonMillis: Long,
    ): Int = if (item.dueAtMillis <= horizonMillis) 0 else 1
}

data class ActiveQueueInput(
    val items: List<StudyQueueItem>,
    val rows: List<StudyDashboardRow>,
    val nowMillis: Long,
    val studyAheadMillis: Long = 0L,
    val allowedKanji: Set<String>? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
)
