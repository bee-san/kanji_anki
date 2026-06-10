package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "Set new/missed waits. Due reviews move cards up."
    }

    @JvmStatic
    fun reviewMissesLabel(): String = "Missed reviews"

    @JvmStatic
    fun ankiDefaultLabel(): String = "Use Anki defaults"

    @JvmStatic
    fun sameLearningStepsLabel(): String = "Copy new-card steps"

    @JvmStatic
    fun saveLearningStepsLabel(): String = "Save learning steps"

    @JvmStatic
    fun learningStepsSavedToast(): String = "Steps saved."
}
