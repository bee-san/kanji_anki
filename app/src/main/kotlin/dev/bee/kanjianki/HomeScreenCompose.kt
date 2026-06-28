package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(model: HomeScreenModel) {
    var showSecondaryPanels by remember(model) { mutableStateOf(false) }

    LaunchedEffect(model) {
        withFrameNanos { }
        showSecondaryPanels = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeHeader(title = model.title, subtitle = model.subtitle)
        Spacer(modifier = Modifier.height(14.dp))
        HomePrimaryHomeCta(model)
        if (showSecondaryPanels) {
            HomeSecondaryPanels(model)
        }
    }
}

@Composable
private fun HomeSecondaryPanels(model: HomeScreenModel) {
    Spacer(modifier = Modifier.height(12.dp))
    HomeMetricRow(metrics = model.metrics)
    if (model.todayPlan.summary.isNotBlank() || model.todayPlan.details.isNotEmpty() || model.todayPlan.actionLabel != null) {
        Spacer(modifier = Modifier.height(12.dp))
        HomeTodayPlanCard(model.todayPlan)
    }
    if (model.deckOverviewRows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HomeDeckOverview(model.deckOverviewRows)
    }
    HomeActionGrid(actions = model.actions)
    Spacer(modifier = Modifier.height(16.dp))
    HomeSectionHeader(
        title = model.focusTitle,
        actionLabel = model.focusActionLabel,
        onAction = model.onFocusAction
    )
    if (model.previewCards.isEmpty()) {
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            HomeEmptyState(
                title = requireNotNull(model.emptyTitle),
                body = requireNotNull(model.emptyBody),
                style = HomeEmptyStateStyle.Panel
            )
        }
    } else {
        Column {
            model.previewCards.forEach { card ->
                Box(modifier = Modifier.padding(vertical = 7.dp)) {
                    HomeFocusQueueCard(card)
                }
            }
        }
    }
}

@Composable
private fun HomePrimaryHomeCta(model: HomeScreenModel) {
    if (model.showSyncCta) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
        ) {
            HomePrimaryCta(
                label = model.syncLabel,
                color = MainActivityUiSupport.CORAL,
                onClick = model.onSync
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HomeStudyCtaMinHeight)
        ) {
            HomeStudyCta(
                title = model.studyLabel,
                onClick = model.onStudy
            )
        }
    }
}
