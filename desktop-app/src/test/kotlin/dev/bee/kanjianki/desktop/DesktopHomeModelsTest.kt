package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.core.SyncStatus
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.SyncStatusSnapshot
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.presentation.FocusEmptyReason
import dev.bee.kanjianki.presentation.HomeAccent
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeRecommendation
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectHandshake
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectStatusMapping
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the desktop side of Goal 194's claim: Home is one set of models derived from
 * one set of facts, and desktop reaches them through the same `:core` policies
 * Android calls rather than through a second derivation.
 *
 * These are the desktop half of a twinned pair. The shared half — that the surfaces
 * render each model identically — lives in `:feature-home`'s own tests; what can only
 * be checked here is that the mapping feeding them is the shared one.
 */
class DesktopHomeModelsTest {
    @Test
    fun theThreeTilesKeepAndroidsOrderAndOnlySyncIsTappable() {
        val metrics = DesktopHomeModels.metrics(
            sync = sync(status = "success"),
            streak = streak(studiedToday = true),
            canSync = true,
            dailyPlan = null,
            plan = null,
        )

        // The order is load-bearing: `HomeMetricRow` renders the list as given, so a
        // reordering here would move the tiles on desktop only.
        assertEquals(
            listOf(HomeMetricKind.SYNC, HomeMetricKind.STREAK, HomeMetricKind.FOCUS),
            metrics.map { it.kind },
        )
        // And only sync was ever tappable on Android.
        assertEquals(
            listOf<KaniAction?>(KaniAction.Provider.RequestSync, null, null),
            metrics.map { it.action },
        )
    }

    @Test
    fun aSyncThatCannotHappenIsNotReportedAsUpToDate() {
        val succeeded = sync(status = "success")

        // Ready, synced, nothing waiting: the only combination that is neutral.
        assertEquals(
            HomeAccent.NEUTRAL,
            syncTile(sync = succeeded, canSync = true).accent,
        )
        // A successful sync against a provider Kani can no longer reach is stale
        // evidence, not a current collection.
        assertEquals(
            HomeAccent.DUE,
            syncTile(sync = succeeded, canSync = false).accent,
        )
        // Never synced at all.
        assertEquals(HomeAccent.DUE, syncTile(sync = null, canSync = true).accent)
        // Synced, but the plan says the result cannot be judged without another one.
        assertEquals(
            HomeAccent.DUE,
            syncTile(
                sync = succeeded,
                canSync = true,
                dailyPlan = DailyStudyPlanPolicy.plan(
                    DailyStudyPlanRequest(nowMillis = NOW, consecutiveFailedSyncs = 3),
                ).copy(syncStatus = SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS),
            ).accent,
        )
    }

    @Test
    fun theStreakTileReportsRatherThanAsks() {
        assertEquals(
            HomeAccent.RESTING,
            tile(HomeMetricKind.STREAK, streak = streak(studiedToday = true)).accent,
        )
        // NEUTRAL rather than DUE for an unstudied day: the Today card is what asks.
        assertEquals(
            HomeAccent.NEUTRAL,
            tile(HomeMetricKind.STREAK, streak = streak(studiedToday = false)).accent,
        )
    }

    @Test
    fun aTodayCardWithNothingToSayIsNoCardAtAll() {
        assertNull(DesktopHomeModels.todayPlan(null))

        // Nothing due, nothing new, never synced, no streak: the summary is empty and
        // the recommendation offers no action, so there is no card to title.
        val idle = DesktopHomeModels.todayPlan(
            DailyStudyPlanPolicy.plan(DailyStudyPlanRequest(nowMillis = NOW)),
        )
        if (idle != null) {
            assertFalse(idle.summary == UiText.EMPTY && idle.details.isEmpty())
        }

        val due = DesktopHomeModels.todayPlan(
            DailyStudyPlanPolicy.plan(
                DailyStudyPlanRequest(
                    nowMillis = NOW,
                    dueAtMillis = listOf(NOW - 1_000L, NOW - 2_000L),
                    studiedToday = false,
                    streak = streak(studiedToday = false),
                    lastSuccessfulSyncAtMillis = NOW - 1_000L,
                ),
            ),
        )
        assertNotNull(due)
        assertEquals(HomeRecommendation.STUDY_NOW, due?.recommendation)
    }

