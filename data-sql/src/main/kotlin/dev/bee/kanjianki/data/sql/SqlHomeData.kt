package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.KanjiInventorySearchQuery
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StringListJsonCodec
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.data.HomeKanjiDetailSnapshot
import dev.bee.kanjianki.data.HomeNewCardSortPreviewSnapshot
import dev.bee.kanjianki.data.HomeSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.SyncStatusSnapshot

internal class SqlHomeData(
    private val session: SqlSession,
) {
    fun loadHome(nowMillis: Long): HomeSnapshot {
        val activeRows = activeDashboardRows()
        return HomeSnapshot(
            activeRows = activeRows,
            studyItems = studyItemsForKanji(activeRows.map { it.kanji }),
            locallySuspendedKanji = locallySuspendedKanji(),
            latestSync = latestSync(),
            latestSuccessfulSyncAtMillis = latestSuccessfulSyncAtMillis(),
            studyStreak = studyStreak(nowMillis),
            dueLegacyWritingRepairs = dueWritingRepairs(nowMillis),
            repairedHandoffKanji = repairedHandoffKanji(),
            consecutiveFailedSyncs = consecutiveFailedSyncCount(),
        )
    }

    fun loadKanjiDetail(
        kanji: String,
        nowMillis: Long,
    ): HomeKanjiDetailSnapshot =
        HomeKanjiDetailSnapshot(
            kanji = kanji,
            dashboardRow = dashboardRowForKanji(kanji),
            inventoryItem = inventoryItemForKanji(kanji),
            timeline = timelineForKanji(kanji),
            mnemonic = mnemonic(kanji),
            similarPairs = similarPairsForKanji(kanji),
            wrongPickCounts = wrongPickCounts(nowMillis),
            inventory = searchInventory("", onlySimilarKanji = false, InventoryScope.ALL),
            locallySuspended = isLocallySuspended(kanji),
        )

    fun loadGameData(): HomeGameDataSnapshot =
        HomeGameDataSnapshot(
            activeRows = activeDashboardRows(),
            inventory = searchInventory("", onlySimilarKanji = false, InventoryScope.ALL),
            similarPairs = allSimilarPairs(),
        )

    fun loadNewCardSortPreviewData(sourceVersion: Long): HomeNewCardSortPreviewSnapshot =
        HomeNewCardSortPreviewSnapshot(
            activeRows = activeDashboardRows(),
            similarPairs = allSimilarPairs(),
            sourceVersion = sourceVersion,
        )

    fun searchInventory(
        query: String,
        onlySimilarKanji: Boolean,
        scope: InventoryScope,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        val terms = KanjiInventorySearchQuery.parse(query).terms()
        val clauses = ArrayList<String>()
        val arguments = ArrayList<String>()
        terms.forEach { term ->
            clauses += "inventory.search_text LIKE ? ESCAPE '\\'"
            arguments += "%${escapeLikeTerm(term)}%"
        }
        if (onlySimilarKanji) {
            clauses +=
                """
                EXISTS (
                    SELECT 1
                    FROM similar_kanji_pairs pair
                    WHERE pair.kanji_a = inventory.kanji
                       OR pair.kanji_b = inventory.kanji
                )
                """.trimIndent()
        }
        when (scope) {
            InventoryScope.ALL -> Unit
            InventoryScope.STUDY_QUEUE -> {
                clauses +=
                    """
                    EXISTS (
                        SELECT 1 FROM study_items item
                        WHERE item.kanji = inventory.kanji AND item.state <> ?
                    )
                    """.trimIndent()
                arguments += StudyLadderRules.STATE_RETIRED
                clauses +=
                    """
                    NOT EXISTS (
                        SELECT 1 FROM local_kanji_suspensions suspension
                        WHERE suspension.kanji = inventory.kanji
                    )
                    """.trimIndent()
            }
            InventoryScope.STUDY_QUEUE_WITH_SUSPENDED -> {
                clauses +=
                    """
                    (
                        EXISTS (
                            SELECT 1 FROM study_items item
                            WHERE item.kanji = inventory.kanji AND item.state <> ?
                        )
                        OR EXISTS (
                            SELECT 1 FROM local_kanji_suspensions suspension
                            WHERE suspension.kanji = inventory.kanji
                        )
                    )
                    """.trimIndent()
                arguments += StudyLadderRules.STATE_RETIRED
            }
        }
        val where = clauses.takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = " AND ", prefix = "WHERE ")
            .orEmpty()
        val matches = session.queryList(
            """
            SELECT inventory.*,
                   EXISTS (
                       SELECT 1 FROM local_kanji_suspensions suspension
                       WHERE suspension.kanji = inventory.kanji
                   ) AS locally_suspended
            FROM kanji_inventory inventory
            $where
            ORDER BY inventory.kanji ASC
            LIMIT $INVENTORY_ROW_LIMIT
            """.trimIndent(),
            bind = {
                arguments.forEachIndexed { index, value -> bindText(index + 1, value) }
            },
            map = ::inventoryItem,
        )
        return rankInventorySearch(matches, terms, onlySimilarKanji, scope)
    }

    fun locallySuspendedKanji(): Set<String> =
        session.queryList(
            "SELECT kanji FROM local_kanji_suspensions ORDER BY kanji",
        ) { row -> row.text(0) }.toSet()

    fun isLocallySuspended(kanji: String): Boolean =
        session.queryOneOrNull(
            "SELECT 1 FROM local_kanji_suspensions WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { true } == true

    fun mnemonic(kanji: String): String {
        val normalized = TextUtil.normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) return ""
        return session.queryOneOrNull(
            "SELECT note FROM kanji_mnemonic_notes WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, normalized) },
        ) { row -> row.textOrEmpty(0) }.orEmpty()
    }

    internal fun activeDashboardRows(): List<RecordsImportModels.DashboardRow> =
        activeDashboardRows(admittedOnly = true)

    /**
     * The scheduler-facing dashboard. [admittedOnly] mirrors the legacy split:
     * Home shows only admitted manual sources (`true`), while the Study queue
     * surfaces every active manual source candidate (`false`).
     */
    internal fun activeDashboardRows(
        admittedOnly: Boolean,
    ): List<RecordsImportModels.DashboardRow> {
        val suspended = locallySuspendedKanji()
        val providerRows = dashboardRows(excludeLocallySuspended = suspended.isNotEmpty())
        val sources = manualSources(admittedOnly)
            .filterNot { it.candidate.literal in suspended }
        if (sources.isEmpty()) return providerRows

        val mergedProviders = LinkedHashMap<String, RecordsImportModels.DashboardRow>()
        providerRows.forEach { mergedProviders[it.kanji] = it }
        providerRowsForManualSources()
            .filterNot { it.kanji in suspended }
            .forEach { mergedProviders[it.kanji] = it }
        return ManualKanjiAdmissionPolicy.mergeRows(
            providerRows = mergedProviders.values.toList(),
            candidates = sources.map(ManualSource::candidate),
            reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
        )
    }

    private fun dashboardRows(
        excludeLocallySuspended: Boolean,
    ): List<RecordsImportModels.DashboardRow> {
        val where =
            if (excludeLocallySuspended) {
                """
                WHERE dashboard.kanji NOT IN (
                    SELECT kanji FROM local_kanji_suspensions
                )
                """.trimIndent()
            } else {
                ""
            }
        val headers = session.queryList(
            """
            SELECT dashboard.*
            FROM dashboard_rows dashboard
            $where
            ORDER BY weakness_score DESC, suspended_example_count DESC, kanji ASC
            LIMIT $DASHBOARD_ROW_LIMIT
            """.trimIndent(),
        ) { row -> DashboardHeader(NamedSqlRow(row)) }
        return headers.map { header ->
            header.toRow(examplesForKanji(header.kanji))
        }
    }

    private fun providerRowsForManualSources(): List<RecordsImportModels.DashboardRow> =
        session.queryList(
            """
            SELECT dashboard.*
            FROM dashboard_rows dashboard
            INNER JOIN manual_kanji_sources manual
              ON manual.literal = dashboard.kanji
             AND manual.active = 1
            WHERE EXISTS (
                SELECT 1
                FROM study_items item
                WHERE item.kanji = manual.literal
                  AND item.state <> ?
            )
            ORDER BY dashboard.kanji
            """.trimIndent(),
            bind = { bindText(1, StudyLadderRules.STATE_RETIRED) },
        ) { row -> DashboardHeader(NamedSqlRow(row)) }
            .map { header -> header.toRow(examplesForKanji(header.kanji)) }

    private fun dashboardRowForKanji(kanji: String): RecordsImportModels.DashboardRow? {
        val provider = session.queryOneOrNull(
            "SELECT * FROM dashboard_rows WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row ->
            val header = DashboardHeader(NamedSqlRow(row))
            header.toRow(examplesForKanji(header.kanji))
        }
        val manual = manualSource(kanji) ?: return provider
        return ManualKanjiAdmissionPolicy.mergeRows(
            providerRows = listOfNotNull(provider),
            candidates = listOf(manual.candidate),
            reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
        ).firstOrNull()
    }

    private fun inventoryItemForKanji(kanji: String): RecordsImportModels.KanjiInventoryItem? =
        session.queryOneOrNull(
            """
            SELECT inventory.*,
                   EXISTS (
                       SELECT 1 FROM local_kanji_suspensions suspension
                       WHERE suspension.kanji = inventory.kanji
                   ) AS locally_suspended
            FROM kanji_inventory inventory
            WHERE inventory.kanji = ?
            LIMIT 1
            """.trimIndent(),
            bind = { bindText(1, kanji) },
            map = ::inventoryItem,
        )

    private fun examplesForKanji(kanji: String): List<RecordsImportModels.Example> =
        session.queryList(
            """
            SELECT *
            FROM kanji_examples
            WHERE kanji = ?
            ORDER BY source_type DESC, id ASC
            LIMIT $EXAMPLES_PER_KANJI
            """.trimIndent(),
            bind = { bindText(1, kanji) },
            map = ::example,
        )

    private fun timelineForKanji(kanji: String): RecordsStudyModels.KanjiRecoveryTimeline {
        val events = session.queryList(
            """
            SELECT *
            FROM kanji_timeline_events
            WHERE kanji = ?
              AND (
                  sync_id IS NULL
                  OR sync_id IN (SELECT id FROM sync_runs WHERE status = ?)
              )
            ORDER BY occurred_at DESC, id DESC
            LIMIT 50
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindText(2, STATUS_SUCCESS)
            },
            map = ::timelineEvent,
        ).asReversed()
        return RecordsStudyModels.KanjiRecoveryTimeline(
            inventoryItemForKanji(kanji),
            dashboardRowForKanji(kanji),
            timelineStudyItem(kanji),
            events,
        )
    }

    private fun timelineStudyItem(kanji: String): RecordsStudyModels.StudyItem? {
        val item = session.queryOneOrNull(
            """
            SELECT *
            FROM study_items
            WHERE kanji = ?
            ORDER BY state = 'retired' ASC, due_at ASC
            LIMIT 1
            """.trimIndent(),
            bind = { bindText(1, kanji) },
            map = SqlStudyItemMapper::read,
        ) ?: return null
        val hasSimilar = session.queryOneOrNull(
            """
            SELECT 1 FROM similar_kanji_pairs
            WHERE kanji_a = ? OR kanji_b = ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, kanji)
                bindText(2, kanji)
            },
        ) { true } == true
        return if (item.hasSimilarKanji == hasSimilar) item else item.withHasSimilarKanji(hasSimilar)
    }

    internal fun studyItemsForKanji(
        kanji: Collection<String>,
    ): List<RecordsStudyModels.StudyItem> {
        val keys = kanji.filter(String::isNotBlank).distinct().sorted()
        if (keys.isEmpty()) return emptyList()
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        keys.chunked(SQLITE_BIND_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            items += session.queryList(
                """
                SELECT *
                FROM study_items
                WHERE kanji IN ($placeholders)
                ORDER BY due_at ASC
                """.trimIndent(),
                bind = {
                    chunk.forEachIndexed { index, value -> bindText(index + 1, value) }
                },
                map = SqlStudyItemMapper::read,
            )
        }
        items.sortWith(compareBy<RecordsStudyModels.StudyItem> { it.dueAtMillis }.thenBy { it.kanji })
        return annotateConditionalRungs(items)
    }

    internal fun annotateConditionalRungs(
        items: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        if (items.isEmpty()) return items
        val requested = items.mapTo(LinkedHashSet()) { it.kanji }
        val similar = stringSet(
            """
            SELECT kanji_a
            FROM similar_kanji_pairs
            WHERE kanji_a IN (SELECT kanji FROM kanji_inventory)
              AND kanji_b IN (SELECT kanji FROM kanji_inventory)
            UNION
            SELECT kanji_b
            FROM similar_kanji_pairs
            WHERE kanji_a IN (SELECT kanji FROM kanji_inventory)
              AND kanji_b IN (SELECT kanji FROM kanji_inventory)
            """.trimIndent(),
        ).intersect(requested)
        val kanjiReading = stringSet(
            """
            SELECT usage.kanji
            FROM kanji_reading_usage usage
            JOIN (
                SELECT kanji, COUNT(*) AS reading_count
                FROM kanji_reading_pool
                GROUP BY kanji
                HAVING reading_count >= 2
            ) pool ON usage.kanji = pool.kanji
            GROUP BY usage.kanji
            """.trimIndent(),
        ).intersect(requested)
        val readingKanji = stringSet(
            """
            SELECT DISTINCT kanji
            FROM kanji_reading_usage
            WHERE reading IN (
                SELECT reading
                FROM (
                    SELECT reading, COUNT(DISTINCT kanji) AS kanji_count
                    FROM kanji_reading_usage
                    GROUP BY reading
                )
                WHERE kanji_count >= 3
            )
            """.trimIndent(),
        ).intersect(requested)
        val sentenceReading = stringSet(
            """
            SELECT DISTINCT kanji
            FROM kanji_examples
            WHERE sentence IS NOT NULL
              AND TRIM(sentence) <> ''
              AND reading IS NOT NULL
              AND TRIM(reading) <> ''
            """.trimIndent(),
        ).intersect(requested)
        return items.map { item ->
            item.copyBuilder()
                .hasSimilarKanji(item.kanji in similar)
                .hasKanjiReading(item.kanji in kanjiReading)
                .hasReadingKanji(item.kanji in readingKanji)
                .hasSentenceReading(item.kanji in sentenceReading)
                .build()
        }
    }

    private fun latestSync(): SyncStatusSnapshot? =
        session.queryOneOrNull(
            "SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1",
        ) { row ->
            val values = NamedSqlRow(row)
            SyncStatusSnapshot(
                status = values.text("status"),
                activeNotes = values.int("active_notes_count"),
                activeCards = values.int("active_cards_count"),
                suspendedCards = values.int("suspended_cards_archived_count"),
                importedKanji = values.int("suspended_kanji_imported_count"),
                finishedAtMillis = values.long("finished_at"),
                errorMessage = values.text("error_message"),
                removalMessage = values.text("removal_message"),
            )
        }

    internal fun latestSuccessfulSyncAtMillis(): Long? =
        session.queryOneOrNull(
            """
            SELECT finished_at
            FROM sync_runs
            WHERE status = ? AND finished_at IS NOT NULL
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent(),
            bind = { bindText(1, STATUS_SUCCESS) },
        ) { row -> row.long(0) }

    internal fun consecutiveFailedSyncCount(): Int =
        session.queryOneOrNull(
            """
            SELECT COUNT(*)
            FROM sync_runs
            WHERE status <> ?
              AND id > COALESCE((
                  SELECT MAX(id) FROM sync_runs WHERE status = ?
              ), 0)
            """.trimIndent(),
            bind = {
                bindText(1, STATUS_SUCCESS)
                bindText(2, STATUS_SUCCESS)
            },
        ) { row -> row.long(0).coerceAtLeast(0).toInt() } ?: 0

    internal fun studyStreak(nowMillis: Long): StudyStreakSnapshot {
        val today = LocalDayPolicy.localDayStart(nowMillis)
        val rows = session.queryList(
            """
            SELECT review_day_start, COUNT(*) AS review_count, MAX(reviewed_at) AS last_reviewed_at
            FROM review_log
            WHERE review_day_start > 0
            GROUP BY review_day_start
            ORDER BY review_day_start DESC
            """.trimIndent(),
        ) { row ->
            StudyDay(
                day = row.long(0),
                reviews = row.long(1).toInt(),
                lastReviewedAt = row.long(2),
            )
        }
        val summary = StudyStreakPolicy.summarize(
            rows.map(StudyDay::day),
            today,
            rows.firstOrNull { it.day == today }?.reviews ?: 0,
            rows.firstOrNull()?.lastReviewedAt ?: 0L,
        )
        return StudyStreakSnapshot(
            currentDays = summary.currentDays,
            bestDays = summary.bestDays,
            studiedToday = summary.studiedToday,
            reviewsToday = summary.reviewsToday,
            lastStudyAtMillis = summary.lastStudyAtMillis,
        )
    }

    internal fun dueWritingRepairs(
        nowMillis: Long,
    ): List<RecordsImportModels.SimilarKanjiWritingRepair> =
        session.queryList(
            """
            SELECT *
            FROM similar_kanji_repair_queue
            WHERE status = ? AND due_at <= ?
            ORDER BY created_at ASC, id ASC
            """.trimIndent(),
            bind = {
                bindText(1, STATUS_PENDING)
                bindLong(2, nowMillis)
            },
            map = ::writingRepair,
        )

    private fun repairedHandoffKanji(): List<String> =
        session.queryOneOrNull(
            "SELECT value FROM settings WHERE key = ? LIMIT 1",
            bind = { bindText(1, REPAIRED_HANDOFF_SETTING_KEY) },
        ) { row -> row.textOrEmpty(0) }
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .toList()

    internal fun similarPairsForKanji(
        kanji: String,
    ): List<RecordsImportModels.SimilarKanjiPair> {
        val normalized = TextUtil.normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) return emptyList()
        return session.queryList(
            """
            SELECT *
            FROM similar_kanji_pairs
            WHERE kanji_a = ? OR kanji_b = ?
            ORDER BY kanji_a ASC, kanji_b ASC, source ASC
            """.trimIndent(),
            bind = {
                bindText(1, normalized)
                bindText(2, normalized)
            },
            map = ::similarPair,
        )
    }

    private fun allSimilarPairs(): List<RecordsImportModels.SimilarKanjiPair> =
        session.queryList(
            """
            SELECT *
            FROM similar_kanji_pairs
            ORDER BY kanji_a ASC, kanji_b ASC, source ASC
            """.trimIndent(),
            map = ::similarPair,
        )

    internal fun wrongPickCounts(nowMillis: Long): Map<String, Map<String, Int>> {
        val counts = LinkedHashMap<String, MutableMap<String, Int>>()
        session.queryList(
            """
            SELECT target_kanji, selected_kanji, COUNT(*)
            FROM similar_kanji_review_log
            WHERE correct = 0
              AND reviewed_at >= ?
              AND selected_kanji <> ''
              AND selected_kanji <> target_kanji
            GROUP BY target_kanji, selected_kanji
            ORDER BY target_kanji, selected_kanji
            """.trimIndent(),
            bind = { bindLong(1, ConfusionPairMiner.windowStartMillis(nowMillis)) },
        ) { row ->
            Triple(row.text(0), row.text(1), row.long(2).toInt())
        }.forEach { (target, selected, count) ->
            if (target.isNotEmpty() && selected.isNotEmpty()) {
                counts.getOrPut(target, ::LinkedHashMap)[selected] = count
            }
        }
        return counts
    }

    private fun manualSources(admittedOnly: Boolean): List<ManualSource> {
        val admitted =
            if (admittedOnly) {
                """
                AND EXISTS (
                    SELECT 1 FROM study_items item
                    WHERE item.kanji = manual.literal AND item.state <> ?
                )
                """.trimIndent()
            } else {
                ""
            }
        return session.queryList(
            """
            SELECT manual.*
            FROM manual_kanji_sources manual
            WHERE manual.active = 1
            $admitted
            ORDER BY manual.jiten_rank IS NULL, manual.jiten_rank, manual.literal
            """.trimIndent(),
            bind = {
                if (admittedOnly) bindText(1, StudyLadderRules.STATE_RETIRED)
            },
            map = ::manualSource,
        )
    }

    private fun manualSource(literal: String): ManualSource? {
        val normalized = TextUtil.normalizeSingleKanji(literal)
        if (normalized.isEmpty()) return null
        return session.queryOneOrNull(
            """
            SELECT *
            FROM manual_kanji_sources
            WHERE literal = ? AND active = 1
            LIMIT 1
            """.trimIndent(),
            bind = { bindText(1, normalized) },
            map = ::manualSource,
        )
    }

    private fun rankInventorySearch(
        matches: List<RecordsImportModels.KanjiInventoryItem>,
        terms: List<String>,
        onlySimilarKanji: Boolean,
        scope: InventoryScope,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        if (terms.isEmpty()) return matches
        val queryGlyphs = terms.flatMapTo(LinkedHashSet(), TextUtil::extractKanji)
        if (queryGlyphs.isEmpty()) return matches
        val withExact = matches.toMutableList()
        val exactGlyph = if (terms.size == 1) TextUtil.normalizeSingleKanji(terms[0]) else ""
        if (
            !onlySimilarKanji &&
            exactGlyph.isNotEmpty() &&
            withExact.none { it.kanji == exactGlyph }
        ) {
            inventoryItemForKanji(exactGlyph)
                ?.takeIf { inventoryItemMatchesScope(it, scope) }
                ?.let(withExact::add)
        }
        return withExact.sortedBy { if (it.kanji in queryGlyphs) 0 else 1 }
    }

    private fun inventoryItemMatchesScope(
        item: RecordsImportModels.KanjiInventoryItem,
        scope: InventoryScope,
    ): Boolean {
        if (scope == InventoryScope.ALL) return true
        if (scope == InventoryScope.STUDY_QUEUE && item.suspended) return false
        if (scope == InventoryScope.STUDY_QUEUE_WITH_SUSPENDED && item.suspended) return true
        return session.queryOneOrNull(
            """
            SELECT 1
            FROM study_items
            WHERE kanji = ? AND state <> ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, item.kanji)
                bindText(2, StudyLadderRules.STATE_RETIRED)
            },
        ) { true } == true
    }

    private fun stringSet(sql: String): Set<String> =
        session.queryList(sql) { row -> row.textOrEmpty(0) }
            .filter(String::isNotEmpty)
            .toSet()

    private fun escapeLikeTerm(term: String): String =
        term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private data class DashboardHeader(
        val kanji: String,
        val jitenRank: Int?,
        val primaryMeaning: String,
        val reading: String,
        val browserSearch: String,
        val weaknessScore: Int,
        val reasonCode: String,
        val reasonText: String,
        val activeExampleCount: Int,
        val suspendedExampleCount: Int,
        val matureSupportCount: Int,
    ) {
        constructor(row: NamedSqlRow) : this(
            kanji = row.text("kanji"),
            jitenRank = row.nullableInt("jiten_rank"),
            primaryMeaning = row.text("primary_meaning"),
            reading = row.text("reading"),
            browserSearch = row.text("browser_search"),
            weaknessScore = row.int("weakness_score"),
            reasonCode = row.text("reason_code"),
            reasonText = row.text("reason_text"),
            activeExampleCount = row.int("active_example_count"),
            suspendedExampleCount = row.int("suspended_example_count"),
            matureSupportCount = row.int("mature_support_count"),
        )

        fun toRow(
            examples: List<RecordsImportModels.Example>,
        ): RecordsImportModels.DashboardRow =
            RecordsImportModels.DashboardRow(
                kanji,
                jitenRank,
                primaryMeaning,
                reading,
                browserSearch,
                weaknessScore,
                reasonCode,
                reasonText,
                activeExampleCount,
                suspendedExampleCount,
                matureSupportCount,
                examples,
            )
    }

    private data class ManualSource(
        val candidate: MissingKanjiCandidate,
    )

    private data class StudyDay(
        val day: Long,
        val reviews: Int,
        val lastReviewedAt: Long,
    )

    enum class InventoryScope {
        ALL,
        STUDY_QUEUE,
        STUDY_QUEUE_WITH_SUSPENDED,
    }

    private companion object {
        const val DASHBOARD_ROW_LIMIT = 120
        const val INVENTORY_ROW_LIMIT = 300
        const val EXAMPLES_PER_KANJI = 8
        const val SQLITE_BIND_CHUNK_SIZE = 900
        const val STATUS_SUCCESS = "success"
        const val STATUS_PENDING = "pending"
        const val REPAIRED_HANDOFF_SETTING_KEY = "repaired_handoff_kanji"

        fun inventoryItem(row: SqlRow): RecordsImportModels.KanjiInventoryItem {
            val values = NamedSqlRow(row)
            return RecordsImportModels.KanjiInventoryItem(
                values.text("kanji"),
                values.text("primary_meaning"),
                values.text("readings"),
                values.text("browser_search"),
                values.int("source_count"),
                values.int("example_count"),
                values.int("locally_suspended") == 1,
                values.long("last_seen_at"),
            )
        }

        fun example(row: SqlRow): RecordsImportModels.Example {
            val values = NamedSqlRow(row)
            return RecordsImportModels.Example(
                values.text("source_type"),
                values.long("card_id"),
                values.long("note_id"),
                values.text("expression"),
                values.text("reading"),
                values.text("meaning"),
                values.text("sentence"),
                values.int("mature") == 1,
                values.int("lapses"),
                values.int("interval_days"),
                values.int("reps"),
                values.nullableDouble("fsrs_stability"),
                values.nullableDouble("fsrs_difficulty"),
                values.nullableDouble("fsrs_retrievability"),
            )
        }

        fun similarPair(row: SqlRow): RecordsImportModels.SimilarKanjiPair {
            val values = NamedSqlRow(row)
            return RecordsImportModels.SimilarKanjiPair(
                values.text("kanji_a"),
                values.text("kanji_b"),
                values.text("source"),
                values.long("first_seen_at"),
                values.long("last_seen_at"),
            )
        }

        fun writingRepair(row: SqlRow): RecordsImportModels.SimilarKanjiWritingRepair {
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

        fun timelineEvent(row: SqlRow): RecordsImportModels.KanjiTimelineEvent {
            val values = NamedSqlRow(row)
            return RecordsImportModels.KanjiTimelineEvent(
                values.long("id"),
                values.text("kanji"),
                values.long("occurred_at"),
                values.text("event_type"),
                values.text("title"),
                values.text("detail"),
                values.text("source_expression"),
                values.text("source_reading"),
                values.text("rating"),
                values.int("writing_required") == 1,
                values.int("writing_passed") == 1,
                values.int("manual_override") == 1,
                values.nullableInt("weakness_score"),
                values.nullableInt("mature_support_count"),
                values.nullableLong("sync_id"),
                values.text("dedupe_key"),
            )
        }

        fun manualSource(row: SqlRow): ManualSource {
            val values = NamedSqlRow(row)
            return ManualSource(
                MissingKanjiCandidate(
                    literal = values.text("literal"),
                    meanings = StringListJsonCodec.decode(values.text("meanings_json")),
                    onReadings = StringListJsonCodec.decode(values.text("on_readings_json")),
                    kunReadings = StringListJsonCodec.decode(values.text("kun_readings_json")),
                    jitenRank = values.nullableInt("jiten_rank"),
                ),
            )
        }
    }
}
