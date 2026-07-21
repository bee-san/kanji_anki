package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal fun homeFirstRunOfflineNoticeTestTag(): String = "home-first-run-offline-notice"

@Composable
fun HomeScreen(model: HomeScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeHeader(title = model.title, subtitle = model.subtitle)
        Spacer(modifier = Modifier.height(14.dp))
        HomePrimaryHomeCta(model)
        model.firstRunOfflineNotice?.let { notice ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag(homeFirstRunOfflineNoticeTestTag()),
                color = KaniTheme.colors.greyText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HomeSecondaryPanels(model)
    }
}

@Composable
private fun HomeSecondaryPanels(model: HomeScreenModel) {
    Spacer(modifier = Modifier.height(12.dp))
    model.repairedHandoff?.let {
        HomeRepairedHandoffCard(it)
        Spacer(modifier = Modifier.height(12.dp))
    }
    model.updateCheckFailedLine?.let { line ->
        HomeUpdateCheckFailedBanner(line = line, onRetry = model.onRetryUpdateCheck)
        Spacer(modifier = Modifier.height(12.dp))
    }
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
        // Empty-state copy travels with an empty queue in every production model;
        // render nothing (instead of crashing) if a caller ever omits it.
        val emptyTitle = model.emptyTitle
        val emptyBody = model.emptyBody
        if (emptyTitle != null && emptyBody != null) {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                HomeEmptyState(
                    title = emptyTitle,
                    body = emptyBody,
                    style = HomeEmptyStateStyle.Panel
                )
            }
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
    // Sync and study CTAs share the same footprint so the primary action does not
    // jump between shapes when the sync state flips.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HomeStudyCtaMinHeight)
    ) {
        if (model.showSyncCta) {
            HomePrimaryCta(
                label = model.syncLabel,
                color = MainActivityUiSupport.CORAL,
                onClick = model.onSync
            )
        } else {
            HomeStudyCta(
                title = model.studyLabel,
                onClick = model.onStudy,
                remainingCount = model.studyRemainingCount,
            )
        }
    }
}
