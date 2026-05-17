package dev.bee.kanjianki.data.inventory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kanji_inventory",
    indices = [
        Index(value = ["search_text"], name = "idx_kanji_inventory_search"),
    ],
)
data class KanjiInventoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "primary_meaning")
    val primaryMeaning: String,
    @ColumnInfo(name = "readings")
    val readings: String,
    @ColumnInfo(name = "browser_search")
    val browserSearch: String,
    @ColumnInfo(name = "search_text")
    val searchText: String,
    @ColumnInfo(name = "source_count")
    val sourceCount: Int,
    @ColumnInfo(name = "example_count")
    val exampleCount: Int,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,
)
