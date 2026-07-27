@file:JvmName("HomeMetricsCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SyncStatus
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.SyncStatusSnapshot

internal fun homeMetricCardTestTag(label: String): String = "home-metric-card-$label"

/**
 * Whether the home sync tile may claim "Up to date". A last sync that succeeded but
 * is stale enough that the daily plan asks for a fresh one ("sync needed before Kani
 * can judge progress") must not read as up to date, or the tile contradicts the
 * Today card sitting right below it.
 */
internal fun syncTileUpToDate(
    canSync: Boolean,
    lastSyncSucceeded: Boolean,
    dailyPlan: DailyStudyPlan?,
): Boolean {
    return canSync &&
        lastSyncSucceeded &&
        dailyPlan?.syncStatus != SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS
}

internal fun homeMetricModels(
    home: MainActivityHome,
    sync: SyncStatusSnapshot?,
    provider: AnkiDroidGateway.ProviderStatus,
    streak: StudyStreakSnapshot?,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    dailyPlan: DailyStudyPlan? = null
): List<HomeMetricModel> {
    return listOf(
        HomeMetricModel(
            R.drawable.ic_sync_24,
            MainActivityUiSupport.TEAL,
            HomeTextCopy.syncMetricLabel(),
            HomeTextCopy.homeSyncValue(sync?.finishedAtMillis),
            HomeTextCopy.syncMetricStatus(
                syncTileUpToDate(
                    canSync = provider.canSync,
                    lastSyncSucceeded = sync != null && sync.status == "success",
                    dailyPlan = dailyPlan,
                )
            ),
            home::confirmSync
        ),
        HomeMetricModel(
            R.drawable.ic_flame_24,
            home.streakAccent(streak),
            HomeTextCopy.streakMetricLabel(),
            HomeTextCopy.streakHeadline(streak?.currentDays ?: 0),
            HomeTextCopy.streakMetricBody(streak?.studiedToday == true, streak?.bestDays ?: 0),
            null
        ),
        HomeMetricModel(
            R.drawable.ic_target_24,
            MainActivityUiSupport.CORAL,
            HomeTextCopy.focusMetricLabel(),
            HomeTextCopy.focusHeadline(plan),
            null,
            null
        )
    )
}

@Composable
fun HomeMetricRow(metrics: List<HomeMetricModel>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale >= 1.3f || maxWidth < 300.dp
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                metrics.forEach { metric ->
                    HomeMetricCard(model = metric, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                metrics.forEach { metric ->
                    HomeMetricCard(
                        model = metric,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        compactLayout = true,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeMetricCard(
    model: HomeMetricModel,
    modifier: Modifier = Modifier,
    compactLayout: Boolean = false,
) {
    val accentColor = kaniColor(model.accent)
    KaniMetricCard(
        iconRes = model.iconRes,
        label = model.label,
        value = model.value,
        delta = model.body,
        accent = accentColor,
        modifier = modifier.fillMaxWidth().heightIn(min = 118.dp).testTag(homeMetricCardTestTag(model.label)),
        onClick = model.onClick?.let { action -> { withButtonTrace("Home metric ${model.label}") { action() } } },
        contentDescriptionPrefix = HomeTextCopy.homeMetricCardDescription(),
        compactValue = true,
        compactLayout = compactLayout,
    )
}
