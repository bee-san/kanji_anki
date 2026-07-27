package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.DeckLimitsSettingsPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.SettingsSaveCommand

internal class MainActivitySettingsDeckLimitsPanel(private val activity: MainActivitySettings) {
    fun deckLimitsSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsDeckLimitsPanelModel {
        return SettingsDeckLimitsPanelModels.create(
            current = current,
            onSave = SettingsDeckLimitsSaveAction { newPerDayText, activeQueueCapText ->
                saveDeckLimits(newPerDayText, current.newPerDay, activeQueueCapText, current.activeQueueCap)
            },
        )
    }

    private fun saveDeckLimits(
        newPerDayText: String,
        newPerDayFallback: Int,
        activeQueueCapText: String,
        activeQueueCapFallback: Int,
    ) {
        val newPerDay = DeckLimitsSettingsPolicy.saveNewPerDay(newPerDayText, newPerDayFallback)
        val activeQueueCap = DeckLimitsSettingsPolicy.saveActiveQueueCap(activeQueueCapText, activeQueueCapFallback)
        activity.runSettingsWrite(
            traceSection = "kani.settings.deck-limits.save",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.DeckLimits(
                        newPerDay.newPerDay,
                        activeQueueCap.newPerDay,
                    ),
                )
            },
        ) {
            Toast.makeText(activity, newPerDay.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
