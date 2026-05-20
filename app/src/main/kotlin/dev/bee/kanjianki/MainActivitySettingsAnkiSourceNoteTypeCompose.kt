@file:JvmName("MainActivitySettingsAnkiSourceNoteTypeCompose")

package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.RecordsSyncModels

private val NoteTypeInk = Color(0xFF2D1635)
private val NoteTypeMuted = Color(0xFF6C5674)
private val NoteTypeTeal = Color(0xFF24756C)
private val NoteTypePlum = Color(0xFF6E2B73)
private val NoteTypePinkDark = Color(0xFFDA3A7A)
private val NoteTypePanelBorder = Color(0xFFFFC7DE)
private val NoteTypeButtonBorder = Color(0xFFEEBDDA)
private val NoteTypeWhite = Color(0xFFFFFFFF)
private val NoteTypePanelShape = RoundedCornerShape(24.dp)
private val NoteTypeButtonShape = RoundedCornerShape(12.dp)

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
    fun run()
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
)

internal fun noteTypeSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsNoteTypePanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsNoteTypePanel(model)
            }
        }
    }
}

@Composable
fun SettingsNoteTypePanel(model: SettingsNoteTypePanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NoteTypePanelShape,
        color = NoteTypeWhite,
        border = BorderStroke(1.dp, NoteTypePanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = NoteTypeInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.status,
                color = NoteTypeTeal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = NoteTypeMuted,
                fontSize = 15.sp
            )
            NoteTypeInput(
                label = model.noteTypeLabel,
                value = model.fields.noteType,
                testTag = SettingsNoteTypeTestTags.NOTE_TYPE_INPUT,
                textSizeSp = 20,
                onValueChange = model.fields::setNoteType
            )
            Text(
                text = model.requiredTitle,
                color = NoteTypePlum,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.requiredBody,
                color = NoteTypeMuted,
                fontSize = 14.sp
            )
            NoteTypeInput(
                label = model.expressionLabel,
                value = model.fields.expression,
                testTag = SettingsNoteTypeTestTags.EXPRESSION_FIELD_INPUT,
                onValueChange = model.fields::setExpression
            )
            NoteTypeInput(
                label = model.readingLabel,
                value = model.fields.reading,
                testTag = SettingsNoteTypeTestTags.READING_FIELD_INPUT,
                onValueChange = model.fields::setReading
            )
            NoteTypeInput(
                label = model.meaningLabel,
                value = model.fields.meaning,
                testTag = SettingsNoteTypeTestTags.MEANING_FIELD_INPUT,
                onValueChange = model.fields::setMeaning
            )
            NoteTypeInput(
                label = model.sentenceLabel,
                value = model.fields.sentence,
                testTag = SettingsNoteTypeTestTags.SENTENCE_FIELD_INPUT,
                onValueChange = model.fields::setSentence
            )
            NoteTypeInput(
                label = model.frequencyLabel,
                value = model.fields.frequency,
                testTag = SettingsNoteTypeTestTags.FREQUENCY_FIELD_INPUT,
                onValueChange = model.fields::setFrequency
            )
            NoteTypeInput(
                label = model.frequencySortLabel,
                value = model.fields.frequencySort,
                testTag = SettingsNoteTypeTestTags.FREQUENCY_SORT_FIELD_INPUT,
                onValueChange = model.fields::setFrequencySort
            )
            NoteTypeOutlinedButton(model.chooseLabel) { model.onChoose.run() }
            NoteTypeOutlinedButton(model.kikuLabel) { model.onUseKiku.run() }
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = NoteTypeButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NoteTypePinkDark,
                    contentColor = NoteTypeWhite
                )
            ) {
                Text(
                    text = model.saveLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NoteTypeInput(
    label: String,
    value: String,
    testTag: String,
    textSizeSp: Int = 18,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        color = NoteTypeInk,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = label },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = NoteTypeInk,
            fontSize = textSizeSp.sp
        )
    )
}

@Composable
private fun NoteTypeOutlinedButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = NoteTypeButtonShape,
        border = BorderStroke(1.dp, NoteTypeButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = NoteTypeWhite,
            contentColor = NoteTypeInk
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
