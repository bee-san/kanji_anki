package dev.bee.kanjianki.data

import androidx.room.Database
import androidx.room.RoomDatabase
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
    ],
    version = KaniRoomDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class KaniRoomDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun sourceNoteDao(): SourceNoteDao
    abstract fun sourceCardDao(): SourceCardDao
    abstract fun syncRunDao(): SyncRunDao

    companion object {
        const val DATABASE_NAME = "kanji_anki_simple.db"
        const val SCHEMA_VERSION = 20
    }
}
