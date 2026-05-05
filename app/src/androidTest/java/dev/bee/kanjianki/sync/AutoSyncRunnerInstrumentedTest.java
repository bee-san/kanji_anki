package dev.bee.kanjianki.sync;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AutoSyncRunnerInstrumentedTest {
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
    public void disabledAutoSyncSkipsWithoutReadingProvider() {
        long now = localDayStart(System.currentTimeMillis()) + 60_000L;
        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        ).run(now);

        assertFalse(result.ran);
        assertEquals(0, providerInt("perNoteCardsQueries"));
        assertEquals(0L, store.autoSyncSettings().lastAttemptAt);
    }

    @Test
    public void dueAutoSyncRunsManualEngineAndRecordsAttempt() {
        long now = localDayStart(System.currentTimeMillis()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        ).run(now);

        assertTrue(result.ran);
        assertTrue(result.success);
        assertNotNull(store.latestSync());
        assertEquals("success", store.latestSync().status);
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(now, auto.lastAttemptAt);
        assertEquals(now, auto.lastSuccessAt);
        assertTrue(providerInt("perNoteCardsQueries") > 0);
    }

    @Test
    public void autoSyncSkipsWhenSuccessfulSyncAlreadyHappenedToday() {
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));
        assertTrue(new ManualSyncEngine(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                SyncSettings.fromStore(store)
        ).run().success);
        resetProvider();

        long now = localDayStart(System.currentTimeMillis()) + 2L * 60L * 60L * 1000L;
        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        ).run(now);

        assertFalse(result.ran);
        assertEquals(0, providerInt("perNoteCardsQueries"));
        assertEquals(0L, store.autoSyncSettings().lastAttemptAt);
    }

    @Test
    public void autoSyncProviderReadinessFailureRecordsSyncFailure() {
        long now = localDayStart(System.currentTimeMillis()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.missing_auto_sync_provider")
        ).run(now);

        assertTrue(result.ran);
        assertFalse(result.success);
        assertEquals(now, store.autoSyncSettings().lastAttemptAt);
        LocalStore.SyncStatus sync = store.latestSync();
        assertNotNull(sync);
        assertEquals("config_error", sync.status);
        assertTrue(sync.errorMessage.contains("AnkiDroid"));
    }

    @Test
    public void schedulerMovesPastTimesToTomorrow() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.JANUARY, 12, 18, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long now = calendar.getTimeInMillis();

        LocalStore.AutoSyncSettings futureToday = new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L);
        assertEquals(now + 60L * 60L * 1000L, AutoSyncScheduler.nextTriggerMillis(futureToday, now));
        assertEquals(now + 25L * 60L * 60L * 1000L, AutoSyncScheduler.nextTriggerMillis(futureToday, now, true));

        LocalStore.AutoSyncSettings pastToday = new LocalStore.AutoSyncSettings(true, true, 17, 0, 0L, 0L, 0L);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 17);
        assertEquals(calendar.getTimeInMillis(), AutoSyncScheduler.nextTriggerMillis(pastToday, now));
    }

    private long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void resetProvider() {
        context.getContentResolver().call(providerUri(), "reset", null, null);
    }

    private int providerInt(String method) {
        android.os.Bundle result = context.getContentResolver().call(providerUri(), method, null, null);
        return result == null ? -1 : result.getInt("value", -1);
    }

    private Uri providerUri() {
        return Uri.parse("content://" + FakeAnkiDroidProvider.AUTHORITY);
    }
}
