package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import kotlin.math.min

class StudyQueueSeeder(
    private val newCardSortPolicy: NewCardSortPolicy = NewCardSortPolicy(),
) {
    fun seed(request: StudyQueueSeedRequest): List<StudyQueueItem> {
        val admissionRows = request.admissionRows()
            .sortedWith { left, right ->
                newCardSortPolicy.compare(left, right, request.settings.newCardSortMode)
            }
        val state = reconcileExistingItems(request)
        for (row in admissionRows) {
            admitSeedRow(request, state, row)
        }
        return state.items.sortedWith(seedItemComparator)
    }

    private fun StudyQueueSeedRequest.admissionRows(): List<StudyDashboardRow> {
        val plan = adaptivePlan ?: return rows
        if (plan.allKanjiMode) {
            return rows
        }
        val rowsByKanji = rows.associateBy { it.kanji }
        return plan.focusKanji.mapNotNull(rowsByKanji::get)
    }

    private fun reconcileExistingItems(request: StudyQueueSeedRequest): SeedQueueState {
        val rowIndex = SeedRowIndex(request.rows)
        val state = SeedQueueState()
        for (item in request.existing) {
            val current = alignOrRetireSeedItem(request, rowIndex, item)
            state.byFamily[current.familyKey] = current
            state.items += current
            state.trackActiveItem(current, request.startOfDayMillis)
        }
        return state
    }

    private fun alignOrRetireSeedItem(
        request: StudyQueueSeedRequest,
        rowIndex: SeedRowIndex,
        item: StudyQueueItem,
    ): StudyQueueItem {
        val row = seedRowForItem(rowIndex, item)
        val current = if (row == null) {
            item.alignRungToLadder(request.ladderSettings)
        } else {
            item.alignAnswerSignature(row, request.nowMillis, request.ladderSettings)
        }
        return if (shouldRetireSeedItem(request.settings, row, item, current)) {
            current.copy(state = StudyItemState.RETIRED, activeToken = null)
        } else {
            current
        }
    }

    private fun seedRowForItem(
        rowIndex: SeedRowIndex,
        item: StudyQueueItem,
    ): StudyDashboardRow? {
        val row = rowIndex.rowByFamily[item.familyKey]
        val familyRows = rowIndex.rowsByKanji[item.kanji]
        if (row != null || familyRows == null || (item.answerSignature.isNotEmpty() && familyRows.size != 1)) {
            return row
        }
        return familyRows.first()
    }

    private fun shouldRetireSeedItem(
        settings: StudyQueueSeedSettings,
        row: StudyDashboardRow?,
        original: StudyQueueItem,
        current: StudyQueueItem,
    ): Boolean =
        original.state != StudyItemState.RETIRED &&
            (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0))

    private fun admitSeedRow(
        request: StudyQueueSeedRequest,
        state: SeedQueueState,
        row: StudyDashboardRow,
    ) {
        val rowKey = row.familyKey()
        val current = state.byFamily[rowKey]
        if (current == null) {
            addNewSeedItemIfRoom(request, state, row, rowKey)
        } else if (canReopenRetiredSeedItem(request, state, row, current)) {
            reopenSeedItem(request, state, row, rowKey, current)
        }
    }

    private fun addNewSeedItemIfRoom(
        request: StudyQueueSeedRequest,
        state: SeedQueueState,
        row: StudyDashboardRow,
        rowKey: String,
    ) {
        if (!state.hasAdmissionRoom(request)) {
            return
        }
        val item = newStudyItem(row, request.nowMillis, request.ladderSettings)
        state.items += item
        state.byFamily[rowKey] = item
        state.activeCount++
        state.newToday++
    }

    private fun canReopenRetiredSeedItem(
        request: StudyQueueSeedRequest,
        state: SeedQueueState,
        row: StudyDashboardRow,
        current: StudyQueueItem,
    ): Boolean =
        current.state == StudyItemState.RETIRED &&
            row.matureSupportCount < request.settings.matureSupportThreshold &&
            state.hasAdmissionRoom(request)

    private fun reopenSeedItem(
        request: StudyQueueSeedRequest,
        state: SeedQueueState,
        row: StudyDashboardRow,
        rowKey: String,
        current: StudyQueueItem,
    ) {
        val reopened = newStudyItem(row, request.nowMillis, request.ladderSettings)
        state.items.remove(current)
        state.items += reopened
        state.byFamily[rowKey] = reopened
        state.activeCount++
        state.newToday++
    }

    private fun newStudyItem(
        row: StudyDashboardRow,
        nowMillis: Long,
        ladderSettings: StudyLadderSettings,
    ): StudyQueueItem = StudyQueueItem(
        kanji = row.kanji,
        state = StudyItemState.NEW,
        dueAtMillis = nowMillis,
        stability = 0.4,
        difficulty = 5.0,
        totalReviews = 0,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = row.answerSignature(),
        rung = ladderSettings.startingRung(hasSimilarKanji = false),
        phase = StudyPhase.NEW_LEARNING,
        createdAtMillis = nowMillis,
    )

    private fun StudyQueueItem.alignAnswerSignature(
        row: StudyDashboardRow,
        nowMillis: Long,
        ladderSettings: StudyLadderSettings,
    ): StudyQueueItem {
        val signature = row.answerSignature()
        if (answerSignature.isEmpty() || signature == answerSignature) {
            return copy(
                answerSignature = signature,
                rung = ladderSettings.effectiveRung(rung, hasSimilarKanji),
            )
        }
        if (state == StudyItemState.RETIRED) {
            return copy(
                answerSignature = signature,
                rung = ladderSettings.effectiveRung(rung, hasSimilarKanji),
            )
        }
        return copy(
            state = StudyItemState.LEARNING,
            dueAtMillis = nowMillis,
            stability = 0.4,
            difficulty = 5.0,
            totalReviews = 0,
            lapses = 0,
            learningStep = 0,
            matureIntervalDays = 0,
            answerSignature = signature,
            activeToken = null,
            memories = TaskMemoryBank(),
            rung = ladderSettings.previousRung(rung, hasSimilarKanji),
            phase = StudyPhase.NEW_LEARNING,
            realPassStreak = 0,
            realAgainStreak = 0,
            lastRealReviewDueAtMillis = 0L,
            suppressedByTaskType = "",
            suppressedAtMillis = 0L,
        )
    }

    private fun StudyQueueItem.alignRungToLadder(ladderSettings: StudyLadderSettings): StudyQueueItem {
        val effective = ladderSettings.effectiveRung(rung, hasSimilarKanji)
        return if (effective == rung) this else copy(rung = effective)
    }

    private class SeedRowIndex(rows: List<StudyDashboardRow>) {
        val rowByFamily: Map<String, StudyDashboardRow> = rows.associateBy { it.familyKey() }
        val rowsByKanji: Map<String, List<StudyDashboardRow>> = rows.groupBy { it.kanji }
    }

    private class SeedQueueState {
        val byFamily = linkedMapOf<String, StudyQueueItem>()
        val items = mutableListOf<StudyQueueItem>()
        var activeCount = 0
        var newToday = 0

        fun trackActiveItem(
            item: StudyQueueItem,
            startOfDayMillis: Long,
        ) {
            if (item.state == StudyItemState.RETIRED) {
                return
            }
            activeCount++
            if (item.createdAtMillis >= startOfDayMillis) {
                newToday++
            }
        }

        fun hasAdmissionRoom(request: StudyQueueSeedRequest): Boolean =
            activeCount < request.activeQueueCap() && newToday < request.admissionLimit()
    }

    private companion object {
        val seedItemComparator = compareBy<StudyQueueItem> { it.state == StudyItemState.RETIRED }
            .thenBy { it.dueAtMillis }
            .thenBy { it.kanji }
    }
}

