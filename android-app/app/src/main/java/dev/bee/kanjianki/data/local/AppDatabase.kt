package dev.bee.kanjianki.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppSettingEntity::class,
        SyncRunEntity::class,
        SourceNoteEntity::class,
        SourceCardEntity::class,
        ExpressionSnapshotEntity::class,
        ProblemKanjiSnapshotEntity::class,
        StudyItemEntity::class,
        StudyReviewLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    abstract fun syncRunDao(): SyncRunDao

    abstract fun sourceSnapshotDao(): SourceSnapshotDao

    abstract fun snapshotDao(): SnapshotDao

    abstract fun studyDao(): StudyDao
}
