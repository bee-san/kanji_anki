package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarChoiceCodec
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy

internal abstract class LocalStoreHistory(context: Context?) : LocalStoreBase(context) {
    private val historicalSyncStore = HistoricalSyncStore(this)

    private fun timeline(): LocalStoreTimeline = LocalStoreTimeline(this)

    private fun inventoryMaintenance(): LocalStoreInventoryMaintenance = LocalStoreInventoryMaintenance(this)

    private fun similarKanjiMaintenance(): LocalStoreSimilarKanjiMaintenance = LocalStoreSimilarKanjiMaintenance(this)

    private fun similarKanjiData(): LocalStoreSimilarKanjiData = LocalStoreSimilarKanjiData(this)

    private fun inventoryData(): LocalStoreInventoryData = LocalStoreInventoryData(this)

    override fun createTimelineTables(db: SQLiteDatabase) {
        timeline().createTimelineTables(db)
    }

    override fun addNullableColumn(db: SQLiteDatabase, table: String, column: String, type: String) {
        try {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
        } catch (error: RuntimeException) {
            if (error.message == null || !error.message!!.contains("duplicate column")) {
                throw error
            }
        }
    }

    override fun backfillTimelineEvents(db: SQLiteDatabase) {
        timeline().backfillTimelineEvents(db)
    }

    fun defaultTimelineTime(occurredAt: Long): Long {
        return timeline().defaultTimelineTime(occurredAt)
    }

