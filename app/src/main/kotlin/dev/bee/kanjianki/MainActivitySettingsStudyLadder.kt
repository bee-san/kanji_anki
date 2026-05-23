package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsStudyLadder(private val activity: MainActivitySettings) {
    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        val ladder = activity.studyLadderSettings()
        return SettingsStudyLadderPanelModel(
            title = SettingsTextCopy.studyLadderTitle(),
            body = SettingsTextCopy.studyLadderBody(),
            rungs = ladder.orderedRungs.mapIndexed { index, rung ->
                rungModel(ladder, rung, index)
            },
            restoreLabel = SettingsTextCopy.restoreDefaultLadderLabel(),
            restoreDescription = SettingsTextCopy.restoreDefaultLadderLabel(),
            onRestore = SettingsStudyLadderAction { restoreDefaultLadderSettings() }
        )
    }

    fun toggleLadderRung(rung: RecordsBase.LadderRung) {
        val current = activity.studyLadderSettings()
        val wasEnabled = current.isEnabled(rung)
        val next = current.withRungEnabled(rung, !wasEnabled)
        if (wasEnabled && next.enabledText() == current.enabledText()) {
            Toast.makeText(activity, SettingsTextCopy.keepAlwaysAvailableRungToast(), Toast.LENGTH_SHORT).show()
            return
        }
        activity.store.saveStudyLadderSettings(next)
        Toast.makeText(activity, SettingsTextCopy.ladderRungToggleToast(rung, wasEnabled), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }

    private fun rungModel(
        ladder: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        index: Int,
    ): SettingsStudyLadderRungModel {
        val label = SettingsTextCopy.settingsLadderRungLabel(rung)
        val rungs = ladder.orderedRungs
        return SettingsStudyLadderRungModel(
            label = label,
            subtitle = SettingsTextCopy.ladderRungSubtitle(ladder, rung),
            toggleLabel = SettingsTextCopy.ladderToggleLabel(ladder.isEnabled(rung)),
            moveUpLabel = SettingsTextCopy.moveUpLabel(),
            moveDownLabel = SettingsTextCopy.moveDownLabel(),
            canMoveUp = index > 0,
            canMoveDown = index < rungs.size - 1,
            toggleDescription = toggleDescription(label, ladder.isEnabled(rung)),
            moveUpDescription = ladderActionDescription(SettingsTextCopy.moveUpLabel(), label),
            moveDownDescription = ladderActionDescription(SettingsTextCopy.moveDownLabel(), label),
            onToggle = SettingsStudyLadderAction { toggleLadderRung(rung) },
            onMoveUp = SettingsStudyLadderAction { moveRung(rung, -1) },
            onMoveDown = SettingsStudyLadderAction { moveRung(rung, 1) }
        )
    }

    private fun moveRung(rung: RecordsBase.LadderRung, direction: Int) {
        activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(rung, direction))
        activity.renderSettings()
    }

    private fun restoreDefaultLadderSettings() {
        activity.store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults())
        Toast.makeText(activity, SettingsTextCopy.studyLadderRestoredToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }

    private companion object {
        fun toggleDescription(rungLabel: String, enabled: Boolean): String {
            return (if (enabled) "Turn off " else "Turn on ") + rungLabel
        }

        fun ladderActionDescription(action: String, rungLabel: String): String {
            return "$action $rungLabel"
        }
    }
}
