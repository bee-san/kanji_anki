package dev.bee.kanjianki.core

object SettingsNoteTypeTextCopy {
    @JvmStatic
    fun noteTypeFieldsTitle(): String = "Note type and fields"

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String = "Using " + modelName.toString()

    @JvmStatic
    fun noteTypeFieldsBody(): String {
        return "Default: Kiku. Keep one note type and map the fields below."
    }

    @JvmStatic
    fun requiredFieldsTitle(): String = "Required fields"

    @JvmStatic
    fun requiredFieldsBody(): String {
        return "Map the fields Kani needs: expression, reading, meaning, sentence, frequency, and frequency sort."
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
    fun expressionFieldRequiredToast(): String = "Choose the field that contains kanji."

    @JvmStatic
    fun noteTypeSavedToast(): String = "Note type saved. Sync again to rebuild practice."
}
