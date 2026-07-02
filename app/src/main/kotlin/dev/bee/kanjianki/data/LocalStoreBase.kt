package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy
import java.util.LinkedHashSet

abstract class LocalStoreBase internal constructor(context: Context?) : SQLiteOpenHelper(
    context!!.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {
    private val settingsRepository = SettingsRepository(SqliteSettingsStorage(this))

    internal fun settingsRepository(): SettingsRepository = settingsRepository

    internal fun migrationHooks(): LocalStoreMigrationHooks = LocalStoreMigrationHooks(this)

    abstract fun createTimelineTables(db: SQLiteDatabase)
    abstract fun backfillTimelineEvents(db: SQLiteDatabase)
    abstract fun addNullableColumn(db: SQLiteDatabase, table: String, column: String, type: String)
    abstract fun backfillKanjiInventory(db: SQLiteDatabase, nowMillis: Long, settings: RecordsSyncModels.Settings)
    abstract fun rebuildSimilarKanjiChoiceStates(db: SQLiteDatabase, nowMillis: Long)
    abstract fun backfillLatestHistoricalSync(db: SQLiteDatabase)

    override fun onCreate(db: SQLiteDatabase) {
        LocalStoreSchema.createInitialTables(db, migrationHooks())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        LocalStoreMigrations.upgrade(db, oldVersion, newVersion, migrationHooks())
    }

    fun rebuildStudyItemsForLadderScheduler(db: SQLiteDatabase) {
        for (sql in StudySchedulerMigration.rebuildLadderStudyItemsSql(
            TABLE_STUDY_ITEMS,
            STUDY_ITEMS_TABLE_SQL.replace(SQL_CREATE_TABLE, SQL_CREATE_TABLE_IF_NEEDED),
            TABLE_LEARNING_REPEATS,
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
        )) {
            db.execSQL(sql)
        }
    }

    fun createStudyTaskLogTable(db: SQLiteDatabase) {
        LocalStoreTableCreator.createStudyTaskLogTable(db)
    }

    /**
     * Clears legacy mature-sibling suppression flags. The suppression layer
     * was removed because a study-item family can never contain more than one
     * item (the family key equals the table primary key), so a dominating
     * sibling could never exist; stale flags from older builds must not keep
     * hiding items now that no code path clears them.
     */
    fun clearStaleSuppressionFlags(db: SQLiteDatabase) {
        db.execSQL(
            "UPDATE " + TABLE_STUDY_ITEMS +
                " SET " + COLUMN_SUPPRESSED_BY_TASK_TYPE + " = '', " + COLUMN_SUPPRESSED_AT + " = 0" +
                " WHERE " + COLUMN_SUPPRESSED_BY_TASK_TYPE + " <> ''"
        )
    }

    fun createStatsIndexes(db: SQLiteDatabase) {
        LocalStoreTableCreator.createStatsIndexes(db)
    }

    fun createStatsCacheTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createStatsCacheTables(db)
    }

    fun ensureStatsAggregateStorage(db: SQLiteDatabase) {
        db.execSQL(REVIEW_LOG_TABLE_SQL.replace(SQL_CREATE_TABLE, SQL_CREATE_TABLE_IF_NEEDED))
        addRichReviewColumns(db)
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_REVIEW_DAY_START, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        createStudyTaskLogTable(db)
        createTimelineTables(db)
        createHistoricalSyncTables(db)
        createStatsIndexes(db)
    }

    fun repairHistoricalSyncSnapshotsIfPossible(db: SQLiteDatabase) {
        createHistoricalSyncTables(db)
        backfillLatestHistoricalSync(db)
    }

    fun createKanjiInventoryTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createKanjiInventoryTables(db)
    }

    fun createSimilarKanjiTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createSimilarKanjiTables(db)
    }

    fun createSimilarKanjiPracticeTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createSimilarKanjiPracticeTables(db)
    }

    fun createHistoricalSyncTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createHistoricalSyncTables(db)
    }

    fun createImportAuditTables(db: SQLiteDatabase) {
        LocalStoreTableCreator.createImportAuditTables(db)
    }

    fun addHistoricalIdentityColumns(db: SQLiteDatabase) {
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_DECK_ID, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_SYNC_CARD_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_MODEL_ID, SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        addNullableColumn(db, TABLE_SYNC_NOTE_SNAPSHOTS, COLUMN_DECK_IDS, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        db.execSQL("UPDATE $TABLE_SYNC_CARD_SNAPSHOTS SET deck_id=deck_name WHERE deck_id=''")
        db.execSQL("UPDATE $TABLE_SYNC_NOTE_SNAPSHOTS SET deck_ids=deck_names WHERE deck_ids=''")
    }

    fun addRichReviewColumns(db: SQLiteDatabase) {
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_TASK_TYPE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, COLUMN_ANSWER_SIGNATURE, SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, "prompt", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, "hints_used", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        addNullableColumn(db, TABLE_REVIEW_LOG, "writing_clean", SQL_INTEGER_NOT_NULL_DEFAULT_ZERO)
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_before", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, "memory_after", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, "scheduler_state_before_json", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
        addNullableColumn(db, TABLE_REVIEW_LOG, "scheduler_state_after_json", SQL_TEXT_NOT_NULL_DEFAULT_EMPTY)
    }

    fun rebuildStudyItemsWithAnswerSignatureKey(db: SQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS idx_study_due")
        db.execSQL("ALTER TABLE study_items RENAME TO study_items_old")
        db.execSQL(STUDY_ITEMS_TABLE_SQL)
        db.execSQL("INSERT OR REPLACE INTO study_items (kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, answer_signature, kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, active_token, created_at) SELECT kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, recognition_stage, consecutive_failed_recognition_days, last_failed_recognition_day, writing_remediation_pending, suppressed_by_task_type, suppressed_at, mature_interval_days, COALESCE(answer_signature, ''), kanji_meaning_memory, font_meaning_memory, word_reading_memory, writing_remediation_memory, active_token, created_at FROM study_items_old")
        db.execSQL("DROP TABLE study_items_old")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_due ON study_items(state, due_at)")
    }

    class SyncTiming(
        @JvmField val startedAt: Long,
        @JvmField val finishedAt: Long,
    )

    class MutableSuspendedImport(
        @JvmField val kanji: String,
        @JvmField val rank: Int?,
        @JvmField val rankKnown: Boolean,
        @JvmField val cutoff: Int,
    ) {
        @JvmField val sources: MutableList<RecordsImportModels.SuspendedSource> = ArrayList()

        fun build(): RecordsImportModels.SuspendedImport {
            return RecordsImportModels.SuspendedImport(kanji, rank, rankKnown, cutoff, sources)
        }
    }

    class ActiveCardIndex(
        @JvmField val noteIds: Set<Long>,
        @JvmField val cardIds: Set<Long>,
        @JvmField val activeCardCount: Int,
    )

    data class SyncRunInsert(
        @JvmField val startedAt: Long,
        @JvmField val finishedAt: Long,
        @JvmField val status: String?,
        @JvmField val activeIndex: ActiveCardIndex,
        @JvmField val archivedSuspendedCardCount: Int,
        @JvmField val importCount: Int,
        @JvmField val errorCode: String?,
        @JvmField val errorMessage: String?,
        @JvmField val removalMessage: String?,
        @JvmField val deletedNotes: Int,
        @JvmField val deletedCards: Int,
    ) {
        fun startedAt(): Long = startedAt
        fun finishedAt(): Long = finishedAt
        fun status(): String? = status
        fun activeIndex(): ActiveCardIndex = activeIndex
        fun archivedSuspendedCardCount(): Int = archivedSuspendedCardCount
        fun importCount(): Int = importCount
        fun errorCode(): String? = errorCode
        fun errorMessage(): String? = errorMessage
        fun removalMessage(): String? = removalMessage
        fun deletedNotes(): Int = deletedNotes
        fun deletedCards(): Int = deletedCards
    }

    data class StudyMemoryFields(
        @JvmField val state: String,
        @JvmField val dueAtMillis: Long,
        @JvmField val stability: Double,
        @JvmField val difficulty: Double,
        @JvmField val totalReviews: Int,
        @JvmField val lapses: Int,
        @JvmField val learningStep: Int,
        @JvmField val matureIntervalDays: Int,
    ) {
        fun state(): String = state
        fun dueAtMillis(): Long = dueAtMillis
        fun stability(): Double = stability
        fun difficulty(): Double = difficulty
        fun totalReviews(): Int = totalReviews
        fun lapses(): Int = lapses
        fun learningStep(): Int = learningStep
        fun matureIntervalDays(): Int = matureIntervalDays
    }

    class HistoricalSyncRun(
        @JvmField val id: Long,
        @JvmField val startedAt: Long,
        @JvmField val finishedAt: Long,
    )

    class HistoricalNoteSnapshot(fields: HistoricalNoteFields) {
        @JvmField val noteId: Long = fields.noteId()
        @JvmField val modelId: Long = fields.modelId()
        @JvmField val modelName: String = nullToEmpty(fields.modelName())
        @JvmField val expression: String = nullToEmpty(fields.expression())
        @JvmField val reading: String = nullToEmpty(fields.reading())
        @JvmField val meaning: String = nullToEmpty(fields.meaning())
        @JvmField val sentence: String = nullToEmpty(fields.sentence())
        @JvmField val tags: String = nullToEmpty(fields.tags())
        @JvmField val fieldsJson: String = nullToEmpty(fields.fieldsJson())

        companion object {
            @JvmStatic
            fun nullToEmpty(value: String?): String = value ?: ""
        }
    }

    data class HistoricalNoteFields(
        @JvmField val noteId: Long,
        @JvmField val modelId: Long,
        @JvmField val modelName: String?,
        @JvmField val expression: String?,
        @JvmField val reading: String?,
        @JvmField val meaning: String?,
        @JvmField val sentence: String?,
        @JvmField val tags: String?,
        @JvmField val fieldsJson: String?,
    ) {
        fun noteId(): Long = noteId
        fun modelId(): Long = modelId
        fun modelName(): String? = modelName
        fun expression(): String? = expression
        fun reading(): String? = reading
        fun meaning(): String? = meaning
        fun sentence(): String? = sentence
        fun tags(): String? = tags
        fun fieldsJson(): String? = fieldsJson
    }

    data class HistoricalCardMetrics(
        @JvmField val intervalDays: Int,
        @JvmField val reps: Int,
        @JvmField val lapses: Int,
        @JvmField val mature: Boolean,
    ) {
        fun intervalDays(): Int = intervalDays
        fun reps(): Int = reps
        fun lapses(): Int = lapses
        fun mature(): Boolean = mature
    }

    data class HistoricalBackfillContext(
        @JvmField val settings: RecordsSyncModels.Settings,
        @JvmField val deckIdsByNote: MutableMap<Long, LinkedHashSet<String>>,
        @JvmField val deckNamesByNote: MutableMap<Long, LinkedHashSet<String>>,
        @JvmField val aggregates: MutableMap<String, HistoricalKanjiAggregate>,
    ) {
        fun settings(): RecordsSyncModels.Settings = settings
        fun deckIdsByNote(): MutableMap<Long, LinkedHashSet<String>> = deckIdsByNote
        fun deckNamesByNote(): MutableMap<Long, LinkedHashSet<String>> = deckNamesByNote
        fun aggregates(): MutableMap<String, HistoricalKanjiAggregate> = aggregates
    }

    class SimilarChoiceSnapshot(
        @JvmField val dueAtMillis: Long,
        @JvmField val passedAtMillis: Long,
        @JvmField val lastReviewedAtMillis: Long,
        @JvmField val correctCount: Int,
        @JvmField val wrongCount: Int,
        @JvmField val firstSeenAtMillis: Long,
    )

    class SourceSnapshot(expression: String?, reading: String?) {
        @JvmField val expression: String = expression ?: ""
        @JvmField val reading: String = reading ?: ""

        companion object {
            @JvmField val EMPTY: SourceSnapshot = SourceSnapshot("", "")
        }
    }

    class RowSnapshot(
        @JvmField val kanji: String,
        @JvmField val weaknessScore: Int,
        @JvmField val matureSupportCount: Int,
        @JvmField val rebuiltAt: Long,
        source: SourceSnapshot?,
    ) {
        @JvmField val source: SourceSnapshot = source ?: SourceSnapshot.EMPTY
    }

    class StudySnapshot(state: String?) {
        @JvmField val state: String = state ?: ""
    }

    class SyncStatus(values: SyncStatusValues) {
        @JvmField val status: String = values.status()
        @JvmField val activeNotes: Int = values.activeNotes()
        @JvmField val activeCards: Int = values.activeCards()
        @JvmField val suspendedCards: Int = values.suspendedCards()
        @JvmField val importedKanji: Int = values.importedKanji()
        @JvmField val finishedAt: Long = values.finishedAt()
        @JvmField val errorMessage: String = values.errorMessage()
        @JvmField val removalMessage: String = values.removalMessage()
    }

    data class SyncStatusValues(
        @JvmField val status: String,
        @JvmField val activeNotes: Int,
        @JvmField val activeCards: Int,
        @JvmField val suspendedCards: Int,
        @JvmField val importedKanji: Int,
        @JvmField val finishedAt: Long,
        @JvmField val errorMessage: String,
        @JvmField val removalMessage: String,
    ) {
        fun status(): String = status
        fun activeNotes(): Int = activeNotes
        fun activeCards(): Int = activeCards
        fun suspendedCards(): Int = suspendedCards
        fun importedKanji(): Int = importedKanji
        fun finishedAt(): Long = finishedAt
        fun errorMessage(): String = errorMessage
        fun removalMessage(): String = removalMessage
    }

    class ReminderSettings(
        @JvmField val enabled: Boolean,
        @JvmField val hour: Int,
        @JvmField val minute: Int,
    ) {
        fun normalized(): ReminderSettings {
            val normalized = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute)
            return ReminderSettings(normalized.enabled, normalized.hour, normalized.minute)
        }

        fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    class AutoSyncSettings(
        @JvmField val configured: Boolean,
        @JvmField val enabled: Boolean,
        @JvmField val hour: Int,
        @JvmField val minute: Int,
        @JvmField val lastAttemptAt: Long,
        @JvmField val lastSuccessAt: Long,
        @JvmField val nextRunAt: Long,
    ) {
        fun normalized(): AutoSyncSettings {
            val normalized = TimeOfDaySettingsPolicy.normalizeAutoSync(
                configured,
                enabled,
                hour,
                minute,
                lastAttemptAt,
                lastSuccessAt,
                nextRunAt,
            )
            return AutoSyncSettings(
                normalized.configured,
                normalized.enabled,
                normalized.hour,
                normalized.minute,
                normalized.lastAttemptAtMillis,
                normalized.lastSuccessAtMillis,
                normalized.nextRunAtMillis,
            )
        }

        fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
    }

    class AutoUpdateStatus(
        enabled: Boolean,
        lastCheckAtMillis: Long,
        lastResult: String?,
        lastVersion: String?,
        pendingApkName: String?,
        pendingMessage: String?,
    ) {
        @JvmField val enabled: Boolean
        @JvmField val lastCheckAtMillis: Long
        @JvmField val lastResult: String
        @JvmField val lastVersion: String
        @JvmField val pendingApkName: String
        @JvmField val pendingMessage: String

        init {
            val normalized = AutoUpdateStatusPolicy.normalize(
                enabled,
                lastCheckAtMillis,
                lastResult,
                lastVersion,
                pendingApkName,
                pendingMessage,
            )
            this.enabled = normalized.enabled()
            this.lastCheckAtMillis = normalized.lastCheckAtMillis()
            this.lastResult = normalized.lastResult()
            this.lastVersion = normalized.lastVersion()
            this.pendingApkName = normalized.pendingApkName()
            this.pendingMessage = normalized.pendingMessage()
        }

        fun hasPendingUpdate(): Boolean = AutoUpdateStatusPolicy.hasPendingUpdate(pendingApkName)
    }

    companion object {
        const val DB_NAME: String = LocalStoreSchema.DB_NAME
        const val DB_VERSION: Int = LocalStoreSchema.DB_VERSION
        const val TABLE_SETTINGS: String = "settings"
        const val TABLE_SYNC_RUNS: String = "sync_runs"
        const val TABLE_SOURCE_NOTES: String = "source_notes"
        const val TABLE_SOURCE_CARDS: String = "source_cards"
        const val TABLE_SUSPENDED_ARCHIVE: String = "suspended_archive"
        const val TABLE_SUSPENDED_IMPORTS: String = "suspended_imports"
        const val TABLE_SUSPENDED_SOURCES: String = "suspended_sources"
        const val TABLE_IMPORT_RULE_AUDITS: String = "import_rule_audits"
        const val TABLE_IMPORT_DECISIONS: String = "import_decisions"
        const val TABLE_DASHBOARD_ROWS: String = "dashboard_rows"
        const val TABLE_KANJI_EXAMPLES: String = "kanji_examples"
        const val TABLE_STUDY_ITEMS: String = "study_items"
        const val TABLE_LEARNING_REPEATS: String = "learning_repeats"
        const val TABLE_REVIEW_LOG: String = "review_log"
        const val TABLE_KANJI_INVENTORY: String = "kanji_inventory"
        const val TABLE_LOCAL_KANJI_SUSPENSIONS: String = "local_kanji_suspensions"
        const val TABLE_SIMILAR_KANJI_PAIRS: String = "similar_kanji_pairs"
        const val TABLE_SIMILAR_KANJI_CHOICE_STATE: String = "similar_kanji_choice_state"
        const val TABLE_SIMILAR_KANJI_REPAIR_QUEUE: String = "similar_kanji_repair_queue"
        const val TABLE_SIMILAR_KANJI_REVIEW_LOG: String = "similar_kanji_review_log"
        const val TABLE_STUDY_TASK_LOG: String = "study_task_log"
        const val TABLE_KANJI_TIMELINE_EVENTS: String = "kanji_timeline_events"
        const val TABLE_SYNC_CARD_SNAPSHOTS: String = "sync_card_snapshots"
        const val TABLE_SYNC_NOTE_SNAPSHOTS: String = "sync_note_snapshots"
        const val TABLE_SYNC_KANJI_SNAPSHOTS: String = "sync_kanji_snapshots"
        const val TABLE_STATS_CACHE_STATE: String = "stats_cache_state"
        const val TABLE_STATS_SCREEN_CACHE: String = "stats_screen_cache"
        const val STATS_CACHE_SOURCE_VERSION_KEY: String = "stats_source_version"
        const val SQL_CREATE_TABLE: String = "CREATE TABLE "
        const val SQL_CREATE_TABLE_IF_NEEDED: String = "CREATE TABLE IF NOT EXISTS "
        const val SQL_TEXT_NOT_NULL_DEFAULT_EMPTY: String = "TEXT NOT NULL DEFAULT ''"
        const val SQL_TEXT_NOT_NULL_DEFAULT_SIMILAR_KANJI_RUNG: String = "TEXT NOT NULL DEFAULT 'similar_kanji'"
        const val SQL_INTEGER_NOT_NULL_DEFAULT_ZERO: String = "INTEGER NOT NULL DEFAULT 0"
        const val SQL_INTEGER_NOT_NULL_DEFAULT_ONE: String = "INTEGER NOT NULL DEFAULT 1"
        const val WHERE_KANJI: String = "kanji=?"
        const val WHERE_SETTING_KEY: String = "key=?"
        const val WHERE_SIMILAR_CHOICE: String = "target_kanji=? AND choice_signature=?"
        const val ORDER_ID_DESC: String = "id DESC"
        const val SQL_DELETE_FROM: String = "DELETE FROM "
        const val ORDER_KANJI_ASC: String = "kanji ASC"
        const val ORDER_SIMILAR_PAIR: String = "kanji_a ASC, kanji_b ASC, source ASC"
        const val COLUMN_ACTIVE_TOKEN: String = "active_token"
        const val COLUMN_ANSWER_SIGNATURE: String = "answer_signature"
        const val COLUMN_ATTEMPTS: String = "attempts"
        const val COLUMN_ACTIVE_CARDS_COUNT: String = "active_cards_count"
        const val COLUMN_ACTIVE_EXAMPLE_COUNT: String = "active_example_count"
        const val COLUMN_ACTIVE_NOTES_COUNT: String = "active_notes_count"
        const val COLUMN_BROWSER_SEARCH: String = "browser_search"
        const val COLUMN_CARD_ID: String = "card_id"
        const val COLUMN_CHOICE_SIGNATURE: String = "choice_signature"
        const val COLUMN_CHOICES: String = "choices"
        const val COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS: String = "consecutive_failed_recognition_days"
        const val COLUMN_COMPLETED_AT: String = "completed_at"
        const val COLUMN_CORRECT_COUNT: String = "correct_count"
        const val COLUMN_CREATED_AT: String = "created_at"
        const val COLUMN_CUTOFF_USED: String = "cutoff_used"
        const val COLUMN_DECK_ID: String = "deck_id"
        const val COLUMN_DECK_IDS: String = "deck_ids"
        const val COLUMN_DECK_NAME: String = "deck_name"
        const val COLUMN_DECK_NAMES: String = "deck_names"
        const val COLUMN_DEDUPE_KEY: String = "dedupe_key"
        const val COLUMN_DETAIL: String = "detail"
        const val COLUMN_DECISION: String = "decision"
        const val COLUMN_DUE_AT: String = "due_at"
        const val COLUMN_ENABLED_SOURCES: String = "enabled_sources"
        const val COLUMN_ERROR_MESSAGE: String = "error_message"
        const val COLUMN_EVENT_TYPE: String = "event_type"
        const val COLUMN_EXPRESSION: String = "expression"
        const val COLUMN_FIELDS_JSON: String = "fields_json"
        const val COLUMN_FINISHED_AT: String = "finished_at"
        const val COLUMN_FIRST_SEEN_AT: String = "first_seen_at"
        const val COLUMN_FONT_MEANING_MEMORY: String = "font_meaning_memory"
        const val COLUMN_FSRS_DIFFICULTY: String = "fsrs_difficulty"
        const val COLUMN_FSRS_RETRIEVABILITY: String = "fsrs_retrievability"
        const val COLUMN_FSRS_STABILITY: String = "fsrs_stability"
        const val COLUMN_INTERVAL_DAYS: String = "interval_days"
        const val COLUMN_JITEN_RANK: String = "jiten_rank"
        const val COLUMN_KANJI: String = "kanji"
        const val COLUMN_KANJI_A: String = "kanji_a"
        const val COLUMN_KANJI_B: String = "kanji_b"
        const val COLUMN_KANJI_MEANING_MEMORY: String = "kanji_meaning_memory"
        const val COLUMN_MEANING_KANJI_MEMORY: String = "meaning_kanji_memory"
        const val COLUMN_LAST_FAILED_RECOGNITION_DAY: String = "last_failed_recognition_day"
        const val COLUMN_LAST_REVIEWED_AT: String = "last_reviewed_at"
        const val COLUMN_LAST_SEEN_AT: String = "last_seen_at"
        const val COLUMN_LAST_SEEN_SYNC_ID: String = "last_seen_sync_id"
        const val COLUMN_LAPSES: String = "lapses"
        const val COLUMN_MANUAL_OVERRIDE: String = "manual_override"
        const val COLUMN_MATURE_SUPPORT_COUNT: String = "mature_support_count"
        const val COLUMN_MATURE_INTERVAL_DAYS: String = "mature_interval_days"
        const val COLUMN_MEANING: String = "meaning"
        const val COLUMN_MODEL_ID: String = "model_id"
        const val COLUMN_MODEL_NAME: String = "model_name"
        const val COLUMN_NOTE_ID: String = "note_id"
        const val COLUMN_OCCURRED_AT: String = "occurred_at"
        const val COLUMN_PASSED_AT: String = "passed_at"
        const val COLUMN_PRIMARY_MEANING: String = "primary_meaning"
        const val COLUMN_QUEUE: String = "queue"
        const val COLUMN_RANK_KNOWN: String = "rank_known"
        const val COLUMN_RATING: String = "rating"
        const val COLUMN_READING: String = "reading"
        const val COLUMN_RECOGNITION_STAGE: String = "recognition_stage"
        const val COLUMN_REASON_CODE: String = "reason_code"
        const val COLUMN_REASON_TEXT: String = "reason_text"
        const val COLUMN_REMOVAL_MESSAGE: String = "removal_message"
        const val COLUMN_REPS: String = "reps"
        const val COLUMN_REVIEW_DAY_START: String = "review_day_start"
        const val COLUMN_REVIEWED_AT: String = "reviewed_at"
        const val COLUMN_SENTENCE: String = "sentence"
        const val COLUMN_SOURCE: String = "source"
        const val COLUMN_SOURCE_CARD_IDS: String = "source_card_ids"
        const val COLUMN_SOURCE_COUNT: String = "source_count"
        const val COLUMN_SOURCE_NOTE_IDS: String = "source_note_ids"
        const val COLUMN_SOURCE_TYPES: String = "source_types"
        const val COLUMN_STATE: String = "state"
        const val COLUMN_STATUS: String = "status"
        const val COLUMN_STARTED_AT: String = "started_at"
        const val COLUMN_SYNC_ID: String = "sync_id"
        const val COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT: String = "suspended_cards_archived_count"
        const val COLUMN_SUSPENDED_EXAMPLE_COUNT: String = "suspended_example_count"
        const val COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT: String = "suspended_kanji_imported_count"
        const val COLUMN_SUPPRESSED_AT: String = "suppressed_at"
        const val COLUMN_SUPPRESSED_BY_TASK_TYPE: String = "suppressed_by_task_type"
        const val COLUMN_TARGET_KANJI: String = "target_kanji"
        const val COLUMN_TAGS: String = "tags"
        const val COLUMN_TASK_TYPE: String = "task_type"
        const val COLUMN_TITLE: String = "title"
        const val COLUMN_TYPING_MEANING_MEMORY: String = "typing_meaning_memory"
        const val COLUMN_TOKEN: String = "token"
        const val COLUMN_UPDATED_AT: String = "updated_at"
        const val COLUMN_VALUE: String = "value"
        const val COLUMN_WEAKNESS_SCORE: String = "weakness_score"
        const val COLUMN_RULE_TYPES: String = "rule_types"
        const val COLUMN_SETTINGS_JSON: String = "settings_json"
        const val COLUMN_WORD_READING_MEMORY: String = "word_reading_memory"
        const val COLUMN_WRONG_COUNT: String = "wrong_count"
        const val COLUMN_WRITING_REMEDIATION_MEMORY: String = "writing_remediation_memory"
        const val COLUMN_WRITING_REMEDIATION_PENDING: String = "writing_remediation_pending"
        const val COLUMN_WRITING_PASSED: String = "writing_passed"
        const val COLUMN_WRITING_REQUIRED: String = "writing_required"
        const val COLUMN_RUNG: String = "rung"
        const val COLUMN_PHASE: String = "phase"
        const val COLUMN_REAL_PASS_STREAK: String = "real_pass_streak"
        const val COLUMN_REAL_AGAIN_STREAK: String = "real_again_streak"
        const val COLUMN_LAST_REAL_REVIEW_DUE_AT: String = "last_real_review_due_at"
        const val COLUMN_SIMILAR_KANJI_MEMORY: String = "similar_kanji_memory"
        const val STUDY_ITEMS_TABLE_SQL: String = "CREATE TABLE study_items (kanji TEXT NOT NULL, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, recognition_stage INTEGER NOT NULL DEFAULT 0, consecutive_failed_recognition_days INTEGER NOT NULL DEFAULT 0, last_failed_recognition_day INTEGER NOT NULL DEFAULT 0, writing_remediation_pending INTEGER NOT NULL DEFAULT 0, suppressed_by_task_type TEXT NOT NULL DEFAULT '', suppressed_at INTEGER NOT NULL DEFAULT 0, mature_interval_days INTEGER NOT NULL DEFAULT 0, answer_signature TEXT NOT NULL DEFAULT '', typing_meaning_memory TEXT NOT NULL DEFAULT '', meaning_kanji_memory TEXT NOT NULL DEFAULT '', kanji_meaning_memory TEXT NOT NULL DEFAULT '', font_meaning_memory TEXT NOT NULL DEFAULT '', word_reading_memory TEXT NOT NULL DEFAULT '', writing_remediation_memory TEXT NOT NULL DEFAULT '', rung TEXT NOT NULL DEFAULT 'kanji_meaning', phase TEXT NOT NULL DEFAULT 'new_learning', real_pass_streak INTEGER NOT NULL DEFAULT 0, real_again_streak INTEGER NOT NULL DEFAULT 0, last_real_review_due_at INTEGER NOT NULL DEFAULT 0, similar_kanji_memory TEXT NOT NULL DEFAULT '', active_token TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature))"
        const val LEARNING_REPEATS_TABLE_SQL: String = "CREATE TABLE learning_repeats (kanji TEXT NOT NULL, answer_signature TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL, repeat_type TEXT NOT NULL, step_index INTEGER NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature, task_type))"
        const val REVIEW_LOG_TABLE_SQL: String = "CREATE TABLE review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL, review_day_start INTEGER NOT NULL DEFAULT 0, task_type TEXT NOT NULL DEFAULT '', answer_signature TEXT NOT NULL DEFAULT '', prompt TEXT NOT NULL DEFAULT '', hints_used INTEGER NOT NULL DEFAULT 0, writing_clean INTEGER NOT NULL DEFAULT 0, memory_before TEXT NOT NULL DEFAULT '', memory_after TEXT NOT NULL DEFAULT '', scheduler_state_before_json TEXT NOT NULL DEFAULT '', scheduler_state_after_json TEXT NOT NULL DEFAULT '')"
        const val STUDY_TASK_LOG_TABLE_SQL: String = "CREATE TABLE IF NOT EXISTS study_task_log (id INTEGER PRIMARY KEY AUTOINCREMENT, task_key TEXT NOT NULL UNIQUE, kanji TEXT NOT NULL, task_type TEXT NOT NULL, started_at INTEGER NOT NULL, answered_at INTEGER NOT NULL, active_elapsed_ms INTEGER NOT NULL, outcome TEXT NOT NULL)"
        const val MAX_STUDY_TASK_ELAPSED_MS: Long = 30L * 60L * 1000L
        const val RATING_AGAIN: String = "again"
        const val STATE_RETIRED: String = "retired"
        const val STATUS_SUCCESS: String = "success"
        const val STATUS_PENDING: String = "pending"
        const val TIMELINE_FIRST_SEEN: String = "first_seen"
        const val TIMELINE_FIRST_SEEN_TITLE: String = "Kani started watching"
        const val TIMELINE_FIRST_SEEN_KEY_PREFIX: String = "first_seen:"
        const val COLUMN_MATURE: String = "mature"
        const val COLUMN_FIRST_IMPORTED_AT: String = "first_imported_at"
        const val KEY_AUTO_SYNC_LAST_ATTEMPT_AT: String = "auto_sync_last_attempt_at"
        const val KEY_AUTO_SYNC_LAST_SUCCESS_AT: String = "auto_sync_last_success_at"
        const val KEY_AUTO_SYNC_NEXT_RUN_AT: String = "auto_sync_next_run_at"
        const val SIMILAR_KEY_DELIMITER: String = "\u0000"
        const val SETTING_STUDY_AHEAD_MINUTES: String = "study_ahead_minutes"
        const val KEY_AUTO_UPDATE_ENABLED: String = "auto_update_enabled"
        const val KEY_AUTO_UPDATE_LAST_CHECK_AT: String = "auto_update_last_check_at"
        const val KEY_AUTO_UPDATE_LAST_RESULT: String = "auto_update_last_result"
        const val KEY_AUTO_UPDATE_LAST_VERSION: String = "auto_update_last_version"
        const val KEY_AUTO_UPDATE_PENDING_APK: String = "auto_update_pending_apk"
        const val KEY_AUTO_UPDATE_PENDING_MESSAGE: String = "auto_update_pending_message"

        @JvmStatic
        fun studyFamilyKey(kanji: String, answerSignature: String?): String {
            return kanji + SIMILAR_KEY_DELIMITER + (answerSignature ?: "")
        }

        @JvmStatic
        fun studyTimelineKey(item: RecordsStudyModels.StudyItem): String {
            return item.kanji + ":" + Integer.toHexString(item.answerSignature.hashCode())
        }

        @JvmStatic
        fun fieldsJson(fields: Map<String, String>): String {
            val out = StringBuilder("{")
            var first = true
            for ((key, value) in fields) {
                if (!first) {
                    out.append(',')
                }
                first = false
                out.append(TextUtil.jsonQuote(key)).append(':').append(TextUtil.jsonQuote(value))
            }
            out.append('}')
            return out.toString()
        }

        @JvmStatic
        fun string(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index)
        }

        @JvmStatic
        fun integer(cursor: Cursor, column: String): Int {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) 0 else cursor.getInt(index)
        }

        @JvmStatic
        fun nullableInt(cursor: Cursor, column: String): Int? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getInt(index)
        }

        @JvmStatic
        fun nullableLong(cursor: Cursor, column: String): Long? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
        }

        @JvmStatic
        fun nullableDouble(cursor: Cursor, column: String): Double? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getDouble(index)
        }

        @JvmStatic
        fun longValue(cursor: Cursor, column: String): Long {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) 0L else cursor.getLong(index)
        }

        @JvmStatic
        fun putNullableDouble(values: ContentValues, key: String, value: Double?) {
            if (value != null) {
                values.put(key, value)
            }
        }
    }
}
