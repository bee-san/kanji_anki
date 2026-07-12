package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsStudyLadder(private val activity: MainActivitySettings) {
    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        val ladder = activity.studyLadderSettings()
        val restoreLabel = SettingsTextCopy.restoreDefaultLadderLabel()
        val rows = buildList {
            REQUIRED_CORE_RUNGS.forEachIndexed { index, rung ->
                add(fixedRungModel(ladder, rung, required = true, sectionTitle = if (index == 0) SettingsTextCopy.requiredCoreChecksTitle() else ""))
            }
            OPTIONAL_VARIANT_RUNGS.forEachIndexed { index, rung ->
                add(fixedRungModel(ladder, rung, required = false, sectionTitle = if (index == 0) SettingsTextCopy.optionalVariantsTitle() else ""))
            }
            ladder.repairTaskOrder.forEachIndexed { index, taskType ->
                add(repairTaskModel(ladder, taskType, index))
            }
        }
        return SettingsStudyLadderPanelModel(
            title = SettingsTextCopy.studyLadderTitle(),
            body = SettingsTextCopy.studyLadderBody(),
            rungs = rows,
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

    fun toggleRepairTask(
        current: RecordsBase.StudyLadderSettings,
        taskType: String,
    ) {
        val wasEnabled = current.isRepairTaskEnabled(taskType)
        saveStudyLadderSettings(
            traceSection = "kani.settings.study-ladder.toggle-repair",
            next = SettingsWriteActions.toggleStudyRepair(current, taskType),
            toastMessage = SettingsTextCopy.repairTaskToggleToast(taskType, wasEnabled),
        )
    }

    private fun fixedRungModel(
        ladder: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        required: Boolean,
        sectionTitle: String,
    ): SettingsStudyLadderRungModel {
        val label = SettingsTextCopy.settingsLadderRungLabel(rung)
        val enabled = ladder.isEnabled(rung)
        val moveUpLabel = SettingsTextCopy.moveUpLabel()
        val moveDownLabel = SettingsTextCopy.moveDownLabel()
        return SettingsStudyLadderRungModel(
            label = label,
            subtitle = if (required) SettingsTextCopy.requiredCoreSubtitle(rung) else SettingsTextCopy.ladderRungSubtitle(ladder, rung),
            toggleLabel = if (required) SettingsTextCopy.requiredCoreLabel() else SettingsTextCopy.ladderToggleLabel(enabled),
            moveUpLabel = moveUpLabel,
            moveDownLabel = moveDownLabel,
            canMoveUp = false,
            canMoveDown = false,
            toggleDescription = if (required) SettingsTextCopy.requiredCoreDescription(label) else toggleDescription(label, enabled),
            moveUpDescription = ladderActionDescription(moveUpLabel, label),
            moveDownDescription = ladderActionDescription(moveDownLabel, label),
            toggleTraceSection = settingsButtonTraceSection(label),
            moveUpTraceSection = settingsButtonTraceSection(moveUpLabel),
            moveDownTraceSection = settingsButtonTraceSection(moveDownLabel),
            onToggle = SettingsStudyLadderAction { toggleLadderRung(ladder, rung) },
            onMoveUp = SettingsStudyLadderAction {},
            onMoveDown = SettingsStudyLadderAction {},
            sectionTitle = sectionTitle,
            toggleEnabled = !required,
            showPriorityControls = false,
        )
    }

    private fun repairTaskModel(
        ladder: RecordsBase.StudyLadderSettings,
        taskType: String,
        index: Int,
    ): SettingsStudyLadderRungModel {
        val label = SettingsTextCopy.repairTaskLabel(taskType)
        val enabled = ladder.isRepairTaskEnabled(taskType)
        val moveUpLabel = SettingsTextCopy.moveUpLabel()
        val moveDownLabel = SettingsTextCopy.moveDownLabel()
        return SettingsStudyLadderRungModel(
            label = label,
            subtitle = SettingsTextCopy.repairTaskSubtitle(taskType, enabled),
            toggleLabel = SettingsTextCopy.ladderToggleLabel(enabled),
            moveUpLabel = moveUpLabel,
            moveDownLabel = moveDownLabel,
            canMoveUp = index > 0,
            canMoveDown = index < ladder.repairTaskOrder.lastIndex,
            toggleDescription = toggleDescription(label, enabled),
            moveUpDescription = ladderActionDescription(moveUpLabel, label),
            moveDownDescription = ladderActionDescription(moveDownLabel, label),
            toggleTraceSection = settingsButtonTraceSection(label),
            moveUpTraceSection = settingsButtonTraceSection(moveUpLabel),
            moveDownTraceSection = settingsButtonTraceSection(moveDownLabel),
            onToggle = SettingsStudyLadderAction { toggleRepairTask(ladder, taskType) },
            onMoveUp = SettingsStudyLadderAction { moveRepairTask(ladder, taskType, -1) },
            onMoveDown = SettingsStudyLadderAction { moveRepairTask(ladder, taskType, 1) },
            sectionTitle = if (index == 0) SettingsTextCopy.repairToolsTitle() else "",
        )
    }

    private fun moveRepairTask(
        current: RecordsBase.StudyLadderSettings,
        taskType: String,
        direction: Int,
    ) {
        val next = SettingsWriteActions.moveStudySupportPriority(current, taskType, direction)
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
            activity.renderSettingsStudyBehavior(true)
        }
    }

    private companion object {
        val REQUIRED_CORE_RUNGS = listOf(
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.LadderRung.WORD_READING,
        )
        val OPTIONAL_VARIANT_RUNGS = listOf(
            RecordsBase.LadderRung.FONT_MEANING,
            RecordsBase.LadderRung.SENTENCE_READING,
        )

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
