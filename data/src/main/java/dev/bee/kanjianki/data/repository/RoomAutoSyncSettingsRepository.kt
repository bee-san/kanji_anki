package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.sync.AutoSyncSettings
import dev.bee.kanjianki.domain.repository.AutoSyncSettingsRepository
import java.util.Locale

class RoomAutoSyncSettingsRepository(
    private val settings: SettingsDao,
) : AutoSyncSettingsRepository {
    override suspend fun get(): AutoSyncSettings {
        val defaults = AutoSyncSettings()
        val values = settings.getAll(ALL_KEYS).associate { it.key to it.value }
        return AutoSyncSettings.fromStored(
            configured = values.boolean(KEY_CONFIGURED, defaults.configured),
            enabled = values.boolean(KEY_ENABLED, defaults.enabled),
            hour = values.int(KEY_HOUR, defaults.hour),
            minute = values.int(KEY_MINUTE, defaults.minute),
            lastAttemptAtMillis = values.long(KEY_LAST_ATTEMPT_AT, defaults.lastAttemptAtMillis),
            lastSuccessAtMillis = values.long(KEY_LAST_SUCCESS_AT, defaults.lastSuccessAtMillis),
            nextRunAtMillis = values.long(KEY_NEXT_RUN_AT, defaults.nextRunAtMillis),
        )
    }

    override suspend fun save(
        settings: AutoSyncSettings,
        updatedAtMillis: Long,
    ) {
        val normalized = AutoSyncSettings.fromStored(
            configured = settings.configured,
            enabled = settings.enabled,
            hour = settings.hour,
            minute = settings.minute,
            lastAttemptAtMillis = settings.lastAttemptAtMillis,
            lastSuccessAtMillis = settings.lastSuccessAtMillis,
            nextRunAtMillis = settings.nextRunAtMillis,
        )
        this.settings.upsertAll(
            listOf(
                KEY_CONFIGURED to normalized.configured.asStoredBoolean(),
                KEY_ENABLED to normalized.enabled.asStoredBoolean(),
                KEY_HOUR to normalized.hour.toString(),
                KEY_MINUTE to normalized.minute.toString(),
                KEY_LAST_ATTEMPT_AT to normalized.lastAttemptAtMillis.toString(),
                KEY_LAST_SUCCESS_AT to normalized.lastSuccessAtMillis.toString(),
                KEY_NEXT_RUN_AT to normalized.nextRunAtMillis.toString(),
            ).map { (key, value) ->
                SettingEntity(
                    key = key,
                    value = value,
                    updatedAt = updatedAtMillis,
                )
            },
        )
    }

    override suspend fun activateAfterFirstSuccess(updatedAtMillis: Long): Boolean {
        val current = get()
        if (current.configured) {
            return false
        }
        save(
            current.copy(
                configured = true,
                enabled = true,
            ),
            updatedAtMillis,
        )
        return true
    }

    override suspend fun setEnabled(
        enabled: Boolean,
        updatedAtMillis: Long,
    ) {
        val current = get()
        save(
            current.copy(
                configured = true,
                enabled = enabled,
            ),
            updatedAtMillis,
        )
    }

    override suspend fun markScheduled(
        nextRunAtMillis: Long,
        updatedAtMillis: Long,
    ) {
        settings.upsert(
            SettingEntity(
                key = KEY_NEXT_RUN_AT,
                value = nextRunAtMillis.coerceAtLeast(0L).toString(),
                updatedAt = updatedAtMillis,
            ),
        )
    }

    override suspend fun recordAttempt(
        attemptedAtMillis: Long,
        success: Boolean,
        updatedAtMillis: Long,
    ) {
        val attemptedAt = attemptedAtMillis.coerceAtLeast(0L)
        val rows = buildList {
            add(KEY_LAST_ATTEMPT_AT to attemptedAt.toString())
            if (success) {
                add(KEY_LAST_SUCCESS_AT to attemptedAt.toString())
            }
        }
        settings.upsertAll(
            rows.map { (key, value) ->
                SettingEntity(
                    key = key,
                    value = value,
                    updatedAt = updatedAtMillis,
                )
            },
        )
    }

    private fun Map<String, String>.int(
        key: String,
        default: Int,
    ): Int = get(key)?.toIntOrNull() ?: default

    private fun Map<String, String>.long(
        key: String,
        default: Long,
    ): Long = get(key)?.toLongOrNull() ?: default

    private fun Map<String, String>.boolean(
        key: String,
        default: Boolean,
    ): Boolean = when (get(key)?.trim()?.lowercase(Locale.ROOT)) {
        "1", "true" -> true
        "0", "false" -> false
        else -> default
    }

    private fun Boolean.asStoredBoolean(): String = if (this) "1" else "0"

    private companion object {
        private const val KEY_CONFIGURED = "auto_sync_configured"
        private const val KEY_ENABLED = "auto_sync_enabled"
        private const val KEY_HOUR = "auto_sync_hour"
        private const val KEY_MINUTE = "auto_sync_minute"
        private const val KEY_LAST_ATTEMPT_AT = "auto_sync_last_attempt_at"
        private const val KEY_LAST_SUCCESS_AT = "auto_sync_last_success_at"
        private const val KEY_NEXT_RUN_AT = "auto_sync_next_run_at"
        private val ALL_KEYS = listOf(
            KEY_CONFIGURED,
            KEY_ENABLED,
            KEY_HOUR,
            KEY_MINUTE,
            KEY_LAST_ATTEMPT_AT,
            KEY_LAST_SUCCESS_AT,
            KEY_NEXT_RUN_AT,
        )
    }
}
