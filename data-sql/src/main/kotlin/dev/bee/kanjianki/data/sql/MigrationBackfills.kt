package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SimilarChoiceCodec
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.core.TimelineCopy
import java.util.LinkedHashMap
import java.util.LinkedHashSet

internal object MigrationBackfills {
    fun timeline(
        session: SqlSession,
        context: MigrationContext,
    ) {
        val dashboardRows = readTimelineDashboardRows(session)
        backfillSuspendedImportTimeline(session, context)
        backfillDashboardTimeline(session, dashboardRows, context)
        backfillStudyTimeline(session, dashboardRows, context)
        backfillReviewTimeline(session, dashboardRows)
    }

    fun inventory(
        session: SqlSession,
        context: MigrationContext,
    ) {
        val builder = KanjiInventoryBuilder(
            context.clock.nowMillis(),
            context.defaults.settings,
        )
        for (imported in readSuspendedImports(session)) {
            builder.addSuspendedImport(imported)
        }
        for (row in readDashboardRows(session)) {
            builder.addDashboardRow(row)
        }
        for (table in listOf(STUDY_ITEMS, REVIEW_LOG, TIMELINE_EVENTS)) {
            session.forEachMigrationRow("SELECT DISTINCT kanji FROM $table") { row ->
                builder.addKnownKanji(row.text("kanji"))
            }
        }

        val previous = LinkedHashMap<String, KanjiInventoryBuilder.PreviousItem>()
        session.forEachMigrationRow(
            "SELECT * FROM $KANJI_INVENTORY ORDER BY kanji ASC",
        ) { row ->
            previous[row.text("kanji")] = KanjiInventoryBuilder.PreviousItem(
                row.text("primary_meaning"),
                row.text("readings"),
                row.text("browser_search"),
                row.int("source_count"),
                row.int("example_count"),
                row.long("first_seen_at"),
                row.long("last_seen_at"),
            )
        }
        for (item in builder.build(previous)) {
            session.executeMigration(
                """
                INSERT OR REPLACE INTO kanji_inventory(
                    kanji, primary_meaning, readings, browser_search, search_text,
                    source_count, example_count, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ) {
                bindText(1, item.kanji())
                bindText(2, item.primaryMeaning())
                bindText(3, item.readings())
                bindText(4, item.browserSearch())
                bindText(5, item.searchText())
                bindLong(6, item.sourceCount().toLong())
                bindLong(7, item.exampleCount().toLong())
                bindLong(8, item.firstSeenAtMillis())
                bindLong(9, item.lastSeenAtMillis())
            }
        }
    }

    fun similarChoiceStates(
        session: SqlSession,
        context: MigrationContext,
    ) {
        val nowMillis = context.clock.nowMillis()
        val previous = readSimilarChoiceSnapshots(session)
        val planner = SimilarKanjiChoicePlanner()
        val candidates = planner.buildCandidates(
            readInventoryItems(session),
            readSimilarPairs(session),
            readWrongPickCounts(
                session,
                ConfusionPairMiner.windowStartMillis(nowMillis),
            ),
        )
        val currentKeys = HashSet<String>()
        for (card in candidates) {
            val key = SimilarKanjiStorageKeys.choiceKey(
                card.targetKanji,
                card.choiceSignature,
            )
            currentKeys += key
            val old = previous[key]
            session.executeMigration(
                """
                INSERT OR REPLACE INTO similar_kanji_choice_state(
                    target_kanji, choice_signature, primary_meaning, choices,
                    due_at, passed_at, last_reviewed_at, correct_count, wrong_count,
                    active_token, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?)
                """.trimIndent(),
            ) {
                bindText(1, card.targetKanji)
                bindText(2, card.choiceSignature)
                bindText(3, card.primaryMeaning)
                bindText(4, SimilarChoiceCodec.serializeChoices(card.choices))
                bindLong(5, old?.dueAtMillis ?: 0L)
                bindLong(6, old?.passedAtMillis ?: 0L)
                bindLong(7, old?.lastReviewedAtMillis ?: 0L)
                bindLong(8, (old?.correctCount ?: 0).toLong())
                bindLong(9, (old?.wrongCount ?: 0).toLong())
                bindLong(10, old?.firstSeenAtMillis ?: nowMillis)
                bindLong(11, nowMillis)
            }
        }

        for (key in previous.keys) {
            val parts = SimilarKanjiStorageKeys.splitChoiceKey(key)
            if (key !in currentKeys && parts.size == 2) {
                session.executeMigration(
                    """
                    DELETE FROM similar_kanji_choice_state
                    WHERE target_kanji = ? AND choice_signature = ?
                    """.trimIndent(),
                ) {
                    bindText(1, parts[0])
                    bindText(2, parts[1])
                }
                session.executeMigration(
                    """
                    DELETE FROM similar_kanji_repair_queue
                    WHERE status = 'pending'
                      AND target_kanji = ?
                      AND choice_signature = ?
                    """.trimIndent(),
                ) {
                    bindText(1, parts[0])
                    bindText(2, parts[1])
                }
            }
        }
    }

    fun latestHistoricalSync(
        session: SqlSession,
        context: MigrationContext,
    ) {
        if (hasSuccessfulHistoricalRows(session, context)) {
            return
        }
        val run = latestSuccessfulSyncRun(session, context) ?: return
        val notes = readHistoricalNotes(session)
        if (notes.isEmpty()) {
            return
        }

        val deckIdsByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        val deckNamesByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        val aggregates = LinkedHashMap<String, HistoricalKanjiAggregate>()
        session.forEachMigrationRow(
            "SELECT * FROM source_cards ORDER BY card_id ASC",
        ) { card ->
            val noteId = card.long("note_id")
            val note = notes[noteId] ?: return@forEachMigrationRow
            val deck = card.text("deck_name")
            deckIdsByNote.getOrPut(noteId, ::LinkedHashSet).add(deck)
            deckNamesByNote.getOrPut(noteId, ::LinkedHashSet).add(deck)
            val intervalDays = card.int("interval_days")
            val reps = card.int("reps")
            val lapses = card.int("lapses")
            val mature = intervalDays >= context.defaults.settings.matureDays
            insertHistoricalCard(
                session = session,
                run = run,
                note = note,
                card = card,
                deck = deck,
                mature = mature,
            )
            for (kanji in TextUtil.extractKanji("${note.expression} ${note.sentence}")) {
                aggregateFor(aggregates, kanji).addCard(
                    intervalDays,
                    reps,
                    lapses,
                    false,
                    mature,
                    HistoricalKanjiAggregate.FsrsMemoryValues(
                        card.nullableDouble("fsrs_stability"),
                        card.nullableDouble("fsrs_difficulty"),
                        card.nullableDouble("fsrs_retrievability"),
                    ),
                )
            }
        }

        for (note in notes.values) {
            val deckIds = deckIdsByNote[note.noteId]
            val deckNames = deckNamesByNote[note.noteId]
            if (deckNames.isNullOrEmpty()) {
                continue
            }
            session.executeMigration(
                """
                INSERT OR REPLACE INTO sync_note_snapshots(
                    sync_id, finished_at, note_id, model_id, model_name,
                    deck_ids, deck_names, expression, reading, meaning, sentence,
                    tags, fields_json, extracted_kanji
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ) {
                bindLong(1, run.id)
                bindLong(2, run.finishedAt)
                bindLong(3, note.noteId)
                bindLong(4, note.modelId)
                bindText(5, note.modelName)
                bindText(6, deckIds.orEmpty().joinToString(" "))
                bindText(7, deckNames.joinToString(" "))
                bindText(8, note.expression)
                bindText(9, note.reading)
                bindText(10, note.meaning)
                bindText(11, note.sentence)
                bindText(12, note.tags)
                bindText(13, note.fieldsJson)
                bindText(
                    14,
                    TextUtil.extractKanji("${note.expression} ${note.sentence}")
                        .joinToString(""),
                )
            }
        }

        session.forEachMigrationRow(
            """
            SELECT
                kanji, weakness_score, reason_code, active_example_count,
                suspended_example_count, mature_support_count
            FROM dashboard_rows
            ORDER BY kanji ASC
            """.trimIndent(),
        ) { row ->
            aggregateFor(aggregates, row.text("kanji")).mergeDashboardEvidence(
                row.int("weakness_score"),
                row.text("reason_code"),
                row.int("active_example_count"),
                row.int("suspended_example_count"),
                row.int("mature_support_count"),
            )
        }
        for (aggregate in aggregates.values) {
            if (aggregate.kanji().isNotEmpty()) {
                insertHistoricalAggregate(session, run, aggregate)
            }
        }
    }

    private fun backfillSuspendedImportTimeline(
        session: SqlSession,
        context: MigrationContext,
    ) {
        session.forEachMigrationRow(
            """
            SELECT kanji, first_imported_at, last_seen_sync_id
            FROM suspended_imports
            ORDER BY first_imported_at ASC, kanji ASC
            """.trimIndent(),
        ) { row ->
            val kanji = row.text("kanji")
            val source = firstSuspendedSource(session, kanji)
            insertTimelineEvent(
                session,
                TimelineEvent(
                    kanji = kanji,
                    occurredAt = timelineTime(row.long("first_imported_at"), context),
                    eventType = "suspended_imported",
                    title = TimelineCopy.suspendedImportedTitle(),
                    detail = TimelineCopy.suspendedImportedDetail(),
                    sourceExpression = source.expression,
                    sourceReading = source.reading,
                    syncId = row.long("last_seen_sync_id"),
                    dedupeKey = "suspended_imported:$kanji",
                ),
            )
        }
    }

    private fun backfillDashboardTimeline(
        session: SqlSession,
        dashboardRows: Map<String, TimelineDashboardRow>,
        context: MigrationContext,
    ) {
        val target = context.defaults.settings.matureSupportThreshold
        for (row in dashboardRows.values) {
            val occurredAt = timelineTime(row.rebuiltAt, context)
            insertTimelineEvent(
                session,
                TimelineEvent(
                    kanji = row.kanji,
                    occurredAt = occurredAt,
                    eventType = "first_seen",
                    title = TimelineCopy.firstSeenTitle(),
                    detail = TimelineCopy.firstSeenAnkiEvidenceDetail(),
                    sourceExpression = row.source.expression,
                    sourceReading = row.source.reading,
                    weaknessScore = row.weaknessScore,
                    matureSupportCount = row.matureSupportCount,
                    dedupeKey = "first_seen:${row.kanji}",
                ),
            )
            insertTimelineEvent(
                session,
                TimelineEvent(
                    kanji = row.kanji,
                    occurredAt = occurredAt,
                    eventType = "weak_support_seen",
                    title = TimelineCopy.weakSupportSeenTitle(),
                    detail = TimelineCopy.supportDetail(
                        "Anki evidence still needs repair",
                        row.matureSupportCount,
                        target,
                    ),
                    sourceExpression = row.source.expression,
                    sourceReading = row.source.reading,
                    weaknessScore = row.weaknessScore,
                    matureSupportCount = row.matureSupportCount,
                    dedupeKey = "weak_support_seen:${row.kanji}:backfill",
                ),
            )
        }
    }

    private fun backfillStudyTimeline(
        session: SqlSession,
        dashboardRows: Map<String, TimelineDashboardRow>,
        context: MigrationContext,
    ) {
        val target = context.defaults.settings.matureSupportThreshold
        session.forEachMigrationRow(
            "SELECT * FROM study_items ORDER BY created_at ASC, kanji ASC",
        ) { study ->
            val kanji = study.text("kanji")
            val occurredAt = timelineTime(study.long("created_at"), context)
            val dashboard = dashboardRows[kanji]
            val source = dashboard?.source ?: firstExampleSource(session, kanji)
            if (dashboard == null) {
                insertTimelineEvent(
                    session,
                    TimelineEvent(
                        kanji = kanji,
                        occurredAt = occurredAt,
                        eventType = "first_seen",
                        title = TimelineCopy.firstSeenTitle(),
                        detail = TimelineCopy.firstSeenHistoricalStudyDetail(),
                        sourceExpression = source.expression,
                        sourceReading = source.reading,
                        dedupeKey = "first_seen:$kanji",
                    ),
                )
            }
            if (study.text("state") == "retired") {
                val matureSupport = dashboard?.matureSupportCount
                insertTimelineEvent(
                    session,
                    TimelineEvent(
                        kanji = kanji,
                        occurredAt = occurredAt,
                        eventType = "retired",
                        title = TimelineCopy.retiredByAnkiSupportTitle(),
                        detail = if (matureSupport == null) {
                            TimelineCopy.historicalRetiredDetail()
                        } else {
                            TimelineCopy.supportDetail(
                                "Mature Anki support met the target",
                                matureSupport,
                                target,
                            )
                        },
                        sourceExpression = source.expression,
                        sourceReading = source.reading,
                        weaknessScore = dashboard?.weaknessScore,
                        matureSupportCount = matureSupport,
                        dedupeKey = "retired:$kanji:backfill",
                    ),
                )
            }
        }
    }

    private fun backfillReviewTimeline(
        session: SqlSession,
        dashboardRows: Map<String, TimelineDashboardRow>,
    ) {
        session.forEachMigrationRow(
            "SELECT * FROM review_log ORDER BY reviewed_at ASC, id ASC",
        ) { review ->
            val rating = review.text("rating")
            val writingPassed = review.int("writing_passed") == 1
            val request = RecordsSchedulerModels.ReviewRequest.fromFields(
                RecordsSchedulerModels.ReviewRequest.Fields(
                    kanji = review.text("kanji"),
                    token = review.text("token"),
                    rating = rating,
                    writingRequired = review.int("writing_required") == 1,
                    writingPassed = writingPassed,
                    writingClean = writingPassed && (rating == "good" || rating == "easy"),
                    manualOverride = review.int("manual_override") == 1,
                    hintsUsed = 0,
                ),
            )
            val event = TimelineCopy.reviewEvent(request, rating)
            val source = firstExampleSource(session, request.kanji)
            val dashboard = dashboardRows[request.kanji]
            insertTimelineEvent(
                session,
                TimelineEvent(
                    kanji = request.kanji,
                    occurredAt = review.long("reviewed_at"),
                    eventType = event.eventType(),
                    title = event.title(),
                    detail = event.detail(),
                    sourceExpression = source.expression,
                    sourceReading = source.reading,
                    rating = rating,
                    writingRequired = request.writingRequired,
                    writingPassed = request.writingPassed,
                    manualOverride = request.manualOverride,
                    weaknessScore = dashboard?.weaknessScore,
                    matureSupportCount = dashboard?.matureSupportCount,
                    dedupeKey = "review:${request.token}",
                ),
            )
        }
    }

    private fun insertTimelineEvent(
        session: SqlSession,
        event: TimelineEvent,
    ) {
        session.executeMigration(
            """
            INSERT OR IGNORE INTO kanji_timeline_events(
                kanji, occurred_at, event_type, title, detail,
                source_expression, source_reading, rating,
                writing_required, writing_passed, manual_override,
                weakness_score, mature_support_count, sync_id, dedupe_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindText(1, event.kanji)
            bindLong(2, event.occurredAt)
            bindText(3, event.eventType)
            bindText(4, event.title)
            bindText(5, event.detail)
            bindText(6, event.sourceExpression)
            bindText(7, event.sourceReading)
            bindText(8, event.rating)
            bindLong(9, if (event.writingRequired) 1 else 0)
            bindLong(10, if (event.writingPassed) 1 else 0)
            bindLong(11, if (event.manualOverride) 1 else 0)
            bindNullableLong(12, event.weaknessScore?.toLong())
            bindNullableLong(13, event.matureSupportCount?.toLong())
            bindNullableLong(14, event.syncId)
            bindText(15, event.dedupeKey)
        }
    }

    private fun readTimelineDashboardRows(
        session: SqlSession,
    ): Map<String, TimelineDashboardRow> {
        val rows = LinkedHashMap<String, TimelineDashboardRow>()
        session.forEachMigrationRow(
            """
            SELECT kanji, weakness_score, mature_support_count, rebuilt_at
            FROM dashboard_rows
            ORDER BY kanji ASC
            """.trimIndent(),
        ) { row ->
            val kanji = row.text("kanji")
            rows[kanji] = TimelineDashboardRow(
                kanji = kanji,
                weaknessScore = row.int("weakness_score"),
                matureSupportCount = row.int("mature_support_count"),
                rebuiltAt = row.long("rebuilt_at"),
                source = firstExampleSource(session, kanji),
            )
        }
        return rows
    }

    private fun firstExampleSource(
        session: SqlSession,
        kanji: String,
    ): Source {
        val row = session.firstMigrationRow(
            """
            SELECT expression, reading
            FROM kanji_examples
            WHERE kanji = ?
            ORDER BY source_type ASC, id ASC
            LIMIT 1
            """.trimIndent(),
        ) {
            bindText(1, kanji)
        }
        return Source(
            expression = row?.text("expression").orEmpty(),
            reading = row?.text("reading").orEmpty(),
        )
    }

    private fun firstSuspendedSource(
        session: SqlSession,
        kanji: String,
    ): Source {
        val row = session.firstMigrationRow(
            """
            SELECT expression, reading
            FROM suspended_sources
            WHERE kanji = ?
            ORDER BY card_id ASC
            LIMIT 1
            """.trimIndent(),
        ) {
            bindText(1, kanji)
        }
        return Source(
            expression = row?.text("expression").orEmpty(),
            reading = row?.text("reading").orEmpty(),
        )
    }

    private fun readSuspendedImports(
        session: SqlSession,
    ): List<RecordsImportModels.SuspendedImport> {
        val imports = LinkedHashMap<String, MutableSuspendedImport>()
        session.forEachMigrationRow(
            "SELECT * FROM suspended_imports ORDER BY jiten_rank ASC, kanji ASC",
        ) { row ->
            val kanji = row.text("kanji")
            imports[kanji] = MutableSuspendedImport(
                kanji = kanji,
                jitenRank = row.nullableInt("jiten_rank"),
                rankKnown = row.int("rank_known") == 1,
                cutoffUsed = row.int("cutoff_used"),
            )
        }
        session.forEachMigrationRow(
            "SELECT * FROM suspended_sources ORDER BY kanji ASC, card_id ASC",
        ) { row ->
            val imported = imports[row.text("kanji")] ?: return@forEachMigrationRow
            imported.sources += RecordsImportModels.SuspendedSource(
                imported.kanji,
                row.long("card_id"),
                row.long("note_id"),
                row.text("expression"),
                row.text("reading"),
                row.text("meaning"),
                row.text("sentence"),
            )
        }
        return imports.values.map(MutableSuspendedImport::build)
    }

    private fun readDashboardRows(
        session: SqlSession,
    ): List<RecordsImportModels.DashboardRow> {
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        session.forEachMigrationRow(
            "SELECT * FROM dashboard_rows ORDER BY kanji ASC",
        ) { row ->
            val kanji = row.text("kanji")
            rows += RecordsImportModels.DashboardRow(
                kanji,
                row.nullableInt("jiten_rank"),
                row.text("primary_meaning"),
                row.text("reading"),
                row.text("browser_search"),
                row.int("weakness_score"),
                row.text("reason_code"),
                row.text("reason_text"),
                row.int("active_example_count"),
                row.int("suspended_example_count"),
                row.int("mature_support_count"),
                readExamples(session, kanji),
            )
        }
        return rows
    }

    private fun readExamples(
        session: SqlSession,
        kanji: String,
    ): List<RecordsImportModels.Example> {
        val examples = ArrayList<RecordsImportModels.Example>()
        session.forEachMigrationRow(
            """
            SELECT *
            FROM kanji_examples
            WHERE kanji = ?
            ORDER BY source_type DESC, id ASC
            LIMIT 8
            """.trimIndent(),
            bind = { bindText(1, kanji) },
        ) { row ->
            examples += RecordsImportModels.Example(
                row.text("source_type"),
                row.long("card_id"),
                row.long("note_id"),
                row.text("expression"),
                row.text("reading"),
                row.text("meaning"),
                row.text("sentence"),
                row.int("mature") == 1,
                row.int("lapses"),
                row.int("interval_days"),
                row.int("reps"),
                row.nullableDouble("fsrs_stability"),
                row.nullableDouble("fsrs_difficulty"),
                row.nullableDouble("fsrs_retrievability"),
            )
        }
        return examples
    }

    private fun readSimilarChoiceSnapshots(
        session: SqlSession,
    ): Map<String, SimilarChoiceSnapshot> {
        val snapshots = HashMap<String, SimilarChoiceSnapshot>()
        session.forEachMigrationRow("SELECT * FROM similar_kanji_choice_state") { row ->
            val target = row.text("target_kanji")
            val signature = row.text("choice_signature")
            snapshots[SimilarKanjiStorageKeys.choiceKey(target, signature)] =
                SimilarChoiceSnapshot(
                    dueAtMillis = row.long("due_at"),
                    passedAtMillis = row.long("passed_at"),
                    lastReviewedAtMillis = row.long("last_reviewed_at"),
                    correctCount = row.int("correct_count"),
                    wrongCount = row.int("wrong_count"),
                    firstSeenAtMillis = row.long("first_seen_at"),
                )
        }
        return snapshots
    }

    private fun readInventoryItems(
        session: SqlSession,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        val suspended = HashSet<String>()
        session.forEachMigrationRow("SELECT kanji FROM local_kanji_suspensions") { row ->
            suspended += row.text("kanji")
        }
        val items = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        session.forEachMigrationRow(
            "SELECT * FROM kanji_inventory ORDER BY kanji ASC",
        ) { row ->
            val kanji = row.text("kanji")
            items += RecordsImportModels.KanjiInventoryItem(
                kanji,
                row.text("primary_meaning"),
                row.text("readings"),
                row.text("browser_search"),
                row.int("source_count"),
                row.int("example_count"),
                kanji in suspended,
                row.long("last_seen_at"),
            )
        }
        return items
    }

    private fun readSimilarPairs(
        session: SqlSession,
    ): List<RecordsImportModels.SimilarKanjiPair> {
        val pairs = ArrayList<RecordsImportModels.SimilarKanjiPair>()
        session.forEachMigrationRow(
            """
            SELECT *
            FROM similar_kanji_pairs
            ORDER BY kanji_a ASC, kanji_b ASC, source ASC
            """.trimIndent(),
        ) { row ->
            pairs += RecordsImportModels.SimilarKanjiPair(
                row.text("kanji_a"),
                row.text("kanji_b"),
                row.text("source"),
                row.long("first_seen_at"),
                row.long("last_seen_at"),
            )
        }
        return pairs
    }

    private fun readWrongPickCounts(
        session: SqlSession,
        sinceMillis: Long,
    ): Map<String, Map<String, Int>> {
        val counts = HashMap<String, MutableMap<String, Int>>()
        session.forEachMigrationRow(
            """
            SELECT target_kanji, selected_kanji, COUNT(*) AS wrong_count
            FROM similar_kanji_review_log
            WHERE correct = 0
              AND reviewed_at >= ?
              AND selected_kanji <> ''
              AND selected_kanji <> target_kanji
            GROUP BY target_kanji, selected_kanji
            """.trimIndent(),
            bind = { bindLong(1, sinceMillis) },
        ) { row ->
            val target = row.text("target_kanji")
            val selected = row.text("selected_kanji")
            if (target.isNotEmpty() && selected.isNotEmpty()) {
                counts.getOrPut(target, ::HashMap)[selected] = row.int("wrong_count")
            }
        }
        return counts
    }

    private fun hasSuccessfulHistoricalRows(
        session: SqlSession,
        context: MigrationContext,
    ): Boolean =
        session.firstMigrationRow(
            """
            SELECT 1 AS present
            FROM sync_kanji_snapshots snapshots
            JOIN sync_runs runs ON runs.id = snapshots.sync_id
            WHERE runs.status = ?
            LIMIT 1
            """.trimIndent(),
        ) {
            bindText(1, context.defaults.successfulSyncStatus)
        } != null

    private fun latestSuccessfulSyncRun(
        session: SqlSession,
        context: MigrationContext,
    ): HistoricalRun? {
        val row = session.firstMigrationRow(
            """
            SELECT id, started_at, finished_at
            FROM sync_runs
            WHERE status = ?
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent(),
        ) {
            bindText(1, context.defaults.successfulSyncStatus)
        } ?: return null
        return HistoricalRun(
            id = row.long("id"),
            startedAt = row.long("started_at"),
            finishedAt = row.long("finished_at"),
        )
    }

    private fun readHistoricalNotes(
        session: SqlSession,
    ): Map<Long, HistoricalNote> {
        val notes = LinkedHashMap<Long, HistoricalNote>()
        session.forEachMigrationRow(
            "SELECT * FROM source_notes ORDER BY note_id ASC",
        ) { row ->
            val note = HistoricalNote(
                noteId = row.long("note_id"),
                modelId = 0L,
                modelName = row.text("model_name"),
                expression = row.text("expression"),
                reading = row.text("reading"),
                meaning = row.text("meaning"),
                sentence = row.text("sentence"),
                tags = row.text("tags"),
                fieldsJson = row.text("fields_json"),
            )
            notes[note.noteId] = note
        }
        return notes
    }

    private fun insertHistoricalCard(
        session: SqlSession,
        run: HistoricalRun,
        note: HistoricalNote,
        card: MigrationRow,
        deck: String,
        mature: Boolean,
    ) {
        session.executeMigration(
            """
            INSERT OR REPLACE INTO sync_card_snapshots(
                sync_id, started_at, finished_at, card_id, note_id,
                deck_id, deck_name, model_id, model_name, ord, queue, type, due,
                interval_days, reps, lapses, suspended, fsrs_stability,
                fsrs_difficulty, fsrs_retrievability, mature
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindLong(1, run.id)
            bindLong(2, run.startedAt)
            bindLong(3, run.finishedAt)
            bindLong(4, card.long("card_id"))
            bindLong(5, note.noteId)
            bindText(6, deck)
            bindText(7, deck)
            bindLong(8, note.modelId)
            bindText(9, note.modelName)
            bindLong(10, card.long("ord"))
            bindLong(11, card.long("queue"))
            bindLong(12, card.long("type"))
            bindLong(13, card.long("due"))
            bindLong(14, card.long("interval_days"))
            bindLong(15, card.long("reps"))
            bindLong(16, card.long("lapses"))
            bindLong(17, 0)
            bindNullableDouble(18, card.nullableDouble("fsrs_stability"))
            bindNullableDouble(19, card.nullableDouble("fsrs_difficulty"))
            bindNullableDouble(20, card.nullableDouble("fsrs_retrievability"))
            bindLong(21, if (mature) 1 else 0)
        }
    }

    private fun insertHistoricalAggregate(
        session: SqlSession,
        run: HistoricalRun,
        aggregate: HistoricalKanjiAggregate,
    ) {
        session.executeMigration(
            """
            INSERT OR REPLACE INTO sync_kanji_snapshots(
                sync_id, finished_at, kanji, active_cards, suspended_cards,
                mature_support_count, average_interval_days, total_lapses,
                total_reps, fsrs_stability_avg, fsrs_difficulty_avg,
                fsrs_retrievability_avg, weakness_score, reason_code,
                active_example_count, suspended_example_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindLong(1, run.id)
            bindLong(2, run.finishedAt)
            bindText(3, aggregate.kanji())
            bindLong(4, aggregate.activeCards().toLong())
            bindLong(5, aggregate.suspendedCards().toLong())
            bindLong(6, aggregate.matureSupportCount().toLong())
            bindDouble(7, aggregate.averageIntervalDays())
            bindLong(8, aggregate.totalLapses().toLong())
            bindLong(9, aggregate.totalReps().toLong())
            bindNullableDouble(10, aggregate.averageStability())
            bindNullableDouble(11, aggregate.averageDifficulty())
            bindNullableDouble(12, aggregate.averageRetrievability())
            bindLong(13, aggregate.weaknessScore().toLong())
            bindText(14, aggregate.reasonCode())
            bindLong(15, aggregate.activeExampleCount().toLong())
            bindLong(16, aggregate.suspendedExampleCount().toLong())
        }
    }

    private fun aggregateFor(
        aggregates: MutableMap<String, HistoricalKanjiAggregate>,
        kanji: String,
    ): HistoricalKanjiAggregate =
        aggregates.getOrPut(kanji) { HistoricalKanjiAggregate(kanji) }

    private fun timelineTime(
        stored: Long,
        context: MigrationContext,
    ): Long = if (stored == 0L) context.clock.nowMillis() else stored

    private data class Source(
        val expression: String,
        val reading: String,
    )

    private data class TimelineDashboardRow(
        val kanji: String,
        val weaknessScore: Int,
        val matureSupportCount: Int,
        val rebuiltAt: Long,
        val source: Source,
    )

    private data class TimelineEvent(
        val kanji: String,
        val occurredAt: Long,
        val eventType: String,
        val title: String,
        val detail: String,
        val sourceExpression: String = "",
        val sourceReading: String = "",
        val rating: String = "",
        val writingRequired: Boolean = false,
        val writingPassed: Boolean = false,
        val manualOverride: Boolean = false,
        val weaknessScore: Int? = null,
        val matureSupportCount: Int? = null,
        val syncId: Long? = null,
        val dedupeKey: String,
    )

    private data class MutableSuspendedImport(
        val kanji: String,
        val jitenRank: Int?,
        val rankKnown: Boolean,
        val cutoffUsed: Int,
        val sources: MutableList<RecordsImportModels.SuspendedSource> = ArrayList(),
    ) {
        fun build(): RecordsImportModels.SuspendedImport =
            RecordsImportModels.SuspendedImport(
                kanji,
                jitenRank,
                rankKnown,
                cutoffUsed,
                sources,
            )
    }

    private data class SimilarChoiceSnapshot(
        val dueAtMillis: Long,
        val passedAtMillis: Long,
        val lastReviewedAtMillis: Long,
        val correctCount: Int,
        val wrongCount: Int,
        val firstSeenAtMillis: Long,
    )

    private data class HistoricalRun(
        val id: Long,
        val startedAt: Long,
        val finishedAt: Long,
    )

    private data class HistoricalNote(
        val noteId: Long,
        val modelId: Long,
        val modelName: String,
        val expression: String,
        val reading: String,
        val meaning: String,
        val sentence: String,
        val tags: String,
        val fieldsJson: String,
    )

    private const val STUDY_ITEMS = "study_items"
    private const val REVIEW_LOG = "review_log"
    private const val TIMELINE_EVENTS = "kanji_timeline_events"
    private const val KANJI_INVENTORY = "kanji_inventory"
}
