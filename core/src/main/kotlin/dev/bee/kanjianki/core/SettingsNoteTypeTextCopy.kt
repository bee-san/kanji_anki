package dev.bee.kanjianki.core

object SettingsNoteTypeTextCopy {
    @JvmStatic
    fun noteTypeFieldsTitle(): String = "Note type setup"

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String = "Using " + modelName.toString()

    @JvmStatic
    fun noteTypeFieldsBody(): String {
        return "Default: Kiku. Map fields below."
    }

    @JvmStatic
    fun requiredFieldsTitle(): String = "Field mappings"

    @JvmStatic
    fun requiredFieldsBody(): String {
        return "Map expression, reading, meaning, sentence, frequency, and sort."
    }

    @JvmStatic
    fun expressionFieldLabel(): String = "Expression field"

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
    fun chooseFromAnkiDroidLabel(): String = "Choose note type"

    @JvmStatic
    fun useKikuLabel(): String = "Use Kiku"

    @JvmStatic
    fun saveNoteTypeLabel(): String = "Save note type"

    @JvmStatic
    fun noteTypeRequiredToast(): String = "Enter a note type name."

    @JvmStatic
    fun expressionFieldRequiredToast(): String = "Choose the kanji field."

    @JvmStatic
    fun noteTypeSavedToast(): String = "Saved. Sync to rebuild practice."
}
