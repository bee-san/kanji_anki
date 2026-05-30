package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import kotlin.math.max
import kotlin.math.min

internal class KanjiImpactReportStore(private val store: LocalStore) {
    fun report(db: SQLiteDatabase = store.readableDatabase): KanjiImpactAnalyzer.Report {
        val latestSyncId = latestSuccessfulSyncId(db)
        if (latestSyncId == 0L) {
            return KanjiImpactAnalyzer.Report(0, 0, 0, emptyList())
        }
        val currentByKanji = kanjiMetricsForSync(db, latestSyncId)
        val reviewCounts = reviewCountsByKanji(db)
        val candidates = impactCandidateKanji(db, latestSyncId)
        candidates.addAll(reviewCounts.keys)
        val histories = candidates.map { kanji ->
            val baseline = baselineKanjiSnapshot(db, kanji)
            val current = currentByKanji[kanji]
            val sameCards = if (baseline == null || baseline.syncId == latestSyncId) {
                SameCardMetrics.EMPTY
            } else {
                sameCardMetrics(db, kanji, baseline.syncId, latestSyncId)
            }
            val commonCards = sameCards.current?.totalCards() ?: 0
            val currentCards = current?.totalCards() ?: 0
            KanjiImpactAnalyzer.KanjiHistory(
                kanji,
                baseline?.metrics,
                current,
                sameCards.baseline,
                sameCards.current,
                commonCards,
                max(0, currentCards - commonCards),
                reviewCounts.getOrDefault(kanji, 0)
            )
        }
        return KanjiImpactAnalyzer().analyze(histories)
    }

    private fun latestSuccessfulSyncId(db: SQLiteDatabase): Long {
        val cursor = db.query(
            TABLE_SYNC_RUNS,
            arrayOf("id"),
            "status=?",
            arrayOf(STATUS_SUCCESS),
            null,
            null,
            ORDER_ID_DESC,
            "1"
        )
        cursor.use {
            return if (it.moveToFirst()) longValue(it, "id") else 0L
        }
    }

    private fun reviewCountsByKanji(db: SQLiteDatabase): Map<String, Int> {
        val counts = HashMap<String, Int>()
        val cursor = db.rawQuery("SELECT kanji, COUNT(*) AS review_count FROM review_log GROUP BY kanji", null)
        cursor.use {
            while (it.moveToNext()) {
                counts[string(it, COLUMN_KANJI)] = integer(it, "review_count")
            }
        }
        return counts
    }

