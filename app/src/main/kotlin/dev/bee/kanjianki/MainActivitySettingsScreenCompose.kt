@file:JvmName("MainActivitySettingsScreenCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

data class SettingsScreenModel(
    val homeLabel: String,
    val onHome: Runnable,
    val hero: SettingsAutomationHeroModel,
    val categories: List<SettingsCategorySectionModel>,
)

data class SettingsCategorySectionModel(
    val title: String,
    val summary: String,
    val iconRes: Int,
    val expanded: Boolean,
    val panelCount: String,
    val contentDescription: String,
    val onToggle: Runnable,
    val panels: List<View>,
)

internal fun settingsScreenView(activity: MainActivitySettings, model: SettingsScreenModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                SettingsScreen(model)
            }
        }
    }
}

@Composable
fun SettingsScreen(model: SettingsScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(bottom = 10.dp)) {
            HomeFullWidthHomeButton(
                label = model.homeLabel,
                onClick = { model.onHome.run() }
            )
        }
        SettingsAutomationHero(model.hero)
        Spacer(modifier = Modifier.height(10.dp))
        model.categories.forEach { category ->
            SettingsCategorySection(category)
        }
    }
}

@Composable
fun SettingsCategorySection(model: SettingsCategorySectionModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp, bottom = 9.dp)
    ) {
        SettingsCategoryHeader(
            title = model.title,
            summary = model.summary,
            iconRes = model.iconRes,
            iconTint = ComposeColor(MainActivityUiSupport.STUDY_PINK_DARK),
            borderColor = ComposeColor(MainActivityUiSupport.STUDY_BORDER),
            expanded = model.expanded,
            countText = model.panelCount,
            titleColor = ComposeColor(MainActivityUiSupport.STUDY_PLUM),
            summaryColor = ComposeColor(MainActivityUiSupport.STUDY_MUTED),
            countColor = ComposeColor(MainActivityUiSupport.STUDY_PINK_DARK),
            contentDescription = model.contentDescription,
            onToggle = { model.onToggle.run() }
        )
        if (model.expanded) {
            model.panels.forEach { panel ->
                key(panel) {
                    AndroidPanel(panel)
                }
            }
        }
    }
}

@Composable
private fun AndroidPanel(panel: View) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            (panel.parent as? ViewGroup)?.removeView(panel)
            panel
        }
    )
}

internal fun settingsCategorySectionModel(
    title: String,
    summary: String,
    iconRes: Int,
    expanded: Boolean,
    onToggle: Runnable,
    panels: List<View>,
): SettingsCategorySectionModel {
    return SettingsCategorySectionModel(
        title = title,
        summary = summary,
        iconRes = iconRes,
        expanded = expanded,
        panelCount = SettingsTextCopy.settingsCategoryPanelCount(panels.size),
        contentDescription = SettingsTextCopy.categoryToggleDescription(expanded, title),
        onToggle = onToggle,
        panels = panels,
    )
}

internal fun settingsScreenModel(
    hero: SettingsAutomationHeroModel,
    categories: List<SettingsCategorySectionModel>,
    onHome: Runnable,
): SettingsScreenModel {
    return SettingsScreenModel(
        homeLabel = HomeTextCopy.homeLabel(),
        onHome = onHome,
        hero = hero,
        categories = categories,
    )
}
