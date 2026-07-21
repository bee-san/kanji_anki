@file:JvmName("MainActivitySettingsAnkiSourceNoteTypeCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NoteTypeInk: Color @Composable get() = KaniUiTokens.Ink
private val NoteTypeMuted: Color @Composable get() = KaniUiTokens.Muted
private val NoteTypeTeal: Color @Composable get() = KaniUiTokens.Teal
private val NoteTypePlum: Color @Composable get() = KaniTheme.colors.plum
private val NoteTypePanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val NoteTypePanelFill: Color @Composable get() = KaniUiTokens.PanelFill
private val NoteTypePanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsNoteTypePanel(model: SettingsNoteTypePanelModel) {
    val fields = rememberSaveable(saver = SettingsNoteTypeFieldState.Saver) { model.fields }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NoteTypePanelShape,
        color = NoteTypePanelFill,
        border = BorderStroke(1.dp, NoteTypePanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                value = fields.noteType,
                testTag = SettingsNoteTypeTestTags.NOTE_TYPE_INPUT,
                textSizeSp = 20,
                onValueChange = fields::setNoteType
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
                value = fields.expression,
                testTag = SettingsNoteTypeTestTags.EXPRESSION_FIELD_INPUT,
                onValueChange = fields::setExpression
            )
            KaniPrimaryButton(label = model.saveLabel, minHeightDp = 48) { model.onSave.run(fields) }
            NoteTypeOutlinedButton(model.chooseLabel, minHeightDp = 48) { model.onChoose.run(fields) }
            NoteTypeOutlinedButton(model.kikuLabel, minHeightDp = 48) { model.onUseKiku.run(fields) }
            NoteTypeInput(
                label = model.readingLabel,
                value = fields.reading,
                testTag = SettingsNoteTypeTestTags.READING_FIELD_INPUT,
                onValueChange = fields::setReading
            )
            NoteTypeInput(
                label = model.meaningLabel,
                value = fields.meaning,
                testTag = SettingsNoteTypeTestTags.MEANING_FIELD_INPUT,
                onValueChange = fields::setMeaning
            )
            NoteTypeInput(
                label = model.sentenceLabel,
                value = fields.sentence,
                testTag = SettingsNoteTypeTestTags.SENTENCE_FIELD_INPUT,
                onValueChange = fields::setSentence
            )
            NoteTypeInput(
                label = model.frequencyLabel,
                value = fields.frequency,
                testTag = SettingsNoteTypeTestTags.FREQUENCY_FIELD_INPUT,
                onValueChange = fields::setFrequency
            )
            NoteTypeInput(
                label = model.frequencySortLabel,
                value = fields.frequencySort,
                testTag = SettingsNoteTypeTestTags.FREQUENCY_SORT_FIELD_INPUT,
                onValueChange = fields::setFrequencySort
            )
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
private fun NoteTypeOutlinedButton(label: String, minHeightDp: Int = 50, onClick: () -> Unit) {
    KaniOutlinedButton(label = label, minHeightDp = minHeightDp, onClick = onClick)
}
