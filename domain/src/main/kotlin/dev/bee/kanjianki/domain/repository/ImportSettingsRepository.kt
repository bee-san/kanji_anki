package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.importing.ImportSettings

interface ImportSettingsRepository {
    suspend fun get(): ImportSettings

    suspend fun save(
        settings: ImportSettings,
        updatedAtMillis: Long,
    )
}
