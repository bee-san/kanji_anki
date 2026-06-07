package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsStudyLadder(private val activity: MainActivitySettings) {
    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        val ladder = activity.studyLadderSettings()
        val restoreLabel = SettingsTextCopy.restoreDefaultLadderLabel()
        return SettingsStudyLadderPanelModel(
            title = SettingsTextCopy.studyLadderTitle(),
            body = SettingsTextCopy.studyLadderBody(),
            rungs = ladder.orderedRungs.mapIndexed { index, rung ->
                rungModel(ladder, rung, index)
            },
            restoreLabel = restoreLabel,
            restoreDescription = restoreLabel,
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
        activity.renderSettings(true)
    }

    private fun rungModel(
        ladder: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        index: Int,
    ): SettingsStudyLadderRungModel {
        val label = SettingsTextCopy.settingsLadderRungLabel(rung)
        val rungs = ladder.orderedRungs
        val enabled = ladder.isEnabled(rung)
        val moveUpLabel = SettingsTextCopy.moveUpLabel()
        val moveDownLabel = SettingsTextCopy.moveDownLabel()
        return SettingsStudyLadderRungModel(
            label = label,
            subtitle = SettingsTextCopy.ladderRungSubtitle(ladder, rung),
            toggleLabel = SettingsTextCopy.ladderToggleLabel(enabled),
            moveUpLabel = moveUpLabel,
            moveDownLabel = moveDownLabel,
            canMoveUp = index > 0,
            canMoveDown = index < rungs.size - 1,
            toggleDescription = toggleDescription(label, enabled),
            moveUpDescription = ladderActionDescription(moveUpLabel, label),
            moveDownDescription = ladderActionDescription(moveDownLabel, label),
            onToggle = SettingsStudyLadderAction { toggleLadderRung(rung) },
            onMoveUp = SettingsStudyLadderAction { moveRung(rung, -1) },
            onMoveDown = SettingsStudyLadderAction { moveRung(rung, 1) }
        )
    }

    private fun moveRung(rung: RecordsBase.LadderRung, direction: Int) {
        activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(rung, direction))
        activity.renderSettings(true)
    }

    private fun restoreDefaultLadderSettings() {
        activity.store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults())
        Toast.makeText(activity, SettingsTextCopy.studyLadderRestoredToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings(true)
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