    @Test
    fun aStreakOnlyStudyRecommendationIsStillStudyNow() {
        // `:core` distinguishes STUDY_NOW from STUDY_ONCE_FOR_STREAK; the portable
        // model does not, because Android gave both the same label and the same
        // action. The distinction survives in the reasons the card still shows.
        val streakOnly = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = NOW,
                dueAtMillis = listOf(NOW + 6L * 60L * 60L * 1000L),
                studiedToday = false,
                streak = streak(studiedToday = false, currentDays = 9),
                lastSuccessfulSyncAtMillis = NOW - 1_000L,
            ),
        )
        val today = DesktopHomeModels.todayPlan(streakOnly)

        assertNotNull(today)
        assertTrue(
            "$streakOnly",
            today?.recommendation == HomeRecommendation.STUDY_NOW ||
                today?.recommendation == HomeRecommendation.WAIT_UNTIL_LATER,
        )
    }

    @Test
    fun aDetailLineThatOnlyRestatesTheSummaryIsDropped() {
        val plan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = NOW,
                dueAtMillis = listOf(NOW - 1_000L),
                studiedToday = false,
                streak = streak(studiedToday = false),
                lastSuccessfulSyncAtMillis = NOW - 1_000L,
            ),
        )
        val today = DesktopHomeModels.todayPlan(plan)
        val summary = (today?.summary as? UiText.Literal)?.text.orEmpty()

        for (detail in today?.details.orEmpty()) {
            val text = (detail as? UiText.Literal)?.text.orEmpty()
            assertFalse("'$text' restates '$summary'", summary.contains(text, ignoreCase = true))
        }
    }

    @Test
    fun theFocusPreviewStopsAtThreeCardsAndKeepsTheQueueOrder() {
        val rows = listOf(row("先"), row("後"), row("新"), row("学"))
        val items = listOf(
            review("先", NOW - 4_000L),
            review("後", NOW - 3_000L),
            review("新", NOW - 2_000L),
            review("学", NOW - 1_000L),
        )
        val entries = FocusQueuePolicy.queuedEntries(rows, items, NOW, 0L, null, ladder())

        val queue = DesktopHomeModels.focusQueue(rows, entries, null, NOW, 2)

        assertEquals(3, queue.cards.size)
        assertEquals(entries.take(3).map { it.row.kanji }, queue.cards.map { it.kanji })
        assertTrue(queue.showsViewAll)
    }

    @Test
    fun anEmptyQueueOnAFullCollectionIsRestingNotUnsynced() {
        val rows = listOf(row("先"))

        // The distinction `FocusQueue.hasImportedKanji` exists for, and the reason it
        // is taken from the rows rather than from the card list.
        assertEquals(
            FocusEmptyReason.NOTHING_ACTIVE,
            DesktopHomeModels.focusQueue(rows, emptyList(), null, NOW, 2).emptyReason,
        )
        assertEquals(
            FocusEmptyReason.NOTHING_IMPORTED,
            DesktopHomeModels.focusQueue(emptyList(), emptyList(), null, NOW, 2).emptyReason,
        )
    }

    @Test
    fun aCardsAccentAndTagsFollowItsSchedulerState() {
        val due = card(review("先", NOW - 1_000L))
        assertEquals(HomeAccent.DUE, due.accent)

        val learning = card(item("学", "learning", NOW + 1_000L, totalReviews = 1))
        assertEquals(HomeAccent.LEARNING, learning.accent)

        val resting = card(review("後", NOW + 1_000L))
        assertEquals(HomeAccent.RESTING, resting.accent)

        // Every card carries its rung; only a relearning or partly-learned one carries
        // a second tag.
        assertEquals(1, resting.tags.size)
        assertEquals(HomeAccent.LEARNING, resting.tags.first().accent)

        // The phase is set explicitly rather than left to `derivedPhase`, which reads
        // any answered card as relearning — the two extra tags are keyed off the
        // phase, so deriving it would make these two cases the same case.
        val relearning = card(
            item("裂", "review", NOW - 1_000L, totalReviews = 4)
                .withPhase(RecordsBase.SchedulerPhase.RELEARNING),
        )
        assertEquals(2, relearning.tags.size)
        assertEquals(HomeAccent.DUE, relearning.tags[1].accent)

        // A brand-new card has no second tag; one part-way through learning does.
        val untouched = card(
            item("字", "new", NOW, totalReviews = 0)
                .withPhase(RecordsBase.SchedulerPhase.NEW_LEARNING),
        )
        assertEquals(1, untouched.tags.size)
        val partlyLearned = card(
            item("字", "new", NOW, totalReviews = 2)
                .withPhase(RecordsBase.SchedulerPhase.NEW_LEARNING),
        )
        assertEquals(2, partlyLearned.tags.size)
        assertEquals(HomeAccent.RESTING, partlyLearned.tags[1].accent)
    }

    @Test
    fun theDeckOverviewIsSilentUntilSomethingIsImported() {
        assertEquals(emptyList<UiText>(), DesktopHomeModels.deckOverview(snapshot(), NOW, emptySet()))

        val imported = DesktopHomeModels.deckOverview(
            snapshot(rows = listOf(row("先")), items = listOf(review("先", NOW - 1_000L))),
            NOW,
            emptySet(),
        )
        assertTrue(imported.isNotEmpty())
    }

    @Test
    fun theDailyPlanReadsAutomaticSyncRatherThanAssumingItIsOff() {
        val study = snapshot(rows = listOf(row("先")), items = listOf(review("先", NOW + DAY)))
        // Two days old: past the 24-hour freshness window, inside the three-day grace
        // automatic sync earns. Either side of that pair and the flag makes no
        // difference, which is what made the first version of this test pass twice.
        val stale = NOW - 2L * DAY

        val manual = DesktopHomeModels.dailyPlan(
            study, streak(studiedToday = false), stale, 0, settingsReader(autoSync = false), NOW,
        )
        val automatic = DesktopHomeModels.dailyPlan(
            study, streak(studiedToday = false), stale, 0, settingsReader(autoSync = true), NOW,
        )

        // The flag has to reach the policy: with automatic sync off, a two-day-old
        // collection is the user's job, and with it on Kani is already handling it.
        assertEquals(SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS, manual.syncStatus)
        assertEquals(SyncStatus.CURRENT, automatic.syncStatus)
    }

    @Test
    fun thereIsNoAdaptivePlanUntilSomethingIsImported() {
        assertNull(DesktopHomeModels.adaptivePlan(snapshot(), settings(), streak(), NOW))

        val planned = DesktopHomeModels.adaptivePlan(
            snapshot(rows = listOf(row("先")), items = listOf(review("先", NOW - 1_000L))),
            settings(),
            streak(),
            NOW,
        )
        assertNotNull(planned)
    }

    @Test
    fun theRemainingCountRunsTheRealPipelineRatherThanCountingDueRows() {
        val rows = listOf(row("先"), row("後"))
        val items = listOf(review("先", NOW - 1_000L), review("後", NOW + 10L * DAY))
        val study = snapshot(rows = rows, items = items)
        val plan = DesktopHomeModels.adaptivePlan(study, settings(), streak(), NOW)
        val annotated = ArrayList<Int>()

        val count = DesktopHomeModels.studyRemainingCount(
            study = study,
            settings = settings(),
            plan = plan,
            dueLegacyWritingRepairs = emptyList(),
            annotate = { seeded -> annotated += seeded.size; seeded },
            nowMillis = NOW,
        )

        // The annotator is consulted, which is what makes this the session's own
        // count rather than a due-row tally: a count that disagreed with the session
        // would badge the study button with work the Study route then refuses.
        assertEquals(1, annotated.size)
        assertTrue("count=$count", count >= 1)
    }

    @Test
    fun onboardingCarriesTheProvidersOwnWordsRatherThanASharedString() {
        val plan = DesktopHomeModels.onboarding(
            provider = status(CollectionAvailability.NOT_AVAILABLE),
            settings = settings(),
            latestSync = null,
            repairedKanjiCount = 0,
        )

        assertEquals(OnboardingStep.CONNECT_PROVIDER, plan.step)
        // The one line no shared resource table could hold: which of "start Anki" and
        // "this AnkiConnect is too old" applies is the provider module's answer.
        assertEquals(
            UiText.Literal(AnkiConnectStatusMapping.messageFor(UNAVAILABLE)),
            plan.hostCopy.guidance,
        )
    }

    @Test
    fun onboardingWalksTheStepsTheUserActuallyHasToSatisfy() {
        assertEquals(
            OnboardingStep.AUTHORIZE_PROVIDER,
            DesktopHomeModels.onboarding(
                status(CollectionAvailability.AUTH_REQUIRED), settings(), null, 0,
            ).step,
        )
        // Ready and bound, but never synced.
        assertEquals(
            OnboardingStep.READY_FIRST_SYNC,
            DesktopHomeModels.onboarding(
                status(CollectionAvailability.READY), settings(), null, 0,
            ).step,
        )
        assertEquals(
            OnboardingStep.SYNCED,
            DesktopHomeModels.onboarding(
                status(CollectionAvailability.READY), settings(), sync("success"), 0,
            ).step,
        )
        assertEquals(
            OnboardingStep.RECOVER_SYNC,
            DesktopHomeModels.onboarding(
                status(CollectionAvailability.READY), settings(), sync("error"), 0,
            ).step,
        )
    }

    @Test
    fun aSourceThatImportsNothingIsNotReportedAsASource() {
        // `importTaggedCards` with no tags and a query import with a blank query both
        // import nothing, which is exactly the state CHOOSE_SOURCE is for.
        val emptySources = DesktopHomeModels.onboarding(
            provider = status(CollectionAvailability.READY),
            settings = settings(
                importActiveCards = false,
                importSuspendedCards = false,
                importWeakCards = false,
                importTaggedCards = true,
                importTags = emptyList(),
                importBrowserQueryCards = true,
                importBrowserQuery = "   ",
            ),
            latestSync = null,
            repairedKanjiCount = 0,
        )

        assertEquals(OnboardingStep.CHOOSE_SOURCE, emptySources.step)
        assertEquals(emptySet<ImportSource>(), emptySources.binding.sources)
    }

    @Test
    fun eachImportFlagBecomesItsOwnSource() {
        val everything = DesktopHomeModels.onboarding(
            provider = status(CollectionAvailability.READY),
            settings = settings(
                importActiveCards = true,
                importSuspendedCards = true,
                importWeakCards = true,
                importTaggedCards = true,
                importTags = listOf("leech"),
                importBrowserQueryCards = true,
                importBrowserQuery = "deck:current",
            ),
            latestSync = null,
            repairedKanjiCount = 0,
        ).binding

        assertEquals(
            setOf(
                ImportSource.ACTIVE_CARDS,
                ImportSource.SUSPENDED_CARDS,
                ImportSource.TAGGED_CARDS,
                ImportSource.WEAK_CARDS,
                ImportSource.BROWSER_QUERY,
            ),
            everything.sources,
        )
        assertEquals("deck:current", everything.browserQuery)
        assertEquals("Kiku", everything.noteType)
    }

    @Test
    fun everyProviderFailureKindHasItsOwnPresentationKind() {
        val mapped = CollectionFailureKind.entries.associateWith(DesktopHomeModels::failureKind)

        assertEquals(
            mapOf(
                CollectionFailureKind.NOT_AVAILABLE to
                    PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
                CollectionFailureKind.AUTH_REQUIRED to
                    PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED,
                CollectionFailureKind.INVALID_CONFIGURATION to
                    PresentationFailure.Kind.CONFIGURATION,
                CollectionFailureKind.UNSUPPORTED_CAPABILITY to
                    PresentationFailure.Kind.CAPABILITY_MISSING,
                CollectionFailureKind.TRANSIENT to PresentationFailure.Kind.TRANSIENT,
                CollectionFailureKind.CANCELLED to PresentationFailure.Kind.CANCELLED,
            ),
            mapped,
        )
        // Retryability is carried, not re-decided: a mapping that flipped it would
        // either strand the user or send them in a circle.
        for ((source, target) in mapped) {
            assertEquals("$source", source.retryableByDefault, target.retryable)
        }
    }

    @Test
    fun browseHidesSuspendedRowsUnlessTheUserAskedForThem() {
        val items = listOf(inventory("先", suspended = false), inventory("後", suspended = true))

        val hidden = DesktopHomeModels.browse(items, "", false, false, showSuspended = false)
        assertEquals(listOf("先"), hidden.rows.map { it.kanji })

        val shown = DesktopHomeModels.browse(items, "水", false, false, showSuspended = true)
        assertEquals(listOf("先", "後"), shown.rows.map { it.kanji })
        assertEquals("水", shown.query)
        // A locally suspended kanji is one the user took out of practice.
        assertEquals(listOf(true, false), shown.rows.map { it.studied })
        assertFalse(shown.allStudied)
    }

    @Test
    fun aFullPageOfBrowseResultsIsReportedAsTruncated() {
        val short = DesktopHomeModels.browse(
            List(2) { inventory("先") }, "", false, false, showSuspended = false,
        )
        assertFalse(short.truncated)

        // The query layer's limit is private, so it is pinned here rather than
        // imported; a page filled to the limit is a page with more behind it.
        val full = DesktopHomeModels.browse(
            List(300) { inventory("先") }, "", false, false, showSuspended = false,
        )
        assertTrue(full.truncated)
    }

    private fun syncTile(
        sync: SyncStatusSnapshot?,
        canSync: Boolean,
        dailyPlan: DailyStudyPlan? = null,
    ) = tile(HomeMetricKind.SYNC, sync = sync, canSync = canSync, dailyPlan = dailyPlan)

    private fun tile(
        kind: HomeMetricKind,
        sync: SyncStatusSnapshot? = null,
        streak: StudyStreakPolicy.Streak = streak(),
        canSync: Boolean = true,
        dailyPlan: DailyStudyPlan? = null,
    ) = DesktopHomeModels.metrics(sync, streak, canSync, dailyPlan, plan = null)
        .first { it.kind == kind }

    private fun card(item: RecordsStudyModels.StudyItem) = DesktopHomeModels.focusQueue(
        rows = listOf(row(item.kanji)),
        entries = listOf(FocusQueuePolicy.QueueEntry(row(item.kanji), item)),
        plan = null,
        nowMillis = NOW,
        matureSupportThreshold = 2,
    ).cards.single()

    private fun status(availability: CollectionAvailability) = DesktopProviderStatus(
        message = AnkiConnectStatusMapping.messageFor(UNAVAILABLE),
        availability = availability,
        capabilities = emptySet(),
    )

    private fun snapshot(
        rows: List<RecordsImportModels.DashboardRow> = emptyList(),
        items: List<RecordsStudyModels.StudyItem> = emptyList(),
    ) = StudyQueueSnapshot(
        activeRows = rows,
        availableRows = rows,
        studyItems = items,
        locallySuspendedKanji = emptySet(),
        latestSuccessfulSyncAtMillis = null,
        studyLadder = ladder(),
        syncSettings = settings(),
        schedulerParameters = RecordsSchedulerModels.SchedulerParameters
            .defaults(),
        schedulerFsrsWeights = null,
        learningSteps = RecordsSchedulerModels.LearningStepSettings
            .defaults(),
        adaptiveWorkload = AdaptiveWorkloadSnapshot(
            workPercent = 100,
            maxItems = 40,
            mode = "balanced",
        ),
        studyAheadMinutes = 20,
        studyStreak = StudyStreakSnapshot(
            currentDays = 3,
            bestDays = 9,
            studiedToday = false,
            reviewsToday = 0,
            lastStudyAtMillis = NOW - DAY,
        ),
        recentReviewStats = RecordsSchedulerModels.ReviewStats(
            12, 2, 2, 6, 2, 0, 0,
        ),
        studiedKanjiToday = emptySet(),
        dueLegacyWritingRepairs = emptyList(),
        consecutiveFailedSyncs = 0,
    )

    private fun settings(
        importActiveCards: Boolean = true,
        importSuspendedCards: Boolean = true,
        importWeakCards: Boolean = true,
        importTaggedCards: Boolean = false,
        importTags: List<String> = emptyList(),
        importBrowserQueryCards: Boolean = false,
        importBrowserQuery: String = "",
    ): RecordsSyncModels.Settings = RecordsSyncModels.Settings(
        "Kiku",
        "Mining",
        "Expression",
        "ExpressionReading",
        "MainDefinition",
        "Sentence",
        "Frequency",
        "FreqSort",
        21,
        2,
        1,
        2_500,
        24,
        3,
        14,
        2,
        2,
        importActiveCards,
        importSuspendedCards,
        importTaggedCards,
        importTags,
        importWeakCards,
        7.5,
        3,
        1,
        importBrowserQueryCards,
        importBrowserQuery,
        "balanced_priority",
        21,
        3,
        2,
    )

    private fun ladder() = RecordsBase.StudyLadderSettings.defaults()

    private fun streak(
        studiedToday: Boolean = false,
        currentDays: Int = 3,
    ) = StudyStreakPolicy.Streak(
        currentDays = currentDays,
        bestDays = 9,
        studiedToday = studiedToday,
        reviewsToday = if (studiedToday) 4 else 0,
        lastStudyAtMillis = if (studiedToday) NOW else NOW - DAY,
    )

    private fun sync(status: String) = SyncStatusSnapshot(
        status = status,
        activeNotes = 40,
        activeCards = 80,
        suspendedCards = 5,
        importedKanji = 12,
        finishedAtMillis = NOW - 60_000L,
        errorMessage = if (status == "success") "" else "the collection went away",
        removalMessage = "",
    )

    private fun row(kanji: String) = RecordsImportModels.DashboardRow(
        kanji,
        900,
        "meaning",
        "reading",
        "search",
        50,
        "reason",
        "reason text",
        1,
        1,
        0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun review(kanji: String, dueAtMillis: Long) =
        item(kanji, "review", dueAtMillis, totalReviews = 1)

    private fun item(
        kanji: String,
        state: String,
        dueAtMillis: Long,
        totalReviews: Int,
    ) = RecordsStudyModels.StudyItem(
        kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L,
    )

    private fun inventory(kanji: String, suspended: Boolean = false) =
        RecordsImportModels.KanjiInventoryItem(kanji, "meaning", "reading", "search", 2, 3, suspended, NOW)

    /** A reader answering one key, for the automatic-sync branch. */
    private fun settingsReader(autoSync: Boolean) = object : DeviceSettingsReader {
        override fun contains(key: DeviceSettingKey<*>) = key == DeviceSettingKeys.autoSyncEnabled

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
            if (key == DeviceSettingKeys.autoSyncEnabled) autoSync as T else null
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60L * 60L * 1000L
        val UNAVAILABLE =
            AnkiConnectHandshake.Status.Unavailable(
                detail = "connection refused",
            )
    }
}
