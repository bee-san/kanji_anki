package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "New cards and relearning use short steps. Those repeats are practice only."
    }

    @JvmStatic
    fun reviewMissesLabel(): String = "Relearning"

    @JvmStatic
    fun ankiDefaultLabel(): String = "Anki default"

    @JvmStatic
    fun sameLearningStepsLabel(): String = "Use new-card steps"

    @JvmStatic
    fun saveLearningStepsLabel(): String = "Save learning steps"

    @JvmStatic
    fun learningStepsSavedToast(): String = "Learning steps saved."
}
