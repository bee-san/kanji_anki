package dev.bee.kanjianki

internal class MainActivityStudyWritingToolbar(private val activity: MainActivityStudy) {
    fun buildComposeActionBarState(): WritingActionsBarState {
        val state = WritingActionsBarState()
        activity.writingToolActionsView = WritingToolActionsView(activity, state)
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity, state)
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity, state)
        return state
    }
}
