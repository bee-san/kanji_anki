package dev.bee.kanjianki.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_runs")
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "active_notes_count")
    val activeNotesCount: Int,
    @ColumnInfo(name = "active_cards_count")
    val activeCardsCount: Int,
    @ColumnInfo(name = "suspended_cards_archived_count")
    val suspendedCardsArchivedCount: Int,
    @ColumnInfo(name = "suspended_kanji_imported_count")
    val suspendedKanjiImportedCount: Int,
    @ColumnInfo(name = "deleted_notes_count")
    val deletedNotesCount: Int,
    @ColumnInfo(name = "deleted_cards_count")
    val deletedCardsCount: Int,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    @ColumnInfo(name = "removal_message")
    val removalMessage: String?,
)
