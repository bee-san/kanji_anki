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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NoteTypeInk = KaniUiTokens.Ink
private val NoteTypeMuted = KaniUiTokens.Muted
private val NoteTypeTeal = KaniUiTokens.Teal
private val NoteTypePlum = Color(0xFF6E2B73)
private val NoteTypePanelBorder = KaniUiTokens.PanelBorder
private val NoteTypePanelFill = KaniUiTokens.PanelFill
private val NoteTypePanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsNoteTypePanel(model: SettingsNoteTypePanelModel) {
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
            KaniPrimaryButton(label = model.saveLabel, minHeightDp = 48) { model.onSave.run() }
            NoteTypeOutlinedButton(model.chooseLabel, minHeightDp = 44) { model.onChoose.run() }
            NoteTypeOutlinedButton(model.kikuLabel, minHeightDp = 44) { model.onUseKiku.run() }
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
