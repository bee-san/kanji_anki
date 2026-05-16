package dev.bee.kanjianki.anki;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class RealAnkiDroidLiveProviderInstrumentedTest {
    private static final String LIVE_ARG = "kanjiLiveAnkiDroid";
    private static final String LIVE_MINIMUM_NOTES_ARG = "kanjiLiveMinimumNotes";
    private static final int MIN_USER_KIKU_NOTES = 7000;

    private Context context;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", "true".equals(arguments.getString(LIVE_ARG)));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.deleteDatabase("kanji_anki_simple.db");
        }
    }

    @Test
    public void readsUserKikuCollectionThroughRealAnkiDroid() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = new AnkiDroidGateway(context);
        AnkiDroidGateway.ProviderStatus status = gateway.status();

        assertTrue(status.message, status.installed);
        assertTrue(status.message, status.permissionGranted);
        assertEquals("com.ichi2.anki.flashcards", status.authority);

        Records.CollectionSnapshot snapshot = gateway.readCollection(settings);
        int minimumNotes = liveMinimumNotes();
        assertTrue("Expected the copied user Kiku collection, got " + snapshot.notes.size() + " notes.",
                snapshot.notes.size() >= minimumNotes);
        assertTrue("Expected the copied user Kiku collection, got " + snapshot.cards.size() + " cards.",
                snapshot.cards.size() >= minimumNotes);
        assertAllCardsHaveNotes(snapshot);
        assertHasRealSchedulerState(snapshot);
    }

    private static int liveMinimumNotes() {
        String raw = InstrumentationRegistry.getArguments().getString(LIVE_MINIMUM_NOTES_ARG);
        if (raw == null || raw.trim().isEmpty()) {
            return MIN_USER_KIKU_NOTES;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return MIN_USER_KIKU_NOTES;
        }
    }

    private void assertAllCardsHaveNotes(Records.CollectionSnapshot snapshot) {
        Set<Long> noteIds = new LinkedHashSet<>();
        for (Records.Note note : snapshot.notes) {
            noteIds.add(note.noteId);
        }
        for (Records.Card card : snapshot.cards) {
            assertTrue("Card " + card.cardId + " points at missing note " + card.noteId, noteIds.contains(card.noteId));
            assertEquals("Kiku Mining template must stay on ord 0.", 0, card.ord);
        }
    }

    private void assertHasRealSchedulerState(Records.CollectionSnapshot snapshot) {
        for (Records.Card card : snapshot.cards) {
            if (card.queue != 0 || card.type != 0 || card.intervalDays > 0 || card.reps > 0 || card.lapses > 0) {
                return;
            }
        }
        throw new AssertionError("Real AnkiDroid scheduler columns were not read from the live provider.");
    }
}
