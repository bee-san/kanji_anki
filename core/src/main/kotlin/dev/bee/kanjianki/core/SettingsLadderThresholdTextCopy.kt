package dev.bee.kanjianki.core

import java.util.Locale

object SettingsLadderThresholdTextCopy {
    @JvmStatic
    fun ladderThresholdsTitle(): String = "Ladder thresholds"

    @JvmStatic
    fun ladderThresholdsBody(): String {
        return "Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only."
    }

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = "FSRS days to go up"

    @JvmStatic
    fun failsToGoDownLabel(): String = "Fails to go down"

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String {
        return String.format(
            Locale.ROOT,
            "Use %d and %d",
            RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
            RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
        )
    }

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = "Save ladder thresholds"

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = "Ladder thresholds saved."
}
