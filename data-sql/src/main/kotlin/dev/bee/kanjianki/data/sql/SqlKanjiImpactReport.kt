package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import kotlin.math.max
import kotlin.math.min

/**
 * Driver-neutral port of the app's `KanjiImpactReportStore`: builds the
 * per-kanji before/after history from the successful sync snapshots and feeds
 * `KanjiImpactAnalyzer`. Read-only.
 */
internal class SqlKanjiImpactReport(
    private val session: SqlSession,
) {
    fun report(): KanjiImpactAnalyzer.Report {
        val latestSyncId = latestSuccessfulSyncId()
        if (latestSyncId == 0L) {
            return KanjiImpactAnalyzer.Report(0, 0, 0, emptyList())
        }
        val currentByKanji = kanjiMetricsForSync(latestSyncId)
        val reviewCounts = reviewCountsByKanji()
        val candidates = impactCandidateKanji(latestSyncId).toMutableSet()
        candidates.addAll(reviewCounts.keys)
        val histories = candidates.map { kanji ->
            val baseline = baselineKanjiSnapshot(kanji)
            val current = currentByKanji[kanji]
            val sameCards = if (baseline == null || baseline.syncId == latestSyncId) {
                SameCardMetrics(null, null)
            } else {
                sameCardMetrics(kanji, baseline.syncId, latestSyncId)
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
                reviewCounts.getOrDefault(kanji, 0),
            )
        }
        return KanjiImpactAnalyzer().analyze(histories)
    }

    private fun latestSuccessfulSyncId(): Long =
        session.queryOneOrNull(
            "SELECT id FROM sync_runs WHERE status = ? ORDER BY id DESC LIMIT 1",
            bind = { bindText(1, STATUS_SUCCESS) },
        ) { row -> row.long(0) } ?: 0L

    private fun reviewCountsByKanji(): Map<String, Int> =
        session.queryList(
            "SELECT kanji, COUNT(*) FROM review_log GROUP BY kanji",
        ) { row -> row.text(0) to row.long(1).toInt() }.toMap()

    private fun impactCandidateKanji(latestSyncId: Long): Set<String> {
        val candidates = LinkedHashSet<String>()
        session.queryList(
            """
            SELECT kanji FROM sync_kanji_snapshots
            WHERE sync_id = ? AND (weakness_score > 0 OR reason_code <> '' OR active_example_count > 0 OR suspended_example_count > 0)
            """.trimIndent(),
            bind = { bindLong(1, latestSyncId) },
        ) { row -> row.text(0) }.let(candidates::addAll)
        session.queryList("SELECT DISTINCT kanji FROM study_items") { row -> row.text(0) }.let(candidates::addAll)
        session.queryList(
            "SELECT DISTINCT kanji FROM suspended_imports WHERE last_seen_sync_id IN (SELECT id FROM sync_runs WHERE status = ?)",
            bind = { bindText(1, STATUS_SUCCESS) },
        ) { row -> row.text(0) }.let(candidates::addAll)
        return candidates
    }

    private fun kanjiMetricsForSync(syncId: Long): Map<String, KanjiImpactAnalyzer.MetricSnapshot> =
        session.queryList(
            "SELECT * FROM sync_kanji_snapshots WHERE sync_id = ? ORDER BY kanji ASC",
            bind = { bindLong(1, syncId) },
        ) { row -> NamedSqlRow(row).let { it.text("kanji") to readMetric(it) } }.toMap()

    private fun baselineKanjiSnapshot(kanji: String): HistoricalKanjiSnapshot? {
        val startedAt = firstKaniSignalAt(kanji)
        if (startedAt <= 0L) return firstKanjiSnapshot(kanji)
        return firstKanjiSnapshotAtOrAfter(kanji, startedAt) ?: latestKanjiSnapshotAtOrBefore(kanji, startedAt)
    }

    private fun firstKaniSignalAt(kanji: String): Long {
        var first = minLong(
            """
            SELECT MIN(occurred_at) FROM kanji_timeline_events WHERE kanji = ?
              AND (sync_id IS NULL OR sync_id IN (SELECT id FROM sync_runs WHERE status = ?))
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindText(2, STATUS_SUCCESS)
        }
        first = earliestPositive(first, minLong("SELECT MIN(reviewed_at) FROM review_log WHERE kanji = ?") { bindText(1, kanji) })
        first = earliestPositive(first, minLong("SELECT MIN(created_at) FROM study_items WHERE kanji = ?") { bindText(1, kanji) })
        return earliestPositive(
            first,
            minLong(
                "SELECT MIN(first_imported_at) FROM suspended_imports WHERE kanji = ? AND last_seen_sync_id IN (SELECT id FROM sync_runs WHERE status = ?)",
            ) {
                bindText(1, kanji)
                bindText(2, STATUS_SUCCESS)
            },
        )
    }

    private fun minLong(sql: String, bind: SqlStatement.() -> Unit): Long =
        session.queryOneOrNull(sql, bind = bind) { row -> if (row.isNull(0)) 0L else row.long(0) } ?: 0L

    private fun earliestPositive(left: Long, right: Long): Long {
        if (left <= 0L) return max(0L, right)
        if (right <= 0L) return left
        return min(left, right)
    }

    private fun firstKanjiSnapshotAtOrAfter(kanji: String, startedAt: Long): HistoricalKanjiSnapshot? =
        snapshotRow(
            "kanji = ? AND finished_at >= ? AND sync_id IN (SELECT id FROM sync_runs WHERE status = ?)",
            "finished_at ASC, sync_id ASC",
            kanji,
            startedAt,
        )

    private fun latestKanjiSnapshotAtOrBefore(kanji: String, startedAt: Long): HistoricalKanjiSnapshot? =
        snapshotRow(
            "kanji = ? AND finished_at <= ? AND sync_id IN (SELECT id FROM sync_runs WHERE status = ?)",
            "finished_at DESC, sync_id DESC",
            kanji,
            startedAt,
        )

    private fun firstKanjiSnapshot(kanji: String): HistoricalKanjiSnapshot? =
        session.queryOneOrNull(
            "SELECT * FROM sync_kanji_snapshots WHERE kanji = ? AND sync_id IN (SELECT id FROM sync_runs WHERE status = ?) ORDER BY sync_id ASC LIMIT 1",
            bind = {
                bindText(1, kanji)
                bindText(2, STATUS_SUCCESS)
            },
        ) { row -> NamedSqlRow(row).let { HistoricalKanjiSnapshot(it.long("sync_id"), readMetric(it)) } }

    private fun snapshotRow(
        where: String,
        order: String,
        kanji: String,
        boundary: Long,
    ): HistoricalKanjiSnapshot? =
        session.queryOneOrNull(
            "SELECT * FROM sync_kanji_snapshots WHERE $where ORDER BY $order LIMIT 1",
            bind = {
                bindText(1, kanji)
                bindLong(2, boundary)
                bindText(3, STATUS_SUCCESS)
            },
        ) { row -> NamedSqlRow(row).let { HistoricalKanjiSnapshot(it.long("sync_id"), readMetric(it)) } }

    private fun readMetric(values: NamedSqlRow): KanjiImpactAnalyzer.MetricSnapshot =
        KanjiImpactAnalyzer.MetricSnapshot(
            values.int("active_cards"),
            values.int("suspended_cards"),
            values.int("mature_support_count"),
            values.double("average_interval_days"),
            values.int("total_reps"),
            values.int("total_lapses"),
            values.nullableDouble("fsrs_stability_avg"),
            values.nullableDouble("fsrs_difficulty_avg"),
            values.nullableDouble("fsrs_retrievability_avg"),
        )

    private fun sameCardMetrics(kanji: String, baselineSyncId: Long, currentSyncId: Long): SameCardMetrics {
        val baseline = HistoricalKanjiAggregate(kanji)
        val current = HistoricalKanjiAggregate(kanji)
        session.queryList(
            """
            SELECT
                b.interval_days AS b_interval_days, b.reps AS b_reps, b.lapses AS b_lapses, b.suspended AS b_suspended, b.mature AS b_mature,
                b.fsrs_stability AS b_fsrs_stability, b.fsrs_difficulty AS b_fsrs_difficulty, b.fsrs_retrievability AS b_fsrs_retrievability,
                c.interval_days AS c_interval_days, c.reps AS c_reps, c.lapses AS c_lapses, c.suspended AS c_suspended, c.mature AS c_mature,
                c.fsrs_stability AS c_fsrs_stability, c.fsrs_difficulty AS c_fsrs_difficulty, c.fsrs_retrievability AS c_fsrs_retrievability
            FROM sync_card_snapshots b
            JOIN sync_card_snapshots c ON c.card_id = b.card_id
            JOIN sync_note_snapshots nb ON nb.sync_id = b.sync_id AND nb.note_id = b.note_id
            JOIN sync_note_snapshots nc ON nc.sync_id = c.sync_id AND nc.note_id = c.note_id
            WHERE b.sync_id = ? AND c.sync_id = ? AND instr(nb.extracted_kanji, ?) > 0 AND instr(nc.extracted_kanji, ?) > 0
            """.trimIndent(),
            bind = {
                bindLong(1, baselineSyncId)
                bindLong(2, currentSyncId)
                bindText(3, kanji)
                bindText(4, kanji)
            },
        ) { row ->
            val values = NamedSqlRow(row)
            addCardMetrics(baseline, values, "b_")
            addCardMetrics(current, values, "c_")
        }
        if (current.activeCards() + current.suspendedCards() == 0) {
            return SameCardMetrics(null, null)
        }
        return SameCardMetrics(baseline.impactMetricSnapshot(), current.impactMetricSnapshot())
    }

    private fun addCardMetrics(aggregate: HistoricalKanjiAggregate, values: NamedSqlRow, prefix: String) {
        aggregate.addCard(
            values.int("${prefix}interval_days"),
            values.int("${prefix}reps"),
            values.int("${prefix}lapses"),
            values.int("${prefix}suspended") == 1,
            values.int("${prefix}mature") == 1,
            HistoricalKanjiAggregate.FsrsMemoryValues(
                values.nullableDouble("${prefix}fsrs_stability"),
                values.nullableDouble("${prefix}fsrs_difficulty"),
                values.nullableDouble("${prefix}fsrs_retrievability"),
            ),
        )
    }

    private data class HistoricalKanjiSnapshot(
        val syncId: Long,
        val metrics: KanjiImpactAnalyzer.MetricSnapshot,
    )

    private data class SameCardMetrics(
        val baseline: KanjiImpactAnalyzer.MetricSnapshot?,
        val current: KanjiImpactAnalyzer.MetricSnapshot?,
    )

    private companion object {
        const val STATUS_SUCCESS = "success"
    }
}
