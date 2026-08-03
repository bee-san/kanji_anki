package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.StudyNowCountCoordinator
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.FocusQueueCopy
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.HomeDeckOverviewPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecommendedAction
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyNowCountPolicy
import dev.bee.kanjianki.core.StudyProjectionEligibilityPolicy
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.SyncStatus
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.SyncStatusSnapshot
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.BrowseRow
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FocusCard
import dev.bee.kanjianki.presentation.FocusQueue
import dev.bee.kanjianki.presentation.FocusTag
import dev.bee.kanjianki.presentation.HomeAccent
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.HomeMetric
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeRecommendation
import dev.bee.kanjianki.presentation.HostOnboardingCopy
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingPolicy
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SyncOutcome
import dev.bee.kanjianki.presentation.TodayPlan
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.syncapi.CollectionFailureKind

/**
 * Turns a loaded profile snapshot into the portable Home models `:feature-home`
 * renders.
 *
 * Every derivation here is Android's, reached through the shared `:core` policies
 * Android already calls — `HomeDeckOverviewPolicy`, `DailyStudyPlanPolicy`,
 * `AdaptiveLoadPlanner`, `FocusQueuePolicy`, `HomeTextCopy`, `FocusQueueCopy`. That
 * is the point rather than an economy: Goal 194's claim is that both hosts show the
 * same Home from the same facts, and the only checkable form of that claim is both
 * hosts calling the same pure functions. What Android's `buildHomeScreenModel` does
 * *around* those calls — six `() -> Unit` callbacks, drawable ids, packed ARGB
 * accents — is exactly what `:presentation-api` replaced, so it is not mirrored.
 *
 * Three Android surfaces are deliberately absent, and none is an oversight: the
 * update-check banner and the first-run offline notice are host-specific (Goals 198
 * and 201 own them, and [HomeDashboard]'s own KDoc says so), and the writing-repair
 * count feeding `studyRemainingCount` reads the legacy repair queue, which desktop
 * has never written a row into.
 */
