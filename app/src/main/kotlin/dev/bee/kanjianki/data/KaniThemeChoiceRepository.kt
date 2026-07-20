package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniThemeChoice

internal class KaniThemeChoiceRepository(
    private val settingsRepository: SettingsRepository,
) {
    fun currentChoice(): KaniThemeChoice {
        return KaniThemeChoice.fromStorageKey(
            settingsRepository.getString(KaniThemeChoice.SETTING_KEY, null)
        )
    }

    fun saveChoice(choice: KaniThemeChoice?): KaniThemeChoice {
        val normalized = choice ?: KaniThemeChoice.GIRLYPOP
        settingsRepository.putString(KaniThemeChoice.SETTING_KEY, normalized.storageKey)
        return normalized
    }
}
