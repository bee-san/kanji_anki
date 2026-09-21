package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.data.CollectionMirrorIdentityEvidence
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

/**
 * Driver-neutral read side of the sync repository: the stored-state summary the
 * sync engine needs before deciding to publish, plus the raw provider-mirror
 * reads the repaired write-back proposal builds on. Reuses [SqlHomeData] for
 * study items and latest-sync reads so those stay identical to Home/Study.
 */
internal class SqlSyncData(
    private val session: SqlSession,
) {
    private val home = SqlHomeData(session)

    fun loadStoredState(): StoredSyncState {
        val hasMirror = tableHasRows("source_notes") || tableHasRows("source_cards")
        return StoredSyncState(
            hasCollectionMirror = hasMirror,
            suspendedImports = suspendedImports(),
            unrestoredSuspendedArchiveCardIds = unrestoredSuspendedArchiveCardIds(),
            studyItems = home.studyItemsForKanji(allStudyItemKanji()),
            latestSuccessfulSyncAtMillis = home.latestSuccessfulSyncAtMillis(),
            mirrorIdentityEvidence = mirrorIdentityEvidence(),
            databaseIsEmpty = isEmptyProfile(),
        )
    }

    private fun allStudyItemKanji(): List<String> =
        session.queryList("SELECT DISTINCT kanji FROM study_items") { row -> row.text(0) }

    private fun suspendedImports(): List<RecordsImportModels.SuspendedImport> {
        val bySources = LinkedHashMap<String, MutableList<RecordsImportModels.SuspendedSource>>()
        val headers = session.queryList(
            "SELECT * FROM suspended_imports ORDER BY jiten_rank ASC, kanji ASC",
        ) { row -> NamedSqlRow(row).let(::SuspendedImportHeader) }
        session.queryList(
            "SELECT * FROM suspended_sources ORDER BY kanji ASC, card_id ASC",
        ) { row ->
            val values = NamedSqlRow(row)
            val kanji = values.text("kanji")
            RecordsImportModels.SuspendedSource(
                kanji,
                values.long("card_id"),
                values.long("note_id"),
                values.text("expression"),
                values.text("reading"),
                values.text("meaning"),
                values.text("sentence"),
            ).also { source ->
                bySources.getOrPut(kanji) { ArrayList() }.add(source)
            }
        }
        return headers.map { header ->
            RecordsImportModels.SuspendedImport(
                header.kanji,
                header.jitenRank,
                header.rankKnown,
                header.cutoffUsed,
                bySources[header.kanji].orEmpty(),
            )
        }
    }

    private fun unrestoredSuspendedArchiveCardIds(): Set<Long> =
        session.queryList(
            "SELECT card_id FROM suspended_archive WHERE restored_at IS NULL ORDER BY card_id ASC",
        ) { row -> row.long(0) }.toSet()

    private fun mirrorIdentityEvidence(): CollectionMirrorIdentityEvidence =
        CollectionMirrorIdentityEvidence(
            stableNoteIds = stableIdSample("source_notes", "note_id"),
            stableCardIds = stableIdSample("source_cards", "card_id"),
        )

    private fun stableIdSample(table: String, column: String): List<Long> =
        session.queryList(
            "SELECT $column FROM $table ORDER BY ($column < 0) ASC, $column ASC LIMIT 64",
        ) { row -> row.long(0) }

    private fun isEmptyProfile(): Boolean {
        val hasSuccessfulSync = session.queryOneOrNull(
            "SELECT 1 FROM sync_runs WHERE status = ? LIMIT 1",
            bind = { bindText(1, STATUS_SUCCESS) },
        ) { true } == true
        return !hasSuccessfulSync && PROFILE_STATE_TABLES.none(::tableHasRows)
    }

    private fun tableHasRows(table: String): Boolean =
        session.queryOneOrNull("SELECT 1 FROM $table LIMIT 1") { true } == true

    fun pendingRepairedHandoffKanji(): List<String> =
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

    private data class SuspendedImportHeader(
        val kanji: String,
        val jitenRank: Int?,
        val rankKnown: Boolean,
        val cutoffUsed: Int,
    ) {
        constructor(row: NamedSqlRow) : this(
            kanji = row.text("kanji"),
            jitenRank = row.nullableInt("jiten_rank"),
            rankKnown = row.int("rank_known") == 1,
            cutoffUsed = row.int("cutoff_used"),
        )
    }

    internal companion object {
        const val STATUS_SUCCESS = "success"
        const val REPAIRED_HANDOFF_SETTING_KEY = "repaired_handoff_kanji"

        private val PROFILE_STATE_TABLES = listOf(
            "source_notes", "source_cards", "suspended_archive", "suspended_imports",
            "suspended_sources", "import_rule_audits", "import_decisions", "dashboard_rows",
            "kanji_examples", "study_items", "learning_repeats", "review_log",
            "kanji_inventory", "kanji_mnemonic_notes", "anki_kanji_inventory",
            "anki_kanji_inventory_scans", "manual_kanji_sources", "missing_kanji_exports",
            "local_kanji_suspensions", "similar_kanji_choice_state",
            "similar_kanji_repair_queue", "similar_kanji_review_log", "kanji_reading_usage",
            "kanji_reading_pool", "study_task_log", "kanji_timeline_events",
            "sync_card_snapshots", "sync_note_snapshots", "sync_kanji_snapshots",
        )
    }
}