internal object DesktopHomeModels {
    /**
     * The three Home tiles, in Android's order, with only the sync tile actionable.
     *
     * The order is load-bearing: `HomeMetricRow` renders the list as given and
     * `homeMetricTestTag(kind)` keys off the kind, so a reordering here would move
     * the tiles on desktop only. Only SYNC carries an action because only the sync
     * tile was ever tappable on Android — and it dispatches
     * [KaniAction.Provider.RequestSync] rather than starting a sync, because every
     * sync in Kani passes through the user's confirmation.
     */
    fun metrics(
        sync: SyncStatusSnapshot?,
        streak: StudyStreakPolicy.Streak,
        canSync: Boolean,
        dailyPlan: DailyStudyPlan?,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<HomeMetric> {
        val lastSyncSucceeded = sync != null && sync.status == STATUS_SUCCESS
        val upToDate = canSync &&
            lastSyncSucceeded &&
            dailyPlan?.syncStatus != SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS
        return listOf(
            HomeMetric(
                kind = HomeMetricKind.SYNC,
                value = UiText.Literal(HomeTextCopy.homeSyncValue(sync?.finishedAtMillis)),
                detail = UiText.Literal(HomeTextCopy.syncMetricStatus(upToDate)),
                // Not "up to date means neutral": a sync that has not happened is
                // work waiting, which is what DUE means everywhere else on Home.
                accent = if (upToDate) HomeAccent.NEUTRAL else HomeAccent.DUE,
                action = KaniAction.Provider.RequestSync,
            ),
            HomeMetric(
                kind = HomeMetricKind.STREAK,
                value = UiText.Literal(HomeTextCopy.streakHeadline(streak.currentDays)),
                detail = UiText.Literal(
                    HomeTextCopy.streakMetricBody(streak.studiedToday, streak.bestDays),
                ),
                // RESTING rather than DUE for a day not yet studied: the streak tile
                // reports, it does not ask. The Today card is what asks.
                accent = if (streak.studiedToday) HomeAccent.RESTING else HomeAccent.NEUTRAL,
            ),
            HomeMetric(
                kind = HomeMetricKind.FOCUS,
                value = UiText.Literal(HomeTextCopy.focusHeadline(plan)),
                accent = HomeAccent.LEARNING,
            ),
        )
    }

    /**
     * The Today card, or `null` when there is nothing worth a card.
     *
     * The details rule is `MainActivityHomeTodayPlan`'s, including the reason its
     * comment gives: a reason line that just restates the summary adds visual noise,
     * so only reasons the summary does not already contain survive. The
     * next-useful-time line is appended only for a wait, because on any other
     * recommendation it would name a time the user is not waiting for.
     */
    fun todayPlan(plan: DailyStudyPlan?): TodayPlan? {
        if (plan == null) return null
        val summary = HomeTextCopy.todayPlanSummary(plan)
        val details = buildList {
            for (reason in plan.reasons) {
                if (!summary.contains(reason, ignoreCase = true)) add(UiText.Literal(reason))
            }
            if (
                plan.recommendedAction == RecommendedAction.WAIT_UNTIL_LATER &&
                plan.nextUsefulReminderAtMillis > 0L
            ) {
                add(UiText.Literal(HomeTextCopy.nextUsefulTimeLabel(plan.nextUsefulReminderAtMillis)))
            }
        }
        val today = TodayPlan(
            recommendation = recommendation(plan.recommendedAction),
            summary = UiText.Literal(summary),
            details = details,
        )
        // `isEmpty` exists so a host cannot render a titled card with nothing in it;
        // this is the desktop side of the check Android made inline.
        return if (today.isEmpty) null else today
    }

    /**
     * `:core`'s five recommendations as the portable four.
     *
     * `STUDY_ONCE_FOR_STREAK` folds into `STUDY_NOW` because that is already
     * Android's own grouping — `homeTodayPlanModel` gives both the same label and the
     * same `onStudy` callback — so the collapse loses nothing the user could see. The
     * distinction survives in [plan.reasons], which the Today card still shows.
     */
    private fun recommendation(action: RecommendedAction): HomeRecommendation = when (action) {
        RecommendedAction.STUDY_NOW,
        RecommendedAction.STUDY_ONCE_FOR_STREAK,
        -> HomeRecommendation.STUDY_NOW
        RecommendedAction.SYNC_FIRST -> HomeRecommendation.SYNC_FIRST
        RecommendedAction.WAIT_UNTIL_LATER -> HomeRecommendation.WAIT_UNTIL_LATER
        RecommendedAction.NOTHING_USEFUL_NOW -> HomeRecommendation.NOTHING_USEFUL_NOW
    }

    /**
     * The focus queue preview, capped at the same three cards Android shows.
     *
     * [hasImportedKanji] is taken from the dashboard rows rather than from the card
     * list, which is the distinction [FocusQueue] exists to preserve: an empty queue
     * on a full collection is resting, and an empty queue on an empty collection is a
     * sync that has not happened.
     */
    fun focusQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        entries: List<FocusQueuePolicy.QueueEntry>,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        nowMillis: Long,
        matureSupportThreshold: Int,
    ): FocusQueue = FocusQueue(
        plan = UiText.Literal(AdaptiveFocusCopy.adaptiveFocusText(plan)),
        cards = entries.take(PREVIEW_ROW_LIMIT).map { entry ->
            focusCard(entry, nowMillis, matureSupportThreshold)
        },
        hasImportedKanji = rows.isNotEmpty(),
    )

    private fun focusCard(
        entry: FocusQueuePolicy.QueueEntry,
        nowMillis: Long,
        matureSupportThreshold: Int,
    ): FocusCard {
        val row = entry.row
        val item = entry.item
        return FocusCard(
            kanji = row.kanji,
            meaning = UiText.Literal(StudyTextCopy.rowMeaning(row)),
            sourceEvidence = UiText.Literal(FocusQueueCopy.sourceEvidenceText(row)),
            reasonLine = UiText.Literal(
                FocusQueueCopy.focusReasonLine(row, item, nowMillis, matureSupportThreshold),
            ),
            body = UiText.Literal(
                StudyTextCopy.compact(FocusQueueCopy.queueCardBody(row), BODY_MAX_CHARS),
            ),
            tags = buildList {
                add(
                    FocusTag(
                        label = UiText.Literal(FocusQueueCopy.recognitionStageLabel(item)),
                        accent = HomeAccent.LEARNING,
                    ),
                )
                if (item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                    add(
                        FocusTag(
                            label = UiText.Literal(HomeTextCopy.relearningChipLabel()),
                            accent = HomeAccent.DUE,
                        ),
                    )
                } else if (
                    item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING &&
                    item.totalReviews > 0
                ) {
                    add(
                        FocusTag(
                            label = UiText.Literal(HomeTextCopy.deckOverviewLearningLabel()),
                            accent = HomeAccent.RESTING,
                        ),
                    )
                }
            },
            accent = accent(FocusQueuePolicy.rowTone(item, nowMillis)),
        )
    }

