package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.sync.AutoSyncSettings

interface AutoSyncSettingsRepository {
    suspend fun get(): AutoSyncSettings

    suspend fun save(
        settings: AutoSyncSettings,
        updatedAtMillis: Long,
    )

    suspend fun activateAfterFirstSuccess(updatedAtMillis: Long): Boolean

    suspend fun setEnabled(
        enabled: Boolean,
        updatedAtMillis: Long,
    )

    suspend fun markScheduled(
        nextRunAtMillis: Long,
        updatedAtMillis: Long,
    )

    suspend fun recordAttempt(
        attemptedAtMillis: Long,
        success: Boolean,
        updatedAtMillis: Long,
    )
}
