package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import java.util.UUID

class StudySessionSelector(
    private val activeQueueSelector: StudyActiveQueueSelector = StudyActiveQueueSelector(),
    private val studyAheadPolicy: StudyAheadPolicy = StudyAheadPolicy(),
    private val newCardSortPolicy: NewCardSortPolicy = NewCardSortPolicy(),
    private val tokenFactory: (StudyQueueItem) -> String = { item -> "${item.kanji}-${UUID.randomUUID()}" },
) {
    fun nextSession(input: NextSessionInput): StudySession? {
        val rowByKanji = input.rows.associateBy { it.kanji }
        val horizon = studyAheadPolicy.horizon(input.nowMillis, input.studyAheadMillis)
        val active = activeQueueSelector.activeQueueItems(input.toActiveQueueInput())
        val best = active
            .asSequence()
            .filter { it.dueAtMillis <= horizon }
            .minWithOrNull { left, right -> compareDueItems(left, right, rowByKanji, input.newCardSortMode) }
            ?: return null

        val row = rowByKanji[best.kanji]
        val token = best.activeToken?.takeIf { it.isNotEmpty() } ?: tokenFactory(best)
        val taskType = best.rung.wireName
        return StudySession(
            item = best.copy(activeToken = token),
            row = row,
            token = token,
            taskType = taskType,
            writingRequired = best.rung == StudyRung.WRITE_KANJI,
            prompt = row?.reasonText.orEmpty(),
        )
    }

    private fun NextSessionInput.toActiveQueueInput(): ActiveQueueInput = ActiveQueueInput(
        items = items,
        rows = rows,
        nowMillis = nowMillis,
        studyAheadMillis = studyAheadMillis,
        allowedKanji = allowedKanji,
        ladderSettings = ladderSettings,
    )

    private fun compareDueItems(
        left: StudyQueueItem,
        right: StudyQueueItem,
        rowByKanji: Map<String, StudyDashboardRow>,
        newCardSortMode: NewCardSortMode,
    ): Int {
        val priority = duePriority(left).compareTo(duePriority(right))
        if (priority != 0) {
            return priority
        }
        val due = left.dueAtMillis.compareTo(right.dueAtMillis)
        if (due != 0) {
            return due
        }
        if (left.isUnseenNewItem() && right.isUnseenNewItem()) {
            val newCardSort = newCardSortPolicy.compare(
                rowByKanji[left.kanji],
                rowByKanji[right.kanji],
                newCardSortMode,
            )
            if (newCardSort != 0) {
                return newCardSort
            }
        }
        val weakness = rowWeakness(rowByKanji[right.kanji]).compareTo(rowWeakness(rowByKanji[left.kanji]))
        if (weakness != 0) {
            return weakness
        }
        return left.kanji.compareTo(right.kanji)
    }

    private fun StudyQueueItem.isUnseenNewItem(): Boolean =
        phase == StudyPhase.NEW_LEARNING && totalReviews == 0

    private fun duePriority(item: StudyQueueItem): Int = when {
        item.rung == StudyRung.WRITE_KANJI || item.phase == StudyPhase.RELEARNING -> 0
        item.phase == StudyPhase.NEW_LEARNING -> if (item.totalReviews > 0) 0 else 2
        else -> 1
    }

    private fun rowWeakness(row: StudyDashboardRow?): Int = row?.weaknessScore ?: 0
}

data class NextSessionInput(
    val items: List<StudyQueueItem>,
    val rows: List<StudyDashboardRow>,
    val nowMillis: Long,
    val studyAheadMillis: Long = 0L,
    val allowedKanji: Set<String>? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    val newCardSortMode: NewCardSortMode = NewCardSortMode.default,
)

data class StudySession(
    val item: StudyQueueItem,
    val row: StudyDashboardRow?,
    val token: String,
    val taskType: String,
    val writingRequired: Boolean,
    val prompt: String,
)
