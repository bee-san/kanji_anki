@file:JvmName("HomeMetricsCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.SyncStatus
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.StudyStatsStore

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
    sync: LocalStoreBase.SyncStatus?,
    provider: AnkiDroidGateway.ProviderStatus,
    streak: StudyStatsStore.StudyStreak?,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    dailyPlan: DailyStudyPlan? = null
): List<HomeMetricModel> {
    return listOf(
        HomeMetricModel(
            R.drawable.ic_sync_24,
            MainActivityUiSupport.TEAL,
            HomeTextCopy.syncMetricLabel(),
            HomeTextCopy.homeSyncValue(sync?.finishedAt),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        metrics.forEach { metric ->
            HomeMetricCard(
                model = metric,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun HomeMetricCard(
    model: HomeMetricModel,
    modifier: Modifier = Modifier
) {
    val accentColor = kaniColor(model.accent)
    val compactBody = remember(model.body) { model.body?.let { StudyTextCopy.compact(it, 22) } }
    KaniMetricCard(
        iconRes = model.iconRes,
        label = model.label,
        value = model.value,
        delta = compactBody,
        accent = accentColor,
        modifier = modifier.fillMaxWidth().heightIn(min = 118.dp).testTag(homeMetricCardTestTag(model.label)),
        onClick = model.onClick?.let { action -> { withButtonTrace("Home metric ${model.label}") { action() } } },
        contentDescriptionPrefix = HomeTextCopy.homeMetricCardDescription(),
        compactValue = true,
    )
}

private val HomeMetricInk: Color @Composable get() = KaniTheme.colors.ink
private val HomeMetricMuted: Color @Composable get() = KaniTheme.colors.muted
