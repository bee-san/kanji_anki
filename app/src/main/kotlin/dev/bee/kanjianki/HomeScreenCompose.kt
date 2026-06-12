package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(model: HomeScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeHeader(title = model.title, subtitle = model.subtitle)
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
        Spacer(modifier = Modifier.height(14.dp))
        HomePrimaryHomeCta(model)
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
                .heightIn(min = 94.dp)
        ) {
            HomeStudyCta(
                title = model.studyLabel,
                onClick = model.onStudy
            )
        }
    }
}
