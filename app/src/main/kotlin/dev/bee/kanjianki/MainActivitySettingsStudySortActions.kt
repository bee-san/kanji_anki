package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.sync.SyncSettings

internal class MainActivitySettingsStudySortActions(private val activity: MainActivitySettings) {
    fun saveNewCardSort(mode: String) {
        val request = NewCardSortSettingsPolicy.saveRequest(mode)
        activity.store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, request.mode)
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }
}
