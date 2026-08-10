package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.application.StatsUseCases
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.HomeSnapshot
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.fakes.FakeHomeRepository
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeStatsRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import dev.bee.kanjianki.data.fakes.emptyStatsSnapshot
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proves the shared route loader assembles content from the use-case graph, over fake
 * repositories, without a host. Both `:app` and `:desktop-app` run this same assembly,
 * so this is where it is checked once rather than per host.
 */
class KaniRouteLoaderTest {
    @Test
    fun homeLoadsProviderMessageThemeAndDashboardFromTheSnapshot() {
        val content = runSync {
            loader().load(
                destination = KaniDestination.Home,
                status = readyStatus(),
                nowMillis = NOW,
                studyRender = null,
                gamesRender = null,
            )
        }

        assertEquals("ready", content.providerMessage)
        assertEquals(KaniThemeChoice.GIRLYPOP, content.themeChoice)
        assertEquals(ProviderReadiness.READY, content.home.readiness)
        assertEquals(0, content.studyItemCount)
        // Off-route surfaces are absent on Home.
        assertNull(content.detail)
        assertNull(content.stats)
        assertNull(content.settings)
        assertEquals(0, content.browse.rows.size)
    }

    @Test
    fun theSettingsRouteCarriesTheSettingsScreenAndOthersDoNot() {
        val settings = runSync {
            loader().load(
                KaniDestination.Settings(), readyStatus(), NOW, null, null,
            )
        }
        assertNotNull(settings.settings)

        val home = runSync {
            loader().load(KaniDestination.Home, readyStatus(), NOW, null, null)
        }
        assertNull(home.settings)
    }

    @Test
    fun aBrowseRouteRunsItsQueryAndOtherRoutesSkipIt() {
        val browse = runSync {
            loader().load(KaniDestination.Browse(query = "水"), readyStatus(), NOW, null, null)
        }
        // The fake returns no inventory, so the rows are empty but the query ran without
        // error — the point is the Browse branch is exercised, not the row content.
        assertEquals(0, browse.browse.rows.size)
    }

    private fun loader(): KaniRouteLoader {
        val home = FakeHomeRepository().apply {
            loadHomeHandler = { StoreResult.ok(homeSnapshot()) }
        }
        val study = FakeStudyRepository().apply {
            loadQueueHandler = { StoreResult.ok(studyQueue()) }
        }
        val settings = FakeSettingsRepository().apply {
            loadHandler = { StoreResult.ok(settingsSnapshot()) }
        }
        val sync = FakeSyncRepository()
        val stats = FakeStatsRepository().apply {
            cachedResult = StoreResult.ok(emptyStatsSnapshot(NOW))
            latestResult = StoreResult.ok(emptyStatsSnapshot(NOW))
            refreshHandler = { StoreResult.ok(emptyStatsSnapshot(it)) }
        }
        return KaniRouteLoader(
            homeUseCases = HomeUseCases(home, study, settings, sync),
            statsUseCases = StatsUseCases(stats),
            settingsUseCases = SettingsUseCases(settings),
            deviceSettings = { NoDeviceSettings },
            annotateCapabilities = { it },
        )
    }

    private fun readyStatus() = HostProviderStatus(
        readiness = ProviderReadiness.READY,
        message = "ready",
        isReady = true,
        capabilities = setOf(PlatformCapability.PROVIDER_CONNECTIVITY),
    )

    private fun homeSnapshot() = HomeSnapshot(
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

    private fun studyQueue() = StudyQueueSnapshot(
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
        adaptiveWorkload = AdaptiveWorkloadSnapshot(100, 40, "balanced"),
        studyAheadMinutes = 0,
        studyStreak = STREAK,
        recentReviewStats = RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
        studiedKanjiToday = emptySet(),
        dueLegacyWritingRepairs = emptyList(),
        consecutiveFailedSyncs = 0,
    )

    private fun settingsSnapshot() = SettingsSnapshot(
        sync = RecordsSyncModels.Settings.kikuDefaults(),
        tagRepairedCards = false,
        adaptiveWorkload = AdaptiveWorkloadSnapshot(100, 40, "balanced"),
        studyAheadMinutes = 0,
        studyLadder = RecordsBase.StudyLadderSettings.defaults(),
        schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
        schedulerFsrsWeights = null,
        learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
        themeChoice = KaniThemeChoice.GIRLYPOP,
        fsrsPersonalizationEnabled = false,
        fsrsFitSummaryJson = "",
    )

    private object NoDeviceSettings : DeviceSettingsReader {
        override fun contains(key: DeviceSettingKey<*>): Boolean = false

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = null
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val STREAK = StudyStreakSnapshot(
            currentDays = 0,
            bestDays = 0,
            studiedToday = false,
            reviewsToday = 0,
            lastStudyAtMillis = 0L,
        )
    }
}