    private fun impactCandidateKanji(db: SQLiteDatabase, latestSyncId: Long): MutableSet<String> {
        val candidates = HashSet<String>()
        addKanjiFromCursor(
            candidates,
            db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                arrayOf(COLUMN_KANJI),
                "sync_id=? AND (weakness_score>0 OR reason_code<>'' OR active_example_count>0 OR suspended_example_count>0)",
                arrayOf(latestSyncId.toString()),
                null,
                null,
                null
            )
        )
        addKanjiFromCursor(
            candidates,
            db.query(true, TABLE_STUDY_ITEMS, arrayOf(COLUMN_KANJI), null, null, null, null, null, null)
        )
        addKanjiFromCursor(
            candidates,
            db.query(true, TABLE_SUSPENDED_IMPORTS, arrayOf(COLUMN_KANJI), null, null, null, null, null, null)
        )
        return candidates
    }

    private fun addKanjiFromCursor(candidates: MutableSet<String>, cursor: Cursor) {
        cursor.use {
            while (it.moveToNext()) {
                candidates.add(string(it, COLUMN_KANJI))
            }
        }
    }

    private fun kanjiMetricsForSync(
        db: SQLiteDatabase,
        syncId: Long,
    ): Map<String, KanjiImpactAnalyzer.MetricSnapshot> {
        val out = LinkedHashMap<String, KanjiImpactAnalyzer.MetricSnapshot>()
        val cursor = db.query(
            TABLE_SYNC_KANJI_SNAPSHOTS,
            null,
            "sync_id=?",
            arrayOf(syncId.toString()),
            null,
            null,
            ORDER_KANJI_ASC
        )
        cursor.use {
            while (it.moveToNext()) {
                out[string(it, COLUMN_KANJI)] = readKanjiImpactMetric(it)
            }
        }
        return out
    }

    private fun baselineKanjiSnapshot(db: SQLiteDatabase, kanji: String): HistoricalKanjiSnapshot? {
        val startedAt = firstKaniSignalAt(db, kanji)
        if (startedAt <= 0L) {
            return firstKanjiSnapshot(db, kanji)
        }
        val atOrAfterStart = firstKanjiSnapshotAtOrAfter(db, kanji, startedAt)
        if (atOrAfterStart != null) {
            return atOrAfterStart
        }
        return latestKanjiSnapshotAtOrBefore(db, kanji, startedAt)
    }

    private fun firstKaniSignalAt(db: SQLiteDatabase, kanji: String): Long {
        var first = minLongQuery(
            db,
            "SELECT MIN(occurred_at) FROM kanji_timeline_events WHERE kanji=?",
            arrayOf(kanji)
        )
        val firstReview = minLongQuery(
            db,
            "SELECT MIN(reviewed_at) FROM review_log WHERE kanji=?",
            arrayOf(kanji)
        )
        val firstStudyItem = minLongQuery(
            db,
            "SELECT MIN(created_at) FROM study_items WHERE kanji=?",
            arrayOf(kanji)
        )
        val firstSuspendedImport = minLongQuery(
            db,
            "SELECT MIN(first_imported_at) FROM suspended_imports WHERE kanji=?",
            arrayOf(kanji)
        )
        first = earliestPositive(first, firstReview)
        first = earliestPositive(first, firstStudyItem)
        return earliestPositive(first, firstSuspendedImport)
    }

    private fun minLongQuery(db: SQLiteDatabase, sql: String, args: Array<String>): Long {
        val cursor = db.rawQuery(sql, args)
        cursor.use {
            it.moveToFirst()
            if (it.isNull(0)) {
                return 0L
            }
            return it.getLong(0)
        }
    }

    private fun earliestPositive(left: Long, right: Long): Long {
        if (left <= 0L) {
            return max(0L, right)
        }
        if (right <= 0L) {
            return left
        }
        return min(left, right)
    }

    private fun firstKanjiSnapshotAtOrAfter(
        db: SQLiteDatabase,
        kanji: String,
        startedAt: Long,
    ): HistoricalKanjiSnapshot? {
        val cursor = db.query(
            TABLE_SYNC_KANJI_SNAPSHOTS,
            null,
            "kanji=? AND finished_at>=?",
            arrayOf(kanji, startedAt.toString()),
            null,
            null,
            "finished_at ASC, sync_id ASC",
            "1"
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return HistoricalKanjiSnapshot(longValue(it, COLUMN_SYNC_ID), readKanjiImpactMetric(it))
        }
    }

    private fun latestKanjiSnapshotAtOrBefore(
        db: SQLiteDatabase,
        kanji: String,
        startedAt: Long,
    ): HistoricalKanjiSnapshot? {
        val cursor = db.query(
            TABLE_SYNC_KANJI_SNAPSHOTS,
            null,
            "kanji=? AND finished_at<=?",
            arrayOf(kanji, startedAt.toString()),
            null,
            null,
            "finished_at DESC, sync_id DESC",
            "1"
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return HistoricalKanjiSnapshot(longValue(it, COLUMN_SYNC_ID), readKanjiImpactMetric(it))
        }
    }

    private fun firstKanjiSnapshot(db: SQLiteDatabase, kanji: String): HistoricalKanjiSnapshot? {
        val cursor = db.query(
            TABLE_SYNC_KANJI_SNAPSHOTS,
            null,
            "kanji=?",
            arrayOf(kanji),
            null,
            null,
            "sync_id ASC",
            "1"
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return HistoricalKanjiSnapshot(longValue(it, COLUMN_SYNC_ID), readKanjiImpactMetric(it))
        }
    }

    private fun readKanjiImpactMetric(cursor: Cursor): KanjiImpactAnalyzer.MetricSnapshot {
        return KanjiImpactAnalyzer.MetricSnapshot(
            integer(cursor, "active_cards"),
            integer(cursor, "suspended_cards"),
            integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
            cursor.getDouble(cursor.getColumnIndexOrThrow("average_interval_days")),
            integer(cursor, "total_reps"),
            integer(cursor, "total_lapses"),
            nullableDouble(cursor, "fsrs_stability_avg"),
            nullableDouble(cursor, "fsrs_difficulty_avg"),
            nullableDouble(cursor, "fsrs_retrievability_avg")
        )
    }

    private fun sameCardMetrics(
        db: SQLiteDatabase,
        kanji: String,
        baselineSyncId: Long,
        currentSyncId: Long,
    ): SameCardMetrics {
        val baseline = HistoricalKanjiAggregate(kanji)
        val current = HistoricalKanjiAggregate(kanji)
        val cursor = db.rawQuery(
            "SELECT " +
                "b.interval_days AS b_interval_days, b.reps AS b_reps, b.lapses AS b_lapses, b.suspended AS b_suspended, b.mature AS b_mature, b.fsrs_stability AS b_fsrs_stability, b.fsrs_difficulty AS b_fsrs_difficulty, b.fsrs_retrievability AS b_fsrs_retrievability, " +
                "c.interval_days AS c_interval_days, c.reps AS c_reps, c.lapses AS c_lapses, c.suspended AS c_suspended, c.mature AS c_mature, c.fsrs_stability AS c_fsrs_stability, c.fsrs_difficulty AS c_fsrs_difficulty, c.fsrs_retrievability AS c_fsrs_retrievability " +
                "FROM sync_card_snapshots b " +
                "JOIN sync_card_snapshots c ON c.card_id=b.card_id " +
                "JOIN sync_note_snapshots nb ON nb.sync_id=b.sync_id AND nb.note_id=b.note_id " +
                "JOIN sync_note_snapshots nc ON nc.sync_id=c.sync_id AND nc.note_id=c.note_id " +
                "WHERE b.sync_id=? AND c.sync_id=? AND instr(nb.extracted_kanji, ?) > 0 AND instr(nc.extracted_kanji, ?) > 0",
            arrayOf(baselineSyncId.toString(), currentSyncId.toString(), kanji, kanji)
        )
        cursor.use {
            while (it.moveToNext()) {
                addCardMetrics(baseline, it, "b_")
                addCardMetrics(current, it, "c_")
            }
        }
        if (current.activeCards() + current.suspendedCards() == 0) {
            return SameCardMetrics.EMPTY
        }
        return SameCardMetrics(baseline.impactMetricSnapshot(), current.impactMetricSnapshot())
    }

    private fun addCardMetrics(aggregate: HistoricalKanjiAggregate, cursor: Cursor, prefix: String) {
        aggregate.addCard(
            integer(cursor, prefix + "interval_days"),
            integer(cursor, prefix + "reps"),
            integer(cursor, prefix + "lapses"),
            integer(cursor, prefix + "suspended") == 1,
            integer(cursor, prefix + "mature") == 1,
            HistoricalKanjiAggregate.FsrsMemoryValues(
                nullableDouble(cursor, prefix + "fsrs_stability"),
                nullableDouble(cursor, prefix + "fsrs_difficulty"),
                nullableDouble(cursor, prefix + "fsrs_retrievability")
            )
        )
    }

    private data class HistoricalKanjiSnapshot(
        val syncId: Long,
        val metrics: KanjiImpactAnalyzer.MetricSnapshot,
    )

    private data class SameCardMetrics(
        val baseline: KanjiImpactAnalyzer.MetricSnapshot?,
        val current: KanjiImpactAnalyzer.MetricSnapshot?,
    ) {
        companion object {
            val EMPTY = SameCardMetrics(null, null)
        }
    }

    private companion object {
        const val TABLE_SYNC_RUNS = "sync_runs"
        const val TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots"
        const val TABLE_STUDY_ITEMS = "study_items"
        const val TABLE_SUSPENDED_IMPORTS = "suspended_imports"
        const val COLUMN_KANJI = "kanji"
        const val COLUMN_SYNC_ID = "sync_id"
        const val COLUMN_MATURE_SUPPORT_COUNT = "mature_support_count"
        const val ORDER_ID_DESC = "id DESC"
        const val ORDER_KANJI_ASC = "kanji ASC"
        const val STATUS_SUCCESS = "success"

        fun string(cursor: Cursor, column: String): String {
            return cursor.getString(cursor.getColumnIndexOrThrow(column))
        }

        fun integer(cursor: Cursor, column: String): Int {
            return cursor.getInt(cursor.getColumnIndexOrThrow(column))
        }

        fun nullableDouble(cursor: Cursor, column: String): Double? {
            val idx = cursor.getColumnIndexOrThrow(column)
            return if (cursor.isNull(idx)) null else cursor.getDouble(idx)
        }

        fun longValue(cursor: Cursor, column: String): Long {
            return cursor.getLong(cursor.getColumnIndexOrThrow(column))
        }
    }
}
