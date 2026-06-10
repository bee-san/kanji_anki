package dev.bee.kanjianki.core

object SettingsNoteTypeTextCopy {
    @JvmStatic
    fun noteTypeFieldsTitle(): String = "Note type"

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String {
        val safeModelName = modelName?.javaTrim() ?: ""
        if (safeModelName.isEmpty()) {
            return "Select a note type"
        }
        return safeModelName
    }

    @JvmStatic
    fun noteTypeFieldsBody(): String {
        return "Use Kiku or map Anki fields."
    }

    @JvmStatic
    fun requiredFieldsTitle(): String = "Fields"

    @JvmStatic
    fun requiredFieldsBody(): String {
        return "Choose the fields Kani reads."
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
    fun noteTypeSavedToast(): String = "Saved. Sync to apply fields."

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }
}