data class StudyQueueSeedRequest(
    val rows: List<StudyDashboardRow>,
    val existing: List<StudyQueueItem>,
    val settings: StudyQueueSeedSettings,
    val nowMillis: Long,
    val startOfDayMillis: Long,
    val adaptivePlan: AdaptiveStudyPlan? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
) {
    internal fun activeQueueCap(): Int =
        if (adaptivePlan?.allKanjiMode == true) Int.MAX_VALUE else settings.activeQueueCap

    internal fun admissionLimit(): Int =
        if (adaptivePlan?.allKanjiMode == true) {
            Int.MAX_VALUE
        } else {
            val planLimit = adaptivePlan?.newAdmissionLimit ?: settings.newPerDay
            min(settings.newPerDay, planLimit).coerceAtLeast(0)
        }
}

data class StudyQueueSeedSettings(
    val activeQueueCap: Int,
    val newPerDay: Int,
    val matureSupportThreshold: Int,
    val newCardSortMode: NewCardSortMode = NewCardSortMode.default,
) {
    init {
        require(activeQueueCap >= 0) { "activeQueueCap must not be negative" }
        require(newPerDay >= 0) { "newPerDay must not be negative" }
        require(matureSupportThreshold >= 1) { "matureSupportThreshold must be positive" }
    }
}

class SeedStudyQueueUseCase(
    private val studyQueueRepository: StudyQueueRepository,
    private val clock: AppClock,
    private val seeder: StudyQueueSeeder = StudyQueueSeeder(),
) {
    suspend operator fun invoke(request: SeedStudyQueueUseCaseRequest): List<StudyQueueItem> {
        val seeded = seeder.seed(
            StudyQueueSeedRequest(
                rows = request.rows,
                existing = studyQueueRepository.listAllForSeeding(),
                settings = request.settings,
                nowMillis = clock.nowMillis(),
                startOfDayMillis = request.startOfDayMillis,
                adaptivePlan = request.adaptivePlan,
                ladderSettings = request.ladderSettings,
            ),
        )
        studyQueueRepository.replaceAllSeeded(seeded)
        return seeded
    }
}

data class SeedStudyQueueUseCaseRequest(
    val rows: List<StudyDashboardRow>,
    val settings: StudyQueueSeedSettings,
    val startOfDayMillis: Long,
    val adaptivePlan: AdaptiveStudyPlan? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
)

private fun StudyLadderSettings.startingRung(hasSimilarKanji: Boolean): StudyRung =
    effectiveRung(StudyRung.KANJI_MEANING, hasSimilarKanji)
