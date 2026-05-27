package dev.bee.kanjianki.core

import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

class StudyQueueSeeder {
    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        existing: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        return seedQueueInternal(
            SeedQueueRequest(
                SeedQueueSource(rows, rows, existing, settings),
                SeedQueueTiming(nowMillis, startOfDayMillis),
                SeedQueueLimits(settings.newPerDay, false),
                StudyLadderRules.safeLadder(ladder),
            ),
        )
    }

    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        existing: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        if (plan == null) {
            return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, ladder)
        }
        val admissionRows = if (plan.allKanjiMode) rows else rowsForFocus(rows, plan.focusKanji)
        val cappedAdmission = if (plan.allKanjiMode) {
            plan.newAdmissionLimit
        } else {
            min(plan.newAdmissionLimit, settings.newPerDay)
        }
        return seedQueueInternal(
            SeedQueueRequest(
                SeedQueueSource(rows, admissionRows, existing, settings),
                SeedQueueTiming(nowMillis, startOfDayMillis),
                SeedQueueLimits(cappedAdmission, plan.allKanjiMode),
                StudyLadderRules.safeLadder(ladder),
            ),
        )
    }

    fun seedExtraNewCards(
        rows: List<RecordsImportModels.DashboardRow>,
        existing: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        requestedCount: Int,
        ladder: RecordsBase.StudyLadderSettings?,
    ): BridgeScheduler.ExtraNewCardsResult {
        val requested = max(0, requestedCount)
        val request = SeedQueueRequest(
            SeedQueueSource(rows, rows, existing, settings),
            SeedQueueTiming(nowMillis, startOfDayMillis),
            SeedQueueLimits(Int.MAX_VALUE, true),
            StudyLadderRules.safeLadder(ladder),
        )
        val rowIndex = indexSeedRows(request.allRows)
        val state = reconcileExistingItems(request, rowIndex)
        val admittedKanji = ArrayList<String>()
        var available = 0
        for (row in request.admissionRows) {
            val rowKey = rowFamilyKey(row)
            val current = state.byFamily[rowKey]
            val eligible = current == null || canReopenRetiredExtraSeedItem(request.settings, row, current)
            if (eligible) {
                available++
                if (admittedKanji.size < requested) {
                    admitExtraSeedRow(request, state, row, rowKey, current)
                    admittedKanji.add(row.kanji)
                }
            }
        }
        sortSeedItems(state.items)
        return BridgeScheduler.ExtraNewCardsResult(state.items, admittedKanji, available)
    }

    private fun canReopenRetiredExtraSeedItem(
        settings: RecordsSyncModels.Settings,
        row: RecordsImportModels.DashboardRow,
        current: RecordsStudyModels.StudyItem,
    ): Boolean {
        return StudyLadderRules.STATE_RETIRED == current.state &&
            row.matureSupportCount < settings.matureSupportThreshold
    }

    private fun admitExtraSeedRow(
        request: SeedQueueRequest,
        state: SeedQueueState,
        row: RecordsImportModels.DashboardRow,
        rowKey: String,
        current: RecordsStudyModels.StudyItem?,
    ) {
        val admitted = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder)
        if (current != null) {
            state.items.remove(current)
        }
        state.items.add(admitted)
        state.byFamily[rowKey] = admitted
        state.activeCount++
        state.newToday++
    }

    private fun seedQueueInternal(request: SeedQueueRequest): List<RecordsStudyModels.StudyItem> {
        val rowIndex = indexSeedRows(request.allRows)
        val state = reconcileExistingItems(request, rowIndex)
        for (row in request.admissionRows) {
            admitSeedRow(request, state, row)
        }
        sortSeedItems(state.items)
        return state.items
    }

    private fun sortSeedItems(items: MutableList<RecordsStudyModels.StudyItem>) {
        items.sortWith(
            compareBy<RecordsStudyModels.StudyItem> { it.state == StudyLadderRules.STATE_RETIRED }
                .thenBy { it.dueAtMillis }
                .thenBy { it.kanji },
        )
    }

    private fun rowsForFocus(
        rows: List<RecordsImportModels.DashboardRow>,
        focusKanji: List<String>,
    ): List<RecordsImportModels.DashboardRow> {
        val byKanji = HashMap<String, RecordsImportModels.DashboardRow>()
        for (row in rows) {
            byKanji[row.kanji] = row
        }
        val out = ArrayList<RecordsImportModels.DashboardRow>()
        for (kanji in focusKanji) {
            val row = byKanji[kanji]
            if (row != null) {
                out.add(row)
            }
        }
        return out
    }

    private fun indexSeedRows(rows: List<RecordsImportModels.DashboardRow>): SeedRowIndex {
        val index = SeedRowIndex()
        for (row in rows) {
            index.rowByFamily[rowFamilyKey(row)] = row
            var familyRows = index.rowsByKanji[row.kanji]
            if (familyRows == null) {
                familyRows = ArrayList()
                index.rowsByKanji[row.kanji] = familyRows
            }
            familyRows.add(row)
        }
        return index
    }

    private fun reconcileExistingItems(request: SeedQueueRequest, rowIndex: SeedRowIndex): SeedQueueState {
        val state = SeedQueueState()
        for (item in request.existing) {
            val current = alignOrRetireSeedItem(request, rowIndex, item)
            state.byFamily[familyKey(current)] = current
            state.items.add(current)
            state.trackActiveItem(current, request.startOfDayMillis)
        }
        return state
    }

    private fun alignOrRetireSeedItem(
        request: SeedQueueRequest,
        rowIndex: SeedRowIndex,
        item: RecordsStudyModels.StudyItem,
    ): RecordsStudyModels.StudyItem {
        val row = seedRowForItem(rowIndex, item)
        val current = if (row == null) {
            StudyLadderRules.alignRungToLadder(item, request.ladder)
        } else {
            alignAnswerSignature(item, row, request.nowMillis, request.ladder)
        }
        if (shouldRetireSeedItem(request.settings, row, item, current)) {
            return retiredCopy(current)
        }
        return current
    }

    private fun seedRowForItem(
        rowIndex: SeedRowIndex,
        item: RecordsStudyModels.StudyItem,
    ): RecordsImportModels.DashboardRow? {
        val row = rowIndex.rowByFamily[familyKey(item)]
        val familyRows: List<RecordsImportModels.DashboardRow>? = rowIndex.rowsByKanji[item.kanji]
        if (row != null || familyRows == null || (item.answerSignature.isNotEmpty() && familyRows.size != 1)) {
            return row
        }
        return familyRows[0]
    }

    private fun shouldRetireSeedItem(
        settings: RecordsSyncModels.Settings,
        row: RecordsImportModels.DashboardRow?,
        original: RecordsStudyModels.StudyItem,
        current: RecordsStudyModels.StudyItem,
    ): Boolean {
        return StudyLadderRules.STATE_RETIRED != original.state &&
            (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0))
    }

    private fun admitSeedRow(request: SeedQueueRequest, state: SeedQueueState, row: RecordsImportModels.DashboardRow) {
        val rowKey = rowFamilyKey(row)
        val current = state.byFamily[rowKey]
        if (current == null) {
            addNewSeedItemIfRoom(request, state, row, rowKey)
        } else if (canReopenRetiredSeedItem(request, state, row, current)) {
            reopenSeedItem(request, state, row, rowKey, current)
        }
    }

    private fun addNewSeedItemIfRoom(
        request: SeedQueueRequest,
        state: SeedQueueState,
        row: RecordsImportModels.DashboardRow,
        rowKey: String,
    ) {
        if (!state.hasAdmissionRoom(request)) {
            return
        }
        val item = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder)
        state.items.add(item)
        state.byFamily[rowKey] = item
        state.activeCount++
        state.newToday++
    }

    private fun canReopenRetiredSeedItem(
        request: SeedQueueRequest,
        state: SeedQueueState,
        row: RecordsImportModels.DashboardRow,
        current: RecordsStudyModels.StudyItem,
    ): Boolean {
        return StudyLadderRules.STATE_RETIRED == current.state &&
            row.matureSupportCount < request.settings.matureSupportThreshold &&
            state.hasAdmissionRoom(request)
    }

    private fun reopenSeedItem(
        request: SeedQueueRequest,
        state: SeedQueueState,
        row: RecordsImportModels.DashboardRow,
        rowKey: String,
        current: RecordsStudyModels.StudyItem,
    ) {
        val reopened = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder)
        state.items.remove(current)
        state.items.add(reopened)
        state.byFamily[rowKey] = reopened
        state.activeCount++
        state.newToday++
    }

    private fun retiredCopy(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        return item.copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .activeToken(null)
            .build()
    }

    private fun newStudyItem(
        kanji: String,
        nowMillis: Long,
        answerSignature: String,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        val startingRung = StudyLadderRules.safeLadder(ladder).startingRung(false)
        return RecordsStudyModels.StudyItem(
            kanji,
            StudyLadderRules.STATE_NEW,
            nowMillis,
            0.4,
            5.0,
            0,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            0L,
            0,
            answerSignature,
            null,
            nowMillis,
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            startingRung,
            RecordsBase.SchedulerPhase.NEW_LEARNING,
            0,
            0,
            0L,
            false,
            RecordsStudyModels.TaskMemory.initial(),
        )
    }

    private fun alignAnswerSignature(
        item: RecordsStudyModels.StudyItem,
        row: RecordsImportModels.DashboardRow,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        val signature = answerSignature(row)
        if (item.answerSignature.isEmpty() || signature == item.answerSignature) {
            return StudyLadderRules.alignRungToLadder(item.withAnswerSignature(signature), ladder)
        }
        val retired = StudyLadderRules.STATE_RETIRED == item.state
        if (retired) {
            return StudyLadderRules.alignRungToLadder(item.copyBuilder().answerSignature(signature).build(), ladder)
        }
        val fallbackRung = StudyLadderRules.demoteRung(item.rung, item.hasSimilarKanji, ladder)
        return item.copyBuilder()
            .state(StudyLadderRules.STATE_LEARNING)
            .dueAtMillis(nowMillis)
            .stability(0.4)
            .difficulty(5.0)
            .totalReviews(0)
            .lapses(0)
            .learningStep(0)
            .consecutiveFailedRecognitionDays(0)
            .lastFailedRecognitionDayMillis(0L)
            .writingRemediationPending(false)
            .suppressedByTaskType(null)
            .suppressedAtMillis(0L)
            .matureIntervalDays(0)
            .answerSignature(signature)
            .activeToken(null)
            .typingMeaningMemory(RecordsStudyModels.TaskMemory.initial())
            .meaningKanjiMemory(RecordsStudyModels.TaskMemory.initial())
            .kanjiMeaningMemory(RecordsStudyModels.TaskMemory.initial())
            .fontMeaningMemory(RecordsStudyModels.TaskMemory.initial())
            .wordReadingMemory(RecordsStudyModels.TaskMemory.initial())
            .writingRemediationMemory(RecordsStudyModels.TaskMemory.initial())
            .similarKanjiMemory(RecordsStudyModels.TaskMemory.initial())
            .rung(fallbackRung)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .realPassStreak(0)
            .realAgainStreak(0)
            .lastRealReviewDueAtMillis(0L)
            .build()
    }

    private class SeedQueueLimits(
        private val newAdmissionLimit: Int,
        private val allKanjiMode: Boolean,
    ) {
        fun activeQueueCap(settings: RecordsSyncModels.Settings): Int {
            return if (allKanjiMode) Int.MAX_VALUE else settings.activeQueueCap
        }

        fun admissionLimit(): Int {
            return if (allKanjiMode) Int.MAX_VALUE else max(0, newAdmissionLimit)
        }
    }

    private class SeedQueueSource(
        val allRows: List<RecordsImportModels.DashboardRow>,
        val admissionRows: List<RecordsImportModels.DashboardRow>,
        val existing: List<RecordsStudyModels.StudyItem>,
        val settings: RecordsSyncModels.Settings,
    )

    private class SeedQueueTiming(
        val nowMillis: Long,
        val startOfDayMillis: Long,
    )

    private class SeedQueueRequest(
        source: SeedQueueSource,
        timing: SeedQueueTiming,
        val limits: SeedQueueLimits,
        ladder: RecordsBase.StudyLadderSettings?,
    ) {
        val allRows: List<RecordsImportModels.DashboardRow> = source.allRows
        val admissionRows: List<RecordsImportModels.DashboardRow> = sortedAdmissionRows(source.admissionRows, source.settings)
        val existing: List<RecordsStudyModels.StudyItem> = source.existing
        val settings: RecordsSyncModels.Settings = source.settings
        val nowMillis: Long = timing.nowMillis
        val startOfDayMillis: Long = timing.startOfDayMillis
        val ladder: RecordsBase.StudyLadderSettings = StudyLadderRules.safeLadder(ladder)
    }

    private class SeedRowIndex {
        val rowByFamily = HashMap<String, RecordsImportModels.DashboardRow>()
        val rowsByKanji = HashMap<String, MutableList<RecordsImportModels.DashboardRow>>()
    }

    private class SeedQueueState {
        val byFamily = HashMap<String, RecordsStudyModels.StudyItem>()
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        var activeCount = 0
        var newToday = 0

        fun trackActiveItem(item: RecordsStudyModels.StudyItem, startOfDayMillis: Long) {
            if (StudyLadderRules.STATE_RETIRED == item.state) {
                return
            }
            activeCount++
            if (item.createdAtMillis >= startOfDayMillis) {
                newToday++
            }
        }

        fun hasAdmissionRoom(request: SeedQueueRequest): Boolean {
            return activeCount < request.limits.activeQueueCap(request.settings) &&
                newToday < request.limits.admissionLimit()
        }
    }

    companion object {
        private val MULTI_WHITESPACE: Pattern = Pattern.compile("\\s+")

        @JvmStatic
        fun sortedAdmissionRows(
            rows: List<RecordsImportModels.DashboardRow>,
            settings: RecordsSyncModels.Settings?,
        ): List<RecordsImportModels.DashboardRow> {
            return NewCardSortPlanner.sortedRowsForSettings(rows, settings)
        }

        @JvmStatic
        fun compareRowsForNewCardSort(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
            settings: RecordsSyncModels.Settings?,
        ): Int {
            return NewCardSortPlanner.compareRowsForSettings(left, right, settings)
        }

        @JvmStatic
        fun familyKey(item: RecordsStudyModels.StudyItem): String {
            return familyKey(item.kanji, item.answerSignature)
        }

        @JvmStatic
        fun rowFamilyKey(row: RecordsImportModels.DashboardRow): String {
            return familyKey(row.kanji, answerSignature(row))
        }

        @JvmStatic
        fun answerSignature(row: RecordsImportModels.DashboardRow): String {
            var example: RecordsImportModels.Example? = null
            for (candidate in row.examples) {
                if ("suspended" == candidate.sourceType) {
                    example = candidate
                    break
                }
                if (example == null && "active" == candidate.sourceType) {
                    example = candidate
                }
            }
            if (example == null && row.examples.isNotEmpty()) {
                example = row.examples[0]
            }
            val expression = if (example == null) "" else example.expression
            val reading = if (example == null) row.reading else example.reading
            val meaning = if (example == null) row.primaryMeaning else example.meaning
            return normalizeSignature(row.kanji) + "|" +
                normalizeSignature(expression) + "|" +
                normalizeSignature(reading) + "|" +
                normalizeSignature(meaning)
        }

        private fun familyKey(kanji: String, answerSignature: String?): String {
            return kanji + "\u0000" + (answerSignature ?: "")
        }

        private fun normalizeSignature(value: String?): String {
            return MULTI_WHITESPACE.matcher((value ?: "").trimJavaWhitespace()).replaceAll(" ")
        }

        private fun String.trimJavaWhitespace(): String {
            var start = 0
            var end = length
            while (start < end && this[start].code <= ' '.code) {
                start++
            }
            while (start < end && this[end - 1].code <= ' '.code) {
                end--
            }
            return substring(start, end)
        }
    }
}
