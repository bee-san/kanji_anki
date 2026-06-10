package dev.bee.kanjianki.core

object SettingsLadderThresholdTextCopy {
    @JvmStatic
    fun ladderThresholdsTitle(): String = "Ladder movement"

    @JvmStatic
    fun ladderThresholdsBody(): String {
        return "Due reviews move cards. Repeats stay practice-only."
    }

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = "Days to move up"

    @JvmStatic
    fun failsToGoDownLabel(): String = "Fails to move down"

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String = "Use default movement rules"

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = "Save rules"

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = "Movement rules saved."
}
