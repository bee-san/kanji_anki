package dev.bee.kanjianki.data.importing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "import_decisions",
    primaryKeys = ["sync_id", "kanji"],
    indices = [
        Index(
            value = ["kanji", "sync_id"],
            name = "idx_import_decisions_kanji_sync",
        ),
    ],
)
data class ImportDecisionEntity(
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "decision")
    val decision: String,
    @ColumnInfo(name = "reason_code")
    val reasonCode: String,
    @ColumnInfo(name = "reason_text")
    val reasonText: String,
    @ColumnInfo(name = "jiten_rank")
    val jitenRank: Int?,
    @ColumnInfo(name = "rank_known")
    val rankKnown: Int,
    @ColumnInfo(name = "rank_min")
    val rankMin: Int,
    @ColumnInfo(name = "rank_max")
    val rankMax: Int,
    @ColumnInfo(name = "min_matching_cards")
    val minMatchingCards: Int,
    @ColumnInfo(name = "source_count")
    val sourceCount: Int,
    @ColumnInfo(name = "source_types")
    val sourceTypes: String,
    @ColumnInfo(name = "rule_types")
    val ruleTypes: String,
    @ColumnInfo(name = "source_card_ids")
    val sourceCardIds: String,
    @ColumnInfo(name = "source_note_ids")
    val sourceNoteIds: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
