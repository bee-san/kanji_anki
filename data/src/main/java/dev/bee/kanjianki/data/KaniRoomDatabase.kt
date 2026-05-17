package dev.bee.kanjianki.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.bee.kanjianki.data.history.KanjiTimelineEventDao
import dev.bee.kanjianki.data.history.KanjiTimelineEventEntity
import dev.bee.kanjianki.data.history.SyncCardSnapshotDao
import dev.bee.kanjianki.data.history.SyncCardSnapshotEntity
import dev.bee.kanjianki.data.history.SyncKanjiSnapshotDao
import dev.bee.kanjianki.data.history.SyncKanjiSnapshotEntity
import dev.bee.kanjianki.data.history.SyncNoteSnapshotDao
import dev.bee.kanjianki.data.history.SyncNoteSnapshotEntity
import dev.bee.kanjianki.data.importing.ImportDecisionDao
import dev.bee.kanjianki.data.importing.ImportDecisionEntity
import dev.bee.kanjianki.data.importing.ImportRuleAuditDao
import dev.bee.kanjianki.data.importing.ImportRuleAuditEntity
import dev.bee.kanjianki.data.importing.SuspendedArchiveDao
import dev.bee.kanjianki.data.importing.SuspendedArchiveEntity
import dev.bee.kanjianki.data.importing.SuspendedImportDao
import dev.bee.kanjianki.data.importing.SuspendedImportEntity
import dev.bee.kanjianki.data.importing.SuspendedSourceDao
import dev.bee.kanjianki.data.importing.SuspendedSourceEntity
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.KanjiInventoryEntity
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.data.source.SourceCardDao
import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteDao
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.study.LearningRepeatDao
import dev.bee.kanjianki.data.study.LearningRepeatEntity
import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.data.study.ReviewLogEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.data.study.StudyTaskLogDao
import dev.bee.kanjianki.data.study.StudyTaskLogEntity
import dev.bee.kanjianki.data.similar.SimilarChoiceStateDao
import dev.bee.kanjianki.data.similar.SimilarChoiceStateEntity
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairEntity
import dev.bee.kanjianki.data.similar.SimilarRepairQueueDao
import dev.bee.kanjianki.data.similar.SimilarRepairQueueEntity
import dev.bee.kanjianki.data.similar.SimilarReviewLogDao
import dev.bee.kanjianki.data.similar.SimilarReviewLogEntity
import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.data.sync.SyncRunEntity

@Database(
    entities = [
        SettingEntity::class,
        SourceNoteEntity::class,
        SourceCardEntity::class,
        SyncRunEntity::class,
        SuspendedArchiveEntity::class,
        SuspendedImportEntity::class,
        SuspendedSourceEntity::class,
        ImportRuleAuditEntity::class,
        ImportDecisionEntity::class,
        DashboardRowEntity::class,
        KanjiExampleEntity::class,
        KanjiInventoryEntity::class,
        LocalKanjiSuspensionEntity::class,
        StudyItemEntity::class,
        LearningRepeatEntity::class,
        ReviewLogEntity::class,
        StudyTaskLogEntity::class,
        SimilarKanjiPairEntity::class,
        SimilarChoiceStateEntity::class,
        SimilarRepairQueueEntity::class,
        SimilarReviewLogEntity::class,
        KanjiTimelineEventEntity::class,
        SyncCardSnapshotEntity::class,
        SyncNoteSnapshotEntity::class,
        SyncKanjiSnapshotEntity::class,
    ],
    version = KaniRoomDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class KaniRoomDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun sourceNoteDao(): SourceNoteDao
    abstract fun sourceCardDao(): SourceCardDao
    abstract fun syncRunDao(): SyncRunDao
    abstract fun suspendedArchiveDao(): SuspendedArchiveDao
    abstract fun suspendedImportDao(): SuspendedImportDao
    abstract fun suspendedSourceDao(): SuspendedSourceDao
    abstract fun importRuleAuditDao(): ImportRuleAuditDao
    abstract fun importDecisionDao(): ImportDecisionDao
    abstract fun dashboardRowDao(): DashboardRowDao
    abstract fun kanjiExampleDao(): KanjiExampleDao
    abstract fun kanjiInventoryDao(): KanjiInventoryDao
    abstract fun localKanjiSuspensionDao(): LocalKanjiSuspensionDao
    abstract fun studyItemDao(): StudyItemDao
    abstract fun learningRepeatDao(): LearningRepeatDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun studyTaskLogDao(): StudyTaskLogDao
    abstract fun similarKanjiPairDao(): SimilarKanjiPairDao
    abstract fun similarChoiceStateDao(): SimilarChoiceStateDao
    abstract fun similarRepairQueueDao(): SimilarRepairQueueDao
    abstract fun similarReviewLogDao(): SimilarReviewLogDao
    abstract fun kanjiTimelineEventDao(): KanjiTimelineEventDao
    abstract fun syncCardSnapshotDao(): SyncCardSnapshotDao
    abstract fun syncNoteSnapshotDao(): SyncNoteSnapshotDao
    abstract fun syncKanjiSnapshotDao(): SyncKanjiSnapshotDao

    companion object {
        const val DATABASE_NAME = "kanji_anki_simple.db"
        const val SCHEMA_VERSION = 20
    }
}
