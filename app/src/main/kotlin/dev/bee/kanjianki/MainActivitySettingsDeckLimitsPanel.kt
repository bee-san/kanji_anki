package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.DeckLimitsSettingsPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncSettings

internal class MainActivitySettingsDeckLimitsPanel(private val activity: MainActivitySettings) {
    fun deckLimitsSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsDeckLimitsPanelModel {
        return SettingsDeckLimitsPanelModels.create(
            current = current,
            onSave = SettingsDeckLimitsSaveAction { text -> saveNewPerDay(text, current.newPerDay) },
        )
    }

    private fun saveNewPerDay(text: String, fallback: Int) {
        val request = DeckLimitsSettingsPolicy.saveNewPerDay(text, fallback)
        activity.runSettingsWrite(
            traceSection = "kani.settings.deck-limits.save",
            write = {
                activity.store.putIntSetting(SyncSettings.NEW_PER_DAY_SETTING_KEY, request.newPerDay)
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettings(true)
        }
    }
}
