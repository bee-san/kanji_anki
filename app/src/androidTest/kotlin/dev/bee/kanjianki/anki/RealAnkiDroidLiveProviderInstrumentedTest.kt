package dev.bee.kanjianki.anki

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
