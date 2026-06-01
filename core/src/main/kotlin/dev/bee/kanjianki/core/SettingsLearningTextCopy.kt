package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "New cards and review misses can come back fast. Repeats after first answer don't change Kani SRS."
    }

    @JvmStatic
    fun reviewMissesLabel(): String = "Review misses"

    @JvmStatic
    fun ankiDefaultLabel(): String = "Anki default"

    @JvmStatic
    fun sameLearningStepsLabel(): String = "Both 1m 10m"

    @JvmStatic
    fun saveLearningStepsLabel(): String = "Save learning steps"

    @JvmStatic
    fun learningStepsSavedToast(): String = "Learning steps saved."
}
