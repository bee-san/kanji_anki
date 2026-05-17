package dev.bee.kanjianki.data.importing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_rule_audits")
data class ImportRuleAuditEntity(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "enabled_sources")
    val enabledSources: String,
    @ColumnInfo(name = "rank_min")
    val rankMin: Int,
    @ColumnInfo(name = "rank_max")
    val rankMax: Int,
    @ColumnInfo(name = "min_matching_cards")
    val minMatchingCards: Int,
    @ColumnInfo(name = "import_tags")
    val importTags: String,
    @ColumnInfo(name = "weak_fsrs_difficulty")
    val weakFsrsDifficulty: Double,
    @ColumnInfo(name = "weak_lapses")
    val weakLapses: Int,
    @ColumnInfo(name = "browser_query")
    val browserQuery: String,
    @ColumnInfo(name = "settings_json")
    val settingsJson: String,
)
