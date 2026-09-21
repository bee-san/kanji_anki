package dev.bee.kanjianki.host

import dev.bee.kanjianki.hostpresentation.SyncRunResult
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two projections that carry Android's sync across the shared port.
 *
 * Plain JVM, no Robolectric: both functions are pure mappings, and the parts that need
 * a `Context` are the engine construction this deliberately does not exercise.
 */
class AndroidSyncEngineAdapterTest {
    @Test
    fun aStageWithNoTotalYetReportsWordsWithoutAFraction() {
        val progress = AndroidSyncEngineAdapter.runProgress(
            SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE),
        )

        // The point of the null: `SyncProgressCopy.progressPermille` answers 1000 for an
        // unknown total, so a fraction taken unconditionally would read 100% before the
        // provider has been asked how much there is.
        assertNull(progress.fraction)
        assertTrue(progress.label != UiText.EMPTY)
    }

    @Test
    fun aKnownTotalReportsTheFractionScanned() {
        val progress = AndroidSyncEngineAdapter.runProgress(
            SyncProgress.cardsScanned(scannedCards = 250, totalCards = 1_000),
        )

        assertEquals(0.25f, progress.fraction!!, 0.001f)
    }

    @Test
    fun aZeroCardCollectionReportsNoFractionRatherThanComplete() {
        val progress = AndroidSyncEngineAdapter.runProgress(
            SyncProgress.cardsScanned(scannedCards = 0, totalCards = 0),
        )

        assertNull(progress.fraction)
    }

    @Test
    fun everyStageResolvesToWordsRatherThanBlankCopy() {
        for (stage in SyncProgress.Stage.entries) {
            val progress = AndroidSyncEngineAdapter.runProgress(SyncProgress.atStage(stage))
            assertTrue("$stage has no label", progress.label != UiText.EMPTY)
        }
    }

    @Test
    fun aSuccessCarriesTheImportedCount() {
        val result = AndroidSyncEngineAdapter.runResult(
            syncResult(success = true, importedSuspendedKanji = 7),
        )

        assertEquals(SyncRunResult.Succeeded(importedKanji = 7), result)
    }

    @Test
    fun anAlreadyRunningSyncIsSkippedNotFailed() {
        // `skipped` is read before `success`, and this is why: a skipped run reports
        // neither, so testing `success` first would put a retry button in front of the
        // user for a sync that is already in flight.
        val result = AndroidSyncEngineAdapter.runResult(
            syncResult(success = false, skipped = true, message = "Sync already running."),
        )

        assertEquals(SyncRunResult.Skipped(UiText.Literal("Sync already running.")), result)
    }

    @Test
    fun aRetryableFailureIsTransientSoTheRetryButtonIsHonest() {
        val result = AndroidSyncEngineAdapter.runResult(
            syncResult(success = false, message = "AnkiDroid is busy.", retryable = true),
        )

        val failed = result as SyncRunResult.Failed
        assertEquals(PresentationFailure.Kind.TRANSIENT, failed.failure.kind)
        assertEquals(UiText.Literal("AnkiDroid is busy."), failed.failure.message)
    }

    @Test
    fun aPermanentFailureIsProviderUnavailable() {
        val result = AndroidSyncEngineAdapter.runResult(
            syncResult(success = false, message = "AnkiDroid is not installed.", retryable = false),
        )

        assertEquals(
            PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
            (result as SyncRunResult.Failed).failure.kind,
        )
    }

    @Test
    fun aFailureWithNoWordsStillProjectsRatherThanThrowing() {
        val result = AndroidSyncEngineAdapter.runResult(syncResult(success = false, message = null))

        assertEquals(UiText.Literal(""), (result as SyncRunResult.Failed).failure.message)
    }

    private fun syncResult(
        success: Boolean,
        skipped: Boolean = false,
        importedSuspendedKanji: Int = 0,
        message: String? = null,
        retryable: Boolean = false,
    ): ManualSyncEngine.SyncResult = ManualSyncEngine.SyncResult.create(
        success = success,
        skipped = skipped,
        dashboardRows = 0,
        importedSuspendedKanji = importedSuspendedKanji,
        message = message,
        adaptiveSummary = null,
        retryable = retryable,
    )
}
