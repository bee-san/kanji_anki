package dev.bee.kanjianki.core

/**
 * Counts the persisted study-item tasks that the current Study route can serve.
 *
 * [RecordsSchedulerModels.AdaptiveLoadPlan.remaining] is deliberately a daily
 * focus/admission count: an unstudied focus kanji can remain there even when its
 * persisted review is not due yet. The Study-now badge needs a different value.
 * This policy dry-runs the same queue seeder used by the Study route, so missing
 * and reopenable retired items are counted once admission makes them due now,
 * while admission gates and caps are honored. It then counts the distinct,
 * non-empty task keys the session selector can serve at
 * [SelectionContext.studyAheadMillis] in
 * the caller's current focus/all-kanji mode, matching StudySessionTracker's
 * reconciled session plan. App-owned
 * tasks that deliberately bypass `study_items` (currently similar-kanji writing
 * repairs) are added with [includingAdditionalTaskKeys].
 *
 * The dry run is pure: the returned seeded items are never persisted.
 */
object StudyNowCountPolicy {
    /** Inputs shared by seeded and dry-run counts for the current Study route. */
    data class SelectionContext(
        val nowMillis: Long,
        val studyAheadMillis: Long,
        val plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        val continueAllKanjiSession: Boolean,
        val ladder: RecordsBase.StudyLadderSettings?,
    )

    /** Inputs for a count that must first dry-run queue admission. */
    data class CountRequest(
        val rows: List<RecordsImportModels.DashboardRow>?,
        val currentItems: List<RecordsStudyModels.StudyItem>?,
        val settings: RecordsSyncModels.Settings?,
        val startOfDayMillis: Long,
        val selection: SelectionContext,
    )

    /** Inputs for a queue that the caller has already seeded. */
    data class SeededCountRequest(
        val seededItems: List<RecordsStudyModels.StudyItem>?,
        val rows: List<RecordsImportModels.DashboardRow>?,
        val settings: RecordsSyncModels.Settings?,
        val selection: SelectionContext,
    )

    /** Adds non-study-item tasks without letting blank or duplicate keys inflate the count. */
    @JvmStatic
    fun includingAdditionalTaskKeys(
        studyItemCount: Int,
        additionalTaskKeys: Iterable<String?>?,
    ): Int {
        val additionalCount = (additionalTaskKeys ?: emptyList())
            .asSequence()
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .distinct()
            .count()
        return saturatingAddNonNegative(studyItemCount, additionalCount)
    }

    @JvmStatic
    fun count(request: CountRequest?): Int {
        if (request == null || request.selection.plan == null || request.rows.isNullOrEmpty()) {
            return 0
        }

        val scheduler = BridgeScheduler()
        val seeded = scheduler.seedQueue(
            request.rows,
            request.currentItems,
            request.settings,
            request.selection.nowMillis,
            request.startOfDayMillis,
            request.selection.plan,
            request.selection.ladder,
        )
        return countSeeded(
            scheduler,
            SeededCountRequest(
                seeded,
                request.rows,
                request.settings,
                request.selection,
            ),
        )
    }

    /** Counts an already-seeded queue without repeating the admission dry run. */
    @JvmStatic
    fun countSeeded(request: SeededCountRequest?): Int {
        if (request == null || request.selection.plan == null || request.rows.isNullOrEmpty()) {
            return 0
        }
        return countSeeded(BridgeScheduler(), request)
    }

    private fun countSeeded(
        scheduler: BridgeScheduler,
        request: SeededCountRequest,
    ): Int {
        val selection = request.selection
        val allowedKanji = StudySessionFocusPolicy.allowedKanji(
            selection.plan,
            selection.continueAllKanjiSession,
        )
        return scheduler.randomizedSessionTaskKeys(
            request.seededItems,
            request.rows,
            selection.nowMillis,
            selection.studyAheadMillis,
            allowedKanji,
            request.settings,
            selection.ladder,
            0L,
        ).asSequence()
            .filter { it.isNotEmpty() }
            .distinct()
            .count()
    }
}
