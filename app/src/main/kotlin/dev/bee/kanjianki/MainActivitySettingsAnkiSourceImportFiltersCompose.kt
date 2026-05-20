@file:JvmName("MainActivitySettingsAnkiSourceImportFiltersCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

private val ImportFilterInk = Color(0xFF2D1635)
private val ImportFilterMuted = Color(0xFF6C5674)
private val ImportFilterTeal = Color(0xFF24756C)
private val ImportFilterPinkDark = Color(0xFFDA3A7A)
private val ImportFilterPanelBorder = Color(0xFFFFC7DE)
private val ImportFilterButtonBorder = Color(0xFFEEBDDA)
private val ImportFilterWhite = Color(0xFFFFFFFF)
private val ImportFilterPanelShape = RoundedCornerShape(24.dp)
private val ImportFilterButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsImportFilterAction {
    fun run()
}

data class SettingsImportPresetButtonModel(
    val label: String,
    val onClick: SettingsImportFilterAction,
)

data class SettingsImportFilterFieldModel(
    val label: String,
    val input: EditText,
    val heightDp: Int,
)

data class SettingsImportFiltersPanelModel(
    val title: String,
    val summary: String,
    val body: String,
    val presetsTitle: String,
    val presets: List<SettingsImportPresetButtonModel>,
    val sourceChecks: List<CheckBox>,
    val browserQueryField: SettingsImportFilterFieldModel,
    val tagsField: SettingsImportFilterFieldModel,
    val difficultyField: SettingsImportFilterFieldModel,
    val lapsesField: SettingsImportFilterFieldModel,
    val minMatchingField: SettingsImportFilterFieldModel,
    val saveLabel: String,
    val onSave: SettingsImportFilterAction,
)

internal fun importFiltersSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsImportFiltersPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsImportFiltersPanel(model)
            }
        }
    }
}

@Composable
fun SettingsImportFiltersPanel(model: SettingsImportFiltersPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportFilterPanelShape,
        color = ImportFilterWhite,
        border = BorderStroke(1.dp, ImportFilterPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = ImportFilterInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.summary,
                color = ImportFilterTeal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = ImportFilterMuted,
                fontSize = 15.sp
            )
            Text(
                text = model.presetsTitle,
                color = ImportFilterInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            model.presets.forEach { preset ->
                ImportFilterOutlinedButton(preset.label) { preset.onClick.run() }
            }
            model.sourceChecks.forEach { check ->
                AndroidView(
                    factory = { check },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ImportFilterField(model.browserQueryField)
            ImportFilterField(model.tagsField)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ImportFilterField(model.difficultyField, Modifier.weight(1f))
                ImportFilterField(model.lapsesField, Modifier.weight(1f))
            }
            ImportFilterField(model.minMatchingField)
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = ImportFilterButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImportFilterPinkDark,
                    contentColor = ImportFilterWhite
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
private fun ImportFilterField(
    field: SettingsImportFilterFieldModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = field.label,
            color = ImportFilterInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        AndroidView(
            factory = { field.input },
            modifier = Modifier
                .fillMaxWidth()
                .height(field.heightDp.dp)
        )
    }
}

@Composable
private fun ImportFilterOutlinedButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = ImportFilterButtonShape,
        border = BorderStroke(1.dp, ImportFilterButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ImportFilterWhite,
            contentColor = ImportFilterInk
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
