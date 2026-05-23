package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer."
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
