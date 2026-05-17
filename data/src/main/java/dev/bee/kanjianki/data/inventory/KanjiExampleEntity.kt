package dev.bee.kanjianki.data.inventory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kanji_examples",
    indices = [
        Index(
            value = ["kanji"],
            name = "idx_examples_kanji",
        ),
    ],
)
data class KanjiExampleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "card_id")
    val cardId: Long,
    @ColumnInfo(name = "note_id")
    val noteId: Long,
    @ColumnInfo(name = "expression")
    val expression: String,
    @ColumnInfo(name = "reading")
    val reading: String,
    @ColumnInfo(name = "meaning")
    val meaning: String,
    @ColumnInfo(name = "sentence")
    val sentence: String,
    @ColumnInfo(name = "mature")
    val mature: Int,
    @ColumnInfo(name = "lapses")
    val lapses: Int,
    @ColumnInfo(name = "interval_days", defaultValue = "0")
    val intervalDays: Int,
    @ColumnInfo(name = "reps", defaultValue = "0")
    val reps: Int,
    @ColumnInfo(name = "fsrs_stability")
    val fsrsStability: Double?,
    @ColumnInfo(name = "fsrs_difficulty")
    val fsrsDifficulty: Double?,
    @ColumnInfo(name = "fsrs_retrievability")
    val fsrsRetrievability: Double?,
)
