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
 * non-empty task keys the session selector can serve at [studyAheadMillis] in
 * the caller's current focus/all-kanji mode, matching StudySessionTracker's
 * reconciled session plan. App-owned
 * tasks that deliberately bypass `study_items` (currently similar-kanji writing
 * repairs) are added with [includingAdditionalTaskKeys].
 *
 * The dry run is pure: the returned seeded items are never persisted.
 */
object StudyNowCountPolicy {
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
        return studyItemCount.coerceAtLeast(0) + additionalCount
    }

    @JvmStatic
    fun count(
        rows: List<RecordsImportModels.DashboardRow>?,
        currentItems: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        studyAheadMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        continueAllKanjiSession: Boolean,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Int {
        if (plan == null || rows.isNullOrEmpty()) {
            return 0
        }

        val scheduler = BridgeScheduler()
        val seeded = scheduler.seedQueue(
            rows,
            currentItems,
            settings,
            nowMillis,
            startOfDayMillis,
            plan,
            ladder,
        )
        return countSeeded(
            scheduler,
            seeded,
            rows,
            settings,
            nowMillis,
            studyAheadMillis,
            plan,
            continueAllKanjiSession,
            ladder,
        )
    }

    /** Counts an already-seeded queue without repeating the admission dry run. */
    @JvmStatic
    fun countSeeded(
        seededItems: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        studyAheadMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        continueAllKanjiSession: Boolean,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Int {
        if (plan == null || rows.isNullOrEmpty()) {
            return 0
        }
        return countSeeded(
            BridgeScheduler(),
            seededItems,
            rows,
            settings,
            nowMillis,
            studyAheadMillis,
            plan,
            continueAllKanjiSession,
            ladder,
        )
    }

    private fun countSeeded(
        scheduler: BridgeScheduler,
        seededItems: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        studyAheadMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan,
        continueAllKanjiSession: Boolean,
        ladder: RecordsBase.StudyLadderSettings?,
    ): Int {
        val allowedKanji = StudySessionFocusPolicy.allowedKanji(plan, continueAllKanjiSession)
        return scheduler.randomizedSessionTaskKeys(
            seededItems,
            rows,
            nowMillis,
            studyAheadMillis,
            allowedKanji,
            settings,
            ladder,
            0L,
        ).asSequence()
            .filter { it.isNotEmpty() }
            .distinct()
            .count()
    }
}
