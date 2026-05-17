package dev.bee.kanjianki.data.inventory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_kanji_suspensions")
data class LocalKanjiSuspensionEntity(
    @PrimaryKey
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "suspended_at")
    val suspendedAt: Long,
)
