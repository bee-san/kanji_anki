@file:JvmName("MainActivitySettingsAnkiSourceNoteTypeCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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

fun interface SettingsNoteTypeAction {
    fun run()
}

data class SettingsNoteTypeFieldModel(
    val label: String,
    val input: EditText,
)

data class SettingsNoteTypePanelModel(
    val title: String,
    val status: String,
    val body: String,
    val noteTypeInput: EditText,
    val requiredTitle: String,
    val requiredBody: String,
    val fields: List<SettingsNoteTypeFieldModel>,
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
            AndroidView(
                factory = { model.noteTypeInput },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
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
            model.fields.forEach { field ->
                NoteTypeField(field)
            }
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
private fun NoteTypeField(field: SettingsNoteTypeFieldModel) {
    Text(
        text = field.label,
        color = NoteTypeInk,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    AndroidView(
        factory = { field.input },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
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
