package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ReadingKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.SimilarChoiceCodec
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.ReviewTokenStatus
import dev.bee.kanjianki.data.StudyChoiceDataSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot

/**
 * Driver-neutral Study read projections. Reuses [SqlHomeData] for the
 * dashboard/inventory/similar/streak reads so both surfaces stay identical,
 * and adds the Study-only queue, choice-data, and token/recovery reads.
 */
internal class SqlStudyData(
    private val session: SqlSession,
) {
    private val home = SqlHomeData(session)

    fun loadQueue(nowMillis: Long): StudyQueueSnapshot {
        val settings = SqlSettingsRepository.readSnapshot(session as SqlReadScope)
        val studyRows = home.activeDashboardRows(admittedOnly = false)
        val dayStart = LocalDayPolicy.localDayStart(nowMillis)
        return StudyQueueSnapshot(
            activeRows = studyRows,
            availableRows = home.activeDashboardRows(admittedOnly = true),
            studyItems = if (studyRows.isEmpty()) {
                emptyList()
            } else {
                home.studyItemsForKanji(studyRows.map { it.kanji })
            },
            locallySuspendedKanji = home.locallySuspendedKanji(),
            latestSuccessfulSyncAtMillis = home.latestSuccessfulSyncAtMillis(),
            studyLadder = settings.studyLadder,
            syncSettings = settings.sync,
            schedulerParameters = settings.schedulerParameters,
            schedulerFsrsWeights = settings.schedulerFsrsWeights,
            learningSteps = settings.learningSteps,
            adaptiveWorkload = AdaptiveWorkloadSnapshot(
                settings.adaptiveWorkload.workPercent,
                settings.adaptiveWorkload.maxItems,
                settings.adaptiveWorkload.mode,
            ),
            studyAheadMinutes = settings.studyAheadMinutes,
            studyStreak = home.studyStreak(nowMillis),
            recentReviewStats = reviewStatsSince(nowMillis - RECENT_REVIEW_WINDOW_MILLIS),
            studiedKanjiToday = studiedKanjiSince(dayStart),
            dueLegacyWritingRepairs = home.dueWritingRepairs(nowMillis),
            consecutiveFailedSyncs = home.consecutiveFailedSyncCount(),
        )
    }

    fun loadChoiceData(kanji: String, nowMillis: Long): StudyChoiceDataSnapshot =
        StudyChoiceDataSnapshot(
            kanjiReadingUsages = kanjiReadingUsagesFor(kanji),
            kanjiReadingPool = kanjiReadingPoolFor(kanji),
            readingKanjiUsages = kanjiReadingUsagesFor(kanji).map { usage ->
                ReadingKanjiChoicePlanner.TargetUsage(
                    usage.word,
                    usage.reading,
                    usage.meaning,
                    usage.noteId,
                    usage.mature,
                    usage.lapses,
                )
            },
            readingKanjiCandidates = readingKanjiCandidatesFor(kanji),
            activeRows = home.activeDashboardRows(admittedOnly = true),
            inventory = home.searchInventory("", onlySimilarKanji = false, SqlHomeData.InventoryScope.ALL),
            similarPairs = home.similarPairsForKanji(kanji),
            wrongPickCounts = home.wrongPickCounts(nowMillis),
        )

    fun reviewTokenStatus(query: TokenLookup): ReviewTokenStatus =
        ReviewTokenStatus(
            consumed = hasConsumedToken(query.token),
            matchesReview = hasMatchingConsumedReview(query),
        )

    fun hasConsumedToken(token: String): Boolean =
        session.queryOneOrNull(
            "SELECT 1 FROM review_log WHERE token = ? LIMIT 1",
            bind = { bindText(1, token) },
        ) { true } == true

    fun hasMatchingConsumedReview(query: TokenLookup): Boolean =
        session.queryOneOrNull(
            """
            SELECT 1 FROM review_log
            WHERE token = ? AND kanji = ? AND task_type = ? AND answer_signature = ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, query.token)
                bindText(2, query.kanji)
                bindText(3, query.taskType)
                bindText(4, query.answerSignature)
            },
        ) { true } == true

    fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> =
        home.studyItemsForKanji(kanji)

    fun dueSimilarChoiceForActiveTarget(
        kanji: String,
        nowMillis: Long,
    ): RecordsImportModels.SimilarKanjiChoiceCard? {
        val target = TextUtil.normalizeSingleKanji(kanji)
        if (target.isEmpty()) return null
        val card = session.queryOneOrNull(
            """
            SELECT * FROM similar_kanji_choice_state
            WHERE target_kanji = ? AND passed_at = 0 AND due_at <= ?
            ORDER BY due_at ASC, first_seen_at ASC
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, target)
                bindLong(2, nowMillis)
            },
            map = ::similarChoiceCard,
        ) ?: return null
        return if (hasPendingSimilarRepairs(card.targetKanji, card.choiceSignature)) null else card
    }

    fun dueWritingRepairs(nowMillis: Long): List<RecordsImportModels.SimilarKanjiWritingRepair> =
        home.dueWritingRepairs(nowMillis)

    fun mnemonic(kanji: String): String = home.mnemonic(kanji)

    fun hasFinishedSimilarWritingRepairAttempt(
        repairId: Long,
        token: String,
        attemptsBefore: Int,
        passed: Boolean,
    ): Boolean {
        if (repairId <= 0L || token.isBlank() || attemptsBefore < 0) return false
        val current = similarWritingRepair(repairId) ?: return false
        if (current.activeToken.isNotEmpty()) return false
        return if (passed) {
            current.status == STATUS_COMPLETE &&
                current.completedAtMillis > 0L &&
                current.attempts == attemptsBefore
        } else {
            current.status == STATUS_PENDING &&
                current.completedAtMillis == 0L &&
                current.attempts == attemptsBefore + 1
        }
    }

    fun similarWritingRepair(repairId: Long): RecordsImportModels.SimilarKanjiWritingRepair? =
        session.queryOneOrNull(
            "SELECT * FROM similar_kanji_repair_queue WHERE id = ? LIMIT 1",
            bind = { bindLong(1, repairId) },
            map = ::writingRepair,
        )

    private fun hasPendingSimilarRepairs(targetKanji: String, choiceSignature: String): Boolean =
        session.queryOneOrNull(
            """
            SELECT 1 FROM similar_kanji_repair_queue
            WHERE status = ? AND target_kanji = ? AND choice_signature = ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, STATUS_PENDING)
                bindText(2, targetKanji)
                bindText(3, choiceSignature)
            },
        ) { true } == true

    private fun reviewStatsSince(sinceMillis: Long): RecordsSchedulerModels.ReviewStats =
        session.queryOneOrNull(
            """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count,
                COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count,
                COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count,
                COALESCE(SUM(CASE WHEN rating NOT IN ('again','hard','easy') THEN 1 ELSE 0 END), 0) AS good_count,
                COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count
            FROM review_log
            WHERE reviewed_at >= ?
            """.trimIndent(),
            bind = { bindLong(1, sinceMillis) },
        ) { row ->
            RecordsSchedulerModels.ReviewStats(
                row.long(0).toInt(),
                row.long(1).toInt(),
                row.long(2).toInt(),
                row.long(4).toInt(),
                row.long(3).toInt(),
                row.long(5).toInt(),
                row.long(6).toInt(),
            )
        } ?: RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)

    private fun studiedKanjiSince(sinceMillis: Long): Set<String> =
        session.queryList(
            "SELECT DISTINCT kanji FROM review_log WHERE reviewed_at >= ?",
            bind = { bindLong(1, sinceMillis) },
        ) { row -> row.text(0) }
            .filter(String::isNotEmpty)
            .toSet()

    private fun kanjiReadingUsagesFor(kanji: String): List<KanjiReadingChoicePlanner.Usage> {
        val normalized = TextUtil.normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) return emptyList()
        return session.queryList(
            """
            SELECT reading, expression, note_id, mature, lapses
            FROM kanji_reading_usage
            WHERE kanji = ?
            ORDER BY note_id ASC
            """.trimIndent(),
            bind = { bindText(1, normalized) },
        ) { row ->
            KanjiReadingChoicePlanner.Usage(
                row.textOrEmpty(1),
                row.textOrEmpty(0),
                wordMeaningFor(row.textOrEmpty(1)),
                row.long(2),
                row.long(3) == 1L,
                row.long(4).toInt(),
            )
        }
    }

    private fun kanjiReadingPoolFor(kanji: String): List<KanjiReadingChoicePlanner.PoolReading> {
        val normalized = TextUtil.normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) return emptyList()
        val matureReadings = session.queryList(
            """
            SELECT DISTINCT reading FROM kanji_reading_usage
            WHERE kanji = ? AND mature = 1
            """.trimIndent(),
            bind = { bindText(1, normalized) },
        ) { row -> row.textOrEmpty(0) }.toHashSet()
        return session.queryList(
            """
            SELECT reading, attested FROM kanji_reading_pool
            WHERE kanji = ?
            ORDER BY reading ASC
            """.trimIndent(),
            bind = { bindText(1, normalized) },
        ) { row ->
            val reading = row.textOrEmpty(0)
            val attested = row.long(1) == 1L
            KanjiReadingChoicePlanner.PoolReading(
                reading,
                attested,
                attested && matureReadings.contains(reading),
            )
        }
    }

    private fun readingKanjiCandidatesFor(
        kanji: String,
    ): Map<String, List<ReadingKanjiChoicePlanner.Candidate>> {
        val normalized = TextUtil.normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) return emptyMap()
        val targetReadings = session.queryList(
            "SELECT DISTINCT reading FROM kanji_reading_usage WHERE kanji = ?",
            bind = { bindText(1, normalized) },
        ) { row -> row.textOrEmpty(0) }
        val out = LinkedHashMap<String, List<ReadingKanjiChoicePlanner.Candidate>>()
        for (reading in targetReadings) {
            val candidates = session.queryList(
                """
                SELECT kanji, MAX(mature) FROM kanji_reading_usage
                WHERE reading = ? AND kanji <> ?
                GROUP BY kanji
                """.trimIndent(),
                bind = {
                    bindText(1, reading)
                    bindText(2, normalized)
                },
            ) { row -> ReadingKanjiChoicePlanner.Candidate(row.textOrEmpty(0), row.long(1) == 1L) }
                .filter { it.kanji.isNotEmpty() }
            if (candidates.isNotEmpty()) {
                out[reading] = candidates
            }
        }
        return out
    }

    private fun wordMeaningFor(expression: String): String {
        val expr = expression.trim()
        if (expr.isEmpty()) return ""
        return session.queryOneOrNull(
            "SELECT meaning FROM kanji_examples WHERE expression = ? LIMIT 1",
            bind = { bindText(1, expr) },
        ) { row -> row.textOrEmpty(0) }.orEmpty()
    }

    private fun similarChoiceCard(row: SqlRow): RecordsImportModels.SimilarKanjiChoiceCard {
        val values = NamedSqlRow(row)
        return RecordsImportModels.SimilarKanjiChoiceCard(
            values.text("target_kanji"),
            values.text("primary_meaning"),
            SimilarChoiceCodec.deserializeChoices(values.text("choices")),
            values.text("choice_signature"),
            values.long("due_at"),
            values.long("passed_at"),
            values.long("last_reviewed_at"),
            values.int("correct_count"),
            values.int("wrong_count"),
        )
    }

    private fun writingRepair(row: SqlRow): RecordsImportModels.SimilarKanjiWritingRepair {
        val values = NamedSqlRow(row)
        return RecordsImportModels.SimilarKanjiWritingRepair(
            values.long("id"),
            values.text("target_kanji"),
            values.text("repair_kanji"),
            values.text("choice_signature"),
            values.text("wrong_selection"),
            values.text("prompt_meaning"),
            values.text("status"),
            values.long("due_at"),
            values.text("active_token"),
            values.int("attempts"),
            values.long("created_at"),
            values.long("updated_at"),
            values.long("completed_at"),
        )
    }

    /** Immutable token identity used by both queue and recovery reads. */
    data class TokenLookup(
        val token: String,
        val kanji: String,
        val taskType: String,
        val answerSignature: String,
    )

    internal companion object {
        const val RECENT_REVIEW_WINDOW_MILLIS = 7L * 86_400_000L
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETE = "complete"
    }
}
