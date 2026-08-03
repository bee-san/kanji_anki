package dev.bee.kanjianki.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsRoot
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val SETTINGS_SCREEN_TEST_TAG: String = "kani-settings"
const val SETTINGS_ROOT_TEST_TAG: String = "kani-settings-root"
const val SETTINGS_CONTROLS_TEST_TAG: String = "kani-settings-controls"
const val SETTINGS_PLACEHOLDER_TEST_TAG: String = "kani-settings-placeholder"

/** One root category card, tagged by its section route. */
fun settingsCategoryTestTag(route: String): String = "kani-settings-category-$route"

/** One control, tagged by its label. */
fun settingsControlTestTag(label: String): String = "kani-settings-control-$label"

/** A stepper's decrement/increment button, tagged by its control label and direction. */
fun settingsStepperButtonTestTag(label: String, up: Boolean): String =
    "kani-settings-stepper-${if (up) "up" else "down"}-$label"

/**
 * The Settings surface, from one [SettingsScreen].
 *
 * The root category menu at [SettingsScreen.root], a section's controls when shared,
 * or the honest not-yet-shared placeholder. Capability notices ride on the category
 * and info controls, so a platform limitation is stated where the user meets it
 * rather than hidden. Every toggle/choice/button dispatches; nothing is decided here.
 */
@Composable
fun SettingsScreenView(
    screen: SettingsScreen,
    copy: SettingsCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SETTINGS_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val root = screen.root
        if (root != null) {
            RootMenu(root, dispatch)
        }
        when (val content = screen.content) {
            SettingsSectionContent.Placeholder -> if (root == null) PlaceholderPanel(copy)
            is SettingsSectionContent.Controls -> ControlsPanel(content, dispatch)
        }
    }
}

@Composable
private fun RootMenu(root: SettingsRoot, dispatch: (KaniAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SETTINGS_ROOT_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = root.title,
            modifier = Modifier.semantics { heading() },
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
        for (category in root.categories) {
            CategoryCard(category, dispatch)
        }
    }
}

@Composable
private fun CategoryCard(category: SettingsCategory, dispatch: (KaniAction) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(settingsCategoryTestTag(category.section.route))
            .semantics { contentDescription = "${category.title}. ${category.summary}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        onClick = { dispatch(category.action) },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = category.title, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            Text(text = category.summary, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            for (notice in category.notices) {
                Text(text = notice, color = KaniTheme.colors.coral, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
        }
    }
}

@Composable
private fun ControlsPanel(content: SettingsSectionContent.Controls, dispatch: (KaniAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(SETTINGS_CONTROLS_TEST_TAG),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = content.title,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            for (control in content.controls) {
                ControlRow(control, dispatch)
            }
        }
    }
}

@Composable
private fun ControlRow(control: SettingsControl, dispatch: (KaniAction) -> Unit) {
    val tag = Modifier.testTag(settingsControlTestTag(control.label))
    when (control) {
        is SettingsControl.Toggle -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(tag)
                .toggleable(
                    value = control.checked,
                    enabled = control.enabled,
                    onValueChange = { dispatch(control.onChange(it)) },
                )
                .semantics { contentDescription = control.label },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = control.checked, onCheckedChange = null, enabled = control.enabled)
            Text(text = control.label, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
        }

        is SettingsControl.Choice -> Column(modifier = tag, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = control.label, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in control.options) {
                    val selected = option.id == control.selectedId
                    OutlinedButton(
                        onClick = { dispatch(option.action) },
                        shape = KaniUiTokens.ButtonShape,
                        border = BorderStroke(1.dp, if (selected) KaniTheme.colors.primary else KaniTheme.colors.borderSoft),
                    ) {
                        Text(
                            text = option.label,
                            color = if (selected) KaniTheme.colors.primary else KaniTheme.colors.ink,
                        )
                    }
                }
            }
        }

        is SettingsControl.Stepper -> Row(
            modifier = Modifier.fillMaxWidth().then(tag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = control.label, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { dispatch(control.onChange(control.decremented())) },
                    enabled = control.canDecrement,
                    shape = KaniUiTokens.ButtonShape,
                    modifier = Modifier.testTag(settingsStepperButtonTestTag(control.label, up = false)),
                ) {
                    Text(text = "−", color = KaniTheme.colors.ink)
                }
                Text(
                    text = if (control.unit.isBlank()) "${control.value}" else "${control.value} ${control.unit}",
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedButton(
                    onClick = { dispatch(control.onChange(control.incremented())) },
                    enabled = control.canIncrement,
                    shape = KaniUiTokens.ButtonShape,
                    modifier = Modifier.testTag(settingsStepperButtonTestTag(control.label, up = true)),
                ) {
                    Text(text = "+", color = KaniTheme.colors.ink)
                }
            }
        }

        is SettingsControl.ActionButton -> if (control.destructive) {
            OutlinedButton(
                onClick = { dispatch(control.action) },
                modifier = tag.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT),
                enabled = control.enabled,
                shape = KaniUiTokens.ButtonShape,
                border = BorderStroke(1.dp, KaniTheme.colors.coral),
            ) {
                Text(text = control.label, color = KaniTheme.colors.coral, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { dispatch(control.action) },
                modifier = tag.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT),
                enabled = control.enabled,
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = control.label, fontWeight = FontWeight.Bold)
            }
        }

        is SettingsControl.Info -> Row(
            modifier = Modifier.fillMaxWidth().then(tag),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = control.label, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            Text(text = control.value, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
        }
    }
}

@Composable
private fun PlaceholderPanel(copy: SettingsCopy) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SETTINGS_PLACEHOLDER_TEST_TAG)
            .semantics { contentDescription = "${copy.placeholder} ${copy.placeholderHint}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = copy.placeholder, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            Text(text = copy.placeholderHint, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

private val ACTION_MIN_HEIGHT = 54.dp
