package dev.bee.kanjianki.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_kanji_snapshots",
    primaryKeys = ["sync_id", "kanji"],
    indices = [
        Index(value = ["kanji", "sync_id"], name = "idx_sync_kanji_snapshots_kanji_sync"),
        Index(value = ["kanji", "finished_at"], name = "idx_sync_kanji_snapshots_kanji_finished"),
    ],
)
data class SyncKanjiSnapshotEntity(
    @ColumnInfo(name = "sync_id")
    val syncId: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long,
    @ColumnInfo(name = "kanji")
    val kanji: String,
    @ColumnInfo(name = "active_cards")
    val activeCards: Int,
    @ColumnInfo(name = "suspended_cards")
    val suspendedCards: Int,
    @ColumnInfo(name = "mature_support_count")
    val matureSupportCount: Int,
    @ColumnInfo(name = "average_interval_days")
    val averageIntervalDays: Double,
    @ColumnInfo(name = "total_lapses")
    val totalLapses: Int,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "fsrs_stability_avg")
    val fsrsStabilityAvg: Double?,
    @ColumnInfo(name = "fsrs_difficulty_avg")
    val fsrsDifficultyAvg: Double?,
    @ColumnInfo(name = "fsrs_retrievability_avg")
    val fsrsRetrievabilityAvg: Double?,
    @ColumnInfo(name = "weakness_score")
    val weaknessScore: Int,
    @ColumnInfo(name = "reason_code")
    val reasonCode: String,
    @ColumnInfo(name = "active_example_count")
    val activeExampleCount: Int,
    @ColumnInfo(name = "suspended_example_count")
    val suspendedExampleCount: Int,
)
