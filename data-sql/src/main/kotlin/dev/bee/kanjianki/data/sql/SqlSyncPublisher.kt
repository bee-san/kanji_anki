package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.KanjiReadingAligner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys
import dev.bee.kanjianki.core.SyncSnapshotRetentionPolicy
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.syncdomain.ImportAuditBuilder

/**
 * Mirror staging for one atomic sync publication, operating entirely on a
 * [SqlTransactionScope]. Ported from the legacy `saveSuccessfulSync` chain: it
 * writes the sync_run, historical snapshots, provider mirror, suspended
 * imports, import audit, dashboard rows, inventory, similar pairs/choice
 * states, and reading-usage tables, then finalizes the pending sync's study
 * queue. Every write stays inside the caller's single transaction.
 */
internal class SqlSyncPublisher(
    private val scope: SqlTransactionScope,
) {
    /** Inserts the initial (pending) sync_run row and returns its id. */
    fun insertSyncRun(
        startedAt: Long,
        finishedAt: Long,
        status: String,
        activeNotesCount: Int,
        activeCardsCount: Int,
        archivedSuspendedCardCount: Int,
        importedSuspendedKanjiCount: Int,
        deletedNotes: Int,
        deletedCards: Int,
        removalMessage: String,
    ): Long {
        scope.insertRow(
            "sync_runs",
            "ABORT",
            linkedMapOf(
                "started_at" to startedAt,
                "finished_at" to finishedAt,
                "status" to status,
                "active_notes_count" to activeNotesCount,
                "active_cards_count" to activeCardsCount,
                "suspended_cards_archived_count" to archivedSuspendedCardCount,
                "suspended_kanji_imported_count" to importedSuspendedKanjiCount,
                "deleted_notes_count" to deletedNotes,
                "deleted_cards_count" to deletedCards,
                "error_code" to null,
                "error_message" to null,
                "removal_message" to removalMessage,
            ),
        )
        return scope.lastInsertRowId()
    }

    fun countExistingMissing(table: String, idColumn: String, keepIds: Set<Long>): Int {
        if (keepIds.isEmpty()) {
            return scope.queryOneOrNull("SELECT COUNT(*) FROM $table") { row -> row.long(0).toInt() } ?: 0
        }
        val existing = scope.queryList("SELECT $idColumn FROM $table") { row -> row.long(0) }.toSet()
        return existing.count { it !in keepIds }
    }

    fun rowSnapshots(): Map<String, RowSnapshot> {
        val out = LinkedHashMap<String, RowSnapshot>()
        scope.queryList(
            "SELECT kanji, weakness_score, mature_support_count FROM dashboard_rows ORDER BY kanji ASC",
        ) { row ->
            val values = NamedSqlRow(row)
            RowSnapshot(values.text("kanji"), values.int("weakness_score"), values.int("mature_support_count"))
        }.forEach { out[it.kanji] = it }
        return out
    }

    fun studyStateSnapshots(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        scope.queryList(
            "SELECT kanji, state FROM study_items ORDER BY scheduler_revision ASC, created_at DESC",
        ) { row -> NamedSqlRow(row).let { it.text("kanji") to it.text("state") } }
            .forEach { (kanji, state) -> out[kanji] = state }
        return out
    }

    fun clearMirrorTables() {
        listOf("source_notes", "source_cards").forEach { table ->
            scope.executeBound("DELETE FROM $table")
        }
    }

    fun saveSourceNotes(
        notes: List<RecordsSyncModels.Note>,
        activeNoteIds: Set<Long>,
        settings: RecordsSyncModels.Settings,
        syncId: Long,
    ) {
        for (note in notes) {
            if (note.noteId !in activeNoteIds) continue
            scope.insertRow(
                "source_notes",
                "REPLACE",
                linkedMapOf(
                    "note_id" to note.noteId,
                    "model_name" to note.modelName,
                    "expression" to TextUtil.normalizeJapanese(note.expression(settings)),
                    "reading" to TextUtil.normalizeJapanese(note.reading(settings)),
                    "meaning" to TextUtil.firstMeaningLine(note.meaning(settings)),
                    "sentence" to TextUtil.normalizeJapanese(note.sentence(settings)),
                    "fields_json" to fieldsJson(note.fields),
                    "tags" to note.tags.joinToString(" "),
                    "last_seen_sync_id" to syncId,
                ),
            )
        }
    }

    fun saveSourceCardsAndArchive(
        cards: List<RecordsSyncModels.Card>,
        notesById: Map<Long, RecordsSyncModels.Note>,
        selectedSuspendedCardIds: Set<Long>,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        for (card in cards) {
            val note = notesById[card.noteId] ?: continue
            if (card.suspended) {
                if (card.cardId in selectedSuspendedCardIds) {
                    saveSuspendedArchiveCard(card, note, settings, finishedAt, syncId)
                }
            } else {
                saveSourceCard(card, syncId)
            }
        }
    }

    private fun saveSuspendedArchiveCard(
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        scope.insertRow(
            "suspended_archive",
            "IGNORE",
            linkedMapOf(
                "card_id" to card.cardId,
                "note_id" to card.noteId,
                "deck_name" to card.deckName,
                "model_name" to note.modelName,
                "expression" to TextUtil.normalizeJapanese(note.expression(settings)),
                "reading" to TextUtil.normalizeJapanese(note.reading(settings)),
                "meaning" to TextUtil.firstMeaningLine(note.meaning(settings)),
                "sentence" to TextUtil.normalizeJapanese(note.sentence(settings)),
                "fields_json" to fieldsJson(note.fields),
                "archived_at" to finishedAt,
                "archived_sync_id" to syncId,
            ),
        )
    }

    private fun saveSourceCard(card: RecordsSyncModels.Card, syncId: Long) {
        scope.insertRow(
            "source_cards",
            "REPLACE",
            linkedMapOf(
                "card_id" to card.cardId,
                "note_id" to card.noteId,
                "deck_name" to card.deckName,
                "ord" to card.ord,
                "queue" to card.queue,
                "type" to card.type,
                "due" to card.due,
                "interval_days" to card.intervalDays,
                "reps" to card.reps,
                "lapses" to card.lapses,
                "fsrs_stability" to card.fsrsStability,
                "fsrs_difficulty" to card.fsrsDifficulty,
                "fsrs_retrievability" to card.fsrsRetrievability,
                "last_seen_sync_id" to syncId,
            ),
        )
    }

    fun saveSuspendedImports(
        imports: List<RecordsImportModels.SuspendedImport>,
        finishedAt: Long,
        syncId: Long,
    ) {
        for (imported in imports) {
            scope.insertRow(
                "suspended_imports",
                "REPLACE",
                linkedMapOf(
                    "kanji" to imported.kanji,
                    "jiten_rank" to imported.jitenRank,
                    "rank_known" to imported.rankKnown,
                    "cutoff_used" to imported.cutoffUsed,
                    "first_imported_at" to firstImportedAt(imported.kanji, finishedAt),
                    "last_seen_sync_id" to syncId,
                ),
            )
            for (source in imported.sources) {
                scope.insertRow(
                    "suspended_sources",
                    "REPLACE",
                    linkedMapOf(
                        "kanji" to imported.kanji,
                        "card_id" to source.cardId,
                        "note_id" to source.noteId,
                        "expression" to source.expression,
                        "reading" to source.reading,
                        "meaning" to source.meaning,
                        "sentence" to source.sentence,
                        "sync_id" to syncId,
                    ),
                )
            }
        }
    }

    private fun firstImportedAt(kanji: String, fallback: Long): Long =
        scope.queryOneOrNull(
            "SELECT first_imported_at FROM suspended_imports WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row -> row.long(0) } ?: fallback

    fun saveImportAudit(
        imports: List<RecordsImportModels.SuspendedImport>,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        val snapshot = importSettings(settings)
        val audit = ImportAuditBuilder.ruleAudit(snapshot)
        scope.insertRow(
            "import_rule_audits",
            "REPLACE",
            linkedMapOf(
                "sync_id" to syncId,
                "created_at" to finishedAt,
                "model_name" to settings.modelName,
                "enabled_sources" to audit.enabledSources().joinToString(" "),
                "rank_min" to settings.suspendedRankMin,
                "rank_max" to settings.suspendedRankMax,
                "min_matching_cards" to settings.importMinMatchingCardsPerKanji,
                "import_tags" to settings.importTagsText(),
                "weak_fsrs_difficulty" to settings.importWeakFsrsDifficultyThreshold,
                "weak_lapses" to settings.importWeakLapsesThreshold,
                "browser_query" to ImportAuditBuilder.browserQueryAuditValue(snapshot),
                "settings_json" to audit.settingsJson(),
            ),
        )
        for (imported in imports) {
            val decision = ImportAuditBuilder.decision(importCandidate(imported), snapshot)
            scope.insertRow(
                "import_decisions",
                "REPLACE",
                linkedMapOf(
                    "sync_id" to syncId,
                    "kanji" to imported.kanji,
                    "decision" to "imported",
                    "reason_code" to decision.reasonCode(),
                    "reason_text" to decision.reasonText(),
                    "jiten_rank" to imported.jitenRank,
                    "rank_known" to imported.rankKnown,
                    "rank_min" to settings.suspendedRankMin,
                    "rank_max" to settings.suspendedRankMax,
                    "min_matching_cards" to settings.importMinMatchingCardsPerKanji,
                    "source_count" to decision.sourceCount(),
                    "source_types" to decision.sourceTypes().joinToString(" "),
                    "rule_types" to decision.ruleTypes().joinToString(" "),
                    "source_card_ids" to decision.sourceCardIds(),
                    "source_note_ids" to decision.sourceNoteIds(),
                    "created_at" to finishedAt,
                ),
            )
        }
    }

    private fun importCandidate(
        imported: RecordsImportModels.SuspendedImport,
    ): ImportAuditBuilder.ImportCandidate {
        val sources = imported.sources.map { source ->
            ImportAuditBuilder.ImportSource(source.cardId, source.noteId, source.sourceType, source.ruleTypes)
        }
        return ImportAuditBuilder.ImportCandidate(imported.kanji, imported.jitenRank, imported.rankKnown, sources)
    }

    private fun importSettings(settings: RecordsSyncModels.Settings): ImportAuditBuilder.SettingsSnapshot =
        ImportAuditBuilder.SettingsSnapshot(
            settings.modelName,
            settings.importActiveCards,
            settings.importSuspendedCards,
            settings.importTaggedCardsEnabled(),
            settings.importTags,
            settings.importWeakCards,
            settings.importWeakFsrsDifficultyThreshold,
            settings.importWeakLapsesThreshold,
            settings.importMinMatchingCardsPerKanji,
            settings.importBrowserQueryCards,
            settings.normalizedBrowserQuery(),
            settings.suspendedRankMin,
            settings.suspendedRankMax,
        )

    fun saveDashboardRows(rows: List<RecordsImportModels.DashboardRow>, rebuiltAt: Long) {
        for (row in rows) {
            scope.insertRow(
                "dashboard_rows",
                "REPLACE",
                linkedMapOf(
                    "kanji" to row.kanji,
                    "jiten_rank" to row.jitenRank,
                    "primary_meaning" to row.primaryMeaning,
                    "reading" to row.reading,
                    "browser_search" to row.browserSearch,
                    "weakness_score" to row.weaknessScore,
                    "reason_code" to row.reasonCode,
                    "reason_text" to row.reasonText,
                    "active_example_count" to row.activeExampleCount,
                    "suspended_example_count" to row.suspendedExampleCount,
                    "mature_support_count" to row.matureSupportCount,
                    "rebuilt_at" to rebuiltAt,
                ),
            )
            for (example in row.examples) {
                scope.insertRow(
                    "kanji_examples",
                    "ABORT",
                    linkedMapOf(
                        "kanji" to row.kanji,
                        "source_type" to example.sourceType,
                        "card_id" to example.cardId,
                        "note_id" to example.noteId,
                        "expression" to example.expression,
                        "reading" to example.reading,
                        "meaning" to example.meaning,
                        "sentence" to example.sentence,
                        "mature" to example.mature,
                        "lapses" to example.lapses,
                        "interval_days" to example.intervalDays,
                        "reps" to example.reps,
                        "fsrs_stability" to example.fsrsStability,
                        "fsrs_difficulty" to example.fsrsDifficulty,
                        "fsrs_retrievability" to example.fsrsRetrievability,
                    ),
                )
            }
        }
    }

    fun clearDashboardExampleTables() {
        listOf("dashboard_rows", "kanji_examples").forEach { table ->
            scope.executeBound("DELETE FROM $table")
        }
    }

    fun rebuildKanjiInventory(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        settings: RecordsSyncModels.Settings,
        activeNoteIds: Set<Long>,
    ) {
        val builder = KanjiInventoryBuilder(nowMillis, settings)
        for (note in snapshot.notes) {
            if (note.noteId in activeNoteIds) builder.addSnapshotNote(note)
        }
        imports.forEach(builder::addSuspendedImport)
        rows.forEach(builder::addDashboardRow)
        addKnownKanji(builder, "study_items")
        addKnownKanji(builder, "review_log")
        addKnownKanji(builder, "kanji_timeline_events")
        val previous = previousInventoryItems()
        for (item in builder.build(previous)) {
            scope.insertRow(
                "kanji_inventory",
                "REPLACE",
                linkedMapOf(
                    "kanji" to item.kanji(),
                    "primary_meaning" to item.primaryMeaning(),
                    "readings" to item.readings(),
                    "browser_search" to item.browserSearch(),
                    "search_text" to item.searchText(),
                    "source_count" to item.sourceCount(),
                    "example_count" to item.exampleCount(),
                    "first_seen_at" to item.firstSeenAtMillis(),
                    "last_seen_at" to item.lastSeenAtMillis(),
                ),
            )
        }
    }

    private fun addKnownKanji(builder: KanjiInventoryBuilder, table: String) {
        scope.queryList("SELECT DISTINCT kanji FROM $table") { row -> row.text(0) }
            .forEach(builder::addKnownKanji)
    }

    private fun previousInventoryItems(): Map<String, KanjiInventoryBuilder.PreviousItem> {
        val out = LinkedHashMap<String, KanjiInventoryBuilder.PreviousItem>()
        scope.queryList("SELECT * FROM kanji_inventory") { row ->
            val values = NamedSqlRow(row)
            values.text("kanji") to KanjiInventoryBuilder.PreviousItem(
                values.text("primary_meaning"),
                values.text("readings"),
                values.text("browser_search"),
                values.int("source_count"),
                values.int("example_count"),
                values.long("first_seen_at"),
                values.long("last_seen_at"),
            )
        }.forEach { (kanji, item) -> out[kanji] = item }
        return out
    }

    fun rebuildKanjiReadingUsage(
        rows: List<RecordsImportModels.DashboardRow>,
        dictionary: DictionaryLookup?,
    ) {
        scope.executeBound("DELETE FROM kanji_reading_usage")
        scope.executeBound("DELETE FROM kanji_reading_pool")
        if (dictionary == null || rows.isEmpty()) return
        val attestedByKanji = HashMap<String, MutableSet<String>>()
        for (row in rows) {
            for (example in row.examples) {
                if (example.expression.isEmpty() || example.reading.isEmpty()) continue
                val pairs = KanjiReadingAligner.alignPlain(example.expression, example.reading, dictionary)
                    ?: continue
                for (pair in pairs) {
                    scope.insertRow(
                        "kanji_reading_usage",
                        "REPLACE",
                        linkedMapOf(
                            "kanji" to pair.kanji,
                            "reading" to pair.canonicalReading,
                            "expression" to example.expression,
                            "note_id" to example.noteId,
                            "source_type" to example.sourceType,
                            "mature" to example.mature,
                            "lapses" to example.lapses,
                            "interval_days" to example.intervalDays,
                        ),
                    )
                    attestedByKanji.getOrPut(pair.kanji) { LinkedHashSet() }.add(pair.canonicalReading)
                }
            }
        }
        val kanjiSet = rows.mapNotNull { it.kanji.takeIf(String::isNotEmpty) }.toCollection(LinkedHashSet())
        for (kanji in kanjiSet) {
            val attested = attestedByKanji[kanji] ?: emptySet()
            val allReadings = LinkedHashSet<String>()
            allReadings.addAll(attested)
            allReadings.addAll(dictionaryCanonicalReadings(dictionary, kanji))
            for (reading in allReadings) {
                scope.insertRow(
                    "kanji_reading_pool",
                    "REPLACE",
                    linkedMapOf(
                        "kanji" to kanji,
                        "reading" to reading,
                        "attested" to attested.contains(reading),
                    ),
                )
            }
        }
    }

    private fun dictionaryCanonicalReadings(dictionary: DictionaryLookup, kanji: String): Set<String> {
        val entry = dictionary.lookupKanji(kanji) ?: return emptySet()
        val out = LinkedHashSet<String>()
        for ((_, canonical) in KanjiReadingAligner.readingInventory(entry)) {
            if (canonical.isNotEmpty()) out.add(canonical)
        }
        return out
    }

    fun rebuildSimilarKanjiPairs(similarIndex: SimilarKanjiIndex?, nowMillis: Long) {
        if (similarIndex == null) return
        val firstSeen = similarPairFirstSeen()
        val inventoryKanji = inventoryKanji()
        scope.executeBound("DELETE FROM similar_kanji_pairs")
        similarIndex.pairsWithin(inventoryKanji).forEach { pair ->
            insertSimilarPair(pair.kanjiA, pair.kanjiB, pair.source, firstSeen, nowMillis)
        }
        minedConfusionPairs(inventoryKanji, nowMillis).forEach { pair ->
            insertSimilarPair(pair.kanjiA, pair.kanjiB, pair.source, firstSeen, nowMillis)
        }
    }

    private fun insertSimilarPair(
        kanjiA: String,
        kanjiB: String,
        source: String,
        firstSeen: Map<String, Long>,
        nowMillis: Long,
    ) {
        scope.insertRow(
            "similar_kanji_pairs",
            "REPLACE",
            linkedMapOf(
                "kanji_a" to kanjiA,
                "kanji_b" to kanjiB,
                "source" to source,
                "first_seen_at" to (firstSeen[SimilarKanjiStorageKeys.pairKey(kanjiA, kanjiB, source)] ?: nowMillis),
                "last_seen_at" to nowMillis,
            ),
        )
    }

    private fun minedConfusionPairs(
        inventoryKanji: Set<String>,
        nowMillis: Long,
    ): List<RecordsImportModels.SimilarKanjiPair> {
        val windowStart = ConfusionPairMiner.windowStartMillis(nowMillis)
        scope.executeBound("DELETE FROM similar_kanji_review_log WHERE reviewed_at < ?") {
            bindLong(1, windowStart)
        }
        val rows = scope.queryList(
            """
            SELECT target_kanji, selected_kanji, correct, reviewed_at
            FROM similar_kanji_review_log
            WHERE correct = 0 AND reviewed_at >= ?
            """.trimIndent(),
            bind = { bindLong(1, windowStart) },
        ) { row ->
            ConfusionPairMiner.WrongPickRow(
                row.textOrEmpty(0),
                row.textOrEmpty(1),
                row.long(2) != 0L,
                row.long(3),
            )
        }
        return ConfusionPairMiner().minePairs(rows, nowMillis)
            .filter { it.kanjiA in inventoryKanji && it.kanjiB in inventoryKanji }
    }

    fun rebuildSimilarKanjiChoiceStates(nowMillis: Long) {
        val previous = similarChoiceSnapshots()
        val candidates = SimilarKanjiChoicePlanner().buildCandidates(
            SqlHomeData(scope).searchInventory("", onlySimilarKanji = false, SqlHomeData.InventoryScope.ALL),
            SqlHomeData(scope).allSimilarPairs(),
            SqlHomeData(scope).wrongPickCounts(nowMillis),
        )
        val currentKeys = HashSet<String>()
        for (card in candidates) {
            val key = SimilarKanjiStorageKeys.choiceKey(card.targetKanji, card.choiceSignature)
            currentKeys.add(key)
            val old = previous[key]
            scope.insertRow(
                "similar_kanji_choice_state",
                "REPLACE",
                linkedMapOf(
                    "target_kanji" to card.targetKanji,
                    "choice_signature" to card.choiceSignature,
                    "primary_meaning" to card.primaryMeaning,
                    "choices" to dev.bee.kanjianki.core.SimilarChoiceCodec.serializeChoices(card.choices),
                    "due_at" to (old?.dueAtMillis ?: 0L),
                    "passed_at" to (old?.passedAtMillis ?: 0L),
                    "last_reviewed_at" to (old?.lastReviewedAtMillis ?: 0L),
                    "correct_count" to (old?.correctCount ?: 0),
                    "wrong_count" to (old?.wrongCount ?: 0),
                    "active_token" to "",
                    "first_seen_at" to (old?.firstSeenAtMillis ?: nowMillis),
                    "last_seen_at" to nowMillis,
                ),
            )
        }
        for (key in previous.keys) {
            if (key in currentKeys) continue
            val parts = SimilarKanjiStorageKeys.splitChoiceKey(key)
            if (parts.size != 2) continue
            scope.executeBound(
                "DELETE FROM similar_kanji_choice_state WHERE target_kanji = ? AND choice_signature = ?",
            ) {
                bindText(1, parts[0])
                bindText(2, parts[1])
            }
            scope.executeBound(
                "DELETE FROM similar_kanji_repair_queue WHERE status = ? AND target_kanji = ? AND choice_signature = ?",
            ) {
                bindText(1, STATUS_PENDING)
                bindText(2, parts[0])
                bindText(3, parts[1])
            }
        }
    }

    private fun similarPairFirstSeen(): Map<String, Long> {
        val out = HashMap<String, Long>()
        scope.queryList("SELECT kanji_a, kanji_b, source, first_seen_at FROM similar_kanji_pairs") { row ->
            val values = NamedSqlRow(row)
            SimilarKanjiStorageKeys.pairKey(
                values.text("kanji_a"),
                values.text("kanji_b"),
                values.text("source"),
            ) to values.long("first_seen_at")
        }.forEach { (key, value) -> out[key] = value }
        return out
    }

    private fun similarChoiceSnapshots(): Map<String, SimilarChoiceSnapshot> {
        val out = HashMap<String, SimilarChoiceSnapshot>()
        scope.queryList("SELECT * FROM similar_kanji_choice_state") { row ->
            val values = NamedSqlRow(row)
            SimilarKanjiStorageKeys.choiceKey(
                values.text("target_kanji"),
                values.text("choice_signature"),
            ) to SimilarChoiceSnapshot(
                values.long("due_at"),
                values.long("passed_at"),
                values.long("last_reviewed_at"),
                values.int("correct_count"),
                values.int("wrong_count"),
                values.long("first_seen_at"),
            )
        }.forEach { (key, value) -> out[key] = value }
        return out
    }

    private fun inventoryKanji(): Set<String> =
        scope.queryList("SELECT kanji FROM kanji_inventory") { row -> row.text(0) }
            .map(TextUtil::normalizeSingleKanji)
            .filter(String::isNotEmpty)
            .toSet()

    data class SimilarChoiceSnapshot(
        val dueAtMillis: Long,
        val passedAtMillis: Long,
        val lastReviewedAtMillis: Long,
        val correctCount: Int,
        val wrongCount: Int,
        val firstSeenAtMillis: Long,
    )

    fun appendHistoricalSyncSnapshots(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        notesById: Map<Long, RecordsSyncModels.Note>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        syncId: Long,
        startedAt: Long,
        finishedAt: Long,
    ) {
        val deckIdsByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        val deckNamesByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        snapshot.cards.forEach { card ->
            deckIdsByNote.getOrPut(card.noteId) { LinkedHashSet() }.add(card.deckId)
            deckNamesByNote.getOrPut(card.noteId) { LinkedHashSet() }.add(card.deckName)
        }
        val aggregates = LinkedHashMap<String, HistoricalKanjiAggregate>()
        for (card in snapshot.cards) {
            val note = notesById[card.noteId] ?: continue
            scope.insertRow(
                "sync_card_snapshots",
                "REPLACE",
                linkedMapOf(
                    "sync_id" to syncId,
                    "started_at" to startedAt,
                    "finished_at" to finishedAt,
                    "card_id" to card.cardId,
                    "note_id" to card.noteId,
                    "deck_id" to card.deckId,
                    "deck_name" to card.deckName,
                    "model_id" to note.modelId,
                    "model_name" to note.modelName,
                    "ord" to card.ord,
                    "queue" to card.queue,
                    "type" to card.type,
                    "due" to card.due,
                    "interval_days" to card.intervalDays,
                    "reps" to card.reps,
                    "lapses" to card.lapses,
                    "suspended" to card.suspended,
                    "fsrs_stability" to card.fsrsStability,
                    "fsrs_difficulty" to card.fsrsDifficulty,
                    "fsrs_retrievability" to card.fsrsRetrievability,
                    "mature" to card.mature(settings.matureDays),
                ),
            )
            for (kanji in extractedKanji(note, settings)) {
                aggregates.getOrPut(kanji) { HistoricalKanjiAggregate(kanji) }.add(card, settings.matureDays)
            }
        }
        for (note in snapshot.notes) {
            val decks = deckNamesByNote[note.noteId]
            if (decks.isNullOrEmpty()) continue
            val expression = TextUtil.normalizeJapanese(note.expression(settings))
            val sentence = TextUtil.normalizeJapanese(note.sentence(settings))
            scope.insertRow(
                "sync_note_snapshots",
                "REPLACE",
                linkedMapOf(
                    "sync_id" to syncId,
                    "finished_at" to finishedAt,
                    "note_id" to note.noteId,
                    "model_id" to note.modelId,
                    "model_name" to note.modelName,
                    "deck_ids" to (deckIdsByNote[note.noteId]?.joinToString(" ") ?: ""),
                    "deck_names" to decks.joinToString(" "),
                    "expression" to expression,
                    "reading" to TextUtil.normalizeJapanese(note.reading(settings)),
                    "meaning" to TextUtil.firstMeaningLine(note.meaning(settings)),
                    "sentence" to sentence,
                    "tags" to note.tags.joinToString(" "),
                    "fields_json" to fieldsJson(note.fields),
                    "extracted_kanji" to TextUtil.extractKanji("$expression $sentence").joinToString(""),
                ),
            )
        }
        for (row in rows) {
            aggregates.getOrPut(row.kanji) { HistoricalKanjiAggregate(row.kanji) }
                .mergeDashboardEvidence(
                    row.weaknessScore,
                    row.reasonCode,
                    row.activeExampleCount,
                    row.suspendedExampleCount,
                    row.matureSupportCount,
                )
        }
        insertHistoricalAggregates(syncId, finishedAt, aggregates)
    }

    private fun insertHistoricalAggregates(
        syncId: Long,
        finishedAt: Long,
        aggregates: Map<String, HistoricalKanjiAggregate>,
    ) {
        for (aggregate in aggregates.values) {
            if (aggregate.kanji().isEmpty()) continue
            scope.insertRow(
                "sync_kanji_snapshots",
                "REPLACE",
                linkedMapOf(
                    "sync_id" to syncId,
                    "finished_at" to finishedAt,
                    "kanji" to aggregate.kanji(),
                    "active_cards" to aggregate.activeCards(),
                    "suspended_cards" to aggregate.suspendedCards(),
                    "mature_support_count" to aggregate.matureSupportCount(),
                    "average_interval_days" to aggregate.averageIntervalDays(),
                    "total_lapses" to aggregate.totalLapses(),
                    "total_reps" to aggregate.totalReps(),
                    "fsrs_stability_avg" to aggregate.averageStability(),
                    "fsrs_difficulty_avg" to aggregate.averageDifficulty(),
                    "fsrs_retrievability_avg" to aggregate.averageRetrievability(),
                    "weakness_score" to aggregate.weaknessScore(),
                    "reason_code" to aggregate.reasonCode(),
                    "active_example_count" to aggregate.activeExampleCount(),
                    "suspended_example_count" to aggregate.suspendedExampleCount(),
                ),
            )
        }
    }

    private fun extractedKanji(note: RecordsSyncModels.Note, settings: RecordsSyncModels.Settings): List<String> {
        val expression = TextUtil.normalizeJapanese(note.expression(settings))
        val sentence = TextUtil.normalizeJapanese(note.sentence(settings))
        return TextUtil.extractKanji("$expression $sentence")
    }

    fun purgeNonSuccessfulSyncTimelineEvents() {
        scope.executeBound(
            """
            DELETE FROM kanji_timeline_events
            WHERE sync_id IS NOT NULL
              AND sync_id NOT IN (SELECT id FROM sync_runs WHERE status = ?)
            """.trimIndent(),
        ) {
            bindText(1, STATUS_SUCCESS)
        }
    }

    fun purgeNonSuccessfulSnapshots() {
        val filter = "sync_id NOT IN (SELECT id FROM sync_runs WHERE status = ?)"
        listOf("sync_card_snapshots", "sync_note_snapshots", "sync_kanji_snapshots").forEach { table ->
            scope.executeBound("DELETE FROM $table WHERE $filter") { bindText(1, STATUS_SUCCESS) }
        }
    }

    fun pruneSupersededSnapshots() {
        val existing = scope.queryList(
            """
            SELECT DISTINCT snapshots.sync_id
            FROM sync_card_snapshots snapshots
            JOIN sync_runs runs ON runs.id = snapshots.sync_id
            WHERE runs.status = ?
            """.trimIndent(),
            bind = { bindText(1, STATUS_SUCCESS) },
        ) { row -> row.long(0) }
        for (syncId in SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(existing)) {
            listOf("sync_card_snapshots", "sync_note_snapshots").forEach { table ->
                scope.executeBound("DELETE FROM $table WHERE sync_id = ?") { bindLong(1, syncId) }
            }
        }
    }

    fun appendSuspendedImportedEvents(
        imports: List<RecordsImportModels.SuspendedImport>,
        syncId: Long,
        occurredAt: Long,
    ) {
        for (imported in imports) {
            val source = firstSuspendedSource(imported.kanji)
            insertTimelineEvent(
                kanji = imported.kanji,
                occurredAt = occurredAt,
                eventType = "suspended_imported",
                title = TimelineCopy.suspendedImportedTitle(),
                detail = TimelineCopy.suspendedImportedDetail(),
                sourceExpression = source.first,
                sourceReading = source.second,
                syncId = syncId,
                dedupeKey = "suspended_imported:" + imported.kanji,
            )
        }
    }

    fun appendRowTimelineEvents(
        previousRows: Map<String, RowSnapshot>,
        rows: List<RecordsImportModels.DashboardRow>,
        syncId: Long,
        occurredAt: Long,
        target: Int,
    ) {
        for (row in rows) {
            val source = firstExample(row.kanji)
            insertTimelineEvent(
                kanji = row.kanji,
                occurredAt = occurredAt,
                eventType = "first_seen",
                title = TimelineCopy.firstSeenTitle(),
                detail = TimelineCopy.firstSeenAnkiEvidenceDetail(),
                sourceExpression = source.first,
                sourceReading = source.second,
                weaknessScore = row.weaknessScore,
                matureSupportCount = row.matureSupportCount,
                syncId = syncId,
                dedupeKey = "first_seen:" + row.kanji,
            )
            val previous = previousRows[row.kanji]
            when {
                previous == null -> insertTimelineEvent(
                    kanji = row.kanji,
                    occurredAt = occurredAt,
                    eventType = "weak_support_seen",
                    title = TimelineCopy.weakSupportSeenTitle(),
                    detail = TimelineCopy.supportDetail("Anki evidence still needs repair", row.matureSupportCount, target),
                    sourceExpression = source.first,
                    sourceReading = source.second,
                    weaknessScore = row.weaknessScore,
                    matureSupportCount = row.matureSupportCount,
                    syncId = syncId,
                    dedupeKey = "weak_support_seen:" + row.kanji + ":" + syncId,
                )
                row.matureSupportCount > previous.matureSupportCount -> insertTimelineEvent(
                    kanji = row.kanji,
                    occurredAt = occurredAt,
                    eventType = "support_improved",
                    title = TimelineCopy.supportImprovedTitle(),
                    detail = TimelineCopy.supportImprovedDetail(previous.matureSupportCount, row.matureSupportCount),
                    sourceExpression = source.first,
                    sourceReading = source.second,
                    weaknessScore = row.weaknessScore,
                    matureSupportCount = row.matureSupportCount,
                    syncId = syncId,
                    dedupeKey = "support_improved:" + row.kanji + ":" + syncId + ":" +
                        previous.matureSupportCount + "-" + row.matureSupportCount,
                )
                row.matureSupportCount < previous.matureSupportCount -> insertTimelineEvent(
                    kanji = row.kanji,
                    occurredAt = occurredAt,
                    eventType = "support_dropped",
                    title = TimelineCopy.supportDroppedTitle(),
                    detail = TimelineCopy.supportDroppedDetail(previous.matureSupportCount, row.matureSupportCount),
                    sourceExpression = source.first,
                    sourceReading = source.second,
                    weaknessScore = row.weaknessScore,
                    matureSupportCount = row.matureSupportCount,
                    syncId = syncId,
                    dedupeKey = "support_dropped:" + row.kanji + ":" + syncId + ":" +
                        previous.matureSupportCount + "-" + row.matureSupportCount,
                )
            }
        }
    }

    fun appendStudyStateEvents(
        previous: Map<String, String>,
        current: List<RecordsStudyModels.StudyItem>,
        syncId: Long,
        occurredAt: Long,
        target: Int,
    ) {
        for (item in current) {
            val previousState = previous[item.kanji] ?: continue
            val retired = item.state == STATE_RETIRED
            if (retired == (previousState == STATE_RETIRED)) continue
            val source = firstExample(item.kanji)
            val row = rowSnapshotForKanji(item.kanji)
            insertTimelineEvent(
                kanji = item.kanji,
                occurredAt = occurredAt,
                eventType = if (retired) STATE_RETIRED else "reopened",
                title = if (retired) TimelineCopy.retiredByAnkiSupportTitle() else TimelineCopy.repairReopenedTitle(),
                detail = TimelineCopy.studyStateDetail(retired, row?.matureSupportCount, target),
                sourceExpression = source.first,
                sourceReading = source.second,
                weaknessScore = row?.weaknessScore,
                matureSupportCount = row?.matureSupportCount,
                syncId = syncId,
                dedupeKey = (if (retired) "retired:" else "reopened:") +
                    studyTimelineKey(item) + ":" + syncId,
            )
        }
    }

    private fun studyTimelineKey(item: RecordsStudyModels.StudyItem): String {
        val signature = item.answerSignature
        return if (signature.isEmpty()) item.kanji else item.kanji + " " + signature
    }

    fun upsertStudyItem(item: RecordsStudyModels.StudyItem) {
        val columns = SqlStudyItemMapper.COLUMNS
        val placeholders = columns.joinToString(",") { "?" }
        val assignments = columns
            .filterNot { it == "kanji" || it == "answer_signature" }
            .joinToString(",") { "$it = excluded.$it" }
        scope.prepare(
            """
            INSERT INTO study_items(${columns.joinToString(",")})
            VALUES ($placeholders)
            ON CONFLICT(kanji, answer_signature) DO UPDATE SET $assignments
            """.trimIndent(),
        ).use { statement ->
            SqlStudyItemMapper.bindUpsert(statement, item)
            statement.execute()
        }
    }

    fun deleteAllStudyItems() {
        scope.executeBound("DELETE FROM study_items")
    }

    fun finalizePendingSyncRun(syncId: Long) {
        scope.executeBound(
            "UPDATE sync_runs SET status = ? WHERE id = ? AND status = ?",
        ) {
            bindText(1, STATUS_SUCCESS)
            bindLong(2, syncId)
            bindText(3, STATUS_PENDING)
        }
        check(scope.changes() == 1L) { "Pending sync $syncId could not be finalized" }
    }

    fun markStatsDirty() {
        scope.executeBound(
            """
            INSERT INTO stats_cache_state(key, value)
            VALUES (?, 2)
            ON CONFLICT(key) DO UPDATE SET value = value + 1
            """.trimIndent(),
        ) {
            bindText(1, STATS_SOURCE_VERSION_KEY)
        }
    }

    fun studyItems(): List<RecordsStudyModels.StudyItem> =
        scope.queryList("SELECT * FROM study_items", map = SqlStudyItemMapper::read)

    fun locallySuspendedKanji(): Set<String> =
        scope.queryList("SELECT kanji FROM local_kanji_suspensions") { row -> row.text(0) }.toSet()

    /** Active manual dictionary source candidates, in Home's ordering. */
    fun manualCandidates(): List<dev.bee.kanjianki.core.MissingKanjiCandidate> =
        scope.queryList(
            """
            SELECT * FROM manual_kanji_sources
            WHERE active = 1
            ORDER BY jiten_rank IS NULL, jiten_rank, literal
            """.trimIndent(),
        ) { row ->
            val values = NamedSqlRow(row)
            dev.bee.kanjianki.core.MissingKanjiCandidate(
                literal = values.text("literal"),
                meanings = dev.bee.kanjianki.core.StringListJsonCodec.decode(values.text("meanings_json")),
                onReadings = dev.bee.kanjianki.core.StringListJsonCodec.decode(values.text("on_readings_json")),
                kunReadings = dev.bee.kanjianki.core.StringListJsonCodec.decode(values.text("kun_readings_json")),
                jitenRank = values.nullableInt("jiten_rank"),
            )
        }

    private fun firstExample(kanji: String): Pair<String, String> =
        scope.queryOneOrNull(
            "SELECT expression, reading FROM kanji_examples WHERE kanji = ? ORDER BY source_type ASC, id ASC LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row -> row.textOrEmpty(0) to row.textOrEmpty(1) } ?: ("" to "")

    private fun firstSuspendedSource(kanji: String): Pair<String, String> =
        scope.queryOneOrNull(
            "SELECT expression, reading FROM suspended_sources WHERE kanji = ? ORDER BY card_id ASC LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row -> row.textOrEmpty(0) to row.textOrEmpty(1) } ?: ("" to "")

    private fun rowSnapshotForKanji(kanji: String): RowSnapshot? =
        scope.queryOneOrNull(
            "SELECT kanji, weakness_score, mature_support_count FROM dashboard_rows WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row ->
            val values = NamedSqlRow(row)
            RowSnapshot(values.text("kanji"), values.int("weakness_score"), values.int("mature_support_count"))
        }

    private fun insertTimelineEvent(
        kanji: String,
        occurredAt: Long,
        eventType: String,
        title: String,
        detail: String,
        sourceExpression: String,
        sourceReading: String,
        weaknessScore: Int? = null,
        matureSupportCount: Int? = null,
        syncId: Long?,
        dedupeKey: String,
    ) {
        scope.insertRow(
            "kanji_timeline_events",
            "IGNORE",
            linkedMapOf(
                "kanji" to kanji,
                "occurred_at" to occurredAt,
                "event_type" to eventType,
                "title" to title,
                "detail" to detail,
                "source_expression" to sourceExpression,
                "source_reading" to sourceReading,
                "rating" to "",
                "writing_required" to false,
                "writing_passed" to false,
                "manual_override" to false,
                "weakness_score" to weaknessScore,
                "mature_support_count" to matureSupportCount,
                "sync_id" to syncId,
                "dedupe_key" to dedupeKey,
            ),
        )
    }

    private fun fieldsJson(fields: Map<String, String>): String {
        val out = StringBuilder("{")
        var first = true
        for ((key, value) in fields) {
            if (!first) out.append(',')
            first = false
            out.append(TextUtil.jsonQuote(key)).append(':').append(TextUtil.jsonQuote(value))
        }
        out.append('}')
        return out.toString()
    }

    data class RowSnapshot(
        val kanji: String,
        val weaknessScore: Int,
        val matureSupportCount: Int,
    )

    internal companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_PENDING = "pending"
        const val STATE_RETIRED = "retired"
        const val STATS_SOURCE_VERSION_KEY = "stats_source_version"
    }
}
