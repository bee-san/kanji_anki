package dev.bee.kanjianki.anki;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.ManualSyncEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AnkiDroidGatewayProviderInstrumentedTest {
    private Context context;
    private LocalStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
        resetProvider();
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        context.deleteDatabase("kanji_anki_simple.db");
        resetProvider();
    }

    @Test
    public void readsKikuCollectionWhenTopLevelCardsUriIsUnsupported() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        Records.CollectionSnapshot snapshot = gateway.readCollection(Records.Settings.kikuDefaults());

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertEquals(2, snapshot.cards.get(0).queue);
        assertEquals(42, snapshot.cards.get(0).intervalDays);
        assertEquals(80, snapshot.cards.get(0).reps);
        assertEquals(3, snapshot.cards.get(0).lapses);
        assertTrue(snapshot.cards.get(1).suspended);
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncWorksAgainstFakeAnkiDroidProviderContract() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        List<Records.SuspendedImport> imports = store.suspendedImports();
        assertEquals(1, imports.size());
        assertEquals("笥", imports.get(0).kanji);
        assertTrue(result.message.contains("tagged in AnkiDroid"));
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncUsesCardQueueWhenAnkiDroidRejectsSuspendedSearch() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "failSuspendedSearch", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        List<Records.SuspendedImport> imports = store.suspendedImports();
        assertEquals(1, imports.size());
        assertEquals("笥", imports.get(0).kanji);
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncFallsBackWhenPerNoteSchedulerProjectionIsUnsupported() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "rejectSchedulerProjection", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("schedulerProjectionRejects"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncFallsBackWhenPerNoteSchedulerCursorThrowsUnknownQueue() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "deferSchedulerProjectionFailure", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("schedulerProjectionRejects"));
        assertEquals(4, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    private void resetProvider() {
        context.getContentResolver().call(providerUri(), "reset", null, null);
    }

    private int providerInt(String method) {
        Bundle result = context.getContentResolver().call(providerUri(), method, null, null);
        return result == null ? -1 : result.getInt("value", -1);
    }

    private Uri providerUri() {
        return Uri.parse("content://" + FakeAnkiDroidProvider.AUTHORITY);
    }
}
