package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import org.json.JSONArray
import org.json.JSONObject
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
        val now = 1_234L
        val reviewDaySummaries = listOf(
            StatsCacheStore.ReviewDaySummarySnapshot(1_000L, 8, 2, 1, 3, 2, 4, 1),
            StatsCacheStore.ReviewDaySummarySnapshot(2_000L, 4, 1, 1, 1, 1, 0, 0),
        )
        cacheStore.write(db, snapshot(7L, now, 2, 5, reviewDaySummaries = reviewDaySummaries))

        val fresh = cacheStore.readFresh(db, nowMillis = now)

        assertNotNull(fresh)
        assertEquals(7L, fresh!!.sourceVersion)
        assertEquals(1234L, fresh.generatedAtMillis)
        assertEquals(2, fresh.outcomeStats.weakKanjiImproved.improvedCount)
        assertEquals(5, fresh.impactReport.helpedCount)
        assertEquals(STATS_CACHE_FORMAT_VERSION, fresh.cacheFormatVersion)
        assertEquals(8, fresh.studyImpactStats.totalReviews)
        assertEquals(3, fresh.studyImpactStats.distinctReviewedKanji)
        assertEquals(2, fresh.recentMistakes.size)
        assertEquals("痛", fresh.recentMistakes[0].kanji)
        assertEquals("again", fresh.recentMistakes[0].rating)
        assertEquals(reviewDaySummaries, fresh.reviewDaySummaries)
    }

    @Test
    fun readLatestLegacyCacheDefaultsMissingExtrasToEmptyCollections() {
        val oldOutcomeJson = StatsCacheCodec.outcomeToJson(
            StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(4, 80.0, 40.0, Collections.emptyList()),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                StudyStatsStore.LadderHealthMetric.empty(),
            ),
        )
        val oldImpactJson = StatsCacheCodec.impactReportToJson(KanjiImpactAnalyzer.Report(2, 1, 0, Collections.emptyList()))
        db.execSQL(
            "INSERT OR REPLACE INTO ${LocalStoreBase.TABLE_STATS_SCREEN_CACHE} (id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, ?, ?, ?, ?)",
            arrayOf<Any>(3L, 999L, oldOutcomeJson, oldImpactJson),
        )

        val latest = cacheStore.readLatest(db)

        assertNotNull(latest)
        latest!!
        assertEquals(1, latest.cacheFormatVersion)
        assertEquals(0, latest.studyImpactStats.totalReviews)
        assertEquals(0, latest.studyStreak.currentDays)
        assertEquals(0, latest.studyStreak.bestDays)
        assertFalse(latest.studyStreak.studiedToday)
        assertEquals(0, latest.studyStreak.reviewsToday)
        assertEquals(0L, latest.studyStreak.lastStudyAtMillis)
        assertEquals(0L, latest.studyTaskTimeStats.todayMillis)
        assertEquals(0L, latest.studyTaskTimeStats.lastSevenDaysMillis)
        assertEquals(0, latest.studyTaskTimeStats.answeredTasks)
        assertTrue(latest.recentMistakes.isEmpty())
        assertTrue(latest.reviewDaySummaries.isEmpty())
    }

    @Test
    fun readLatestLegacyCacheWithoutReviewDaySummariesDefaultsToEmptyListAndIsLegacy() {
        val reviewDaySummaries = listOf(
            StatsCacheStore.ReviewDaySummarySnapshot(1_000L, 8, 2, 1, 3, 2, 4, 1),
        )
        val legacyOutcomeJson = JSONObject(
            StatsCacheCodec.outcomeToJson(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(4, 80.0, 40.0, Collections.emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                StudyStatsStore.StudyImpactStats(8, 3, 1, 1, 0, 0),
                listOf(
                    StudyStatsStore.RecentMistake("痛", "again", 1_000L),
                ),
                StudyStatsStore.StudyStreak(2, 5, true, 1, 2_000L),
                StudyStatsStore.StudyTaskTimeStats(3_000L, 4_000L, 5),
                reviewDaySummaries,
            )
        ).apply {
            remove("reviewDaySummaries")
            put("cacheFormatVersion", STATS_CACHE_FORMAT_VERSION - 1)
        }
        val oldImpactJson = StatsCacheCodec.impactReportToJson(KanjiImpactAnalyzer.Report(2, 1, 0, Collections.emptyList()))
        db.execSQL(
            "INSERT OR REPLACE INTO ${LocalStoreBase.TABLE_STATS_SCREEN_CACHE} (id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, ?, ?, ?, ?)",
            arrayOf<Any>(3L, 999L, legacyOutcomeJson.toString(), oldImpactJson),
        )

        val latest = cacheStore.readLatest(db)

        assertNotNull(latest)
        latest!!
        assertEquals(STATS_CACHE_FORMAT_VERSION - 1, latest.cacheFormatVersion)
        assertTrue(latest.reviewDaySummaries.isEmpty())
        assertNull(cacheStore.readFresh(db))
    }

    @Test
    fun readLatestMalformedReviewDaySummariesSkipsBadEntriesAndKeepsOtherFields() {
        val malformedOutcomeJson = JSONObject(
            StatsCacheCodec.outcomeToJson(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(4, 80.0, 40.0, Collections.emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                StudyStatsStore.StudyImpactStats(8, 3, 1, 1, 0, 0),
                listOf(
                    StudyStatsStore.RecentMistake("痛", "again", 1_000L),
                ),
                StudyStatsStore.StudyStreak(2, 5, true, 1, 2_000L),
                StudyStatsStore.StudyTaskTimeStats(3_000L, 4_000L, 5),
                listOf(
                    StatsCacheStore.ReviewDaySummarySnapshot(1_000L, 8, 2, 1, 3, 2, 4, 1),
                ),
            )
        ).apply {
            put("reviewDaySummaries", JSONArray().put("bad-entry").put(17))
            put("cacheFormatVersion", STATS_CACHE_FORMAT_VERSION - 1)
        }
        val oldImpactJson = StatsCacheCodec.impactReportToJson(KanjiImpactAnalyzer.Report(2, 1, 0, Collections.emptyList()))
        db.execSQL(
            "INSERT OR REPLACE INTO ${LocalStoreBase.TABLE_STATS_SCREEN_CACHE} (id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, ?, ?, ?, ?)",
            arrayOf<Any>(3L, 999L, malformedOutcomeJson.toString(), oldImpactJson),
        )

        val latest = cacheStore.readLatest(db)

        assertNotNull(latest)
        latest!!
        assertEquals(STATS_CACHE_FORMAT_VERSION - 1, latest.cacheFormatVersion)
        assertEquals(8, latest.studyImpactStats.totalReviews)
        assertEquals(1, latest.recentMistakes.size)
        assertTrue(latest.reviewDaySummaries.isEmpty())
    }

    @Test
    fun readFreshStatsReturnsNullWhenSourceVersionChanges() {
        setSourceVersion(8L)
        cacheStore.write(db, snapshot(7L, 1234L, 2, 5))

        assertNull(cacheStore.readFresh(db))

        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        assertEquals(7L, latest!!.sourceVersion)
    }

    @Test
    fun readFreshStatsReturnsNullWhenCacheFormatVersionChanges() {
        setSourceVersion(7L)
        val legacySnapshot = snapshot(7L, 1_234L, 2, 5, cacheFormatVersion = STATS_CACHE_FORMAT_VERSION - 1)
        val legacyOutcomeJson = JSONObject(
            StatsCacheCodec.outcomeToJson(
                legacySnapshot.outcomeStats,
                legacySnapshot.studyImpactStats,
                legacySnapshot.recentMistakes,
                legacySnapshot.studyStreak,
                legacySnapshot.studyTaskTimeStats,
                legacySnapshot.reviewDaySummaries,
            )
        ).apply {
            put("cacheFormatVersion", STATS_CACHE_FORMAT_VERSION - 1)
        }
        db.execSQL(
            "INSERT OR REPLACE INTO stats_screen_cache " +
                "(id, source_version, generated_at, cache_format_version, outcome_json, impact_report_json) VALUES (1, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                7L,
                1_234L,
                STATS_CACHE_FORMAT_VERSION - 1,
                legacyOutcomeJson.toString(),
                StatsCacheCodec.impactReportToJson(legacySnapshot.impactReport),
            ),
        )

        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        assertEquals(STATS_CACHE_FORMAT_VERSION - 1, latest!!.cacheFormatVersion)
        assertFalse(cacheStore.hasFreshSnapshot(db, nowMillis = 1_234L))
        assertNull(cacheStore.readFresh(db, nowMillis = 1_234L))
    }

    @Test
    fun writeSnapshotUsesCurrentCacheFormatVersionForColumnAndJson() {
        setSourceVersion(13L)
        cacheStore.write(db, snapshot(13L, 1_234L, 2, 5, cacheFormatVersion = STATS_CACHE_FORMAT_VERSION - 1))

        assertEquals(STATS_CACHE_FORMAT_VERSION, cacheFormatVersionColumn())
        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        assertEquals(STATS_CACHE_FORMAT_VERSION, latest!!.cacheFormatVersion)
        assertTrue(cacheStore.hasFreshSnapshot(db, nowMillis = 1_234L))
        assertNotNull(cacheStore.readFresh(db, nowMillis = 1_234L))
    }

    @Test
    fun hasFreshSnapshotChecksVersionsWithoutDecodingSnapshot() {
        val now = LocalDayPolicy.localDayStart(1_234_567_890_000L)
        setSourceVersion(11L)
        db.execSQL(
            "INSERT OR REPLACE INTO stats_screen_cache " +
                "(id, source_version, generated_at, cache_format_version, outcome_json, impact_report_json) VALUES (1, 11, ?, ?, 'not-json', '{}')",
            arrayOf<Any>(now + 12_000L, STATS_CACHE_FORMAT_VERSION),
        )

        assertTrue(cacheStore.hasFreshSnapshot(db, nowMillis = now + 6_000L))

        cacheStore.markDirty(db)

        assertFalse(cacheStore.hasFreshSnapshot(db, nowMillis = now + 6_000L))
    }

    @Test
    fun hasFreshSnapshotReturnsFalseForLegacyCacheFormatVersionWithoutDecodingSnapshot() {
        val now = LocalDayPolicy.localDayStart(1_234_567_890_000L)
        setSourceVersion(12L)
        db.execSQL(
            "INSERT OR REPLACE INTO stats_screen_cache " +
                "(id, source_version, generated_at, cache_format_version, outcome_json, impact_report_json) VALUES (1, 12, ?, ?, 'not-json', '{}')",
            arrayOf<Any>(now + 12_000L, STATS_CACHE_FORMAT_VERSION - 1),
        )

        assertFalse(cacheStore.hasFreshSnapshot(db, nowMillis = now + 6_000L))
    }

    @Test
    fun readFreshStatsReturnsNullWhenSnapshotNotFromCurrentDay() {
        val today = LocalDayPolicy.localDayStart(1_234_567_890_000L)
        val yesterday = LocalDayPolicy.moveLocalDays(today, -1)
        setSourceVersion(12L)
        cacheStore.write(
            db,
            snapshot(
                sourceVersion = 12L,
                generatedAtMillis = yesterday + 60_000L,
                improvedCount = 2,
                helpedCount = 5,
            ),
        )

        assertNull(cacheStore.readFresh(db, nowMillis = today))
    }

    @Test
    fun latestStatsSnapshotOrNullReturnsStaleButReadableCacheRows() {
        val now = System.currentTimeMillis()
        val yesterday = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(now), -1)
        val sourceVersion = cacheStore.currentSourceVersion(db)
        cacheStore.write(
            db,
            snapshot(
                sourceVersion = sourceVersion,
                generatedAtMillis = yesterday + 60_000L,
                improvedCount = 4,
                helpedCount = 6,
            ),
        )

        assertNull(localStore!!.cachedStatsSnapshotOrNull())
        val latest = localStore!!.latestStatsSnapshotOrNull()
        assertNotNull(latest)
        assertEquals(sourceVersion, latest!!.sourceVersion)
        assertEquals(yesterday + 60_000L, latest.generatedAtMillis)
    }

    @Test
    fun recordStudyTaskAnsweredMarksStatsCacheStaleAfterSuccessfulInsert() {
        val sourceVersion = cacheStore.currentSourceVersion(db)
        val generatedAtMillis = System.currentTimeMillis()
        cacheStore.write(db, snapshot(sourceVersion, generatedAtMillis, 2, 5))

        assertNotNull(localStore!!.cachedStatsSnapshotOrNull())

        val inserted = localStore!!.recordStudyTaskAnswered(
            taskKey = "task-1",
            kanji = "痛",
            taskType = "study",
            startedAt = generatedAtMillis - 5_000L,
            answeredAt = generatedAtMillis,
            activeElapsedMillis = 5_000L,
            outcome = "correct",
        )

        assertTrue(inserted)
        assertNull(localStore!!.cachedStatsSnapshotOrNull())
        assertNotNull(localStore!!.latestStatsSnapshotOrNull())
        assertEquals(sourceVersion + 1L, cacheStore.currentSourceVersion(db))
        assertEquals(sourceVersion, localStore!!.latestStatsSnapshotOrNull()!!.sourceVersion)
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

    private fun cacheFormatVersionColumn(): Int {
        val cursor = db.rawQuery("SELECT cache_format_version FROM stats_screen_cache WHERE id=1", null)
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
        studyImpactStats: StudyStatsStore.StudyImpactStats = StudyStatsStore.StudyImpactStats(8, 3, 1, 1, 0, 0),
        recentMistakes: List<StudyStatsStore.RecentMistake> = listOf(
            StudyStatsStore.RecentMistake("痛", "again", 1_000L),
            StudyStatsStore.RecentMistake("弱", "hard", 2_000L),
        ),
        studyStreak: StudyStatsStore.StudyStreak = StudyStatsStore.StudyStreak(0, 0, false, 0, 0L),
        studyTaskTimeStats: StudyStatsStore.StudyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(0L, 0L, 0),
        cacheFormatVersion: Int = STATS_CACHE_FORMAT_VERSION,
        reviewDaySummaries: List<StatsCacheStore.ReviewDaySummarySnapshot> = emptyList(),
    ): StatsCacheStore.Snapshot {
        return StatsCacheStore.Snapshot(
            outcomeStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(
                    improvedCount,
                    80.0,
                    40.0,
                    Collections.emptyList(),
                ),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                StudyStatsStore.LadderHealthMetric.empty(),
            ),
            impactReport = KanjiImpactAnalyzer.Report(helpedCount, 0, 0, Collections.emptyList()),
            generatedAtMillis = generatedAtMillis,
            sourceVersion = sourceVersion,
            studyImpactStats = studyImpactStats,
            recentMistakes = recentMistakes,
            studyStreak = studyStreak,
            studyTaskTimeStats = studyTaskTimeStats,
            cacheFormatVersion = cacheFormatVersion,
            reviewDaySummaries = reviewDaySummaries,
        )
    }
}
