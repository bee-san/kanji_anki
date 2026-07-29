package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.RedactedSourceIdentityEvidence
import dev.bee.kanjianki.syncapi.SourceBindingReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineContractsTest {
    @Test
    fun providerProgressMapsEveryPortableStage() {
        val expected = mapOf(
            CollectionProgress.Stage.FINDING_NOTE_TYPE to SyncProgress.Stage.FINDING_NOTE_TYPE,
            CollectionProgress.Stage.READING_NOTES to SyncProgress.Stage.READING_NOTES,
            CollectionProgress.Stage.SCANNING_CARDS to SyncProgress.Stage.SCANNING_CARDS,
            CollectionProgress.Stage.ARCHIVING_IMPORTED_CARDS to SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS,
            CollectionProgress.Stage.TAGGING_REPAIRED to SyncProgress.Stage.TAGGING_REPAIRED,
            CollectionProgress.Stage.READING_INVENTORY to SyncProgress.Stage.READING_NOTES,
            CollectionProgress.Stage.WRITING_MISSING_KANJI to SyncProgress.Stage.SAVING_LOCAL_DATA,
        )

        expected.forEach { (providerStage, syncStage) ->
            val progress = SyncProgress.fromCollection(CollectionProgress(providerStage, 2, 5))
            assertEquals(syncStage, progress.stage)
            assertEquals(2, progress.scannedCards)
            assertEquals(5, progress.totalCards)
            assertTrue(progress.totalKnown())
        }
    }

    @Test
    fun localProgressFactoriesClampCountsAndMapCoreStages() {
        val scanned = SyncProgress.cardsScanned(-1, -2)
        assertEquals(0, scanned.scannedCards)
        assertEquals(0, scanned.totalCards)
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, scanned.coreStage())

        SyncProgress.Stage.entries.forEach { stage ->
            assertEquals(SyncProgress.coreStage(stage), SyncProgress.atStage(stage).coreStage())
        }
        val unknown = SyncProgress.atStage(null)
        assertFalse(unknown.totalKnown())
        assertNull(unknown.coreStage())
    }

    @Test
    fun cancellationAndBindingFailuresExposePortableSemantics() {
        assertFalse(SyncCancellation.NONE.isStopped())
        assertFalse(SyncCancellation.NONE.isCancelled())

        val evidence = SourceBindingEvidence(
            RedactedSourceIdentityEvidence(CollectionProviderKind.TEST, 2, 3),
            priorNoteSampleSize = 1,
            priorCardSampleSize = 2,
        )
        val failure = SourceBindingFailure(
            SourceBindingReason.INSUFFICIENT_OVERLAP,
            "rebind required",
            evidence,
        )

        assertEquals(CollectionFailureKind.INVALID_CONFIGURATION, failure.kind)
        assertFalse(failure.retryable)
        assertEquals(evidence, failure.evidence)
        assertEquals(SourceBindingReason.INSUFFICIENT_OVERLAP, failure.reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sourceBindingEvidenceRejectsNegativeSamples() {
        SourceBindingEvidence(
            RedactedSourceIdentityEvidence(CollectionProviderKind.TEST, 0, 0),
            priorNoteSampleSize = -1,
            priorCardSampleSize = 0,
        )
    }
}