    fun appendSyncTimelineEvents(
        db: SQLiteDatabase,
        previousRows: Map<String, RowSnapshot>,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings,
    ) {
        timeline().appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, occurredAt, settings)
    }

    fun appendStudyStateTimelineEvents(
        db: SQLiteDatabase,
        previousItems: Map<String, StudySnapshot>,
        currentItems: List<RecordsStudyModels.StudyItem>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
    ) {
        timeline().appendStudyStateTimelineEvents(db, previousItems, currentItems, syncId, occurredAt, settings)
    }

    fun appendStudyStateTimelineEvent(
        db: SQLiteDatabase,
        item: RecordsStudyModels.StudyItem,
        previous: StudySnapshot,
        syncId: Long,
        occurredAt: Long,
        target: Int,
    ) {
        timeline().appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target)
    }

    fun appendReviewTimelineEvent(
        db: SQLiteDatabase,
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        dedupeKey: String,
    ) {
        timeline().appendReviewTimelineEvent(db, request, appliedRating, reviewedAt, dedupeKey)
    }

    override fun backfillKanjiInventory(db: SQLiteDatabase, nowMillis: Long, settings: RecordsSyncModels.Settings) {
        inventoryMaintenance().backfillKanjiInventory(db, nowMillis, settings)
    }

    fun dashboardRowsFromDb(db: SQLiteDatabase): List<RecordsImportModels.DashboardRow> {
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(readDashboardRow(db, cursor))
            }
        }
        return rows
    }

    fun suspendedImportsFromDb(db: SQLiteDatabase): List<RecordsImportModels.SuspendedImport> {
        val imports = LinkedHashMap<String, MutableSuspendedImport>()
        db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = string(cursor, COLUMN_KANJI)
                imports[kanji] = MutableSuspendedImport(
                    kanji,
                    nullableInt(cursor, COLUMN_JITEN_RANK),
                    integer(cursor, COLUMN_RANK_KNOWN) == 1,
                    integer(cursor, COLUMN_CUTOFF_USED),
                )
            }
        }
        db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC").use { sources ->
            while (sources.moveToNext()) {
                val imported = imports[string(sources, COLUMN_KANJI)]
                if (imported != null) {
                    imported.sources.add(
                        RecordsImportModels.SuspendedSource(
                            imported.kanji,
                            longValue(sources, COLUMN_CARD_ID),
                            longValue(sources, COLUMN_NOTE_ID),
                            string(sources, COLUMN_EXPRESSION),
                            string(sources, COLUMN_READING),
                            string(sources, COLUMN_MEANING),
                            string(sources, COLUMN_SENTENCE),
                        )
                    )
                }
            }
        }
        val out = ArrayList<RecordsImportModels.SuspendedImport>()
        for (imported in imports.values) {
            out.add(imported.build())
        }
        return out
    }

    fun rebuildKanjiInventory(
        db: SQLiteDatabase,
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        settings: RecordsSyncModels.Settings,
    ) {
        inventoryMaintenance().rebuildKanjiInventory(db, snapshot, imports, rows, nowMillis, settings)
    }

    fun addSnapshotInventory(
        inventory: KanjiInventoryBuilder,
        snapshot: RecordsSyncModels.CollectionSnapshot?,
        settings: RecordsSyncModels.Settings,
    ) {
        if (snapshot == null) {
            return
        }
        val activeIndex = activeCardIndex(snapshot.cards)
        for (note in snapshot.notes) {
            if (activeIndex.noteIds.contains(note.noteId)) {
                inventory.addSnapshotNote(note)
            }
        }
    }

    fun addImportedInventory(inventory: KanjiInventoryBuilder, imports: List<RecordsImportModels.SuspendedImport>) {
        for (imported in imports) {
            inventory.addSuspendedImport(imported)
        }
    }

    fun addDashboardInventory(inventory: KanjiInventoryBuilder, rows: List<RecordsImportModels.DashboardRow>) {
        for (row in rows) {
            inventory.addDashboardRow(row)
        }
    }

    fun addKnownKanji(inventory: KanjiInventoryBuilder, db: SQLiteDatabase, table: String) {
        db.query(true, table, arrayOf(COLUMN_KANJI), null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                inventory.addKnownKanji(string(cursor, COLUMN_KANJI))
            }
        }
    }

    fun writeKanjiInventory(db: SQLiteDatabase, inventory: KanjiInventoryBuilder) {
        for (item in inventory.build(previousInventoryItems(db))) {
            val values = ContentValues()
            values.put(COLUMN_KANJI, item.kanji())
            values.put(COLUMN_PRIMARY_MEANING, item.primaryMeaning())
            values.put("readings", item.readings())
            values.put(COLUMN_BROWSER_SEARCH, item.browserSearch())
            values.put("search_text", item.searchText())
            values.put("source_count", item.sourceCount())
            values.put("example_count", item.exampleCount())
            values.put(COLUMN_FIRST_SEEN_AT, item.firstSeenAtMillis())
            values.put(COLUMN_LAST_SEEN_AT, item.lastSeenAtMillis())
            db.insertWithOnConflict(TABLE_KANJI_INVENTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun rebuildSimilarKanjiPairs(db: SQLiteDatabase, similarIndex: SimilarKanjiIndex, nowMillis: Long) {
        similarKanjiMaintenance().rebuildSimilarKanjiPairs(db, similarIndex, nowMillis)
    }

    override fun rebuildSimilarKanjiChoiceStates(db: SQLiteDatabase, nowMillis: Long) {
        similarKanjiMaintenance().rebuildSimilarKanjiChoiceStates(db, nowMillis)
    }

    fun upsertSimilarKanjiChoiceState(
        db: SQLiteDatabase,
        card: RecordsImportModels.SimilarKanjiChoiceCard,
        old: SimilarChoiceSnapshot?,
        nowMillis: Long,
    ) {
        val values = ContentValues()
        values.put(COLUMN_TARGET_KANJI, card.targetKanji)
        values.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature)
        values.put(COLUMN_PRIMARY_MEANING, card.primaryMeaning)
        values.put(COLUMN_CHOICES, serializeChoices(card.choices))
        values.put(COLUMN_DUE_AT, old?.dueAtMillis ?: 0L)
        values.put(COLUMN_PASSED_AT, old?.passedAtMillis ?: 0L)
        values.put(COLUMN_LAST_REVIEWED_AT, old?.lastReviewedAtMillis ?: 0L)
        values.put(COLUMN_CORRECT_COUNT, old?.correctCount ?: 0)
        values.put(COLUMN_WRONG_COUNT, old?.wrongCount ?: 0)
        values.put(COLUMN_ACTIVE_TOKEN, "")
        values.put(COLUMN_FIRST_SEEN_AT, old?.firstSeenAtMillis ?: nowMillis)
        values.put(COLUMN_LAST_SEEN_AT, nowMillis)
        db.insertWithOnConflict(TABLE_SIMILAR_KANJI_CHOICE_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteStaleSimilarChoiceStates(db: SQLiteDatabase, previousKeys: Set<String>, currentKeys: Set<String>) {
        for (key in previousKeys) {
            val parts = SimilarKanjiStorageKeys.splitChoiceKey(key)
            if (!currentKeys.contains(key) && parts.size == 2) {
                db.delete(TABLE_SIMILAR_KANJI_CHOICE_STATE, WHERE_SIMILAR_CHOICE, arrayOf(parts[0], parts[1]))
                db.delete(
                    TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                    "status=? AND target_kanji=? AND choice_signature=?",
                    arrayOf(STATUS_PENDING, parts[0], parts[1]),
                )
            }
        }
    }

    fun similarPairFirstSeen(db: SQLiteDatabase): Map<String, Long> = similarKanjiData().similarPairFirstSeen(db)

    fun localInventoryKanji(db: SQLiteDatabase): Set<String> = similarKanjiData().localInventoryKanji(db)

    fun readSimilarPair(cursor: Cursor): RecordsImportModels.SimilarKanjiPair = similarKanjiData().readSimilarPair(cursor)

    fun allSimilarPairs(db: SQLiteDatabase): List<RecordsImportModels.SimilarKanjiPair> = similarKanjiData().allSimilarPairs(db)

    fun allInventoryItems(db: SQLiteDatabase): List<RecordsImportModels.KanjiInventoryItem> = similarKanjiData().allInventoryItems(db)

    fun similarChoiceSnapshots(db: SQLiteDatabase): Map<String, SimilarChoiceSnapshot> = similarKanjiData().similarChoiceSnapshots(db)

    fun similarChoiceCard(
        db: SQLiteDatabase,
        targetKanji: String,
        choiceSignature: String,
    ): RecordsImportModels.SimilarKanjiChoiceCard? {
        db.query(
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            WHERE_SIMILAR_CHOICE,
            arrayOf(targetKanji, choiceSignature),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readSimilarChoiceCard(cursor) else null
        }
    }

    fun readSimilarChoiceCard(cursor: Cursor): RecordsImportModels.SimilarKanjiChoiceCard = similarKanjiData().readSimilarChoiceCard(cursor)

    fun hasPendingSimilarRepairs(db: SQLiteDatabase, targetKanji: String, choiceSignature: String): Boolean {
        return similarKanjiData().hasPendingSimilarRepairs(db, targetKanji, choiceSignature)
    }

    fun enqueueSimilarWritingRepair(
        db: SQLiteDatabase,
        card: RecordsImportModels.SimilarKanjiChoiceCard,
        repairKanji: String,
        wrongSelection: String,
        nowMillis: Long,
    ) {
        similarKanjiData().enqueueSimilarWritingRepair(db, card, repairKanji, wrongSelection, nowMillis)
    }

    fun similarWritingRepair(db: SQLiteDatabase, repairId: Long): RecordsImportModels.SimilarKanjiWritingRepair? {
        return similarKanjiData().similarWritingRepair(db, repairId)
    }

    fun readSimilarWritingRepair(cursor: Cursor): RecordsImportModels.SimilarKanjiWritingRepair {
        return similarKanjiData().readSimilarWritingRepair(cursor)
    }

    fun readInventoryItem(db: SQLiteDatabase, kanji: String?): RecordsImportModels.KanjiInventoryItem? {
        return inventoryData().readInventoryItem(db, kanji)
    }

    fun readInventoryItem(db: SQLiteDatabase, cursor: Cursor): RecordsImportModels.KanjiInventoryItem {
        return inventoryData().readInventoryItem(db, cursor)
    }

    fun isKanjiSuspended(db: SQLiteDatabase, kanji: String): Boolean {
        db.query(TABLE_LOCAL_KANJI_SUSPENSIONS, arrayOf(COLUMN_KANJI), WHERE_KANJI, arrayOf(kanji), null, null, null, "1").use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun previousInventoryItems(db: SQLiteDatabase): Map<String, KanjiInventoryBuilder.PreviousItem> {
        val previous = LinkedHashMap<String, KanjiInventoryBuilder.PreviousItem>()
        db.query(TABLE_KANJI_INVENTORY, null, null, null, null, null, ORDER_KANJI_ASC).use { cursor ->
            while (cursor.moveToNext()) {
                previous[string(cursor, COLUMN_KANJI)] = KanjiInventoryBuilder.PreviousItem(
                    string(cursor, COLUMN_PRIMARY_MEANING),
                    string(cursor, "readings"),
                    string(cursor, COLUMN_BROWSER_SEARCH),
                    integer(cursor, "source_count"),
                    integer(cursor, "example_count"),
                    longValue(cursor, COLUMN_FIRST_SEEN_AT),
                    longValue(cursor, COLUMN_LAST_SEEN_AT),
                )
            }
        }
        return previous
    }

    fun readDashboardRow(db: SQLiteDatabase, kanji: String?): RecordsImportModels.DashboardRow? {
        return inventoryData().readDashboardRow(db, kanji)
    }

    fun readDashboardRow(db: SQLiteDatabase, cursor: Cursor): RecordsImportModels.DashboardRow {
        return inventoryData().readDashboardRow(db, cursor)
    }

    fun studyItemForKanji(db: SQLiteDatabase, kanji: String): RecordsStudyModels.StudyItem? {
        return inventoryData().studyItemForKanji(db, kanji)
    }

    fun kanjiHasSimilarNeighbor(db: SQLiteDatabase, kanji: String): Boolean {
        return inventoryData().kanjiHasSimilarNeighbor(db, kanji)
    }

    fun readTimelineEvent(cursor: Cursor): RecordsImportModels.KanjiTimelineEvent {
        return timeline().readTimelineEvent(cursor)
    }

    fun insertTimelineEvent(
        db: SQLiteDatabase,
        kanji: String,
        occurredAt: Long,
        eventType: String,
        title: String,
        detail: String,
        vararg eventValues: Any?,
    ) {
        timeline().insertTimelineEvent(db, kanji, occurredAt, eventType, title, detail, *eventValues)
    }

    fun rowSnapshots(db: SQLiteDatabase): Map<String, RowSnapshot> {
        val rows = LinkedHashMap<String, RowSnapshot>()
        db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC).use { cursor ->
            while (cursor.moveToNext()) {
                val row = rowSnapshotFromCursor(db, cursor)
                rows[row.kanji] = row
            }
        }
        return rows
    }

    fun rowSnapshot(db: SQLiteDatabase, kanji: String): RowSnapshot? {
        db.query(TABLE_DASHBOARD_ROWS, null, WHERE_KANJI, arrayOf(kanji), null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) rowSnapshotFromCursor(db, cursor) else null
        }
    }

    fun rowSnapshotFromCursor(db: SQLiteDatabase, cursor: Cursor): RowSnapshot {
        val kanji = string(cursor, COLUMN_KANJI)
        return RowSnapshot(
            kanji,
            integer(cursor, COLUMN_WEAKNESS_SCORE),
            integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
            longValue(cursor, "rebuilt_at"),
            firstExampleForKanji(db, kanji),
        )
    }

    fun studySnapshots(db: SQLiteDatabase): Map<String, StudySnapshot> {
        val items = HashMap<String, StudySnapshot>()
        db.query(TABLE_STUDY_ITEMS, arrayOf(COLUMN_KANJI, COLUMN_ANSWER_SIGNATURE, COLUMN_STATE), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = string(cursor, COLUMN_KANJI)
                val answerSignature = string(cursor, COLUMN_ANSWER_SIGNATURE)
                items[studyFamilyKey(kanji, answerSignature)] = StudySnapshot(string(cursor, COLUMN_STATE))
            }
        }
        return items
    }

    fun firstExampleForKanji(db: SQLiteDatabase, kanji: String): SourceSnapshot {
        db.query(TABLE_KANJI_EXAMPLES, arrayOf(COLUMN_EXPRESSION, COLUMN_READING), WHERE_KANJI, arrayOf(kanji), null, null, "source_type ASC, id ASC", "1").use { cursor ->
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY
            }
            return SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING))
        }
    }

    fun firstSuspendedSourceForKanji(db: SQLiteDatabase, kanji: String): SourceSnapshot {
        db.query(TABLE_SUSPENDED_SOURCES, arrayOf(COLUMN_EXPRESSION, COLUMN_READING), WHERE_KANJI, arrayOf(kanji), null, null, "card_id ASC", "1").use { cursor ->
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY
            }
            return SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING))
        }
    }

    fun sourceFromImport(imported: RecordsImportModels.SuspendedImport): SourceSnapshot {
        if (imported.sources.isEmpty()) {
            return SourceSnapshot.EMPTY
        }
        val source = imported.sources[0]
        return SourceSnapshot(source.expression, source.reading)
    }

    fun sourceForRow(row: RecordsImportModels.DashboardRow): SourceSnapshot {
        var fallback: RecordsImportModels.Example? = null
        for (example in row.examples) {
            if ("active" == example.sourceType) {
                return SourceSnapshot(example.expression, example.reading)
            }
            if (fallback == null) {
                fallback = example
            }
        }
        val selected = fallback ?: return SourceSnapshot.EMPTY
        return SourceSnapshot(selected.expression, selected.reading)
    }

    fun saveRows(db: SQLiteDatabase, rows: List<RecordsImportModels.DashboardRow>, rebuiltAt: Long) {
        inventoryMaintenance().saveRows(db, rows, rebuiltAt)
    }

    fun appendHistoricalSyncSnapshots(
        db: SQLiteDatabase,
        snapshot: RecordsSyncModels.CollectionSnapshot,
        notesById: Map<Long, RecordsSyncModels.Note>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        syncId: Long,
        timing: SyncTiming,
    ) {
        historicalSyncStore.appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing)
    }

    override fun backfillLatestHistoricalSync(db: SQLiteDatabase) {
        historicalSyncStore.backfillLatestHistoricalSync(db)
    }

    fun insertHistoricalKanjiAggregates(
        db: SQLiteDatabase,
        syncId: Long,
        finishedAt: Long,
        aggregates: Map<String, HistoricalKanjiAggregate>,
    ) {
        historicalSyncStore.insertHistoricalKanjiAggregates(db, syncId, finishedAt, aggregates)
    }

    fun examplesForKanji(db: SQLiteDatabase, kanji: String): List<RecordsImportModels.Example> {
        val examples = ArrayList<RecordsImportModels.Example>()
        db.query(TABLE_KANJI_EXAMPLES, null, WHERE_KANJI, arrayOf(kanji), null, null, "source_type DESC, id ASC", "8").use { cursor ->
            while (cursor.moveToNext()) {
                examples.add(
                    RecordsImportModels.Example(
                        string(cursor, "source_type"),
                        longValue(cursor, COLUMN_CARD_ID),
                        longValue(cursor, COLUMN_NOTE_ID),
                        string(cursor, COLUMN_EXPRESSION),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_MEANING),
                        string(cursor, COLUMN_SENTENCE),
                        integer(cursor, COLUMN_MATURE) == 1,
                        integer(cursor, COLUMN_LAPSES),
                        integer(cursor, COLUMN_INTERVAL_DAYS),
                        integer(cursor, COLUMN_REPS),
                        nullableDouble(cursor, COLUMN_FSRS_STABILITY),
                        nullableDouble(cursor, COLUMN_FSRS_DIFFICULTY),
                        nullableDouble(cursor, COLUMN_FSRS_RETRIEVABILITY),
                    )
                )
            }
        }
        return examples
    }

    fun upsertStudyItem(db: SQLiteDatabase, item: RecordsStudyModels.StudyItem) {
        val values = ContentValues()
        values.put(COLUMN_KANJI, item.kanji)
        values.put(COLUMN_STATE, item.state)
        values.put(COLUMN_DUE_AT, item.dueAtMillis)
        values.put("stability", item.stability)
        values.put("difficulty", item.difficulty)
        values.put("total_reviews", item.totalReviews)
        values.put(COLUMN_LAPSES, item.lapses)
        values.put("learning_step", item.learningStep)
        values.put("writing_level", item.writingLevel)
        values.put(COLUMN_RECOGNITION_STAGE, item.recognitionStage)
        values.put(COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS, item.consecutiveFailedRecognitionDays)
        values.put(COLUMN_LAST_FAILED_RECOGNITION_DAY, item.lastFailedRecognitionDayMillis)
        values.put(COLUMN_WRITING_REMEDIATION_PENDING, if (item.writingRemediationPending) 1 else 0)
        values.put(COLUMN_SUPPRESSED_BY_TASK_TYPE, item.suppressedByTaskType)
        values.put(COLUMN_SUPPRESSED_AT, item.suppressedAtMillis)
        values.put(COLUMN_MATURE_INTERVAL_DAYS, item.matureIntervalDays)
        values.put(COLUMN_ANSWER_SIGNATURE, item.answerSignature)
        values.put(COLUMN_TYPING_MEANING_MEMORY, item.typingMeaningMemory.encode())
        values.put(COLUMN_MEANING_KANJI_MEMORY, item.meaningKanjiMemory.encode())
        values.put(COLUMN_KANJI_MEANING_MEMORY, item.kanjiMeaningMemory.encode())
        values.put(COLUMN_FONT_MEANING_MEMORY, item.fontMeaningMemory.encode())
        values.put(COLUMN_WORD_READING_MEMORY, item.wordReadingMemory.encode())
        values.put(COLUMN_WRITING_REMEDIATION_MEMORY, item.writingRemediationMemory.encode())
        values.put(COLUMN_RUNG, (item.rung ?: RecordsBase.LadderRung.KANJI_MEANING).wireName())
        values.put(COLUMN_PHASE, (item.phase ?: RecordsBase.SchedulerPhase.NEW_LEARNING).wireName())
        values.put(COLUMN_REAL_PASS_STREAK, item.realPassStreak)
        values.put(COLUMN_REAL_AGAIN_STREAK, item.realAgainStreak)
        values.put(COLUMN_LAST_REAL_REVIEW_DUE_AT, item.lastRealReviewDueAtMillis)
        values.put(COLUMN_SIMILAR_KANJI_MEMORY, item.similarKanjiMemory?.encode() ?: "")
        values.put(COLUMN_ACTIVE_TOKEN, item.activeToken)
        values.put(COLUMN_CREATED_AT, item.createdAtMillis)
        db.insertWithOnConflict(TABLE_STUDY_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun readStudyItem(cursor: Cursor): RecordsStudyModels.StudyItem {
        val state = string(cursor, COLUMN_STATE)
        val dueAt = longValue(cursor, COLUMN_DUE_AT)
        val stability = cursor.getDouble(cursor.getColumnIndexOrThrow("stability"))
        val difficulty = cursor.getDouble(cursor.getColumnIndexOrThrow("difficulty"))
        val totalReviews = integer(cursor, "total_reviews")
        val lapses = integer(cursor, COLUMN_LAPSES)
        val learningStep = integer(cursor, "learning_step")
        val recognitionStage = integer(cursor, COLUMN_RECOGNITION_STAGE)
        val writingRemediationPending = integer(cursor, COLUMN_WRITING_REMEDIATION_PENDING) == 1
        val matureIntervalDays = integer(cursor, COLUMN_MATURE_INTERVAL_DAYS)
        val memoryFields = StudyMemoryFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
        val typingFallback = taskMemoryFallback(-1, recognitionStage, memoryFields)
        val kanjiFallback = taskMemoryFallback(0, recognitionStage, memoryFields)
        val fontFallback = taskMemoryFallback(1, recognitionStage, memoryFields)
        val wordFallback = taskMemoryFallback(2, recognitionStage, memoryFields)
        val writingFallback = if (writingRemediationPending) {
            RecordsStudyModels.TaskMemory.fromStudyFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
        } else {
            RecordsStudyModels.TaskMemory.initial()
        }
        val rung = RecordsBase.LadderRung.fromWireName(string(cursor, COLUMN_RUNG))
        val phase = RecordsBase.SchedulerPhase.fromWireName(string(cursor, COLUMN_PHASE))
        val realPassStreak = integer(cursor, COLUMN_REAL_PASS_STREAK)
        val realAgainStreak = integer(cursor, COLUMN_REAL_AGAIN_STREAK)
        val lastRealReviewDueAtMillis = longValue(cursor, COLUMN_LAST_REAL_REVIEW_DUE_AT)
        val similarKanjiMemory = RecordsStudyModels.TaskMemory.decode(
            string(cursor, COLUMN_SIMILAR_KANJI_MEMORY),
            RecordsStudyModels.TaskMemory.initial(),
        )
        return RecordsStudyModels.StudyItem(
            string(cursor, COLUMN_KANJI),
            state,
            dueAt,
            stability,
            difficulty,
            totalReviews,
            lapses,
            learningStep,
            integer(cursor, "writing_level"),
            recognitionStage,
            integer(cursor, COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS),
            longValue(cursor, COLUMN_LAST_FAILED_RECOGNITION_DAY),
            writingRemediationPending,
            string(cursor, COLUMN_SUPPRESSED_BY_TASK_TYPE),
            longValue(cursor, COLUMN_SUPPRESSED_AT),
            matureIntervalDays,
            string(cursor, COLUMN_ANSWER_SIGNATURE),
            string(cursor, COLUMN_ACTIVE_TOKEN),
            longValue(cursor, COLUMN_CREATED_AT),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_TYPING_MEANING_MEMORY), typingFallback),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_MEANING_KANJI_MEMORY), RecordsStudyModels.TaskMemory.initial()),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_KANJI_MEANING_MEMORY), kanjiFallback),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_FONT_MEANING_MEMORY), fontFallback),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_WORD_READING_MEMORY), wordFallback),
            RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_WRITING_REMEDIATION_MEMORY), writingFallback),
            rung,
            phase,
            realPassStreak,
            realAgainStreak,
            lastRealReviewDueAtMillis,
            false,
            similarKanjiMemory,
        )
    }

    fun readLearningRepeat(cursor: Cursor): RecordsSchedulerModels.LearningRepeat {
        return RecordsSchedulerModels.LearningRepeat(
            string(cursor, COLUMN_KANJI),
            string(cursor, COLUMN_ANSWER_SIGNATURE),
            string(cursor, COLUMN_TASK_TYPE),
            string(cursor, "repeat_type"),
            integer(cursor, "step_index"),
            longValue(cursor, COLUMN_DUE_AT),
            string(cursor, COLUMN_ACTIVE_TOKEN),
            longValue(cursor, COLUMN_CREATED_AT),
            longValue(cursor, COLUMN_UPDATED_AT),
        )
    }

    fun taskMemoryFallback(
        memoryStage: Int,
        recognitionStage: Int,
        fields: StudyMemoryFields,
    ): RecordsStudyModels.TaskMemory {
        return if (recognitionStage.coerceIn(-1, 2) == memoryStage) {
            RecordsStudyModels.TaskMemory.fromStudyFields(
                fields.state(),
                fields.dueAtMillis(),
                fields.stability(),
                fields.difficulty(),
                fields.totalReviews(),
                fields.lapses(),
                fields.learningStep(),
                fields.matureIntervalDays(),
            )
        } else {
            RecordsStudyModels.TaskMemory.initial()
        }
    }

    fun firstImportedAt(db: SQLiteDatabase, kanji: String, fallback: Long): Long {
        db.query(TABLE_SUSPENDED_IMPORTS, arrayOf(COLUMN_FIRST_IMPORTED_AT), WHERE_KANJI, arrayOf(kanji), null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) longValue(cursor, COLUMN_FIRST_IMPORTED_AT) else fallback
        }
    }

    fun selectedSuspendedCardIds(imports: List<RecordsImportModels.SuspendedImport>): Set<Long> {
        val sources = ArrayList<SyncMirrorPolicy.SelectedSource>()
        for (imported in imports) {
            for (source in imported.sources) {
                sources.add(SyncMirrorPolicy.SelectedSource(source.cardId, source.suspended))
            }
        }
        return SyncMirrorPolicy.selectedSuspendedCardIds(sources)
    }

    fun activeCardIndex(cards: List<RecordsSyncModels.Card>): ActiveCardIndex {
        val policyCards = ArrayList<SyncMirrorPolicy.Card>()
        for (card in cards) {
            policyCards.add(SyncMirrorPolicy.Card(card.cardId, card.noteId, card.suspended))
        }
        val index = SyncMirrorPolicy.activeCardIndex(policyCards)
        return ActiveCardIndex(index.noteIds(), index.cardIds(), index.activeCardCount())
    }

    fun countDeletedExisting(db: SQLiteDatabase, table: String, idColumn: String, currentIds: Set<Long>): Int {
        var missing = 0
        db.query(table, arrayOf(idColumn), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                if (!currentIds.contains(cursor.getLong(0))) {
                    missing++
                }
            }
        }
        return missing
    }

    companion object {
        @JvmStatic
        fun serializeChoices(choices: List<String>): String = SimilarChoiceCodec.serializeChoices(choices)

        @JvmStatic
        fun deserializeChoices(encoded: String): List<String> = SimilarChoiceCodec.deserializeChoices(encoded)

        @JvmStatic
        fun normalizeSingleKanji(value: String?): String = TextUtil.normalizeSingleKanji(value)

        @JvmStatic
        fun canonicalSimilarPair(first: String?, second: String?): Array<String> {
            return SimilarKanjiStorageKeys.canonicalPair(first, second)
        }

        @JvmStatic
        fun similarKey(first: String?, second: String?, source: String?): String {
            return SimilarKanjiStorageKeys.pairKey(first, second, source)
        }

        @JvmStatic
        fun similarChoiceKey(targetKanji: String?, choiceSignature: String?): String {
            return SimilarKanjiStorageKeys.choiceKey(targetKanji, choiceSignature)
        }

        @JvmStatic
        fun stringValueAt(values: Array<out Any?>, index: Int): String {
            return if (values.size > index && values[index] is String) values[index] as String else ""
        }

        @JvmStatic
        fun booleanValueAt(values: Array<out Any?>, index: Int): Boolean {
            return values.size > index && values[index] is Boolean && values[index] as Boolean
        }

        @JvmStatic
        fun integerValueAt(values: Array<out Any?>, index: Int): Int? {
            return if (values.size > index && values[index] is Int) values[index] as Int else null
        }

        @JvmStatic
        fun longValueAt(values: Array<out Any?>, index: Int): Long? {
            return if (values.size > index && values[index] is Long) values[index] as Long else null
        }
    }
}
