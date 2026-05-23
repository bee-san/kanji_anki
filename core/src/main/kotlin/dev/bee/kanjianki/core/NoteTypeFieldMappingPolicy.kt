package dev.bee.kanjianki.core

import java.util.Collections
import kotlin.jvm.JvmWildcard

object NoteTypeFieldMappingPolicy {
    @JvmStatic
    fun guessFields(fields: List<String?>?): FieldGuesses {
        val safeFields = fields.orEmpty()
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        var expression = firstMatchingField(
            safeFields,
            defaults.expressionField,
            "Front",
            "Japanese",
            "Word",
            "Vocabulary",
            "Term",
        )
        var meaning = firstMatchingField(
            safeFields,
            defaults.meaningField,
            "Meaning",
            "Back",
            "Definition",
            "Glossary",
        )
        if (expression.trim().isEmpty() && safeFields.isNotEmpty()) {
            expression = safeFields[0].orEmpty()
        }
        if (meaning.trim().isEmpty() && safeFields.size > 1) {
            meaning = safeFields[1].orEmpty()
        }
        return FieldGuesses.create(
            expression,
            firstMatchingField(safeFields, defaults.readingField, "Reading", "Kana", "Pronunciation"),
            meaning,
            firstMatchingField(safeFields, defaults.sentenceField, "Context", "Example", "ExampleSentence"),
            firstMatchingField(safeFields, defaults.frequencyField, "Freq"),
            firstMatchingField(safeFields, defaults.frequencySortField, "FrequencySort", defaults.frequencyField),
        )
    }

    @JvmStatic
    fun firstMatchingField(fields: List<String?>?, vararg candidates: String?): String {
        val safeFields = fields.orEmpty()
        for (candidate in candidates) {
            for (field in safeFields) {
                if (field != null && candidate != null && field.equals(candidate, ignoreCase = true)) {
                    return field
                }
            }
        }
        return ""
    }

    @JvmStatic
    fun choice(name: String?, fields: List<String?>?): NoteTypeChoice {
        return NoteTypeChoice(name, fields)
    }

    @JvmStatic
    fun label(noteType: NoteTypeChoice?): String {
        val safeNoteType = noteType ?: choice("", emptyList())
        return safeNoteType.name() + " (" + StudyTextCopy.countText(safeNoteType.fields().size, "field", "fields") + ")"
    }

    @JvmStatic
    fun labels(noteTypes: List<@JvmWildcard NoteTypeChoice?>?): Array<String> {
        return noteTypes.orEmpty().map { label(it) }.toTypedArray()
    }

    class NoteTypeChoice(name: String?, fields: List<String?>?) {
        private val nameValue = name.orEmpty()
        private val fieldsValue: List<String>

        init {
            @Suppress("UNCHECKED_CAST")
            fieldsValue = Collections.unmodifiableList(ArrayList(fields.orEmpty())) as List<String>
        }

        fun name(): String {
            return nameValue
        }

        fun fields(): List<String> {
            return fieldsValue
        }
    }

    class FieldGuesses private constructor(
        @JvmField val expression: String,
        @JvmField val reading: String,
        @JvmField val meaning: String,
        @JvmField val sentence: String,
        @JvmField val frequency: String,
        @JvmField val frequencySort: String,
    ) {
        companion object {
            @JvmSynthetic
            fun create(
                expression: String?,
                reading: String?,
                meaning: String?,
                sentence: String?,
                frequency: String?,
                frequencySort: String?,
            ): FieldGuesses {
                return FieldGuesses(
                    expression.orEmpty(),
                    reading.orEmpty(),
                    meaning.orEmpty(),
                    sentence.orEmpty(),
                    frequency.orEmpty(),
                    frequencySort.orEmpty(),
                )
            }
        }
    }
}
