package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.RetentionSettingsPolicy
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsRetentionPanel(private val activity: MainActivitySettings) {
    fun retentionSettingsPanelModel(): SettingsRetentionPanelModel {
        val current = activity.store.schedulerParameters()
        val selected = intArrayOf(SettingsInputRules.retentionPercent(current.targetRetention))
        val state = SettingsRetentionState(
            current.frequencyRetentionEnabled,
            rankRetentionRangesText(current.frequencyRetentionRanges)
        )
        return SettingsRetentionPanelModel(
            title = SettingsTextCopy.fsrsRetentionTitle(),
            body = SettingsTextCopy.fsrsRetentionBody(),
            selectedRetentionPercent = selected,
            presetValues = intArrayOf(85, 90, 95),
            state = state,
            rankRetentionLabel = SettingsTextCopy.useJitenRankRetentionRangesLabel(),
            rankRangesBody = SettingsTextCopy.jitenRankRetentionRangesBody(),
            exampleRangesText = FrequencyRetentionRanges.exampleText(),
            exampleRangesLabel = SettingsTextCopy.useExampleRangesLabel(),
            saveLabel = SettingsTextCopy.saveRetentionLabel(),
            onSave = SettingsRetentionSaveAction { retentionPercent, rankRetentionEnabled, rankRanges ->
                saveRetention(retentionPercent, rankRetentionEnabled, rankRanges)
            }
        )
    }

    private fun saveRetention(
        retentionPercent: Int,
        rankRetentionEnabled: Boolean,
        rankRanges: String
    ) {
        val request = RetentionSettingsPolicy.saveRequest(
            retentionPercent,
            rankRetentionEnabled,
            rankRanges,
            activity.store.schedulerParameters()
        )
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_LONG).show()
            return
        }
        activity.runSettingsWrite(
            traceSection = "kani.settings.retention.save",
            write = {
                activity.store.saveSchedulerParameters(request.parameters!!)
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }

    private companion object {
        fun rankRetentionRangesText(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            return trimmed.ifEmpty { FrequencyRetentionRanges.exampleText() }
        }
    }
}
