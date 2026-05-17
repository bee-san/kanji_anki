package dev.bee.kanjianki.data.similar

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "similar_kanji_review_log",
    indices = [
        Index(value = ["target_kanji", "reviewed_at"], name = "idx_similar_review_log_target"),
    ],
)
data class SimilarReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "target_kanji")
    val targetKanji: String,
    @ColumnInfo(name = "choice_signature")
    val choiceSignature: String,
    @ColumnInfo(name = "selected_kanji")
    val selectedKanji: String,
    @ColumnInfo(name = "correct")
    val correct: Int,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long,
)
