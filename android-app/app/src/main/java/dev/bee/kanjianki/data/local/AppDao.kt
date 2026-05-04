package dev.bee.kanjianki.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun load(key: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingEntity)
}

@Dao
interface SyncRunDao {
    @Query("SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): SyncRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncRunEntity): Long
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM problem_kanji_snapshots ORDER BY sort_index ASC, kanji ASC")
    suspend fun dashboardRows(): List<ProblemKanjiSnapshotEntity>

    @Query("SELECT * FROM problem_kanji_snapshots WHERE kanji = :kanji LIMIT 1")
    suspend fun problemRow(kanji: String): ProblemKanjiSnapshotEntity?

    @Query("DELETE FROM problem_kanji_snapshots")
    suspend fun clearProblemRows()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProblemRows(rows: List<ProblemKanjiSnapshotEntity>)
}

@Dao
interface SourceSnapshotDao {
    @Query("DELETE FROM source_notes")
    suspend fun clearNotes()

    @Query("DELETE FROM source_cards")
    suspend fun clearCards()

    @Query("DELETE FROM expression_snapshots")
    suspend fun clearExpressions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<SourceNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<SourceCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpressions(expressions: List<ExpressionSnapshotEntity>)
}

@Dao
interface StudyDao {
    @Query(
        """
        SELECT * FROM study_items
        WHERE profile = :profile
        ORDER BY due_ts ASC, kanji ASC
        """,
    )
    suspend fun itemsForProfile(profile: String): List<StudyItemEntity>

    @Query("SELECT * FROM study_items WHERE profile = :profile AND kanji = :kanji LIMIT 1")
    suspend fun item(profile: String, kanji: String): StudyItemEntity?

    @Query("SELECT * FROM study_review_log WHERE profile = :profile AND review_token = :reviewToken LIMIT 1")
    suspend fun reviewLog(profile: String, reviewToken: String): StudyReviewLogEntity?

    @Query("DELETE FROM study_items WHERE profile = :profile")
    suspend fun clearProfile(profile: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(entity: StudyItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(entities: List<StudyItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewLog(entity: StudyReviewLogEntity)
}
