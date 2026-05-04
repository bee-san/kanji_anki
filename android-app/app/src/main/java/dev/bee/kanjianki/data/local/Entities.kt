package dev.bee.kanjianki.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value_json") val valueJson: String,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
)

@Entity(tableName = "sync_runs")
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val status: String,
    @ColumnInfo(name = "started_ts") val startedTs: Long,
    @ColumnInfo(name = "finished_ts") val finishedTs: Long?,
    @ColumnInfo(name = "note_count") val noteCount: Int,
    @ColumnInfo(name = "card_count") val cardCount: Int,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
)

@Entity(tableName = "source_notes")
data class SourceNoteEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "model_name") val modelName: String,
    val expression: String,
    val reading: String,
    val meaning: String,
    @ColumnInfo(name = "fields_json") val fieldsJson: String,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "first_seen_ts") val firstSeenTs: Long,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
    @ColumnInfo(name = "synced_ts") val syncedTs: Long?,
)

@Entity(
    tableName = "source_cards",
    foreignKeys = [
        ForeignKey(
            entity = SourceNoteEntity::class,
            parentColumns = ["note_id"],
            childColumns = ["note_id"],
        ),
    ],
    indices = [Index("note_id")],
)
data class SourceCardEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "deck_name") val deckName: String,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "modified_ts") val modifiedTs: Long,
    @ColumnInfo(name = "due_value") val dueValue: Int,
    @ColumnInfo(name = "card_ord") val cardOrd: Int,
    @ColumnInfo(name = "queue_value") val queueValue: Int,
    @ColumnInfo(name = "card_type") val cardType: Int,
    val reps: Int,
    val lapses: Int,
    @ColumnInfo(name = "is_suspended") val isSuspended: Boolean,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "is_mature") val isMature: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "first_seen_ts") val firstSeenTs: Long,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
    @ColumnInfo(name = "synced_ts") val syncedTs: Long?,
)

@Entity(tableName = "expression_snapshots")
data class ExpressionSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "normalized_expression")
    val normalizedExpression: String,
    val expression: String,
    val reading: String,
    val meaning: String,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    @ColumnInfo(name = "source_note_ids_json") val sourceNoteIdsJson: String,
    @ColumnInfo(name = "source_card_ids_json") val sourceCardIdsJson: String,
    @ColumnInfo(name = "suspended_card_count") val suspendedCardCount: Int,
    @ColumnInfo(name = "active_card_count") val activeCardCount: Int,
    @ColumnInfo(name = "mature_card_count") val matureCardCount: Int,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
)

@Entity(tableName = "problem_kanji_snapshots")
data class ProblemKanjiSnapshotEntity(
    @PrimaryKey val kanji: String,
    @ColumnInfo(name = "jiten_rank") val jitenRank: Double?,
    @ColumnInfo(name = "collection_expression_count") val collectionExpressionCount: Int,
    @ColumnInfo(name = "suspended_expression_count") val suspendedExpressionCount: Int,
    @ColumnInfo(name = "active_recurring_expression_count") val activeRecurringExpressionCount: Int,
    @ColumnInfo(name = "mature_support_count") val matureSupportCount: Int,
    @ColumnInfo(name = "support_deficit") val supportDeficit: Int,
    @ColumnInfo(name = "is_unknown") val isUnknown: Boolean,
    @ColumnInfo(name = "browser_search") val browserSearch: String,
    @ColumnInfo(name = "detail_json") val detailJson: String,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
)

@Entity(
    tableName = "study_items",
    primaryKeys = ["profile", "kanji"],
    indices = [Index(value = ["profile", "item_status", "due_ts"], name = "idx_study_due")],
)
data class StudyItemEntity(
    val profile: String,
    val kanji: String,
    @ColumnInfo(name = "due_ts") val dueTs: Long,
    @ColumnInfo(name = "item_status") val itemStatus: String,
    @ColumnInfo(name = "is_problem_seed") val isProblemSeed: Boolean,
    @ColumnInfo(name = "guide_level") val guideLevel: Int,
    @ColumnInfo(name = "consecutive_writing_successes") val consecutiveWritingSuccesses: Int,
    @ColumnInfo(name = "consecutive_writing_failures") val consecutiveWritingFailures: Int,
    val stability: Double,
    val difficulty: Double,
    @ColumnInfo(name = "total_reviews") val totalReviews: Int,
    @ColumnInfo(name = "total_lapses") val totalLapses: Int,
    @ColumnInfo(name = "last_prompt_type") val lastPromptType: String?,
    @ColumnInfo(name = "latest_problem_snapshot_json") val latestProblemSnapshotJson: String,
    @ColumnInfo(name = "priority_suspended_count") val prioritySuspendedCount: Int,
    @ColumnInfo(name = "priority_support_deficit") val prioritySupportDeficit: Int,
    @ColumnInfo(name = "priority_active_recurring_count") val priorityActiveRecurringCount: Int,
    @ColumnInfo(name = "priority_rank") val priorityRank: Double?,
    @ColumnInfo(name = "created_ts") val createdTs: Long,
    @ColumnInfo(name = "updated_ts") val updatedTs: Long,
    @ColumnInfo(name = "last_reviewed_ts") val lastReviewedTs: Long?,
    @ColumnInfo(name = "active_review_token") val activeReviewToken: String?,
    @ColumnInfo(name = "active_prompt_type") val activePromptType: String?,
    @ColumnInfo(name = "active_session_issued_ts") val activeSessionIssuedTs: Long?,
    @ColumnInfo(name = "learning_step") val learningStep: Int,
    @ColumnInfo(name = "review_cycle_index") val reviewCycleIndex: Int,
    @ColumnInfo(name = "first_introduced_ts") val firstIntroducedTs: Long?,
    @ColumnInfo(name = "inactive_reason") val inactiveReason: String?,
    @ColumnInfo(name = "retired_ts") val retiredTs: Long?,
    @ColumnInfo(name = "retirement_context_json") val retirementContextJson: String,
)

@Entity(
    tableName = "study_review_log",
    indices = [Index(value = ["profile", "review_token"], unique = true, name = "idx_study_review_token")],
)
data class StudyReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profile: String,
    val kanji: String,
    @ColumnInfo(name = "review_token") val reviewToken: String,
    @ColumnInfo(name = "reviewed_ts") val reviewedTs: Long,
    @ColumnInfo(name = "prompt_type") val promptType: String,
    @ColumnInfo(name = "srs_rating") val srsRating: String,
    @ColumnInfo(name = "handwriting_passed") val handwritingPassed: Boolean,
    @ColumnInfo(name = "handwriting_score") val handwritingScore: Double,
    @ColumnInfo(name = "guide_level_before") val guideLevelBefore: Int,
    @ColumnInfo(name = "guide_level_after") val guideLevelAfter: Int,
    @ColumnInfo(name = "hints_used") val hintsUsed: Int,
    @ColumnInfo(name = "review_payload_json") val reviewPayloadJson: String,
)
