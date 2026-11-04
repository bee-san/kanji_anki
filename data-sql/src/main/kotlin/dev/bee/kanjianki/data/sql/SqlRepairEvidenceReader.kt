package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecentMistakePolicy
import dev.bee.kanjianki.core.RecordsBase

/**
 * Driver-neutral port of the repair-evidence input pipeline
 * (`StudyStatsQueries`). Both the sync queue-planning snapshot and the repaired
 * write-back proposal consume these inputs; keeping one reader avoids two
 * copies of the same delicate snapshot-boundary SQL.
 */
internal class SqlRepairEvidenceReader(
    private val session: SqlSession,
) {
    fun inputs(): List<KanjiRepairEvidencePolicy.Input> {
        val candidates = candidates()
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .map(::input)
            .sortedWith(
                compareByDescending<KanjiRepairEvidencePolicy.Input> { it.lastReviewAtMillis() }
                    .thenBy { it.kanji() },
            )
    }

    private fun candidates(): List<String> =
        session.queryList(
            """
            SELECT kanji FROM study_items WHERE state <> ?
            UNION SELECT DISTINCT kanji FROM review_log WHERE kanji <> ''
            ORDER BY kanji ASC
            """.trimIndent(),
            bind = { bindText(1, STATE_RETIRED) },
        ) { row -> row.text(0) }

    private fun input(kanji: String): KanjiRepairEvidencePolicy.Input {
        val summary = summary(kanji)
        return KanjiRepairEvidencePolicy.Input(
            kanji,
            snapshot(kanji, "<", summary.firstReviewAtMillis),
            snapshot(kanji, ">", summary.lastReviewAtMillis),
            summary.kaniReviews,
            summary.postReviewSamples,
            summary.writingFailures,
            summary.lastMistakeAtMillis,
            summary.firstReviewAtMillis,
            summary.lastReviewAtMillis,
            summary.lastSyncAtMillis,
            ladder(kanji),
        )
    }

    private fun summary(kanji: String): Summary {
        val ratings = RecentMistakePolicy.mistakeRatings()
        val summary = session.queryOneOrNull(
            """
            SELECT
                COUNT(*) AS kani_reviews,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failures,
                COALESCE(MAX(CASE WHEN rating IN (?, ?) THEN reviewed_at ELSE 0 END), 0) AS last_mistake_at,
                COALESCE(MIN(reviewed_at), 0) AS first_review_at,
                COALESCE(MAX(reviewed_at), 0) AS last_review_at
            FROM review_log WHERE kanji = ?
            """.trimIndent(),
            bind = {
                bindText(1, ratings[0])
                bindText(2, ratings[1])
                bindText(3, kanji)
            },
        ) { row ->
            Summary(
                kaniReviews = row.long(0).toInt(),
                writingFailures = row.long(1).toInt(),
                lastMistakeAtMillis = row.long(2),
                firstReviewAtMillis = row.long(3),
                lastReviewAtMillis = row.long(4),
                postReviewSamples = 0,
                lastSyncAtMillis = 0L,
            )
        } ?: Summary(0, 0, 0L, 0L, 0L, 0, 0L)
        return summary.copy(
            postReviewSamples = postReviewSamples(kanji, summary.lastReviewAtMillis),
            lastSyncAtMillis = lastSyncAtMillis(kanji),
        )
    }

    private fun lastSyncAtMillis(kanji: String): Long =
        session.queryOneOrNull(
            """
            SELECT COALESCE(MAX(s.finished_at), 0) FROM sync_kanji_snapshots s
            WHERE s.kanji = ? AND s.sync_id IN (SELECT id FROM sync_runs WHERE status = ?)
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindText(2, STATUS_SUCCESS)
            },
        ) { row -> row.long(0) } ?: 0L

    private fun postReviewSamples(kanji: String, lastReviewAtMillis: Long): Int {
        if (lastReviewAtMillis <= 0L) return 0
        return session.queryOneOrNull(
            """
            SELECT COUNT(*) FROM sync_kanji_snapshots s
            WHERE s.kanji = ? AND s.finished_at > ?
              AND s.sync_id IN (SELECT id FROM sync_runs WHERE status = ?)
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindLong(2, lastReviewAtMillis)
                bindText(3, STATUS_SUCCESS)
            },
        ) { row -> row.long(0).toInt() } ?: 0
    }

    private fun snapshot(
        kanji: String,
        comparator: String,
        boundaryMillis: Long,
    ): KanjiRepairEvidencePolicy.Snapshot? {
        if (boundaryMillis <= 0L) return null
        return session.queryOneOrNull(
            """
            SELECT weakness_score, mature_support_count, finished_at,
                   active_example_count, suspended_example_count, reason_code
            FROM sync_kanji_snapshots s
            WHERE s.kanji = ? AND s.finished_at $comparator ?
              AND s.sync_id IN (SELECT id FROM sync_runs WHERE status = ?)
            ORDER BY s.finished_at DESC, s.sync_id DESC
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindLong(2, boundaryMillis)
                bindText(3, STATUS_SUCCESS)
            },
        ) { row ->
            KanjiRepairEvidencePolicy.Snapshot(
                row.long(0).toInt(),
                row.long(1).toInt(),
                row.long(2),
                row.long(3).toInt(),
                row.long(4).toInt(),
                if (row.isNull(5)) null else row.text(5),
            )
        }
    }

    private fun ladder(kanji: String): KanjiRepairEvidencePolicy.Ladder? =
        session.queryOneOrNull(
            """
            SELECT rung, phase, real_pass_streak, real_again_streak, mature_interval_days
            FROM study_items WHERE kanji = ? AND state <> ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindText(2, STATE_RETIRED)
            },
        ) { row ->
            val values = NamedSqlRow(row)
            KanjiRepairEvidencePolicy.Ladder(
                RecordsBase.LadderRung.fromWireName(values.text("rung")),
                RecordsBase.SchedulerPhase.fromWireName(values.text("phase")),
                values.int("real_pass_streak"),
                values.int("real_again_streak"),
                values.int("mature_interval_days"),
            )
        }

    private data class Summary(
        val kaniReviews: Int,
        val writingFailures: Int,
        val lastMistakeAtMillis: Long,
        val firstReviewAtMillis: Long,
        val lastReviewAtMillis: Long,
        val postReviewSamples: Int,
        val lastSyncAtMillis: Long,
    )

    private companion object {
        const val STATE_RETIRED = "retired"
        const val STATUS_SUCCESS = "success"
    }
}
