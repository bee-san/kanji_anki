package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import dev.bee.kanjianki.core.RecordsSyncModels

object SettingsNoteTypeTestTags {
    const val NOTE_TYPE_INPUT = "settings-note-type-input"
    const val EXPRESSION_FIELD_INPUT = "settings-note-type-expression-field-input"
    const val READING_FIELD_INPUT = "settings-note-type-reading-field-input"
    const val MEANING_FIELD_INPUT = "settings-note-type-meaning-field-input"
    const val SENTENCE_FIELD_INPUT = "settings-note-type-sentence-field-input"
    const val FREQUENCY_FIELD_INPUT = "settings-note-type-frequency-field-input"
    const val FREQUENCY_SORT_FIELD_INPUT = "settings-note-type-frequency-sort-field-input"
}

fun interface SettingsNoteTypeAction {
    fun run(fields: SettingsNoteTypeFieldState)
}

class SettingsNoteTypeFieldState(
    noteType: String?,
    expression: String?,
    reading: String?,
    meaning: String?,
    sentence: String?,
    frequency: String?,
    frequencySort: String?,
) : NoteTypeFieldMappings.FieldInputs {
    private var noteTypeState by mutableStateOf(normalizedNoteType(noteType))
    private var expressionState by mutableStateOf(normalizedField(expression))
    private var readingState by mutableStateOf(normalizedField(reading))
    private var meaningState by mutableStateOf(normalizedField(meaning))
    private var sentenceState by mutableStateOf(normalizedField(sentence))
    private var frequencyState by mutableStateOf(normalizedField(frequency))
    private var frequencySortState by mutableStateOf(normalizedField(frequencySort))

    val noteType: String
        get() = noteTypeState
    val expression: String
        get() = expressionState
    val reading: String
        get() = readingState
    val meaning: String
        get() = meaningState
    val sentence: String
        get() = sentenceState
    val frequency: String
        get() = frequencyState
    val frequencySort: String
        get() = frequencySortState

    override fun setNoteType(value: String?) {
        noteTypeState = value.orEmpty()
    }

    override fun setExpression(value: String?) {
        expressionState = value.orEmpty()
    }

    override fun setReading(value: String?) {
        readingState = value.orEmpty()
    }

    override fun setMeaning(value: String?) {
        meaningState = value.orEmpty()
    }

    override fun setSentence(value: String?) {
        sentenceState = value.orEmpty()
    }

    override fun setFrequency(value: String?) {
        frequencyState = value.orEmpty()
    }

    override fun setFrequencySort(value: String?) {
        frequencySortState = value.orEmpty()
    }

    fun applyDefaults(defaults: RecordsSyncModels.Settings) {
        setNoteType(defaults.modelName)
        setExpression(defaults.expressionField)
        setReading(defaults.readingField)
        setMeaning(defaults.meaningField)
        setSentence(defaults.sentenceField)
        setFrequency(defaults.frequencyField)
        setFrequencySort(defaults.frequencySortField)
    }

    companion object {
        val Saver = listSaver<SettingsNoteTypeFieldState, String>(
            save = { state ->
                listOf(
                    state.noteType,
                    state.expression,
                    state.reading,
                    state.meaning,
                    state.sentence,
                    state.frequency,
                    state.frequencySort,
                )
            },
            restore = { values ->
                SettingsNoteTypeFieldState(
                    noteType = values.getOrNull(0),
                    expression = values.getOrNull(1),
                    reading = values.getOrNull(2),
                    meaning = values.getOrNull(3),
                    sentence = values.getOrNull(4),
                    frequency = values.getOrNull(5),
                    frequencySort = values.getOrNull(6),
                ).apply {
                    // The normal constructor supplies Kiku for an initially blank model. A
                    // restored user draft may intentionally be blank and must remain so.
                    setNoteType(values.getOrNull(0))
                }
            },
        )
    }
}

private fun normalizedNoteType(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    return trimmed.ifEmpty { RecordsSyncModels.Settings.kikuDefaults().modelName }
}

private fun normalizedField(value: String?): String = value?.trim().orEmpty()

data class SettingsNoteTypePanelModel(
    val title: String,
    val status: String,
    val body: String,
    val fields: SettingsNoteTypeFieldState,
    val requiredTitle: String,
    val requiredBody: String,
    val noteTypeLabel: String,
    val expressionLabel: String,
    val readingLabel: String,
    val meaningLabel: String,
    val sentenceLabel: String,
    val frequencyLabel: String,
    val frequencySortLabel: String,
    val chooseLabel: String,
    val kikuLabel: String,
    val saveLabel: String,
    val onChoose: SettingsNoteTypeAction,
    val onUseKiku: SettingsNoteTypeAction,
    val onSave: SettingsNoteTypeAction,
) : SettingsPanelModel
