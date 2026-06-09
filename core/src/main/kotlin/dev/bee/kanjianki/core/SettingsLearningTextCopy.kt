package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "Set repeat waits; practice does not move the ladder."
    }

    @JvmStatic
    fun reviewMissesLabel(): String = "Missed reviews"

    @JvmStatic
    fun ankiDefaultLabel(): String = "Anki default"

    @JvmStatic
    fun sameLearningStepsLabel(): String = "Match new-card steps"

    @JvmStatic
    fun saveLearningStepsLabel(): String = "Save learning steps"

    @JvmStatic
    fun learningStepsSavedToast(): String = "Steps saved."
}
