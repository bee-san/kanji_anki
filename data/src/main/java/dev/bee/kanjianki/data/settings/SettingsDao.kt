package dev.bee.kanjianki.data.settings

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<SettingEntity?>

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` IN (:keys)")
    suspend fun getAll(keys: List<String>): List<SettingEntity>

    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Upsert
    suspend fun upsertAll(settings: List<SettingEntity>)
}
