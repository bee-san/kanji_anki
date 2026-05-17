package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.sync.AutoSyncSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAutoSyncSettingsRepositoryTest {
    @Test
    fun emptyRoomSettingsLoadCurrentAutoSyncDefaultsAfterReset() = runBlocking {
        val repository = RoomAutoSyncSettingsRepository(FakeSettingsDao())

        assertEquals(AutoSyncSettings(), repository.get())
    }

    @Test
    fun persistedSettingsRoundTripThroughLegacyStableKeys() = runBlocking {
        val dao = FakeSettingsDao()
        val repository = RoomAutoSyncSettingsRepository(dao)

        repository.save(
            AutoSyncSettings(
                configured = true,
                enabled = true,
                hour = 6,
                minute = 45,
                lastAttemptAtMillis = 1_000L,
                lastSuccessAtMillis = 2_000L,
                nextRunAtMillis = 3_000L,
            ),
            updatedAtMillis = 123L,
        )

        assertEquals("1", dao.values.getValue("auto_sync_configured").value)
        assertEquals("1", dao.values.getValue("auto_sync_enabled").value)
        assertEquals("6", dao.values.getValue("auto_sync_hour").value)
        assertEquals("45", dao.values.getValue("auto_sync_minute").value)
        assertEquals("1000", dao.values.getValue("auto_sync_last_attempt_at").value)
        assertEquals("2000", dao.values.getValue("auto_sync_last_success_at").value)
        assertEquals("3000", dao.values.getValue("auto_sync_next_run_at").value)
        assertTrue(dao.values.values.all { it.updatedAt == 123L })
        assertEquals(
            AutoSyncSettings(
                configured = true,
                enabled = true,
                hour = 6,
                minute = 45,
                lastAttemptAtMillis = 1_000L,
                lastSuccessAtMillis = 2_000L,
                nextRunAtMillis = 3_000L,
            ),
            repository.get(),
        )
    }

    @Test
    fun invalidStoredSettingsNormalizeLikeLegacyLocalStore() = runBlocking {
        val repository = RoomAutoSyncSettingsRepository(
            FakeSettingsDao(
                "auto_sync_configured" to "0",
                "auto_sync_enabled" to "1",
                "auto_sync_hour" to "99",
                "auto_sync_minute" to "-4",
                "auto_sync_last_attempt_at" to "-1",
                "auto_sync_last_success_at" to "-2",
                "auto_sync_next_run_at" to "-3",
            ),
        )

        assertEquals(
            AutoSyncSettings(
                configured = false,
                enabled = false,
                hour = 23,
                minute = 0,
            ),
            repository.get(),
        )
    }

    @Test
    fun activationOnlyConfiguresAutoSyncOnce() = runBlocking {
        val dao = FakeSettingsDao()
        val repository = RoomAutoSyncSettingsRepository(dao)

        assertTrue(repository.activateAfterFirstSuccess(updatedAtMillis = 100L))
        assertEquals(true, repository.get().configured)
        assertEquals(true, repository.get().enabled)
        assertEquals("1", dao.values.getValue("auto_sync_configured").value)
        assertEquals("1", dao.values.getValue("auto_sync_enabled").value)

        assertFalse(repository.activateAfterFirstSuccess(updatedAtMillis = 200L))
        assertEquals(100L, dao.values.getValue("auto_sync_configured").updatedAt)
    }

    @Test
    fun setEnabledConfiguresScheduleAndPreservesTime() = runBlocking {
        val repository = RoomAutoSyncSettingsRepository(
            FakeSettingsDao(
                "auto_sync_hour" to "8",
                "auto_sync_minute" to "15",
            ),
        )

        repository.setEnabled(enabled = false, updatedAtMillis = 10L)

        assertEquals(
            AutoSyncSettings(
                configured = true,
                enabled = false,
                hour = 8,
                minute = 15,
            ),
            repository.get(),
        )
    }

    @Test
    fun scheduledAndAttemptTimestampsMatchLegacyUpdateRules() = runBlocking {
        val dao = FakeSettingsDao(
            "auto_sync_last_success_at" to "500",
        )
        val repository = RoomAutoSyncSettingsRepository(dao)

        repository.markScheduled(nextRunAtMillis = 2_000L, updatedAtMillis = 100L)
        repository.recordAttempt(attemptedAtMillis = 3_000L, success = false, updatedAtMillis = 200L)

        assertEquals("2000", dao.values.getValue("auto_sync_next_run_at").value)
        assertEquals("3000", dao.values.getValue("auto_sync_last_attempt_at").value)
        assertEquals("500", dao.values.getValue("auto_sync_last_success_at").value)

        repository.recordAttempt(attemptedAtMillis = 4_000L, success = true, updatedAtMillis = 300L)

        assertEquals("4000", dao.values.getValue("auto_sync_last_attempt_at").value)
        assertEquals("4000", dao.values.getValue("auto_sync_last_success_at").value)
    }

    private class FakeSettingsDao(
        vararg pairs: Pair<String, String>,
    ) : SettingsDao {
        val values = linkedMapOf<String, SettingEntity>()

        init {
            for ((key, value) in pairs) {
                values[key] = SettingEntity(key = key, value = value, updatedAt = 1L)
            }
        }

        override fun observe(key: String): Flow<SettingEntity?> = emptyFlow()

        override suspend fun get(key: String): SettingEntity? = values[key]

        override suspend fun getAll(keys: List<String>): List<SettingEntity> =
            keys.mapNotNull(values::get)

        override suspend fun upsert(setting: SettingEntity) {
            values[setting.key] = setting
        }

        override suspend fun upsertAll(settings: List<SettingEntity>) {
            settings.forEach { values[it.key] = it }
        }
    }
}
