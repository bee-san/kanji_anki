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
        evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        return seedQueueInternal(
            SeedQueueRequest(
                SeedQueueSource(rows, rows, rows, existing, settings, evidenceStatusByKanji),
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
        evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        return seedQueue(
            rows,
            rows,
            existing,
            settings,
            nowMillis,
            startOfDayMillis,
            plan,
            ladder,
            evidenceStatusByKanji,
        )
    }

    /**
     * Reconcile lifecycle against [allRows] while counting and admitting only
     * provider-active, locally eligible [eligibleRows]. Local Browse suspension
     * is an eligibility concern, not evidence that the provider retired a kanji.
     */
    fun seedQueue(
        allRows: List<RecordsImportModels.DashboardRow>,
        eligibleRows: List<RecordsImportModels.DashboardRow>,
        existing: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        ladder: RecordsBase.StudyLadderSettings?,
        evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        val allKanjiMode = plan?.allKanjiMode == true
        val plannedAdmissionRows = if (plan == null || allKanjiMode) {
            eligibleRows
        } else {
            rowsForFocus(eligibleRows, plan.focusKanji)
        }
        val admissionLimit = when {
            plan == null -> settings.newPerDay
            allKanjiMode -> plan.newAdmissionLimit
            else -> min(plan.newAdmissionLimit, settings.newPerDay)
        }
        return seedQueueInternal(
            SeedQueueRequest(
                SeedQueueSource(
                    allRows,
                    eligibleRows,
                    plannedAdmissionRows,
                    existing,
                    settings,
                    evidenceStatusByKanji,
                ),
                SeedQueueTiming(nowMillis, startOfDayMillis),
                SeedQueueLimits(admissionLimit, allKanjiMode),
                StudyLadderRules.safeLadder(ladder),
            ),
        )
    }

    fun countExtraNewCardsAvailable(
        rows: List<RecordsImportModels.DashboardRow>,
        existing: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        nowMillis: Long,
        startOfDayMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Int {
        val request = SeedQueueRequest(
            SeedQueueSource(rows, rows, rows, existing, settings, null),
            SeedQueueTiming(nowMillis, startOfDayMillis),
            SeedQueueLimits(Int.MAX_VALUE, true),
            StudyLadderRules.safeLadder(ladder),
        )
        val rowIndex = indexSeedRows(request.allRows)
        val state = reconcileExistingItems(request, rowIndex)
        var available = 0
        for (row in request.admissionRows) {
            val rowKey = identityKey(row.kanji)
            val current = state.byFamily[rowKey]
            val eligible = if (current == null) {
                !isAlreadyRepairedRow(request, row)
            } else {
                canReopenRetiredExtraSeedItem(request.settings, row, current)
            }
            if (eligible) {
                available++
            }
        }
        return available
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
            SeedQueueSource(rows, rows, rows, existing, settings, null),
            SeedQueueTiming(nowMillis, startOfDayMillis),
            SeedQueueLimits(Int.MAX_VALUE, true),
            StudyLadderRules.safeLadder(ladder),
        )
        val rowIndex = indexSeedRows(request.allRows)
        val state = reconcileExistingItems(request, rowIndex)
        val admittedKanji = ArrayList<String>()
        var available = 0
        for (row in request.admissionRows) {
            val rowKey = identityKey(row.kanji)
            val current = state.byFamily[rowKey]
            val eligible = if (current == null) {
                !isAlreadyRepairedRow(request, row)
            } else {
                canReopenRetiredExtraSeedItem(request.settings, row, current)
            }
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
        val admitted = if (current != null && StudyLadderRules.STATE_RETIRED == current.state) {
            reopenedCopy(current)
        } else {
            newStudyItem(row, request.nowMillis, answerSignature(row), request.ladder, request.settings)
        }
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
        reopenEligibleRetiredItems(request, state)
        for (row in request.admissionRows) {
            admitSeedRow(request, state, row)
        }
        sortSeedItems(state.items)
        return state.items
    }

    /** Reopening is reconciliation, not admission, so a filtered focus plan must not suppress it. */
    private fun reopenEligibleRetiredItems(request: SeedQueueRequest, state: SeedQueueState) {
        for (row in request.allRows) {
            if (!request.isEligibleFamily(row.kanji)) continue
            val rowKey = identityKey(row.kanji)
            val current = state.byFamily[rowKey] ?: continue
            if (canReopenRetiredSeedItem(request, row, current)) {
                reopenSeedItem(state, rowKey, current)
            }
        }
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
            index.rowByFamily[identityKey(row.kanji)] = row
        }
        return index
    }

    private fun reconcileExistingItems(request: SeedQueueRequest, rowIndex: SeedRowIndex): SeedQueueState {
        val itemsByFamily = LinkedHashMap<String, MutableList<RecordsStudyModels.StudyItem>>()
        for (item in request.existing) {
            val current = alignOrRetireSeedItem(request, rowIndex, item)
            itemsByFamily.getOrPut(identityKey(current.kanji)) { ArrayList() }.add(current)
        }
        val state = SeedQueueState()
        for ((key, family) in itemsByFamily) {
            val canonical = StudyItemReconciliationPolicy.mergeAll(family)
            state.byFamily[key] = canonical
            state.items.add(canonical)
            state.trackActiveItem(canonical, request)
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
            if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
                AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
            } else {
                StudyLadderRules.alignRungToLadder(item, request.ladder)
            }
        } else {
            alignAnswerSignature(item, row, request.nowMillis, request.ladder)
        }
        if (shouldRetireSeedItem(request, row, item)) {
            return retiredCopy(current)
        }
        return current
    }

    private fun seedRowForItem(
        rowIndex: SeedRowIndex,
        item: RecordsStudyModels.StudyItem,
    ): RecordsImportModels.DashboardRow? {
        return rowIndex.rowByFamily[identityKey(item.kanji)]
    }

    private fun shouldRetireSeedItem(
        request: SeedQueueRequest,
        row: RecordsImportModels.DashboardRow?,
        original: RecordsStudyModels.StudyItem,
    ): Boolean {
        if (StudyLadderRules.STATE_RETIRED == original.state) {
            return false
        }
        if (row == null) {
            return true
        }
        return row.matureSupportCount >= request.settings.matureSupportThreshold &&
            !hasRegressingEvidence(request, row.kanji)
    }

    private fun admitSeedRow(request: SeedQueueRequest, state: SeedQueueState, row: RecordsImportModels.DashboardRow) {
        val rowKey = identityKey(row.kanji)
        val current = state.byFamily[rowKey]
        if (current == null) {
            // Do not admit (and then immediately force study of) a kanji whose
            // Anki mature support already meets the retirement threshold. Only
            // regressing repair evidence overrides this.
            if (!isAlreadyRepairedRow(request, row)) {
                addNewSeedItemIfRoom(request, state, row, rowKey)
            }
        } else if (canReopenRetiredSeedItem(request, row, current)) {
            reopenSeedItem(state, rowKey, current)
        }
    }

    private fun isAlreadyRepairedRow(request: SeedQueueRequest, row: RecordsImportModels.DashboardRow): Boolean {
        return row.matureSupportCount >= request.settings.matureSupportThreshold &&
            !hasRegressingEvidence(request, row.kanji)
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
        val item = newStudyItem(row, request.nowMillis, answerSignature(row), request.ladder, request.settings)
        state.items.add(item)
        state.byFamily[rowKey] = item
        state.activeCount++
        state.newToday++
    }

    private fun canReopenRetiredSeedItem(
        request: SeedQueueRequest,
        row: RecordsImportModels.DashboardRow,
        current: RecordsStudyModels.StudyItem,
    ): Boolean {
        return StudyLadderRules.STATE_RETIRED == current.state &&
            (
                row.matureSupportCount < request.settings.matureSupportThreshold ||
                    hasRegressingEvidence(request, row.kanji)
                )
    }

    private fun hasRegressingEvidence(request: SeedQueueRequest, kanji: String): Boolean {
        return request.evidenceStatusByKanji?.get(kanji) == KanjiRepairEvidencePolicy.Status.REGRESSING
    }

    private fun reopenSeedItem(
        state: SeedQueueState,
        rowKey: String,
        current: RecordsStudyModels.StudyItem,
    ) {
        val reopened = reopenedCopy(current)
        state.items.remove(current)
        state.items.add(reopened)
        state.byFamily[rowKey] = reopened
        state.activeCount++
    }

    private fun reopenedCopy(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        val memoryState = item.memoryForRung(item.rung).state
        val restoredState = when (item.phase) {
            RecordsBase.SchedulerPhase.REVIEW -> StudyLadderRules.STATE_REVIEW
            RecordsBase.SchedulerPhase.RELEARNING -> StudyLadderRules.STATE_LEARNING
            RecordsBase.SchedulerPhase.NEW_LEARNING -> if (memoryState == StudyLadderRules.STATE_LEARNING) {
                StudyLadderRules.STATE_LEARNING
            } else {
                StudyLadderRules.STATE_NEW
            }
        }
        return item.copyBuilder()
            .state(restoredState)
            .activeToken(null)
            .schedulerRevision(item.schedulerRevision + 1L)
            .build()
    }

    private fun retiredCopy(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        return item.copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .activeToken(null)
            .schedulerRevision(item.schedulerRevision + 1L)
            .build()
    }

    private fun newStudyItem(
        row: RecordsImportModels.DashboardRow,
        nowMillis: Long,
        answerSignature: String,
        ladder: RecordsBase.StudyLadderSettings,
        settings: RecordsSyncModels.Settings,
    ): RecordsStudyModels.StudyItem {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val seed = AdmissionEvidencePolicy.seedFor(row, safeLadder, settings)
        val base = RecordsStudyModels.StudyItem(
            row.kanji,
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
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.SchedulerPhase.NEW_LEARNING,
            0,
            0,
            0L,
            false,
            RecordsStudyModels.TaskMemory.initial(),
        )
        return applySeed(base, seed, nowMillis)
    }

    private fun applySeed(
        base: RecordsStudyModels.StudyItem,
        seed: AdmissionEvidencePolicy.Seed,
        nowMillis: Long,
    ): RecordsStudyModels.StudyItem {
        val requiredRung = if (seed.isReviewSeed()) {
            RecordsBase.LadderRung.WORD_READING
        } else {
            RecordsBase.LadderRung.KANJI_MEANING
        }
        val seeded = base.copyBuilder()
            .state(seed.state)
            .stability(seed.stability)
            .difficulty(seed.difficulty)
            .rung(requiredRung)
            .phase(seed.phase)
            .matureIntervalDays(seed.matureIntervalDays)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(requiredRung))
            .writingRemediationPending(false)
            .build()
        if (!seed.isReviewSeed()) {
            return seeded
        }
        // Evidence-strong kanji skip the learning climb: carry Anki's memory
        // state into the active rung's TaskMemory so the first confirmation
        // review evolves the real FSRS state rather than the new-card
        // placeholder. Due is now, so this validates the skill once and then
        // rides a real interval.
        val seededMemory = RecordsStudyModels.TaskMemory.fromFields(
            RecordsStudyModels.TaskMemory.Fields(
                state = StudyLadderRules.STATE_REVIEW,
                dueAtMillis = nowMillis,
                stability = seed.stability,
                difficulty = seed.difficulty,
                totalReviews = 0,
                lapses = 0,
                learningStep = 0,
                lastRating = "",
                matureIntervalDays = 0,
            )
        )
        val route = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING)
        return seeded.copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(RecordsBase.LadderRung.WORD_READING))
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
            .withTaskMemory(StudyTaskTypes.WORD_READING, seededMemory)
    }

    private fun alignAnswerSignature(
        item: RecordsStudyModels.StudyItem,
        row: RecordsImportModels.DashboardRow,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        val signature = answerSignature(row)
        if (item.answerSignature.isEmpty() || signature == item.answerSignature) {
            return alignForRoutingVersion(item.withAnswerSignature(signature), ladder)
        }
        val retired = StudyLadderRules.STATE_RETIRED == item.state
        // A suspend/unsuspend flip in Anki can reshuffle which example is the
        // "preferred" one and change expression/reading without the kanji's
        // meaning changing at all. Preserve all earned scheduler state in that
        // case and only adopt the new signature; months of ladder/FSRS progress
        // must not be destroyed by a suspension toggle. Reset only when the
        // meaning itself materially changed (effectively a different card).
        if (signatureMeaning(signature) == signatureMeaning(item.answerSignature)) {
            return alignForRoutingVersion(item.withAnswerSignature(signature), ladder)
        }
        return item.copyBuilder()
            // Keep a retired item retired until the normal support/evidence gate
            // decides whether to reopen it. If reopened below, NEW_LEARNING plus
            // cleared memories restores it as a genuinely new repair.
            .state(if (retired) StudyLadderRules.STATE_RETIRED else StudyLadderRules.STATE_LEARNING)
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
            .kanjiReadingMemory(RecordsStudyModels.TaskMemory.initial())
            .readingKanjiMemory(RecordsStudyModels.TaskMemory.initial())
            .sentenceReadingMemory(RecordsStudyModels.TaskMemory.initial())
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .realPassStreak(0)
            .realAgainStreak(0)
            .lastRealReviewDueAtMillis(0L)
            .routingVersion(1)
            .adaptiveRouteStateJson("")
            .schedulerRevision(item.schedulerRevision + 1L)
            .build()
    }

    private fun alignForRoutingVersion(
        item: RecordsStudyModels.StudyItem,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        return if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
            AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
        } else {
            StudyLadderRules.alignRungToLadder(item, ladder)
        }
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
        val eligibleRows: List<RecordsImportModels.DashboardRow>,
        val admissionRows: List<RecordsImportModels.DashboardRow>,
        val existing: List<RecordsStudyModels.StudyItem>,
        val settings: RecordsSyncModels.Settings,
        val evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>?,
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
        private val eligibleFamilyKeys: Set<String> = source.eligibleRows
            .mapTo(HashSet()) { identityKey(it.kanji) }
        val existing: List<RecordsStudyModels.StudyItem> = source.existing
        val settings: RecordsSyncModels.Settings = source.settings
        val evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>? = source.evidenceStatusByKanji
        val nowMillis: Long = timing.nowMillis
        val startOfDayMillis: Long = timing.startOfDayMillis
        val ladder: RecordsBase.StudyLadderSettings = StudyLadderRules.safeLadder(ladder)

        fun isEligibleFamily(kanji: String): Boolean = eligibleFamilyKeys.contains(identityKey(kanji))
    }

    private class SeedRowIndex {
        val rowByFamily = HashMap<String, RecordsImportModels.DashboardRow>()
    }

    private class SeedQueueState {
        val byFamily = HashMap<String, RecordsStudyModels.StudyItem>()
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        var activeCount = 0
        var newToday = 0

        fun trackActiveItem(item: RecordsStudyModels.StudyItem, request: SeedQueueRequest) {
            if (StudyLadderRules.STATE_RETIRED == item.state || !request.isEligibleFamily(item.kanji)) {
                return
            }
            if (item.createdAtMillis >= request.startOfDayMillis) {
                newToday++
            }
            // Ceiling-parked items stay studyable when due but do not consume an
            // active-queue slot, so a mature kanji riding a long interval at the
            // top rung can never permanently block admission of new repairs.
            if (isCeilingParked(item, request)) {
                return
            }
            activeCount++
        }

        private fun isCeilingParked(item: RecordsStudyModels.StudyItem, request: SeedQueueRequest): Boolean {
            if (StudyLadderRules.STATE_REVIEW != item.state ||
                item.phase != RecordsBase.SchedulerPhase.REVIEW
            ) {
                return false
            }
            val atValidatedCeiling = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
                // sentence_reading is a presentation variant in routing v2,
                // not a rung above the contextual core. A validated contextual
                // item is therefore at the adaptive ceiling even when sentence
                // data and the legacy sentence bit are both available.
                AdaptiveStudyItemPolicy.isContextualComplete(item)
            } else {
                request.ladder.isAtCeiling(item.rung, item.rungAvailability())
            }
            if (!atValidatedCeiling) {
                return false
            }
            val threshold = max(
                request.settings.matureDays,
                request.settings.ladderPromotionIntervalDays * RecordsBase.CEILING_PARK_INTERVAL_MULTIPLIER,
            )
            return item.matureIntervalDays >= threshold
        }

        fun hasAdmissionRoom(request: SeedQueueRequest): Boolean {
            return activeCount < request.limits.activeQueueCap(request.settings) &&
                newToday < request.limits.admissionLimit()
        }
    }

    companion object {
        private val MULTI_WHITESPACE: Pattern = Pattern.compile("\\s+")

        private fun identityKey(kanji: String): String = kanji

        @JvmStatic
        fun sortedAdmissionRows(
            rows: List<RecordsImportModels.DashboardRow>,
            settings: RecordsSyncModels.Settings?,
        ): List<RecordsImportModels.DashboardRow> {
            return NewCardSortPlanner.sortedAdmissionRows(rows, settings)
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


        /**
         * Meaning component of an answer signature
         * (`kanji|expression|reading|meaning`). Used to distinguish a genuine
         * content change from a mere example reshuffle caused by a suspend flip.
         */
        private fun signatureMeaning(signature: String): String {
            val parts = signature.split("|", limit = 4)
            return if (parts.size == 4) parts[3] else ""
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
