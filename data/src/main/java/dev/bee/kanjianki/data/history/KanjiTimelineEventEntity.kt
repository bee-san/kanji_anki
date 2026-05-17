package dev.bee.kanjianki.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kanji_timeline_events",
    indices = [
        Index(value = ["dedupe_key"], unique = true, name = "idx_timeline_dedupe"),
        Index(value = ["kanji", "occurred_at", "id"], name = "idx_timeline_kanji_time"),
    ],
)
data class KanjiTimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "detail")
    val detail: String,
    @ColumnInfo(name = "source_expression")
    val sourceExpression: String,
    @ColumnInfo(name = "source_reading")
    val sourceReading: String,
    @ColumnInfo(name = "rating")
    val rating: String,
    @ColumnInfo(name = "writing_required", defaultValue = "0")
    val writingRequired: Int,
    @ColumnInfo(name = "writing_passed", defaultValue = "0")
    val writingPassed: Int,
    @ColumnInfo(name = "manual_override", defaultValue = "0")
    val manualOverride: Int,
    @ColumnInfo(name = "weakness_score")
    val weaknessScore: Int?,
    @ColumnInfo(name = "mature_support_count")
    val matureSupportCount: Int?,
    @ColumnInfo(name = "sync_id")
    val syncId: Long?,
    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String,
)
