package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        long now = localDayStart(fixedNow()) + 60_000L;
        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                () -> now
        ).run();

        assertFalse(result.ran);
        assertEquals("Daily Anki sync is off.", result.message);
        assertEquals(0, providerInt("perNoteCardsQueries"));
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(0L, auto.lastAttemptAt);
        assertEquals(0L, auto.lastSuccessAt);
        assertFalse(store.hasSuccessfulSyncSince(0L));
    }

    @Test
    public void dueAutoSyncRunsManualEngineAndRecordsAttempt() {
        long now = localDayStart(fixedNow()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                () -> now
        ).run();

        assertTrue(result.ran);
        assertTrue(result.success);
        LocalStore.SyncStatus sync = store.latestSync();
        assertNotNull(sync);
        assertEquals("success", sync.status);
        assertEquals(now, sync.finishedAt);
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(now, auto.lastAttemptAt);
        assertEquals(now, auto.lastSuccessAt);
        assertTrue(store.hasSuccessfulSyncSince(localDayStart(now)));
        assertTrue(providerInt("perNoteCardsQueries") > 0);
    }

    @Test
    public void autoSyncSkipsWhenSuccessfulSyncAlreadyHappenedToday() {
        long now = localDayStart(fixedNow()) + 2L * 60L * 60L * 1000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));
        assertTrue(new ManualSyncEngine(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                SyncSettings.fromStore(store),
                SyncProgress.NONE,
                () -> now
        ).run().success);
        resetProvider();

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                () -> now
        ).run();

        assertFalse(result.ran);
        assertEquals("AnkiDroid already synced today.", result.message);
        assertEquals(0, providerInt("perNoteCardsQueries"));
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(0L, auto.lastAttemptAt);
        assertEquals(0L, auto.lastSuccessAt);
        assertEquals("success", store.latestSync().status);
    }

    @Test
    public void autoSyncProviderReadinessFailureRecordsSyncFailure() {
        long now = localDayStart(fixedNow()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.missing_auto_sync_provider"),
                () -> now
        ).run();

        assertTrue(result.ran);
        assertFalse(result.success);
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(now, auto.lastAttemptAt);
        assertEquals(0L, auto.lastSuccessAt);
        LocalStore.SyncStatus sync = store.latestSync();
        assertNotNull(sync);
        assertEquals("config_error", sync.status);
        assertEquals(now, sync.finishedAt);
        assertTrue(sync.errorMessage.contains("AnkiDroid"));
    }

    @Test
    public void autoSyncManualEngineFailureRecordsFailedAttempt() {
        long now = localDayStart(fixedNow()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                new RetryableGateway(),
                () -> now
        ).run();

        assertTrue(result.ran);
        assertFalse(result.success);
        assertEquals("AnkiDroid returned no configured note cursor.", result.message);
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(now, auto.lastAttemptAt);
        assertEquals(0L, auto.lastSuccessAt);
        LocalStore.SyncStatus sync = store.latestSync();
        assertNotNull(sync);
        assertEquals("retryable_error", sync.status);
        assertEquals(now, sync.finishedAt);
        assertEquals("AnkiDroid returned no configured note cursor.", sync.errorMessage);
    }

    @Test
    public void autoSyncManualEngineSkippedDoesNotRecordAttempt() throws Exception {
        long now = localDayStart(fixedNow()) + 60_000L;
        store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L));
        BlockingGateway blockingGateway = new BlockingGateway(snapshot(), new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"));
        AtomicReference<ManualSyncEngine.SyncResult> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread firstSync = new Thread(() -> {
            try {
                firstResult.set(new ManualSyncEngine(
                        context,
                        store,
                        blockingGateway,
                        SyncSettings.fromStore(store),
                        SyncProgress.NONE,
                        () -> now
                ).run());
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });

        firstSync.start();
        assertTrue(blockingGateway.awaitStarted());

        AutoSyncRunner.Result result = new AutoSyncRunner(
                context,
                store,
                new RetryableGateway(),
                () -> now
        ).run();

        assertFalse(result.ran);
        assertFalse(result.success);
        assertEquals("Sync already running.", result.message);
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        assertEquals(0L, auto.lastAttemptAt);
        assertEquals(0L, auto.lastSuccessAt);

        blockingGateway.release();
        firstSync.join(10_000L);
        assertFalse(firstSync.isAlive());
        assertNull(threadFailure.get());
        assertTrue(firstResult.get().success);
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

    private long fixedNow() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 15, 12, 0, 0);
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

    private RecordsSyncModels.CollectionSnapshot snapshot() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", "確認");
        fields.put("ExpressionReading", "かくにん");
        fields.put("MainDefinition", "confirmation");
        fields.put("Sentence", "確認した。");
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        RecordsSyncModels.Note note = new RecordsSyncModels.Note(1L, "Kiku", fields, Collections.emptyList());
        RecordsSyncModels.Card card = new RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 30, 12, 0, false);
        return new RecordsSyncModels.CollectionSnapshot(Arrays.asList(note), Arrays.asList(card));
    }

    private static final class RetryableGateway implements CollectionGateway {
        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure {
            throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no configured note cursor.");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }

    private static final class BlockingGateway implements CollectionGateway {
        private final RecordsSyncModels.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingGateway(RecordsSyncModels.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure {
            started.countDown();
            try {
                if (!release.await(10L, TimeUnit.SECONDS)) {
                    throw AnkiDroidGateway.SyncFailure.retryable("Timed out waiting for test release.");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw AnkiDroidGateway.SyncFailure.retryable("Interrupted while waiting for test release.", error);
            }
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return removal;
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(10L, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }
}
