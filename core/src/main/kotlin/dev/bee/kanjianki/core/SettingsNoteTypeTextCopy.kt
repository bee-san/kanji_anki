package dev.bee.kanjianki.core

object SettingsNoteTypeTextCopy {
    @JvmStatic
    fun noteTypeFieldsTitle(): String = "Note type"

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String {
        val displayName = modelName?.trim().orEmpty()
        return if (displayName.isEmpty()) "Choose a note type" else "Using $displayName"
    }

    @JvmStatic
    fun noteTypeFieldsBody(): String {
        return "Choose fields or keep Kiku defaults."
    }

    @JvmStatic
    fun requiredFieldsTitle(): String = "Required field"

    @JvmStatic
    fun requiredFieldsBody(): String {
        return "Set the field that contains each kanji."
    }

    @JvmStatic
    fun expressionFieldLabel(): String = "Kanji field"

    @JvmStatic
    fun readingFieldLabel(): String = "Reading field"

    @JvmStatic
    fun meaningFieldLabel(): String = "Meaning field"

    @JvmStatic
    fun sentenceFieldLabel(): String = "Sentence field"

    @JvmStatic
    fun frequencyFieldLabel(): String = "Frequency field"

    @JvmStatic
    fun frequencySortFieldLabel(): String = "Frequency sort field"

    @JvmStatic
    fun chooseFromAnkiDroidLabel(): String = "Choose from AnkiDroid"

    @JvmStatic
    fun useKikuLabel(): String = "Use Kiku defaults"

    @JvmStatic
    fun saveNoteTypeLabel(): String = "Save note type"

    @JvmStatic
    fun noteTypeRequiredToast(): String = "Enter a note type name."

    @JvmStatic
    fun expressionFieldRequiredToast(): String = "Choose the kanji field."

    @JvmStatic
    fun noteTypeSavedToast(): String = "Saved. Sync to rebuild practice."
}
