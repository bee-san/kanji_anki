package dev.bee.kanjianki.core

object SettingsLadderThresholdTextCopy {
    @JvmStatic
    fun ladderThresholdsTitle(): String = "Ladder movement"

    @JvmStatic
    fun ladderThresholdsBody(): String {
        return "Due reviews move cards; repeats stay practice-only."
    }

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = "Days to move up"

    @JvmStatic
    fun failsToGoDownLabel(): String = "Misses to move down"

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String = "Use default rules"

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = "Save movement rules"

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = "Movement rules saved."
}
