package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlan
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncRequest
import dev.bee.kanjianki.domain.sync.SyncAdaptivePlanListener
import dev.bee.kanjianki.domain.sync.SyncAlreadyRunningException
import dev.bee.kanjianki.domain.sync.SyncProgressSnapshot
import dev.bee.kanjianki.domain.sync.SyncProgressStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class DomainManualSyncRunnerTest {
    @Test
    fun successfulRunMapsRecordedSyncToLegacyManualSyncResultAndProgress() = runBlocking {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val progressEvents = mutableListOf<SyncProgress>()
        var factorySettings: RecordsSyncModels.Settings? = null
        var capturedRequest: RunSourceMirrorSyncRequest? = null
        var dashboardCounterCalls = 0
        val runner = DomainManualSyncRunner(
            requestFactory = { requestedSettings ->
                factorySettings = requestedSettings
                RunSourceMirrorSyncRequest(importSettings = ImportSettings())
            },
            runSourceMirrorSync = { request ->
                capturedRequest = request
                request.progress.onSyncProgress(
                    SyncProgressSnapshot.atStage(SyncProgressStage.FINDING_NOTE_TYPE),
                )
                request.progress.onSyncProgress(SyncProgressSnapshot.cardsScanned(3, 10))
                request.adaptivePlanListener.onAdaptivePlan(adaptivePlan("Today's Pareto focus: 3 kanji."))
                SyncRunId(7)
            },
            syncRunReader = { id ->
                assertEquals(SyncRunId(7), id)
                syncRun(
                    id = id,
                    status = SyncRunStatus.SUCCESS,
                    suspendedKanjiImportedCount = 4,
                    removalMessage = "Archived 2 suspended cards.",
                )
            },
            dashboardRowCounter = {
                dashboardCounterCalls++
                12
            },
        )

        val result = runner.run(settings) { progressEvents += it }

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(12, result.dashboardRows)
        assertEquals(4, result.importedSuspendedKanji)
        assertEquals("Archived 2 suspended cards.", result.message)
        assertEquals("Today's Pareto focus: 3 kanji.", result.adaptiveSummary)
        assertEquals(settings, factorySettings)
        assertEquals(1, dashboardCounterCalls)
        assertEquals(ImportSettings(), capturedRequest!!.importSettings)
        assertEquals(
            listOf(SyncProgress.Stage.FINDING_NOTE_TYPE, SyncProgress.Stage.SCANNING_CARDS),
            progressEvents.map { it.stage },
        )
        assertFalse(progressEvents[0].totalKnown())
        assertEquals(3, progressEvents[1].scannedCards)
        assertEquals(10, progressEvents[1].totalCards)
    }

    @Test
    fun failureRunMapsRecordedFailureWithoutCountingDashboardRows() = runBlocking {
        var dashboardCounterCalls = 0
        val runner = runnerReturning(
            syncRun = syncRun(
                status = SyncRunStatus.CONFIG_ERROR,
                errorMessage = "Missing AnkiDroid permission.",
            ),
            dashboardRowCounter = {
                dashboardCounterCalls++
                99
            },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertFalse(result.success)
        assertFalse(result.skipped)
        assertEquals("Missing AnkiDroid permission.", result.message)
        assertEquals(0, result.dashboardRows)
        assertEquals(0, dashboardCounterCalls)
    }

    @Test
    fun missingRecordedSyncRunReturnsFailureResult() = runBlocking {
        val runner = DomainManualSyncRunner(
            requestFactory = { RunSourceMirrorSyncRequest(importSettings = ImportSettings()) },
            runSourceMirrorSync = { SyncRunId(99) },
            syncRunReader = { null },
            dashboardRowCounter = { 1 },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertFalse(result.success)
        assertFalse(result.skipped)
        assertEquals("Sync status was not recorded.", result.message)
    }

    @Test
    fun dashboardCountFailureDoesNotTurnRecordedSuccessIntoFailedResult() = runBlocking {
        val runner = runnerReturning(
            syncRun = syncRun(
                status = SyncRunStatus.SUCCESS,
                suspendedKanjiImportedCount = 2,
            ),
            dashboardRowCounter = {
                throw IllegalStateException("dashboard unavailable")
            },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(0, result.dashboardRows)
        assertEquals(2, result.importedSuspendedKanji)
    }

    @Test
    fun missingAdaptivePlanLeavesSummaryBlank() = runBlocking {
        val runner = runnerReturning(
            syncRun = syncRun(
                status = SyncRunStatus.SUCCESS,
                suspendedKanjiImportedCount = 2,
            ),
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertTrue(result.success)
        assertEquals("", result.adaptiveSummary)
        assertEquals(2, result.importedSuspendedKanji)
    }

    @Test
    fun adaptivePlanListenerFailureDoesNotFailRunner() = runBlocking {
        val runner = DomainManualSyncRunner(
            requestFactory = {
                RunSourceMirrorSyncRequest(
                    importSettings = ImportSettings(),
                    adaptivePlanListener = SyncAdaptivePlanListener {
                        throw IllegalStateException("detached UI")
                    },
                )
            },
            runSourceMirrorSync = { request ->
                request.adaptivePlanListener.onAdaptivePlan(adaptivePlan("Adaptive status."))
                SyncRunId(1)
            },
            syncRunReader = {
                syncRun(
                    status = SyncRunStatus.SUCCESS,
                    suspendedKanjiImportedCount = 2,
                )
            },
            dashboardRowCounter = { 3 },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertTrue(result.success)
        assertEquals("Adaptive status.", result.adaptiveSummary)
        assertEquals(2, result.importedSuspendedKanji)
    }

    @Test
    fun concurrentDomainSyncMapsToSkippedLegacyResult() = runBlocking {
        val runner = DomainManualSyncRunner(
            requestFactory = { RunSourceMirrorSyncRequest(importSettings = ImportSettings()) },
            runSourceMirrorSync = { throw SyncAlreadyRunningException("Sync already running.") },
            syncRunReader = { error("should not read failed run") },
            dashboardRowCounter = { error("should not count dashboard rows") },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults())

        assertFalse(result.success)
        assertTrue(result.skipped)
        assertEquals("Sync already running.", result.message)
    }

    @Test
    fun cancellationIsRethrown() {
        val runner = DomainManualSyncRunner(
            requestFactory = { RunSourceMirrorSyncRequest(importSettings = ImportSettings()) },
            runSourceMirrorSync = { throw CancellationException("cancelled") },
            syncRunReader = { error("should not read cancelled run") },
            dashboardRowCounter = { error("should not count dashboard rows") },
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runner.run(RecordsSyncModels.Settings.kikuDefaults())
            }
        }
    }

    @Test
    fun progressListenerFailureDoesNotFailRunner() = runBlocking {
        val runner = runnerReturning(
            syncRun = syncRun(
                status = SyncRunStatus.SUCCESS,
                suspendedKanjiImportedCount = 1,
            ),
            progressReporter = { request ->
                request.progress.onSyncProgress(
                    SyncProgressSnapshot.atStage(SyncProgressStage.READING_NOTES),
                )
            },
        )

        val result = runner.run(RecordsSyncModels.Settings.kikuDefaults()) {
            throw IllegalStateException("detached UI")
        }

        assertTrue(result.success)
        assertEquals(1, result.importedSuspendedKanji)
    }

    private fun runnerReturning(
        syncRun: SyncRun,
        dashboardRowCounter: suspend () -> Int = { 3 },
        progressReporter: suspend (RunSourceMirrorSyncRequest) -> Unit = {},
    ): DomainManualSyncRunner =
        DomainManualSyncRunner(
            requestFactory = { RunSourceMirrorSyncRequest(importSettings = ImportSettings()) },
            runSourceMirrorSync = { request ->
                progressReporter(request)
                syncRun.id ?: SyncRunId(1)
            },
            syncRunReader = { syncRun },
            dashboardRowCounter = dashboardRowCounter,
        )

    private fun adaptivePlan(status: String): AdaptiveStudyPlan =
        AdaptiveStudyPlan(
            autoMode = true,
            workloadPercent = 20,
            targetCount = 3,
            remainingCount = 3,
            focusKanji = listOf("日"),
            newAdmissionLimit = 1,
            allKanjiMode = false,
            status = status,
        )

    private fun syncRun(
        id: SyncRunId = SyncRunId(1),
        status: SyncRunStatus,
        suspendedKanjiImportedCount: Int = 0,
        errorMessage: String? = null,
        removalMessage: String = "",
    ): SyncRun =
        SyncRun(
            id = id,
            startedAt = 100,
            finishedAt = 200,
            status = status,
            activeNotesCount = 1,
            activeCardsCount = 2,
            suspendedCardsArchivedCount = suspendedKanjiImportedCount,
            suspendedKanjiImportedCount = suspendedKanjiImportedCount,
            deletedNotesCount = 0,
            deletedCardsCount = 0,
            errorCode = null,
            errorMessage = errorMessage,
            removalMessage = removalMessage,
        )
}
