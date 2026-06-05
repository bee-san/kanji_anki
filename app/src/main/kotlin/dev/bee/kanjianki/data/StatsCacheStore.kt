package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import org.json.JSONObject

internal const val STATS_CACHE_FORMAT_VERSION: Int = 3
internal const val STATS_RECENT_MISTAKE_LIMIT: Int = 12

internal class StatsCacheStore(private val store: LocalStore) {
    data class Snapshot(
        val outcomeStats: StudyStatsStore.KaniOutcomeStats,
        val impactReport: KanjiImpactAnalyzer.Report,
        val generatedAtMillis: Long,
        val sourceVersion: Long,
        val studyImpactStats: StudyStatsStore.StudyImpactStats = StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0),
        val recentMistakes: List<StudyStatsStore.RecentMistake> = emptyList(),
        val studyStreak: StudyStatsStore.StudyStreak = StudyStatsStore.StudyStreak(0, 0, false, 0, 0L),
        val studyTaskTimeStats: StudyStatsStore.StudyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(0L, 0L, 0),
        val cacheFormatVersion: Int = 1,
    )

    fun currentSourceVersion(db: SQLiteDatabase = store.readableDatabase): Long {
        ensureSourceVersionRow(db)
        return db.rawQuery(
            "SELECT value FROM ${LocalStoreBase.TABLE_STATS_CACHE_STATE} WHERE key=?",
            arrayOf(LocalStoreBase.STATS_CACHE_SOURCE_VERSION_KEY),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 1L
        }
    }

    fun markDirty(db: SQLiteDatabase = store.writableDatabase): Long {
        val next = currentSourceVersion(db) + 1L
        db.execSQL(
            "UPDATE ${LocalStoreBase.TABLE_STATS_CACHE_STATE} SET value=? WHERE key=?",
            arrayOf<Any>(next, LocalStoreBase.STATS_CACHE_SOURCE_VERSION_KEY),
        )
        return next
    }

    fun readFresh(db: SQLiteDatabase = store.readableDatabase, nowMillis: Long = System.currentTimeMillis()): Snapshot? {
        val snapshot = readLatest(db) ?: return null
        return if (snapshot.sourceVersion == currentSourceVersion(db) &&
            LocalDayPolicy.sameLocalDay(snapshot.generatedAtMillis, nowMillis)
        ) {
            snapshot
        } else {
            null
        }
    }

    fun hasFreshSnapshot(db: SQLiteDatabase = store.readableDatabase, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cursor = db.rawQuery(
            "SELECT source_version, generated_at FROM ${LocalStoreBase.TABLE_STATS_SCREEN_CACHE} WHERE id=1",
            null,
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return false
            }
            val snapshotSourceVersion = it.getLong(0)
            val generatedAtMillis = it.getLong(1)
            return snapshotSourceVersion == currentSourceVersion(db) &&
                LocalDayPolicy.sameLocalDay(generatedAtMillis, nowMillis)
        }
    }

    fun readLatest(db: SQLiteDatabase = store.readableDatabase): Snapshot? {
        return db.rawQuery(
            "SELECT source_version, generated_at, outcome_json, impact_report_json " +
                "FROM ${LocalStoreBase.TABLE_STATS_SCREEN_CACHE} WHERE id=1",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                snapshotFromCursor(cursor)
            }
        }
    }

    fun write(db: SQLiteDatabase, snapshot: Snapshot) {
        val values = ContentValues().apply {
            put("id", 1)
            put("source_version", snapshot.sourceVersion)
            put("generated_at", snapshot.generatedAtMillis)
            put(
                "outcome_json",
                StatsCacheCodec.outcomeToJson(
                    snapshot.outcomeStats,
                    snapshot.studyImpactStats,
                    snapshot.recentMistakes,
                    snapshot.studyStreak,
                    snapshot.studyTaskTimeStats,
                )
            )
            put("impact_report_json", StatsCacheCodec.impactReportToJson(snapshot.impactReport))
        }
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_STATS_SCREEN_CACHE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun snapshotFromCursor(cursor: Cursor): Snapshot? {
        val sourceVersion = cursor.getLong(0)
        val generatedAtMillis = cursor.getLong(1)
        val outcomeJson = cursor.getString(2)
        val impactJson = cursor.getString(3)
        return try {
            val outcomeRoot = JSONObject(outcomeJson)
            JSONObject(impactJson)
            Snapshot(
                outcomeStats = StatsCacheCodec.outcomeFromJson(outcomeJson),
                impactReport = StatsCacheCodec.impactReportFromJson(impactJson),
                generatedAtMillis = generatedAtMillis,
                sourceVersion = sourceVersion,
                studyImpactStats = StatsCacheCodec.studyImpactStatsFromJson(outcomeRoot.optJSONObject("studyImpactStats")),
                recentMistakes = StatsCacheCodec.recentMistakesFromJson(outcomeRoot.optJSONArray("recentMistakes")),
                studyStreak = StatsCacheCodec.studyStreakFromJson(outcomeRoot.optJSONObject("studyStreak")),
                studyTaskTimeStats = StatsCacheCodec.studyTaskTimeStatsFromJson(outcomeRoot.optJSONObject("studyTaskTimeStats")),
                cacheFormatVersion = outcomeRoot.optInt("cacheFormatVersion", 1),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureSourceVersionRow(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO ${LocalStoreBase.TABLE_STATS_CACHE_STATE} (key, value) VALUES (?, 1)",
            arrayOf(LocalStoreBase.STATS_CACHE_SOURCE_VERSION_KEY),
        )
    }
}
