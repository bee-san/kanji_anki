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
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.StudyStatsStore

internal fun homeMetricCardTestTag(label: String): String = "home-metric-card-$label"

internal fun homeMetricModels(
    home: MainActivityHome,
    sync: LocalStoreBase.SyncStatus?,
    provider: AnkiDroidGateway.ProviderStatus,
    streak: StudyStatsStore.StudyStreak?,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?
): List<HomeMetricModel> {
    return listOf(
        HomeMetricModel(
            R.drawable.ic_sync_24,
            MainActivityUiSupport.TEAL,
            HomeTextCopy.syncMetricLabel(),
            HomeTextCopy.homeSyncValue(sync?.finishedAt),
            HomeTextCopy.syncMetricStatus(provider.canSync && sync != null && sync.status == "success"),
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
    val shape = RoundedCornerShape(8.dp)
    val accentColor = kaniColor(model.accent)
    val borderColor = if (KaniTheme.colors.isDark) {
        accentColor.copy(alpha = 0.35f)
    } else {
        androidColor(HomeMetricCardBorder.softened(model.accent))
    }
    val labelStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    val compactBody = remember(model.body) { model.body?.let { StudyTextCopy.compact(it, 22) } }
    val contentDescriptionText = remember(model.label, model.value, compactBody) {
        listOfNotNull(
            HomeTextCopy.homeMetricCardDescription(),
            model.label,
            model.value,
            compactBody,
        ).joinToString(", ")
    }
    val cardModifier = modifier
        .testTag(homeMetricCardTestTag(model.label))
        .semantics {
            contentDescription = contentDescriptionText
        }
        .then(
            model.onClick?.let { action ->
                Modifier.clickable(
                    role = Role.Button,
                    onClick = {
                        withButtonTrace("Home metric ${model.label}") {
                            action()
                        }
                    }
                )
            } ?: Modifier
        )
    Box(
        modifier = cardModifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(KaniTheme.colors.surface)
            .border(1.dp, borderColor, shape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                painter = painterResource(id = model.iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(22.dp)
                    .padding(bottom = 5.dp)
            )
            Text(
                text = model.label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = labelStyle
            )
            Text(
                text = model.value,
                color = HomeMetricInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
                style = labelStyle
            )
            if (!compactBody.isNullOrEmpty()) {
                Text(
                    text = compactBody,
                    color = HomeMetricMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                    style = labelStyle
                )
            }
        }
    }
}

private val HomeMetricInk: Color @Composable get() = KaniTheme.colors.ink
private val HomeMetricMuted: Color @Composable get() = KaniTheme.colors.muted

private object HomeMetricCardBorder {
    fun softened(accent: Int): Int {
        return when (accent) {
            MainActivityBase.CORAL -> android.graphics.Color.rgb(255, 235, 243)
            MainActivityBase.TEAL -> android.graphics.Color.rgb(230, 250, 251)
            MainActivityBase.GOLD, android.graphics.Color.rgb(247, 159, 0) -> android.graphics.Color.rgb(255, 247, 220)
            MainActivityBase.BLUE, MainActivityBase.LILAC -> android.graphics.Color.rgb(242, 238, 255)
            else -> android.graphics.Color.rgb(248, 238, 245)
        }
    }
}

private fun androidColor(argb: Int): Color {
    return Color(argb.toLong() and 0xFFFFFFFFL)
}
