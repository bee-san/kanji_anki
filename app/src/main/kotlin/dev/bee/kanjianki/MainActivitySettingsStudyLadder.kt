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
            restoreTraceSection = settingsButtonTraceSection(restoreLabel),
            onRestore = SettingsStudyLadderAction { restoreDefaultLadderSettings() }
        )
    }

    fun toggleLadderRung(
        current: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
    ) {
        val wasEnabled = current.isEnabled(rung)
        val next = SettingsWriteActions.toggleStudyLadder(current, rung)
        if (next == null) {
            Toast.makeText(activity, SettingsTextCopy.keepAlwaysAvailableRungToast(), Toast.LENGTH_SHORT).show()
            return
        }
        saveStudyLadderSettings(
            traceSection = "kani.settings.study-ladder.toggle",
            next = next,
            toastMessage = SettingsTextCopy.ladderRungToggleToast(rung, wasEnabled),
        )
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
            toggleTraceSection = settingsButtonTraceSection(label),
            moveUpTraceSection = settingsButtonTraceSection(moveUpLabel),
            moveDownTraceSection = settingsButtonTraceSection(moveDownLabel),
            onToggle = SettingsStudyLadderAction { toggleLadderRung(ladder, rung) },
            onMoveUp = SettingsStudyLadderAction { moveRung(ladder, rung, -1) },
            onMoveDown = SettingsStudyLadderAction { moveRung(ladder, rung, 1) }
        )
    }

    private fun moveRung(
        current: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        direction: Int,
    ) {
        val next = SettingsWriteActions.moveStudyLadder(current, rung, direction)
        saveStudyLadderSettings(
            traceSection = "kani.settings.study-ladder.move",
            next = next,
        )
    }

    private fun restoreDefaultLadderSettings() {
        saveStudyLadderSettings(
            traceSection = "kani.settings.study-ladder.restore",
            next = RecordsBase.StudyLadderSettings.defaults(),
            toastMessage = SettingsTextCopy.studyLadderRestoredToast(),
        )
    }

    private fun saveStudyLadderSettings(
        traceSection: String,
        next: RecordsBase.StudyLadderSettings,
        toastMessage: String? = null,
    ) {
        activity.runSettingsWrite(
            traceSection = traceSection,
            write = {
                activity.store.saveStudyLadderSettings(next)
            },
        ) {
            toastMessage?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
            activity.renderSettings(true)
        }
    }

    private companion object {
        fun settingsButtonTraceSection(label: String): String {
            return "kani.button.${traceToken(label)}"
        }

        fun toggleDescription(rungLabel: String, enabled: Boolean): String {
            return (if (enabled) "Turn off " else "Turn on ") + rungLabel
        }

        fun ladderActionDescription(action: String, rungLabel: String): String {
            return "$action $rungLabel"
        }
    }
}
