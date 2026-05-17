package dev.bee.kanjianki.data

import androidx.room.Database
import androidx.room.RoomDatabase
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

    companion object {
        const val DATABASE_NAME = "kanji_anki_simple.db"
        const val SCHEMA_VERSION = 20
    }
}
