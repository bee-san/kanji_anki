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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ImportFilterInk: Color @Composable get() = KaniUiTokens.Ink
private val ImportFilterMuted: Color @Composable get() = KaniUiTokens.Muted
private val ImportFilterTeal: Color @Composable get() = KaniUiTokens.Teal
private val ImportFilterPinkDark: Color @Composable get() = KaniUiTokens.Primary
private val ImportFilterPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val ImportFilterPanelFill: Color @Composable get() = KaniUiTokens.PanelFill
private val ImportFilterPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsImportFiltersPanel(model: SettingsImportFiltersPanelModel) {
    val state = rememberSaveable(saver = SettingsImportFiltersState.Saver) { model.state }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportFilterPanelShape,
        color = ImportFilterPanelFill,
        border = BorderStroke(1.dp, ImportFilterPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            ImportFilterCheckbox(model.activeCardsLabel, state.activeCards) { state.activeCards = it }
            ImportFilterCheckbox(model.suspendedCardsLabel, state.suspendedCards) { state.suspendedCards = it }
            ImportFilterCheckbox(model.taggedCardsLabel, state.taggedCards) { state.taggedCards = it }
            ImportFilterCheckbox(model.weakCardsLabel, state.weakCards) { state.weakCards = it }
            ImportFilterCheckbox(model.browserQueryCardsLabel, state.browserQueryCards) { state.browserQueryCards = it }
            if (model.tagRepairedCardsLabel.isNotBlank()) {
                ImportFilterCheckbox(model.tagRepairedCardsLabel, state.tagRepairedCards) {
                    state.tagRepairedCards = it
                }
            }
            ImportFilterTextField(
                label = model.browserQueryLabel,
                value = state.browserQuery,
                hint = model.browserQueryHint,
                helperText = model.browserQueryHelperText,
                testTag = SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT,
                onValueChange = { state.browserQuery = it }
            )
            ImportFilterTextField(
                label = model.tagsLabel,
                value = state.tags,
                hint = model.tagsHint,
                testTag = SettingsImportFiltersTestTags.TAGS_INPUT,
                onValueChange = { state.tags = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ImportFilterTextField(
                    label = model.difficultyLabel,
                    value = state.difficulty,
                    testTag = SettingsImportFiltersTestTags.DIFFICULTY_INPUT,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                    onValueChange = { state.difficulty = it }
                )
                ImportFilterTextField(
                    label = model.lapsesLabel,
                    value = state.lapses,
                    testTag = SettingsImportFiltersTestTags.LAPSES_INPUT,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = { state.lapses = it }
                )
            }
            Text(
                text = model.presetsTitle,
                color = ImportFilterInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (model.presets.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    model.presets.forEach { preset ->
                        KaniOutlinedButton(
                            label = preset.label,
                            minHeightDp = 48
                        ) { preset.onClick.run() }
                    }
                }
            }
            KaniPrimaryButton(label = model.saveLabel, minHeightDp = 48) { model.onSave.save(state) }
            ImportFilterTextField(
                label = model.minMatchingLabel,
                value = state.minMatching,
                testTag = SettingsImportFiltersTestTags.MIN_MATCHING_INPUT,
                keyboardType = KeyboardType.Number,
                onValueChange = { state.minMatching = it }
            )
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
            fontSize = 16.sp,
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
    helperText: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val showHintBelow = hint.isNotEmpty() && helperText.isNotEmpty()
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
            placeholder = if (showHintBelow || hint.isEmpty()) null else {
                { Text(text = hint, color = ImportFilterMuted, fontSize = 15.sp) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = ImportFilterInk,
                fontSize = 18.sp
            )
        )
        if (showHintBelow) {
            Text(
                text = hint,
                color = ImportFilterMuted,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (helperText.isNotEmpty()) {
            Text(
                text = helperText,
                color = ImportFilterMuted,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (showHintBelow) 2.dp else 4.dp)
            )
        }
    }
}
