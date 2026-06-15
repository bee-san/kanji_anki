@file:JvmName("MainActivitySettingsThemeCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.theme.KaniThemeChoice

private val ThemePanelShape = RoundedCornerShape(24.dp)
private val ThemeChoiceShape = RoundedCornerShape(20.dp)
private val ThemePreviewShape = RoundedCornerShape(14.dp)
private val ThemeSelectedBadgeShape = RoundedCornerShape(999.dp)
private val ThemeSelectedBadgeSize = androidx.compose.ui.unit.DpSize(96.dp, 28.dp)
private val ThemePreviewHeight = 26.dp

@Composable
internal fun SettingsThemePanel(model: SettingsThemePanelModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(settingsThemePanelTestTag()),
        shape = ThemePanelShape,
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = model.title,
                color = KaniTheme.colors.ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = model.body,
                color = KaniTheme.colors.muted,
                fontSize = 15.sp,
            )
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                model.choices.forEach { choice ->
                    ThemeChoiceCard(choice)
                }
            }
        }
    }
}

@Composable
private fun ThemeChoiceCard(choice: SettingsThemeChoiceModel) {
    val selected = choice.selected
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .testTag(settingsThemeChoiceTestTag(choice.choice))
            .selectable(
                selected = selected,
                onClick = { withButtonTrace("settings-theme-${choice.choice.storageKey}") { choice.onSelect.run() } },
                role = Role.RadioButton,
            )
            .semantics {
                contentDescription = choice.contentDescription
            },
        shape = ThemeChoiceShape,
        color = if (selected) KaniTheme.colors.pill else KaniTheme.colors.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) KaniTheme.colors.primary else KaniTheme.colors.borderSoft,
        ),
        shadowElevation = if (selected) 4.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = choice.title,
                        color = KaniTheme.colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = choice.subtitle,
                        color = KaniTheme.colors.muted,
                        fontSize = 13.sp,
                    )
                }
                if (selected) {
                    ThemeSelectedBadge(choice)
                } else {
                    Spacer(modifier = Modifier.size(ThemeSelectedBadgeSize.width, ThemeSelectedBadgeSize.height))
                }
            }
            ThemePreviewStrip(choice)
        }
    }
}

@Composable
private fun ThemeSelectedBadge(choice: SettingsThemeChoiceModel) {
    Surface(
        modifier = Modifier
            .size(ThemeSelectedBadgeSize.width, ThemeSelectedBadgeSize.height)
            .testTag(settingsThemeChoiceSelectedTestTag(choice.choice)),
        shape = ThemeSelectedBadgeShape,
        color = KaniTheme.colors.primary,
        contentColor = KaniTheme.colors.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = SettingsThemeCopy.selectedLabel(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ThemePreviewStrip(choice: SettingsThemeChoiceModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(ThemePreviewHeight)
            .testTag(settingsThemeChoicePreviewTestTag(choice.choice)),
        shape = ThemePreviewShape,
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            choice.swatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(swatch)),
                )
            }
        }
    }
}

internal fun settingsThemePanelTestTag(): String {
    return "settings-theme-panel"
}

internal fun settingsThemeChoiceTestTag(choice: KaniThemeChoice): String {
    return "settings-theme-choice-${choice.storageKey}"
}

internal fun settingsThemeChoicePreviewTestTag(choice: KaniThemeChoice): String {
    return "settings-theme-choice-preview-${choice.storageKey}"
}

internal fun settingsThemeChoiceSelectedTestTag(choice: KaniThemeChoice): String {
    return "settings-theme-choice-selected-${choice.storageKey}"
}
