package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveHealthSnapshot
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.data.HomeSnapshot
import dev.bee.kanjianki.data.KaniOutcomeSnapshot
import dev.bee.kanjianki.data.LadderHealthSnapshot
import dev.bee.kanjianki.data.MatureSupportGainedSnapshot
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyImpactSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.StudyTaskTimeSnapshot
import dev.bee.kanjianki.data.WeakKanjiImprovedSnapshot
import dev.bee.kanjianki.data.fakes.FakeHomeRepository
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeStatsRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NonStudyUseCasesStateTest {
    @Test
    fun emptyRoutesReturnRenderableSnapshotsWithoutRequestingWriteBackConsent() = runTest {
        val repositories = Repositories()
        repositories.home.loadHomeHandler = { StoreResult.ok(emptyHome()) }
        repositories.home.studySearchHandler = { _, _, _ -> StoreResult.ok(emptyList()) }
        repositories.study.loadQueueHandler = { StoreResult.ok(emptyStudyQueue()) }
        repositories.settings.loadHandler = { StoreResult.ok(settings(tagRepairedCards = false)) }
        repositories.stats.cachedResult = StoreResult.ok(emptyStats(NOW))

        val home = repositories.homeUseCases()
        val route = home.loadRoute(NOW)

        assertTrue(route.home.activeRows.isEmpty())
        assertTrue(route.study.studyItems.isEmpty())
        assertTrue(home.searchStudyInventory("", false, false).isEmpty())
        assertTrue(home.loadGameData().activeRows.isEmpty())
        assertEquals(KaniThemeChoice.SYSTEM, repositories.settingsUseCases().load().themeChoice)
        assertEquals(NOW, repositories.statsUseCases().loadForDisplay(NOW).generatedAtMillis)
    }

    @Test
    fun loadingAndCancellationFollowTheSuspendingRepositoryCall() = runTest {
        val repository = FakeSettingsRepository()
        val response = CompletableDeferred<StoreResult<SettingsSnapshot>>()
        repository.loadHandler = { response.await() }
        val request = async { SettingsUseCases(repository).load() }

        runCurrent()
        assertFalse(request.isCompleted)

        request.cancelAndJoin()
        response.complete(StoreResult.ok(settings()))
        assertTrue(request.isCancelled)
    }

    @Test
    fun populatedBrowseGamesAndStatsDataFlowThroughImmutableSnapshots() = runTest {
        val repositories = Repositories()
        val inventory = RecordsImportModels.KanjiInventoryItem(
            "休",
            "rest",
            "やす",
            "休む",
            100,
            1,
            false,
            NOW,
        )
        repositories.home.studySearchHandler = { query, onlySimilar, includeSuspended ->
            assertEquals("休", query)
            assertTrue(onlySimilar)
            assertTrue(includeSuspended)
            StoreResult.ok(listOf(inventory))
        }
        repositories.home.gameDataResult = StoreResult.ok(
            HomeGameDataSnapshot(emptyList(), listOf(inventory), emptyList()),
        )
        repositories.stats.cachedResult = StoreResult.ok(emptyStats(NOW).copy(sourceVersion = 7L))

        val home = repositories.homeUseCases()
        val browse = home.searchStudyInventory("休", true, true)
        val games = home.loadGameData()
        val stats = repositories.statsUseCases().loadForDisplay(NOW)

        assertEquals("休", browse.single().kanji)
        assertEquals("rest", games.inventory.single().primaryMeaning)
        assertEquals(7L, stats.sourceVersion)
    }

    @Test
    fun transientFailureCanBeRetriedAgainstTheSameUseCase() = runTest {
        val repository = FakeSettingsRepository()
        var attempts = 0
        repository.loadHandler = {
            attempts += 1
            if (attempts == 1) {
                StoreResult.transient(IllegalStateException("database busy"))
            } else {
                StoreResult.ok(settings())
            }
        }
        val useCases = SettingsUseCases(repository)

        try {
            useCases.load()
            fail("the first load should fail")
        } catch (error: RepositoryOperationException) {
            assertEquals(RepositoryFailureKind.TRANSIENT, error.kind)
            assertEquals("load settings", error.operation)
        }

        assertEquals(KaniThemeChoice.SYSTEM, useCases.load().themeChoice)
        assertEquals(2, attempts)
    }

    @Test
    fun reconstructedUseCasesReadFreshStateWithoutMutatingThePriorSnapshot() = runTest {
        val repository = FakeSettingsRepository()
        var current = settings(studyAheadMinutes = 10)
        repository.loadHandler = { StoreResult.ok(current) }

        val beforeRecreation = SettingsUseCases(repository).load()
        current = settings(studyAheadMinutes = 45)
        val afterRecreation = SettingsUseCases(repository).load()

        assertEquals(10, beforeRecreation.studyAheadMinutes)
        assertEquals(45, afterRecreation.studyAheadMinutes)
    }

    @Test
    fun syncUseCasesLoadTheInjectedSettingsRepository() = runTest {
        val repositories = Repositories()
        val expected = settings(tagRepairedCards = true, studyAheadMinutes = 30)
        repositories.settings.loadHandler = { StoreResult.ok(expected) }

        val actual = repositories.syncUseCases().loadSettings()

        assertEquals(expected, actual)
    }

    private class Repositories {
        val home = FakeHomeRepository()
        val study = FakeStudyRepository()
        val settings = FakeSettingsRepository()
        val stats = FakeStatsRepository()
        val sync = FakeSyncRepository()

        fun homeUseCases() = HomeUseCases(home, study, settings, sync)

        fun settingsUseCases() = SettingsUseCases(settings)

        fun statsUseCases() = StatsUseCases(stats)

        fun syncUseCases() = SyncUseCases(sync, study, settings)
    }

    private companion object {
        const val NOW = 2_000L
        val STREAK = StudyStreakSnapshot(0, 0, false, 0, 0L)
        val WORKLOAD = AdaptiveWorkloadSnapshot(100, 25, "all")
        val REVIEW_STATS = RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)

        fun emptyHome() = HomeSnapshot(
            activeRows = emptyList(),
            studyItems = emptyList(),
            locallySuspendedKanji = emptySet(),
            latestSync = null,
            latestSuccessfulSyncAtMillis = null,
            studyStreak = STREAK,
            dueLegacyWritingRepairs = emptyList(),
            repairedHandoffKanji = emptyList(),
            consecutiveFailedSyncs = 0,
        )

        fun emptyStudyQueue() = StudyQueueSnapshot(
            activeRows = emptyList(),
            availableRows = emptyList(),
            studyItems = emptyList(),
            locallySuspendedKanji = emptySet(),
            latestSuccessfulSyncAtMillis = null,
            studyLadder = RecordsBase.StudyLadderSettings.defaults(),
            syncSettings = RecordsSyncModels.Settings.kikuDefaults(),
            schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            schedulerFsrsWeights = null,
            learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
            adaptiveWorkload = WORKLOAD,
            studyAheadMinutes = 0,
            studyStreak = STREAK,
            recentReviewStats = REVIEW_STATS,
            studiedKanjiToday = emptySet(),
            dueLegacyWritingRepairs = emptyList(),
            consecutiveFailedSyncs = 0,
        )

        fun settings(
            tagRepairedCards: Boolean = false,
            studyAheadMinutes: Int = 0,
        ) = SettingsSnapshot(
            sync = RecordsSyncModels.Settings.kikuDefaults(),
            tagRepairedCards = tagRepairedCards,
            adaptiveWorkload = WORKLOAD,
            studyAheadMinutes = studyAheadMinutes,
            studyLadder = RecordsBase.StudyLadderSettings.defaults(),
            schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            schedulerFsrsWeights = null,
            learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
            themeChoice = KaniThemeChoice.SYSTEM,
            fsrsPersonalizationEnabled = false,
            fsrsFitSummaryJson = "",
        )

        fun emptyStats(generatedAtMillis: Long) = StatsSnapshot(
            outcomeStats = KaniOutcomeSnapshot(
                weakKanjiImproved = WeakKanjiImprovedSnapshot(0, 0.0, 0.0, emptyList()),
                matureSupportGained = MatureSupportGainedSnapshot(0, 0, 0, emptyList()),
                ladderHealth = LadderHealthSnapshot(
                    emptyMap(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                ),
                adaptiveHealth = AdaptiveHealthSnapshot(
                    coreCounts = CoreSkill.entries.associateWith { 0 },
                    activeRepairsByTask = emptyMap(),
                    activeRepairsByFailure = emptyMap(),
                    totalAdaptiveItems = 0,
                    contextualCompleteCount = 0,
                    activeRepairCount = 0,
                    revalidationPendingCount = 0,
                    recentCoreMissCount = 0,
                    escalationRiskCount = 0,
                    stuckRepairCount = 0,
                    malformedStateCount = 0,
                ),
            ),
            impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
            generatedAtMillis = generatedAtMillis,
            sourceVersion = 1L,
            studyImpactStats = StudyImpactSnapshot(0, 0, 0, 0, 0, 0),
            recentMistakes = emptyList(),
            studyStreak = STREAK,
            studyTaskTimeStats = StudyTaskTimeSnapshot(0L, 0L, 0),
            cacheFormatVersion = 11,
            reviewDaySummaries = emptyList(),
            kanjiRepairEvidence = emptyList(),
            taskTypeDaySummaries = emptyList(),
            cumulativeKanjiPracticed = emptyList(),
            wrongPickCounts = emptyMap(),
            confusionMeanings = emptyMap(),
            ladderForecast = null,
        )
    }
}
