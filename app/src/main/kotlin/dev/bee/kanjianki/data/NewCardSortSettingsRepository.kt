package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.sync.SyncSettings

internal class NewCardSortSettingsRepository(
    private val settings: SettingsRepository,
) {
    fun saveMode(mode: String?): NewCardSortSettingsPolicy.SaveRequest {
        val request = NewCardSortSettingsPolicy.saveRequest(mode)
        settings.putString(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, request.mode)
        return request
    }
}
