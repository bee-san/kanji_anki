package dev.bee.kanjianki.core

object SettingsLadderThresholdTextCopy {
    @JvmStatic
    fun ladderThresholdsTitle(): String = "Ladder thresholds"

    @JvmStatic
    fun ladderThresholdsBody(): String {
        return "Only due reviews move the ladder; learning/relearning repeats are practice only."
    }

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = "Promotion interval days"

    @JvmStatic
    fun failsToGoDownLabel(): String = "Demotion fail streak"

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String = "Use default ladder thresholds"

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = "Save ladder thresholds"

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = "Ladder thresholds saved."
}
