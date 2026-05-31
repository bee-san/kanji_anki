package dev.bee.kanjianki.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StatsCacheStoreTest {
    private Context context;
    private LocalStore localStore;
    private StatsCacheStore cacheStore;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LocalStoreSchema.DB_NAME);
        localStore = new LocalStore(context);
        db = localStore.getWritableDatabase();
        cacheStore = new StatsCacheStore(localStore);
    }

    @After
    public void tearDown() {
        if (localStore != null) {
            localStore.close();
        }
        context.deleteDatabase(LocalStoreSchema.DB_NAME);
    }

    @Test
    public void readFreshStatsReturnsDecodedSnapshotWhenSourceVersionMatches() {
        setSourceVersion(7L);
        cacheStore.write(db, snapshot(7L, 1234L, 2, 5));

        StatsCacheStore.Snapshot fresh = cacheStore.readFresh(db);

        assertNotNull(fresh);
        assertEquals(7L, fresh.getSourceVersion());
        assertEquals(1234L, fresh.getGeneratedAtMillis());
        assertEquals(2, fresh.getOutcomeStats().weakKanjiImproved.improvedCount);
        assertEquals(5, fresh.getImpactReport().helpedCount);
    }

    @Test
    public void readFreshStatsReturnsNullWhenCacheVersionIsStale() {
        setSourceVersion(8L);
        cacheStore.write(db, snapshot(7L, 1234L, 2, 5));

        assertNull(cacheStore.readFresh(db));

        StatsCacheStore.Snapshot latest = cacheStore.readLatest(db);
        assertNotNull(latest);
        assertEquals(7L, latest.getSourceVersion());
    }

    @Test
    public void hasFreshSnapshotChecksVersionsWithoutDecodingSnapshot() {
        setSourceVersion(11L);
        db.execSQL(
                "INSERT OR REPLACE INTO stats_screen_cache " +
                        "(id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, 11, 333, 'not-json', '{}')"
        );

        assertEquals(true, cacheStore.hasFreshSnapshot(db));

        cacheStore.markDirty(db);

        assertEquals(false, cacheStore.hasFreshSnapshot(db));
    }

    @Test
    public void markDirtyIncrementsSourceVersion() {
        long initial = cacheStore.currentSourceVersion(db);

        long firstDirty = cacheStore.markDirty(db);
        long secondDirty = cacheStore.markDirty(db);

        assertEquals(initial + 1L, firstDirty);
        assertEquals(initial + 2L, secondDirty);
        assertEquals(secondDirty, cacheStore.currentSourceVersion(db));
    }

    @Test
    public void writeSnapshotReplacesSingleCacheRow() {
        setSourceVersion(9L);
        cacheStore.write(db, snapshot(9L, 111L, 1, 3));
        cacheStore.write(db, snapshot(9L, 222L, 4, 6));

        assertEquals(1, cacheRowCount());
        StatsCacheStore.Snapshot latest = cacheStore.readLatest(db);
        assertNotNull(latest);
        assertEquals(222L, latest.getGeneratedAtMillis());
        assertEquals(4, latest.getOutcomeStats().weakKanjiImproved.improvedCount);
        assertEquals(6, latest.getImpactReport().helpedCount);
    }

    @Test
    public void corruptJsonReturnsNullWithoutChangingSourceVersion() {
        setSourceVersion(10L);
        db.execSQL(
                "INSERT OR REPLACE INTO stats_screen_cache " +
                        "(id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, 10, 333, 'not-json', '{}')"
        );

        assertNull(cacheStore.readLatest(db));
        assertNull(cacheStore.readFresh(db));
        assertEquals(10L, cacheStore.currentSourceVersion(db));
    }

    private void setSourceVersion(long version) {
        db.execSQL("UPDATE stats_cache_state SET value=? WHERE key='stats_source_version'", new Object[]{version});
    }

    private int cacheRowCount() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM stats_screen_cache", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static StatsCacheStore.Snapshot snapshot(
            long sourceVersion,
            long generatedAtMillis,
            int improvedCount,
            int helpedCount
    ) {
        return new StatsCacheStore.Snapshot(
                new StudyStatsStore.KaniOutcomeStats(
                        new StudyStatsStore.WeakKanjiImprovedMetric(
                                improvedCount,
                                80.0,
                                40.0,
                                Collections.emptyList()
                        ),
                        StudyStatsStore.MatureSupportGainedMetric.empty(),
                        StudyStatsStore.LadderHealthMetric.empty()
                ),
                new KanjiImpactAnalyzer.Report(helpedCount, 0, 0, Collections.emptyList()),
                generatedAtMillis,
                sourceVersion
        );
    }
}
