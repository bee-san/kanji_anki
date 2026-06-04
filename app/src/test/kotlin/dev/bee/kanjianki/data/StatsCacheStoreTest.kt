package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsCacheStoreTest {
    private lateinit var context: Context
    private var localStore: LocalStore? = null
    private lateinit var cacheStore: StatsCacheStore
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        db = localStore!!.writableDatabase
        cacheStore = StatsCacheStore(localStore!!)
    }

    @After
    fun tearDown() {
        localStore?.close()
        localStore = null
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun readFreshStatsReturnsDecodedSnapshotWhenSourceVersionMatches() {
        setSourceVersion(7L)
        cacheStore.write(db, snapshot(7L, 1234L, 2, 5))

        val fresh = cacheStore.readFresh(db)

        assertNotNull(fresh)
        assertEquals(7L, fresh!!.sourceVersion)
        assertEquals(1234L, fresh.generatedAtMillis)
        assertEquals(2, fresh.outcomeStats.weakKanjiImproved.improvedCount)
        assertEquals(5, fresh.impactReport.helpedCount)
    }

    @Test
    fun readFreshStatsReturnsNullWhenCacheVersionIsStale() {
        setSourceVersion(8L)
        cacheStore.write(db, snapshot(7L, 1234L, 2, 5))

        assertNull(cacheStore.readFresh(db))

        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        assertEquals(7L, latest!!.sourceVersion)
    }

    @Test
    fun hasFreshSnapshotChecksVersionsWithoutDecodingSnapshot() {
        setSourceVersion(11L)
        db.execSQL(
            "INSERT OR REPLACE INTO stats_screen_cache " +
                "(id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, 11, 333, 'not-json', '{}')"
        )

        assertTrue(cacheStore.hasFreshSnapshot(db))

        cacheStore.markDirty(db)

        assertFalse(cacheStore.hasFreshSnapshot(db))
    }

    @Test
    fun markDirtyIncrementsSourceVersion() {
        val initial = cacheStore.currentSourceVersion(db)

        val firstDirty = cacheStore.markDirty(db)
        val secondDirty = cacheStore.markDirty(db)

        assertEquals(initial + 1L, firstDirty)
        assertEquals(initial + 2L, secondDirty)
        assertEquals(secondDirty, cacheStore.currentSourceVersion(db))
    }

    @Test
    fun writeSnapshotReplacesSingleCacheRow() {
        setSourceVersion(9L)
        cacheStore.write(db, snapshot(9L, 111L, 1, 3))
        cacheStore.write(db, snapshot(9L, 222L, 4, 6))

        assertEquals(1, cacheRowCount())
        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        assertEquals(222L, latest!!.generatedAtMillis)
        assertEquals(4, latest.outcomeStats.weakKanjiImproved.improvedCount)
        assertEquals(6, latest.impactReport.helpedCount)
    }

    @Test
    fun corruptJsonReturnsNullWithoutChangingSourceVersion() {
        setSourceVersion(10L)
        db.execSQL(
            "INSERT OR REPLACE INTO stats_screen_cache " +
                "(id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, 10, 333, 'not-json', '{}')"
        )

        assertNull(cacheStore.readLatest(db))
        assertNull(cacheStore.readFresh(db))
        assertEquals(10L, cacheStore.currentSourceVersion(db))
    }

    private fun setSourceVersion(version: Long) {
        db.execSQL(
            "UPDATE stats_cache_state SET value=? WHERE key='stats_source_version'",
            arrayOf<Any>(version),
        )
    }

    private fun cacheRowCount(): Int {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM stats_screen_cache", null)
        try {
            cursor.moveToFirst()
            return cursor.getInt(0)
        } finally {
            cursor.close()
        }
    }

    private fun snapshot(
        sourceVersion: Long,
        generatedAtMillis: Long,
        improvedCount: Int,
        helpedCount: Int,
    ): StatsCacheStore.Snapshot {
        return StatsCacheStore.Snapshot(
            StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(
                    improvedCount,
                    80.0,
                    40.0,
                    Collections.emptyList(),
                ),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                StudyStatsStore.LadderHealthMetric.empty(),
            ),
            KanjiImpactAnalyzer.Report(helpedCount, 0, 0, Collections.emptyList()),
            generatedAtMillis,
            sourceVersion,
        )
    }
}
