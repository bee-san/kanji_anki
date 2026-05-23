@file:JvmName("MainActivitySettingsAnkiSourceNoteTypeCompose")

package dev.bee.kanjianki

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
