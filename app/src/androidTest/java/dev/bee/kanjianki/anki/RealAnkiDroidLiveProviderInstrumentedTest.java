package dev.bee.kanjianki.anki;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.ManualSyncEngine;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class RealAnkiDroidLiveProviderInstrumentedTest {
    private static final String LIVE_ARG = "kanjiLiveAnkiDroid";
    private static final int MIN_USER_KIKU_NOTES = 7000;

    private Context context;
    private LocalStore store;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", "true".equals(arguments.getString(LIVE_ARG)));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        if (context != null) {
            context.deleteDatabase("kanji_anki_simple.db");
        }
    }

    @Test
    public void manualSyncReadsUserKikuCollectionThroughRealAnkiDroid() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = new AnkiDroidGateway(context);
        AnkiDroidGateway.ProviderStatus status = gateway.status();

        assertTrue(status.message, status.installed);
        assertTrue(status.message, status.permissionGranted);
        assertEquals("com.ichi2.anki.flashcards", status.authority);

        Records.CollectionSnapshot snapshot = gateway.readCollection(settings);
        assertTrue("Expected the copied user Kiku collection, got " + snapshot.notes.size() + " notes.",
                snapshot.notes.size() >= MIN_USER_KIKU_NOTES);
        assertTrue("Expected the copied user Kiku collection, got " + snapshot.cards.size() + " cards.",
                snapshot.cards.size() >= MIN_USER_KIKU_NOTES);
        assertAllCardsHaveNotes(snapshot);
        assertHasRealSchedulerState(snapshot);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
        assertTrue("Expected live Kiku sync to build a substantial dashboard.", result.dashboardRows > 1000);
        assertFalse(store.studyItems().isEmpty());
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
