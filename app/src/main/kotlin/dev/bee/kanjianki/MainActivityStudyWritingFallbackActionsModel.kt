package dev.bee.kanjianki

data class WritingFallbackActionsModel(
    val replayVisible: Boolean,
    val manualOverrideVisible: Boolean,
    val practiceWithGuideVisible: Boolean,
    val onReplay: Runnable,
    val onManualOverride: Runnable,
    val onPracticeWithGuide: Runnable,
    val manualOverrideLabel: String = dev.bee.kanjianki.core.StudyWritingCopy.manualOverrideLabel(),
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
