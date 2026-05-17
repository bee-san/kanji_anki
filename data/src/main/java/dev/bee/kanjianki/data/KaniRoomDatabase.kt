package dev.bee.kanjianki.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao

@Database(
    entities = [SettingEntity::class],
    version = KaniRoomDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class KaniRoomDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "kanji_anki_simple.db"
        const val SCHEMA_VERSION = 20
    }
}
