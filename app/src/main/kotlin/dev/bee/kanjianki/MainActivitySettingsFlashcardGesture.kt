package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.StudyGestureTextCopy

internal class MainActivitySettingsFlashcardGesture(private val activity: MainActivitySettings) {
    fun panelModel(): SettingsFlashcardGesturePanelModel {
        val enabled = activity.loadSettingsDeviceState().flashcardSwipeGestureEnabled
        return SettingsFlashcardGesturePanelModel(
            title = StudyGestureTextCopy.swipeTitle(),
            body = StudyGestureTextCopy.swipeBody(),
            status = StudyGestureTextCopy.swipeStatus(enabled),
            state = SettingsFlashcardGestureState(enabled),
            toggleLabel = StudyGestureTextCopy.swipeToggleLabel(),
            onToggle = SettingsFlashcardGestureToggleAction(::setEnabled),
        )
    }

    private fun setEnabled(enabled: Boolean) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.flashcard-swipe.toggle",
            write = { activity.deviceSettingsStore.setFlashcardSwipeGestureEnabled(enabled) },
        ) {
            Toast.makeText(
                activity,
                if (enabled) StudyGestureTextCopy.swipeEnabledToast() else StudyGestureTextCopy.swipeDisabledToast(),
                Toast.LENGTH_SHORT,
            ).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
