package dev.bee.kanjianki.anki

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.MissingKanjiCandidate
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiMissingKanjiWriterInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            resetProvider()
        }
    }

    @Test
    fun createsDedicatedDeckModelAndCanonicalNotesInOneBatch() {
        val receipts = mutableListOf<AnkiMissingKanjiWriter.ConfirmedNote>()
        val progress = mutableListOf<AnkiMissingKanjiWriter.ExportProgress>()

        val result = writer().export(
            candidates = listOf(
                candidate("火", 20),
                candidate("水", 10),
                candidate("水", 11),
                candidate("invalid", 12),
            ),
            progress = progress::add,
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { _, confirmed ->
                receipts.addAll(confirmed)
                true
            },
        )

        assertTrue(result.completed)
        assertEquals(4, result.requestedCount)
        assertEquals(2, result.validCount)
        assertEquals(2, result.createdCount)
        assertEquals(0, result.alreadyPresentCount)
        assertEquals(1, result.invalidCount)
        assertEquals(1, result.duplicateRequestCount)
        assertEquals(2, result.skippedCount)
        assertEquals(setOf("invalid"), result.invalidLiterals)
        assertTrue(result.unfinishedLiterals.isEmpty())
        assertEquals(2, receipts.size)
        assertEquals(2, providerInt("exportedNoteCount"))
        assertEquals(1, providerInt("exportBulkInsertCalls"))
        assertEquals(
            setOf("kani-missing:水", "kani-missing:火"),
            providerString("exportedSourceIds").split('|').toSet(),
        )
        assertTrue(providerLong("exportDeckId") > 0L)
        assertTrue(providerLong("exportModelId") > 0L)
        assertEquals(2, progress.first().totalCount)
        assertEquals(0, progress.first().processedCount)
        assertEquals(2, progress.last().processedCount)
    }

    @Test
    fun retryAndNewWriterInstanceCreateNoDuplicateNotes() {
        val candidates = listOf(candidate("水", 10), candidate("火", 20))
        assertTrue(writer().export(candidates).completed)

        val retry = writer().export(candidates, deckName = "Unused::Retry Deck")

        assertTrue(retry.completed)
        assertEquals(0, retry.createdCount)
        assertEquals(2, retry.alreadyPresentCount)
        assertEquals(2, providerInt("exportedNoteCount"))
        assertEquals(2, providerInt("exportDeckCount"))
        assertEquals(1, providerInt("exportBulkInsertCalls"))
    }

    @Test
    fun reconcilesPartialThrowPersistsConfirmedReceiptAndRetryFinishes() {
        providerCall("throwExportBulkInsertAfter", "1")
        val receipts = mutableListOf<AnkiMissingKanjiWriter.ConfirmedNote>()
        val candidates = listOf(candidate("水", 10), candidate("火", 20), candidate("語", 30))

        val partial = writer().export(
            candidates,
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { _, confirmed ->
                receipts.addAll(confirmed)
                true
            },
        )

        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.PROVIDER_UNAVAILABLE,
            partial.failureKind,
        )
        assertEquals(1, partial.createdCount)
        assertEquals(2, partial.unfinishedLiterals.size)
        assertEquals(1, receipts.size)
        assertEquals(1, providerInt("exportedNoteCount"))

        val retry = writer().export(candidates)
        assertTrue(retry.completed)
        assertEquals(2, retry.createdCount)
        assertEquals(1, retry.alreadyPresentCount)
        assertEquals(3, providerInt("exportedNoteCount"))
    }

    @Test
    fun reportsShortWritePreciselyAndRetryTargetsOnlyUnfinishedNotes() {
        providerCall("shortExportBulkInsertAfter", "1")
        val candidates = listOf(candidate("水", 10), candidate("火", 20), candidate("語", 30))

        val partial = writer().export(candidates)

        assertEquals(AnkiMissingKanjiWriter.FailureKind.INCOMPLETE_WRITE, partial.failureKind)
        assertEquals(1, partial.createdCount)
        assertEquals(2, partial.unfinishedLiterals.size)

        val retry = writer().export(candidates)
        assertTrue(retry.completed)
        assertEquals(2, retry.createdCount)
        assertEquals(1, retry.alreadyPresentCount)
        assertEquals(3, providerInt("exportedNoteCount"))
    }

    @Test
    fun checksCancellationBetweenBoundedHundredNoteBatches() {
        val cancelled = AtomicBoolean(false)
        val candidates = candidates(150)
        val writer = AnkiMissingKanjiWriter.testProvider(
            context = context,
            authority = FakeAnkiDroidProvider.AUTHORITY,
            cancellation = AnkiMissingKanjiWriter.Cancellation(cancelled::get),
        )

        val result = writer.export(
            candidates = candidates,
            progress = AnkiMissingKanjiWriter.ProgressListener { progress ->
                if (progress.processedCount >= 100) {
                    cancelled.set(true)
                }
            },
        )

        assertEquals(AnkiMissingKanjiWriter.FailureKind.CANCELLED, result.failureKind)
        assertEquals(100, result.createdCount)
        assertEquals(50, result.unfinishedLiterals.size)
        assertEquals(1, providerInt("exportBulkInsertCalls"))
        assertEquals(100, providerInt("exportedNoteCount"))
    }

    @Test
    fun batchesLargeExportsAtOneHundredNotes() {
        val result = writer().export(candidates(201))

        assertTrue(result.completed)
        assertEquals(201, result.createdCount)
        assertEquals(3, providerInt("exportBulkInsertCalls"))
    }

    @Test
    fun fiveThousandNoteExportStaysBatchedAndRetryIsIdempotent() {
        val candidates = candidates(5_000)

        val first = writer().export(candidates)
        val retry = writer().export(candidates)

        assertTrue(first.completed)
        assertEquals(5_000, first.createdCount)
        assertEquals(50, providerInt("exportBulkInsertCalls"))
        assertTrue(retry.completed)
        assertEquals(0, retry.createdCount)
        assertEquals(5_000, retry.alreadyPresentCount)
        assertEquals(5_000, providerInt("exportedNoteCount"))
    }

    @Test
    fun incompatibleModelAndFilteredDeckAreNeverModified() {
        providerCall("preseedIncompatibleExportModel")
        val modelCollision = writer().export(listOf(candidate("水", 10)))
        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.MODEL_COLLISION,
            modelCollision.failureKind,
        )
        assertEquals(1, providerInt("exportDeckCount"))
        assertEquals(0, providerInt("exportedNoteCount"))

        resetProvider()
        providerCall("preseedFilteredExportDeck")
        val deckCollision = writer().export(listOf(candidate("水", 10)))
        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.DECK_COLLISION,
            deckCollision.failureKind,
        )
        assertEquals(0, providerInt("exportedNoteCount"))
    }

    @Test
    fun unsupportedProviderBlankDeckAndEmptyPayloadDoNotMutateProvider() {
        val unsupported = AnkiMissingKanjiWriter.testProvider(
            context,
            FakeAnkiDroidProvider.AUTHORITY,
            providerSpecVersion = 1,
        ).export(listOf(candidate("水", 10)))
        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.UNSUPPORTED_PROVIDER,
            unsupported.failureKind,
        )

        val blankDeck = writer().export(listOf(candidate("水", 10)), deckName = "  ")
        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.INVALID_DECK_NAME,
            blankDeck.failureKind,
        )

        val empty = writer().export(emptyList())
        assertTrue(empty.completed)
        assertNull(empty.destinationKey)
        assertEquals(0, providerInt("exportedNoteCount"))
    }

    @Test
    fun receiptFailureLeavesExternalWriteDiscoverableForSafeRetry() {
        val candidate = candidate("水", 10)
        val failed = writer().export(
            listOf(candidate),
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { _, _ -> false },
        )

        assertEquals(
            AnkiMissingKanjiWriter.FailureKind.RECEIPT_PERSISTENCE,
            failed.failureKind,
        )
        assertEquals(1, failed.createdCount)
        assertEquals(1, providerInt("exportedNoteCount"))

        val retriedReceipts = mutableListOf<AnkiMissingKanjiWriter.ConfirmedNote>()
        val retry = writer().export(
            listOf(candidate),
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { _, notes ->
                retriedReceipts.addAll(notes)
                true
            },
        )
        assertTrue(retry.completed)
        assertEquals(1, retry.alreadyPresentCount)
        assertEquals(1, retriedReceipts.size)
        assertEquals(1, providerInt("exportedNoteCount"))
    }

    private fun writer(): AnkiMissingKanjiWriter =
        AnkiMissingKanjiWriter.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun candidates(count: Int): List<MissingKanjiCandidate> =
        (1..count).map { index ->
            candidate(String(Character.toChars(0x4E00 + index)), index)
        }

    private fun candidate(literal: String, rank: Int): MissingKanjiCandidate =
        MissingKanjiCandidate(
            literal = literal,
            meanings = listOf("meaning $rank"),
            onReadings = listOf("オン"),
            kunReadings = listOf("くん"),
            jitenRank = rank,
        )

    private fun resetProvider() {
        providerCall("reset")
    }

    private fun providerCall(method: String, argument: String? = null) {
        context.contentResolver.call(providerUri(), method, argument, null)
    }

    private fun providerInt(method: String): Int =
        context.contentResolver.call(providerUri(), method, null, null)?.getInt("value") ?: -1

    private fun providerLong(method: String): Long =
        context.contentResolver.call(providerUri(), method, null, null)?.getLong("value") ?: -1L

    private fun providerString(method: String): String =
        context.contentResolver.call(providerUri(), method, null, null)?.getString("value").orEmpty()

    private fun providerUri(): Uri = Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")
}
