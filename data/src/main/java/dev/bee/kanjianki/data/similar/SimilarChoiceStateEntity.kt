package dev.bee.kanjianki.data.similar

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similar_kanji_choice_state",
    primaryKeys = ["target_kanji", "choice_signature"],
    indices = [
        Index(value = ["passed_at", "due_at"], name = "idx_similar_choice_due"),
    ],
)
data class SimilarChoiceStateEntity(
    @ColumnInfo(name = "target_kanji")
    val targetKanji: String,
    @ColumnInfo(name = "choice_signature")
    val choiceSignature: String,
    @ColumnInfo(name = "primary_meaning")
    val primaryMeaning: String,
    @ColumnInfo(name = "choices")
    val choices: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "passed_at", defaultValue = "0")
    val passedAt: Long,
    @ColumnInfo(name = "last_reviewed_at", defaultValue = "0")
    val lastReviewedAt: Long,
    @ColumnInfo(name = "correct_count", defaultValue = "0")
    val correctCount: Int,
    @ColumnInfo(name = "wrong_count", defaultValue = "0")
    val wrongCount: Int,
    @ColumnInfo(name = "active_token", defaultValue = "''")
    val activeToken: String,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,
)
