@file:JvmName("MainActivitySettingsScreenCompose")

package dev.bee.kanjianki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal const val SETTINGS_SCREEN_BOTTOM_SPACER_TAG = "settings-screen-bottom-spacer"
private val SettingsScreenBottomSpacerHeight = 96.dp

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
        model.cards.forEach { card ->
            Box(
                modifier = Modifier.padding(top = 7.dp, bottom = 9.dp)
            ) {
                SettingsHubCard(
                    title = card.title,
                    summary = card.summary,
                    iconRes = card.iconRes,
                    iconTint = KaniTheme.colors.primary,
                    borderColor = KaniTheme.colors.border,
                    countText = card.panelCount,
                    titleColor = KaniTheme.colors.plum,
                    summaryColor = KaniTheme.colors.muted,
                    countColor = KaniTheme.colors.primary,
                    contentDescription = card.contentDescription,
                    testTagKey = card.routeKey,
                    onOpen = { card.onOpen.run() },
                )
            }
        }
        Spacer(
            modifier = Modifier
                .height(SettingsScreenBottomSpacerHeight)
                .testTag(SETTINGS_SCREEN_BOTTOM_SPACER_TAG)
        )
    }
}

@Composable
fun SettingsSubmenuScreen(model: SettingsSubmenuScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(bottom = 10.dp)) {
            HomeFullWidthHomeButton(
                label = model.homeLabel,
                onClick = { model.onHome.run() }
            )
        }
        Box(modifier = Modifier.padding(bottom = 10.dp)) {
            SettingsUpdateBackButton(
                onClick = { model.onBack.run() },
                label = model.backLabel,
            )
        }
        Text(
            text = model.title,
            color = SettingsUpdateInk,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (model.body.isNotBlank()) {
            Text(
                text = model.body,
                color = SettingsUpdateMuted,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        model.panels.forEach { panel ->
            Box(modifier = Modifier.testTag(settingsPanelTestTag(panel))) {
                SettingsPanel(panel)
            }
        }
        Spacer(
            modifier = Modifier
                .height(SettingsScreenBottomSpacerHeight)
                .testTag(SETTINGS_SCREEN_BOTTOM_SPACER_TAG)
        )
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
            iconTint = KaniTheme.colors.primary,
            borderColor = KaniTheme.colors.border,
            expanded = model.expanded,
            countText = model.panelCount,
            titleColor = KaniTheme.colors.plum,
            summaryColor = KaniTheme.colors.muted,
            countColor = KaniTheme.colors.primary,
            contentDescription = model.contentDescription,
            testTagKey = model.sectionKey,
            onToggle = { model.onToggle.run() }
        )
        AnimatedVisibility(
            visible = model.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                model.panels.forEach { panel ->
                    Box(modifier = Modifier.testTag(settingsPanelTestTag(panel))) {
                        SettingsPanel(panel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(panel: SettingsPanelModel) {
    when (panel) {
        is SettingsNoteTypePanelModel -> SettingsNoteTypePanel(panel)
        is SettingsImportFiltersPanelModel -> SettingsImportFiltersPanel(panel)
        is SettingsFrequencyRangePanelModel -> SettingsFrequencyRangePanel(panel)
        is SettingsNewCardSortPanelModel -> SettingsNewCardSortPanel(panel)
        is SettingsDeckLimitsPanelModel -> SettingsDeckLimitsPanel(panel)
        is SettingsWorkloadPanelModel -> SettingsWorkloadPanel(panel)
        is SettingsRetentionPanelModel -> SettingsRetentionPanel(panel)
        is SettingsLearningStepsPanelModel -> SettingsLearningStepsPanel(panel)
        is SettingsStudyAheadPanelModel -> SettingsStudyAheadPanel(panel)
        is SettingsStudyLadderPanelModel -> SettingsStudyLadderPanel(panel)
        is SettingsLadderThresholdPanelModel -> SettingsLadderThresholdPanel(panel)
        is SettingsReminderPanelModel -> SettingsReminderPanel(panel)
        is SettingsAutoSyncPanelModel -> SettingsAutoSyncPanel(panel)
        is SettingsUpdateOverviewPanelModel -> SettingsUpdateOverviewPanel(panel)
        is SettingsThemePanelModel -> SettingsThemePanel(panel)
        is SettingsReferenceDataLinkModel -> ReferenceDataLinkPanel(panel)
    }
}

internal fun settingsCategorySectionModel(
    sectionKey: String,
    title: String,
    summary: String,
    iconRes: Int,
    expanded: Boolean,
    onToggle: Runnable,
    panels: List<SettingsPanelModel>,
    panelCount: Int = panels.size,
): SettingsCategorySectionModel {
    return SettingsCategorySectionModel(
        sectionKey = sectionKey,
        title = title,
        summary = summary,
        iconRes = iconRes,
        expanded = expanded,
        panelCount = SettingsTextCopy.settingsCategoryPanelCount(panelCount),
        contentDescription = SettingsTextCopy.categoryToggleDescription(expanded, title),
        onToggle = onToggle,
        panels = panels,
    )
}

internal fun settingsCategoryHeaderTestTag(sectionKey: String): String {
    return "settings-category-$sectionKey"
}

internal fun settingsPanelTestTag(panel: SettingsPanelModel): String {
    return when (panel) {
        is SettingsNoteTypePanelModel -> "settings-panel-note-type"
        is SettingsImportFiltersPanelModel -> "settings-panel-import-filters"
        is SettingsFrequencyRangePanelModel -> "settings-panel-frequency-range"
        is SettingsNewCardSortPanelModel -> "settings-panel-new-card-sort"
        is SettingsDeckLimitsPanelModel -> "settings-panel-deck-limits"
        is SettingsWorkloadPanelModel -> "settings-panel-workload"
        is SettingsRetentionPanelModel -> "settings-panel-retention"
        is SettingsLearningStepsPanelModel -> "settings-panel-learning-steps"
        is SettingsStudyAheadPanelModel -> "settings-panel-study-ahead"
        is SettingsStudyLadderPanelModel -> "settings-panel-study-ladder"
        is SettingsLadderThresholdPanelModel -> "settings-panel-ladder-thresholds"
        is SettingsReminderPanelModel -> "settings-panel-reminder"
        is SettingsAutoSyncPanelModel -> "settings-panel-auto-sync"
        is SettingsUpdateOverviewPanelModel -> "settings-panel-app-updates"
        is SettingsThemePanelModel -> "settings-panel-theme"
        is SettingsReferenceDataLinkModel -> "settings-panel-reference-data"
    }
}

internal fun settingsScreenModel(
    hero: SettingsAutomationHeroModel,
    cards: List<SettingsHubCardModel>,
    onHome: Runnable,
): SettingsScreenModel {
    return SettingsScreenModel(
        homeLabel = HomeTextCopy.homeLabel(),
        onHome = onHome,
        hero = hero,
        cards = cards,
    )
}
