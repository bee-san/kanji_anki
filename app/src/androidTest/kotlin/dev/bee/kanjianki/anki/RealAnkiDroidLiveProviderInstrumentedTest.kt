package dev.bee.kanjianki.anki

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedHashSet

@RunWith(AndroidJUnit4::class)
class RealAnkiDroidLiveProviderInstrumentedTest {
    companion object {
        private const val LIVE_ARG = "kanjiLiveAnkiDroid"
        private const val LIVE_MINIMUM_NOTES_ARG = "kanjiLiveMinimumNotes"
        private const val MIN_USER_KIKU_NOTES = 7000
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        val arguments: Bundle = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", arguments.getString(LIVE_ARG) == "true")
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            context.deleteDatabase("kanji_anki_simple.db")
        }
    }

    @Test
    fun readsUserKikuCollectionThroughRealAnkiDroid() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway(context)
        val status = gateway.status()

        assertTrue(status.message, status.installed)
        assertTrue(status.message, status.permissionGranted)
        assertEquals("com.ichi2.anki.flashcards", status.authority)

        val snapshot = gateway.readCollection(settings)
        val minimumNotes = liveMinimumNotes()
        assertTrue("Expected the copied user Kiku collection, got ${snapshot.notes.size} notes.", snapshot.notes.size >= minimumNotes)
        assertTrue("Expected the copied user Kiku collection, got ${snapshot.cards.size} cards.", snapshot.cards.size >= minimumNotes)
        assertAllCardsHaveNotes(snapshot)
        assertHasRealSchedulerState(snapshot)
    }

    @Test
    fun scansCollectionWideKanjiInventoryThroughRealAnkiDroid() {
        val gateway = AnkiDroidCollectionInventoryGateway(context)
        val status = gateway.status()

        assertTrue("Collection inventory provider must be installed.", status.installed)
        assertTrue("Collection inventory permission must be granted.", status.permissionGranted)

        val inventory = AnkiKanjiInventoryReader(gateway).read()
        val minimumNotes = liveMinimumNotes()

        assertTrue(
            "Expected at least $minimumNotes collection notes, got ${inventory.notesScanned}.",
            inventory.notesScanned >= minimumNotes,
        )
        assertTrue("Expected collection-wide kanji membership.", inventory.literals.isNotEmpty())
        println(
            "KANI_LIVE_INVENTORY authority=${status.authority} " +
                "spec=${status.providerSpecVersion} notes=${inventory.notesScanned} " +
                "models=${inventory.modelCount} uniqueKanji=${inventory.uniqueKanjiCount} " +
                "skipped=${inventory.skippedNotes}",
        )
    }

    @Test
    fun createsRendersAndRetriesDisposableMissingKanjiNotesIdempotently() {
        val status = AnkiDroidCollectionInventoryGateway(context).status()
        assertTrue("Direct writer provider must be installed.", status.installed)
        assertTrue("Direct writer permission must be granted.", status.permissionGranted)
        assertTrue("Pinned provider must support writes.", status.canWriteCollection)
        assertTrue("Pinned provider spec must be at least 2.", status.providerSpecVersion >= 2)
        val candidates = listOf(
            exportCandidate("水", "water", "スイ", "みず", 10),
            exportCandidate("火", "fire", "カ", "ひ", 20),
        )
        val writer = AnkiMissingKanjiWriter(context)
        val noteIds = LinkedHashSet<Long>()
        try {
            val first = writer.export(candidates)
            noteIds.addAll(first.createdNotes.values)
            noteIds.addAll(first.alreadyPresentNotes.values)
            assertTrue("Initial direct export failed: ${first.failureKind}", first.completed)
            assertEquals(2, first.createdCount + first.alreadyPresentCount)

            val retry = AnkiMissingKanjiWriter(context).export(candidates)
            noteIds.addAll(retry.createdNotes.values)
            noteIds.addAll(retry.alreadyPresentNotes.values)
            assertTrue("Direct export retry failed: ${retry.failureKind}", retry.completed)
            assertEquals(0, retry.createdCount)
            assertEquals(2, retry.alreadyPresentCount)
            assertEquals(2, noteIds.size)
            assertRenderedRecognitionCards(status.authority.orEmpty(), noteIds)
            println(
                "KANI_LIVE_EXPORT authority=${status.authority} " +
                    "spec=${status.providerSpecVersion} destination=${retry.destinationKey} " +
                    "notes=${noteIds.joinToString(",")}",
            )
        } finally {
            val cleanupFailures = noteIds.filter { noteId ->
                runCatching {
                    context.contentResolver.delete(
                        Uri.parse("content://${status.authority}/notes/$noteId"),
                        null,
                        null,
                    )
                }.getOrDefault(-1) != 1
            }
            assertTrue(
                "Disposable export notes were not cleaned up: $cleanupFailures",
                cleanupFailures.isEmpty(),
            )
        }
    }

    /**
     * Time-boxed D-S8 probe. Writing the card's existing queue value makes the
     * experiment semantically non-destructive even if a future provider accepts it.
     */
    @Test
    fun probesWhetherRealProviderAcceptsCardQueueUpdatesWithoutChangingState() {
        val gateway = AnkiDroidGateway(context)
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val before = gateway.readCollection(settings).cards.first()
        val uri = Uri.parse("content://com.ichi2.anki.flashcards/cards/${before.cardId}")
        var errorType = "none"
        val updatedRows = try {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put("queue", before.queue) },
                null,
                null,
            )
        } catch (error: RuntimeException) {
            errorType = error.javaClass.simpleName
            -1
        }
        val after = gateway.readCollection(settings).cards.first { it.cardId == before.cardId }

        println("KANI_LIVE_QUEUE_UPDATE_PROBE updatedRows=$updatedRows error=$errorType queue=${before.queue}")
        assertEquals("The non-destructive probe must not alter queue state.", before.queue, after.queue)
    }

    private fun liveMinimumNotes(): Int {
        val raw = InstrumentationRegistry.getArguments().getString(LIVE_MINIMUM_NOTES_ARG)
        if (raw.isNullOrBlank()) {
            return MIN_USER_KIKU_NOTES
        }
        return try {
            maxOf(1, raw.trim().toInt())
        } catch (_: NumberFormatException) {
            MIN_USER_KIKU_NOTES
        }
    }

    private fun assertAllCardsHaveNotes(snapshot: RecordsSyncModels.CollectionSnapshot) {
        val noteIds = LinkedHashSet<Long>()
        for (note in snapshot.notes) {
            noteIds.add(note.noteId)
        }
        for (card in snapshot.cards) {
            assertTrue("Card ${card.cardId} points at missing note ${card.noteId}", noteIds.contains(card.noteId))
            assertEquals("Kiku Mining template must stay on ord 0.", 0, card.ord)
        }
    }

    private fun assertHasRealSchedulerState(snapshot: RecordsSyncModels.CollectionSnapshot) {
        for (card in snapshot.cards) {
            if (card.queue != 0 || card.type != 0 || card.intervalDays > 0 || card.reps > 0 || card.lapses > 0) {
                return
            }
        }
        throw AssertionError("Real AnkiDroid scheduler columns were not read from the live provider.")
    }

    private fun assertRenderedRecognitionCards(authority: String, noteIds: Set<Long>) {
        for (noteId in noteIds) {
            val cursor = context.contentResolver.query(
                Uri.parse("content://$authority/notes/$noteId/cards"),
                null,
                null,
                null,
                null,
            ) ?: throw AssertionError("No card cursor for exported note $noteId")
            cursor.use {
                assertTrue("No rendered card for exported note $noteId", it.moveToFirst())
                val question = it.getString(it.getColumnIndexOrThrow("question"))
                val answer = it.getString(it.getColumnIndexOrThrow("answer"))
                assertTrue("Export question did not render a kanji.", question.contains("kani-kanji"))
                assertTrue("Export answer did not render meaning.", answer.contains("kani-meaning"))
                assertTrue("Export answer did not render readings.", answer.contains("kani-reading"))
            }
        }
    }

    private fun exportCandidate(
        literal: String,
        meaning: String,
        onReading: String,
        kunReading: String,
        rank: Int,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = listOf(meaning),
        onReadings = listOf(onReading),
        kunReadings = listOf(kunReading),
        jitenRank = rank,
    )
}
