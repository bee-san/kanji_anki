package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.SettingsSaveCommand

internal class MainActivitySettingsAnkiSourceFrequencyRange(
    private val activity: MainActivitySettings,
    private val validation: MainActivitySettingsAnkiSourceValidation,
) {
    fun frequencyRangeSettingsPanelModel(
        current: RecordsSyncModels.Settings,
    ): SettingsFrequencyRangePanelModel {
        val selected = intArrayOf(current.suspendedRankMin, current.suspendedRankMax)
        return SettingsFrequencyRangePanelModel(
            title = SettingsTextCopy.frequencyRangeTitle(),
            body = SettingsTextCopy.frequencyRangeBody(),
            selectedRanks = selected,
            minRankLabel = SettingsTextCopy.minRankLabel(),
            initialMinRankText = selected[0].toString(),
            maxRankLabel = SettingsTextCopy.maxRankLabel(),
            initialMaxRankText = selected[1].toString(),
            minimumRankLabel = SettingsTextCopy.minimumRankLabel(),
            maximumRankLabel = SettingsTextCopy.maximumRankLabel(),
            saveLabel = SettingsTextCopy.saveFrequencyRangeLabel(),
            onSave = SettingsFrequencyRangeSaveAction { minRankText, maxRankText ->
                saveFrequencyRange(minRankText, maxRankText)
            }
        )
    }

    private fun saveFrequencyRange(minRankText: String, maxRankText: String) {
        val minRank: Int
        val maxRank: Int
        try {
            minRank = validation.parseRankText(minRankText)
            maxRank = validation.parseRankText(maxRankText)
        } catch (_: NumberFormatException) {
            Toast.makeText(activity, SettingsTextCopy.numericRanksToast(), Toast.LENGTH_SHORT).show()
            return
        }
        if (!SettingsInputRules.validRank(minRank) || !SettingsInputRules.validRank(maxRank)) {
            Toast.makeText(activity, SettingsTextCopy.rankRangeToast(), Toast.LENGTH_SHORT).show()
            return
        }
        saveFrequencyRange(SettingsInputRules.normalizedRankRange(minRank, maxRank))
    }

    private fun saveFrequencyRange(rankRange: SettingsInputRules.RankRange) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.frequency-range.save",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.FrequencyRange(rankRange.minRank, rankRange.maxRank),
                )
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.frequencyRangeSavedToast(), Toast.LENGTH_LONG).show()
            activity.renderSettingsImportSync(true)
        }
    }
}
