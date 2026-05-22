@file:JvmName("MainActivitySettingsAnkiSourceImportFiltersCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ImportFilterInk = Color(0xFF2D1635)
private val ImportFilterMuted = Color(0xFF6C5674)
private val ImportFilterTeal = Color(0xFF24756C)
private val ImportFilterPinkDark = Color(0xFFDA3A7A)
private val ImportFilterPanelBorder = Color(0xFFFFC7DE)
private val ImportFilterButtonBorder = Color(0xFFEEBDDA)
private val ImportFilterWhite = Color(0xFFFFFFFF)
private val ImportFilterPanelShape = RoundedCornerShape(24.dp)
private val ImportFilterButtonShape = RoundedCornerShape(12.dp)

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
            ImportFilterCheckbox(model.activeCardsLabel, model.state.activeCards) { model.state.activeCards = it }
            ImportFilterCheckbox(model.suspendedCardsLabel, model.state.suspendedCards) { model.state.suspendedCards = it }
            ImportFilterCheckbox(model.taggedCardsLabel, model.state.taggedCards) { model.state.taggedCards = it }
            ImportFilterCheckbox(model.weakCardsLabel, model.state.weakCards) { model.state.weakCards = it }
            ImportFilterCheckbox(model.browserQueryCardsLabel, model.state.browserQueryCards) { model.state.browserQueryCards = it }
            ImportFilterTextField(
                label = model.browserQueryLabel,
                value = model.state.browserQuery,
                hint = model.browserQueryHint,
                testTag = SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT,
                onValueChange = { model.state.browserQuery = it }
            )
            ImportFilterTextField(
                label = model.tagsLabel,
                value = model.state.tags,
                hint = model.tagsHint,
                testTag = SettingsImportFiltersTestTags.TAGS_INPUT,
                onValueChange = { model.state.tags = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ImportFilterTextField(
                    label = model.difficultyLabel,
                    value = model.state.difficulty,
                    testTag = SettingsImportFiltersTestTags.DIFFICULTY_INPUT,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                    onValueChange = { model.state.difficulty = it }
                )
                ImportFilterTextField(
                    label = model.lapsesLabel,
                    value = model.state.lapses,
                    testTag = SettingsImportFiltersTestTags.LAPSES_INPUT,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = { model.state.lapses = it }
                )
            }
            ImportFilterTextField(
                label = model.minMatchingLabel,
                value = model.state.minMatching,
                testTag = SettingsImportFiltersTestTags.MIN_MATCHING_INPUT,
                keyboardType = KeyboardType.Number,
                onValueChange = { model.state.minMatching = it }
            )
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
private fun ImportFilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = ImportFilterPinkDark)
        )
        Text(
            text = label,
            color = ImportFilterInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ImportFilterTextField(
    label: String,
    value: String,
    testTag: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ImportFilterInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics { contentDescription = label },
            placeholder = if (hint.isEmpty()) null else {
                { Text(text = hint, color = ImportFilterMuted, fontSize = 15.sp) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = ImportFilterInk,
                fontSize = 18.sp
            )
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
