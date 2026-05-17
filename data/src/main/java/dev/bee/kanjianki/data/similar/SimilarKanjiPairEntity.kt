package dev.bee.kanjianki.data.similar

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similar_kanji_pairs",
    primaryKeys = ["kanji_a", "kanji_b", "source"],
    indices = [
        Index(value = ["kanji_a"], name = "idx_similar_kanji_pairs_a"),
        Index(value = ["kanji_b"], name = "idx_similar_kanji_pairs_b"),
    ],
)
data class SimilarKanjiPairEntity(
    @ColumnInfo(name = "kanji_a")
    val kanjiA: String,
    @ColumnInfo(name = "kanji_b")
    val kanjiB: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,
)
