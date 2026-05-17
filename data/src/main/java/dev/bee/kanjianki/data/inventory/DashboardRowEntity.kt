package dev.bee.kanjianki.data.inventory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_rows")
data class DashboardRowEntity(
    @PrimaryKey
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "jiten_rank")
    val jitenRank: Int?,
    @ColumnInfo(name = "primary_meaning")
    val primaryMeaning: String,
    @ColumnInfo(name = "reading")
    val reading: String,
    @ColumnInfo(name = "browser_search")
    val browserSearch: String,
    @ColumnInfo(name = "weakness_score")
    val weaknessScore: Int,
    @ColumnInfo(name = "reason_code")
    val reasonCode: String,
    @ColumnInfo(name = "reason_text")
    val reasonText: String,
    @ColumnInfo(name = "active_example_count")
    val activeExampleCount: Int,
    @ColumnInfo(name = "suspended_example_count")
    val suspendedExampleCount: Int,
    @ColumnInfo(name = "mature_support_count")
    val matureSupportCount: Int,
    @ColumnInfo(name = "rebuilt_at")
    val rebuiltAt: Long,
)
