package dev.bee.kanjianki

data class WritingFallbackActionsModel(
    val replayVisible: Boolean,
    val manualOverrideVisible: Boolean,
    val practiceWithGuideVisible: Boolean,
    val onReplay: Runnable,
    val onManualOverride: Runnable,
    val onPracticeWithGuide: Runnable,
) {
    companion object {
        fun initial(): WritingFallbackActionsModel {
            return WritingFallbackActionsModel(
                replayVisible = false,
                manualOverrideVisible = false,
                practiceWithGuideVisible = false,
                onReplay = Runnable {},
                onManualOverride = Runnable {},
                onPracticeWithGuide = Runnable {}
            )
        }
    }
}
