package dev.bee.kanjianki.core

object SettingsLearningTextCopy {
    @JvmStatic
    fun learningStepsTitle(): String = "Learning steps"

    @JvmStatic
    fun learningStepsBody(): String {
        return "Set waits for new cards and review misses. Repeats stay practice-only."
    }

    @JvmStatic
    fun reviewMissesLabel(): String = "Review misses"

    @JvmStatic
    fun ankiDefaultLabel(): String = "Anki default"

    @JvmStatic
    fun sameLearningStepsLabel(): String = "Use new-card steps"

    @JvmStatic
    fun saveLearningStepsLabel(): String = "Save learning steps"

    @JvmStatic
    fun learningStepsSavedToast(): String = "Steps saved."
}
