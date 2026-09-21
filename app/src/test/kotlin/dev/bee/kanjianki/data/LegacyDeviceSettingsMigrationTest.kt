package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyDeviceSettingsMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        clearDeviceSettings()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        clearDeviceSettings()
    }

    @Test
    fun localStoreConstructionDoesNotOpenDatabase() {
        val database = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        assertFalse(database.exists())

        LocalStore(context).use {
            assertFalse(database.exists())
        }

        assertFalse(database.exists())
    }

    @Test
    fun migrationPreservesLegacyValuesAndRemovesThemFromPortableStorage() {
        seedLegacySettings { store ->
            store.putIntSetting(DeviceSettingKeys.reminderEnabled.storageName, 1)
            store.putIntSetting(DeviceSettingKeys.reminderHour.storageName, 7)
            store.putIntSetting(DeviceSettingKeys.reminderDailyOverrideUsedToday.storageName, 1)
            store.putLongSetting(DeviceSettingKeys.autoSyncNextRunAt.storageName, 42L)
            store.putStringSetting(DeviceSettingKeys.autoUpdateLastResult.storageName, "ready")
            store.putIntSetting(DeviceSettingKeys.betaUpdatesEnabled.storageName, 1)
            store.putIntSetting(DeviceSettingKeys.flashcardSwipeGestureEnabled.storageName, 0)
            store.putIntSetting(LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES, 15)
        }

        LocalStore(context).use { store ->
            assertEquals(7, store.reminderSettings().hour)
            assertEquals(42L, store.autoSyncSettings().nextRunAt)
            assertEquals("ready", store.autoUpdateStatus().lastResult)
            assertTrue(
                store.deviceSettingsStore().read(DeviceSettingKeys.betaUpdatesEnabled) == true,
            )
            assertFalse(
                store.deviceSettingsStore()
                    .read(DeviceSettingKeys.flashcardSwipeGestureEnabled) ?: true,
            )
            assertTrue(
                store.deviceSettingsStore()
                    .read(DeviceSettingKeys.reminderDailyOverrideUsedToday) == true,
            )

            val remaining = sqliteSettingKeys(store)
            assertFalse(remaining.any(LegacyDeviceSettingsMigration.LEGACY_STORAGE_NAMES::contains))
            assertTrue(remaining.contains(LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES))
            assertTrue(
                store.deviceSettingsStore()
                    .read(DeviceSettingKeys.legacySqliteMigrationComplete) == true,
            )
        }
    }

    @Test
    fun completedMigrationCleansRestoredRowsWithoutOverwritingDeviceValues() {
        AndroidDeviceSettingsStore(context).edit {
            put(DeviceSettingKeys.reminderHour, 11)
            put(DeviceSettingKeys.legacySqliteMigrationComplete, true)
        }
        seedLegacySettings(preserveDeviceSettings = true) { store ->
            store.putIntSetting(DeviceSettingKeys.reminderHour.storageName, 7)
        }

        LocalStore(context).use { store ->
            assertEquals(11, store.reminderSettings().hour)
            assertFalse(sqliteSettingKeys(store).contains(DeviceSettingKeys.reminderHour.storageName))
        }
    }

    @Test
    fun malformedLegacyNumbersFailOpenToDeviceDefaults() {
        seedLegacySettings { store ->
            store.putStringSetting(DeviceSettingKeys.reminderHour.storageName, "not-an-int")
            store.putStringSetting(DeviceSettingKeys.autoSyncNextRunAt.storageName, "not-a-long")
        }

        LocalStore(context).use { store ->
            assertEquals(TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR, store.reminderSettings().hour)
            assertEquals(0L, store.autoSyncSettings().nextRunAt)
            assertFalse(sqliteSettingKeys(store).contains(DeviceSettingKeys.reminderHour.storageName))
        }
    }

    @Test
    fun subsequentDeviceWritesNeverRecreatePortableRows() {
        LocalStore(context).use { store ->
            store.reminderSettings()
            store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 9, 30))
            store.recordUpdateCheckFailed(99L)

            val remaining = sqliteSettingKeys(store)
            assertFalse(remaining.contains(DeviceSettingKeys.reminderEnabled.storageName))
            assertFalse(remaining.contains(DeviceSettingKeys.updateCheckFailedAt.storageName))
            assertEquals(9, store.reminderSettings().hour)
            assertEquals(99L, store.updateCheckFailedAt())
        }
    }

    @Test
    fun failedDeviceCommitLeavesLegacyValuesUntouched() {
        val deviceStore = FakeDeviceSettingsStore(failEdits = true)
        var deleteCalls = 0
        val migration = LegacyDeviceSettingsMigration(
            deviceStore = deviceStore,
            readLegacyValues = {
                mapOf(DeviceSettingKeys.reminderHour.storageName to "7")
            },
            deleteLegacyValues = { deleteCalls += 1 },
        )

        assertThrows(IllegalStateException::class.java) { migration.migrate() }

        assertEquals(0, deleteCalls)
        assertFalse(deviceStore.contains(DeviceSettingKeys.legacySqliteMigrationComplete))
    }

    @Test
    fun failedCleanupRetriesWithoutOverwritingNewerDeviceValue() {
        val deviceStore = FakeDeviceSettingsStore()
        var failCleanup = true
        var legacyReads = 0
        val migration = LegacyDeviceSettingsMigration(
            deviceStore = deviceStore,
            readLegacyValues = {
                legacyReads += 1
                mapOf(DeviceSettingKeys.reminderHour.storageName to "7")
            },
            deleteLegacyValues = {
                if (failCleanup) {
                    throw IllegalStateException("cleanup interrupted")
                }
            },
        )

        assertThrows(IllegalStateException::class.java) { migration.migrate() }
        assertEquals(7, deviceStore.read(DeviceSettingKeys.reminderHour))
        assertTrue(deviceStore.read(DeviceSettingKeys.legacySqliteMigrationComplete) == true)
        val readsAfterDeviceCommit = legacyReads
        deviceStore.edit { put(DeviceSettingKeys.reminderHour, 11) }
        failCleanup = false

        migration.migrate()

        assertTrue(legacyReads > readsAfterDeviceCommit)
        assertEquals(11, deviceStore.read(DeviceSettingKeys.reminderHour))
    }

    private fun sqliteSettingKeys(store: LocalStore): Set<String> {
        val keys = linkedSetOf<String>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf("key"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                keys += cursor.getString(0)
            }
        }
        return keys
    }

    private fun clearDeviceSettings() {
        context.getSharedPreferences(
            AndroidDeviceSettingsStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun seedLegacySettings(
        preserveDeviceSettings: Boolean = false,
        block: (LocalStore) -> Unit,
    ) {
        LocalStore(context).use(block)
        if (!preserveDeviceSettings) {
            clearDeviceSettings()
        }
    }

    private class FakeDeviceSettingsStore(
        private val failEdits: Boolean = false,
    ) : DeviceSettingsStore {
        private val values = linkedMapOf<String, Any>()

        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
            values[key.storageName] as T?

        override fun snapshot(): DeviceSettingsReader {
            val captured = LinkedHashMap(values)
            return object : DeviceSettingsReader {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    captured.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    captured[key.storageName] as T?
            }
        }

        override fun edit(block: DeviceSettingsEditor.() -> Unit) {
            if (failEdits) {
                throw IllegalStateException("device commit interrupted")
            }
            val staged = LinkedHashMap(values)
            object : DeviceSettingsEditor {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    staged.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    staged[key.storageName] as T?

                override fun <T : Any> put(key: DeviceSettingKey<T>, value: T) {
                    staged[key.storageName] = value
                }

                override fun remove(key: DeviceSettingKey<*>) {
                    staged.remove(key.storageName)
                }
            }.block()
            values.clear()
            values.putAll(staged)
        }
    }
}
