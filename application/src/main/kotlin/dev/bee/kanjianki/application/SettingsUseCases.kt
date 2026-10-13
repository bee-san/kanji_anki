package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot

/** Portable settings loading and mutation boundary for UI hosts. */
class SettingsUseCases(
    private val repository: SettingsRepository,
) {
    suspend fun load(): SettingsSnapshot =
        repository.load().valueOrThrow("load settings")

    suspend fun save(command: SettingsSaveCommand) {
        repository.save(command).valueOrThrow("save settings")
    }
}
