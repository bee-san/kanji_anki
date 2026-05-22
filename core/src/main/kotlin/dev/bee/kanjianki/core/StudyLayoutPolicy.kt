package dev.bee.kanjianki.core

object StudyLayoutPolicy {
    @JvmStatic
    fun writingPadHeightDp(screenHeightDp: Int): Int {
        if (screenHeightDp < 700) {
            return 300
        }
        if (screenHeightDp < 820) {
            return 340
        }
        return 390
    }
}
