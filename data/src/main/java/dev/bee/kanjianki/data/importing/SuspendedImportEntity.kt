package dev.bee.kanjianki.data.importing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suspended_imports")
data class SuspendedImportEntity(
    @PrimaryKey
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "jiten_rank")
    val jitenRank: Int?,
    @ColumnInfo(name = "rank_known")
    val rankKnown: Int,
    @ColumnInfo(name = "cutoff_used")
    val cutoffUsed: Int,
    @ColumnInfo(name = "first_imported_at")
    val firstImportedAt: Long,
    @ColumnInfo(name = "last_seen_sync_id")
    val lastSeenSyncId: Long,
)