    /**
     * `:core`'s queue tone as a portable accent.
     *
     * One-to-one, which is why the tone is worth translating rather than passing an
     * ARGB colour through: `RESTING` was `PINK_STROKE` from the legacy palette on
     * Android, a hue the active theme may not contain at all.
     */
    private fun accent(tone: FocusQueuePolicy.QueueTone): HomeAccent = when (tone) {
        FocusQueuePolicy.QueueTone.DUE -> HomeAccent.DUE
        FocusQueuePolicy.QueueTone.LEARNING -> HomeAccent.LEARNING
        FocusQueuePolicy.QueueTone.RESTING -> HomeAccent.RESTING
    }

    /** The deck-overview lines, or none at all when nothing has been imported. */
    fun deckOverview(
        study: StudyQueueSnapshot,
        nowMillis: Long,
        locallySuspendedKanji: Set<String>,
    ): List<UiText> {
        if (study.activeRows.isEmpty()) return emptyList()
        return HomeDeckOverviewPolicy
            .from(study.studyItems, study.activeRows, nowMillis, locallySuspendedKanji)
            .rows()
            .map(UiText::Literal)
    }

    /**
     * The daily plan, from the same eligibility projection the Study route uses.
     *
     * `autoSyncEnabled` is read from device settings rather than assumed false,
     * because it changes the plan: an unsynced collection with automatic sync on is a
     * wait, and with it off is a `SYNC_FIRST`.
     */
    fun dailyPlan(
        study: StudyQueueSnapshot,
        streak: StudyStreakPolicy.Streak,
        latestSuccessfulSyncAtMillis: Long?,
        consecutiveFailedSyncs: Int,
        deviceSettings: DeviceSettingsReader,
        nowMillis: Long,
    ): DailyStudyPlan {
        val eligible = StudyProjectionEligibilityPolicy.eligibleStudyItems(
            study.studyItems,
            study.activeRows,
            study.studyLadder,
        )
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = nowMillis,
                dueAtMillis = eligible.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                streak = streak,
                newProblemKanjiAvailable = if (study.activeRows.isEmpty()) {
                    0
                } else {
                    eligible.count { it.totalReviews == 0 }
                },
                lastSuccessfulSyncAtMillis = latestSuccessfulSyncAtMillis,
                autoSyncEnabled = deviceSettings.read(DeviceSettingKeys.autoSyncEnabled) ?: false,
                consecutiveFailedSyncs = consecutiveFailedSyncs,
            ),
        )
    }

    /**
     * The adaptive load plan, or `null` when nothing has been imported.
     *
     * `readingExposure` is left at the builder's `ExposureIndex.EMPTY` default rather
     * than mirrored from Android, which reads it through an `:app`-only media reader.
     * The index refines *ordering* within the plan; absent, the plan is still the
     * correct size and the same kanji are in it.
     */
    fun adaptivePlan(
        study: StudyQueueSnapshot,
        settings: RecordsSyncModels.Settings,
        streak: StudyStreakPolicy.Streak,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan? {
        if (study.activeRows.isEmpty()) return null
        val workload = study.adaptiveWorkload
        return AdaptiveLoadPlanner().plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                study.activeRows,
                study.studyItems,
                study.recentReviewStats,
                streak.currentDays,
                study.studiedKanjiToday,
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                    workload.workPercent,
                    workload.mode,
                    workload.maxItems,
                ),
                nowMillis,
            ).settings(settings).build(),
        )
    }

    /**
     * How many cards the next study session would serve.
     *
     * Runs the real seed, annotate, replan, and select pipeline rather than counting
     * due rows, because those two numbers disagree: seeding admits new kanji and the
     * replan can resize the plan. A count that disagreed with the session would put a
     * badge on the study button promising work the Study route then refuses.
     *
     * The legacy writing-repair task keys are still asked for, because a profile
     * restored from an Android backup can carry rows the v31 compatibility release is
     * draining. Desktop has never enqueued one, so in practice the list is empty; the
     * gate is here so a restored profile counts the same on both hosts.
     *
     * [annotate] is a blocking call because [StudyNowCountCoordinator.QueueAnnotator]
     * is, and the whole load already runs on an IO dispatcher.
     */
    fun studyRemainingCount(
        study: StudyQueueSnapshot,
        settings: RecordsSyncModels.Settings,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        dueLegacyWritingRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair>,
        annotate: (List<RecordsStudyModels.StudyItem>) -> List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
    ): Int {
        val counted = StudyNowCountCoordinator.count(
            StudyNowCountCoordinator.Request(
                queue = StudyNowCountCoordinator.QueueInput(
                    rows = study.activeRows,
                    currentItems = study.studyItems,
                    settings = settings,
                    ladder = study.studyLadder,
                ),
                timing = StudyNowCountCoordinator.Timing(
                    nowMillis = nowMillis,
                    startOfDayMillis = LocalDayPolicy.localDayStart(nowMillis),
                    studyAheadMillis = study.studyAheadMinutes * MINUTE_MILLIS,
                ),
                mode = StudyNowCountCoordinator.Mode(
                    initialPlan = plan,
                    continueAllKanjiSession = false,
                ),
                pipeline = StudyNowCountCoordinator.Pipeline(
                    scheduler = BridgeScheduler.withWeights(
                        study.schedulerFsrsWeights?.toDoubleArray(),
                    ),
                    annotator = StudyNowCountCoordinator.QueueAnnotator(annotate),
                    replanner = { seeded ->
                        AdaptiveLoadPlanner().plan(
                            AdaptiveLoadPlanner.PlanRequest.builder(
                                study.activeRows,
                                seeded,
                                study.recentReviewStats,
                                study.studyStreak.currentDays,
                                study.studiedKanjiToday,
                                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                                    study.adaptiveWorkload.workPercent,
                                    study.adaptiveWorkload.mode,
                                    study.adaptiveWorkload.maxItems,
                                ),
                                nowMillis,
                            ).settings(settings).build(),
                        )
                    },
                ),
            ),
        )
        val repairTaskKeys =
            if (study.studyLadder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
                dueLegacyWritingRepairs.map(StudySessionProgressTracker::similarRepairProgressKey)
            } else {
                emptyList()
            }
        return StudyNowCountPolicy.includingAdditionalTaskKeys(
            counted.studyItemCount,
            repairTaskKeys,
        )
    }

    /**
     * The onboarding step, from the provider's own answer and the stored settings.
     *
     * [HostOnboardingCopy.guidance] carries `AnkiConnectStatusMapping`'s message
     * verbatim, which is the one line no shared resource table could hold: it is what
     * distinguishes "start Anki" from "this AnkiConnect is too old for Kani", and
     * `HostOnboardingCopy`'s own KDoc names desktop's mapping as its source.
     */
    fun onboarding(
        readiness: ProviderReadiness,
        guidance: String,
        settings: RecordsSyncModels.Settings,
        latestSync: SyncStatusSnapshot?,
        repairedKanjiCount: Int,
    ): OnboardingPlan = OnboardingPolicy.plan(
        readiness = readiness,
        binding = binding(settings),
        sync = syncOutcome(latestSync),
        hostCopy = HostOnboardingCopy(guidance = UiText.Literal(guidance)),
        repairedKanjiCount = repairedKanjiCount,
    )

    /**
     * The stored import settings as a portable binding.
     *
     * `importTaggedCardsEnabled()` and `browserQueryImportEnabled()` are asked rather
     * than the raw flags read, because both are false when their content is empty —
     * a tagged import with no tags and a query import with a blank query would
     * otherwise report a source that imports nothing, which is exactly the state
     * `CHOOSE_SOURCE` is for.
     */
    private fun binding(settings: RecordsSyncModels.Settings): CollectionBinding {
        val query = settings.normalizedBrowserQuery()
        return CollectionBinding(
            noteType = settings.modelName,
            sources = buildSet {
                if (settings.importActiveCards) add(ImportSource.ACTIVE_CARDS)
                if (settings.importSuspendedCards) add(ImportSource.SUSPENDED_CARDS)
                if (settings.importTaggedCardsEnabled()) add(ImportSource.TAGGED_CARDS)
                if (settings.importWeakCards) add(ImportSource.WEAK_CARDS)
                if (settings.browserQueryImportEnabled()) add(ImportSource.BROWSER_QUERY)
            },
            browserQuery = query,
        )
    }

    /**
     * The last sync as a [SyncOutcome].
     *
     * The persisted row is the only sync evidence desktop has: no sync engine is
     * wired to this host yet (Goal 202 owns that), so a running sync is not a state
     * this can report. What it can report honestly is what the last one did.
     */
    private fun syncOutcome(latestSync: SyncStatusSnapshot?): SyncOutcome = when {
        latestSync == null -> SyncOutcome.Never
        latestSync.status == STATUS_SUCCESS -> SyncOutcome.Succeeded(
            importedKanji = latestSync.importedKanji.coerceAtLeast(0),
        )
        else -> SyncOutcome.Failed(
            PresentationFailure(
                kind = PresentationFailure.Kind.UNKNOWN,
                message = UiText.Literal(latestSync.errorMessage),
            ),
        )
    }

    /**
     * A provider failure as a presentation one.
     *
     * Total over [CollectionFailureKind] rather than defaulted, so a new provider
     * failure kind is a compile error here instead of a silently retryable
     * `UNKNOWN`. Retryability is not re-decided: each target kind already carries
     * the same answer its source did.
     */
    fun failureKind(kind: CollectionFailureKind): PresentationFailure.Kind = when (kind) {
        CollectionFailureKind.NOT_AVAILABLE -> PresentationFailure.Kind.PROVIDER_UNAVAILABLE
        CollectionFailureKind.AUTH_REQUIRED -> PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED
        CollectionFailureKind.INVALID_CONFIGURATION -> PresentationFailure.Kind.CONFIGURATION
        CollectionFailureKind.UNSUPPORTED_CAPABILITY -> PresentationFailure.Kind.CAPABILITY_MISSING
        CollectionFailureKind.TRANSIENT -> PresentationFailure.Kind.TRANSIENT
        CollectionFailureKind.CANCELLED -> PresentationFailure.Kind.CANCELLED
    }

    /**
     * Browse rows from the persisted study inventory.
     *
     * [truncated] is compared against a local limit rather than read from the query
     * layer because `SqlHomeData`'s `INVENTORY_ROW_LIMIT` is private. That is what
     * [BrowseResults.of]'s `truncated` parameter is for — its KDoc says the limit is
     * the query layer's — and the two are pinned together by test rather than by
     * import.
     *
     * `studied` is left to [BrowseRow]'s `!suspended` default, matching Android:
     * a locally suspended kanji is one the user took out of practice.
     */
    fun browse(
        items: List<RecordsImportModels.KanjiInventoryItem>,
        query: String,
        onlySimilarKanji: Boolean,
        allKanjiScope: Boolean,
        showSuspended: Boolean,
    ): BrowseResults = BrowseResults.of(
        candidates = items.map { item ->
            BrowseRow(
                kanji = item.kanji,
                meaning = UiText.Literal(HomeTextCopy.browseItemMeaning(item)),
                readings = UiText.Literal(item.readings),
                summary = UiText.Literal(
                    HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount),
                ),
                suspended = item.suspended,
            )
        },
        query = query,
        onlySimilarKanji = onlySimilarKanji,
        allKanjiScope = allKanjiScope,
        showSuspended = showSuspended,
        truncated = items.size >= INVENTORY_ROW_LIMIT,
    )

    /** Android's own preview cap, so both hosts show the same three cards. */
    private const val PREVIEW_ROW_LIMIT = 3

    /** `SqlHomeData.INVENTORY_ROW_LIMIT`, which is private to that class. */
    private const val INVENTORY_ROW_LIMIT = 300

    private const val BODY_MAX_CHARS = 72

    private const val MINUTE_MILLIS = 60_000L

    private const val STATUS_SUCCESS = "success"
}
