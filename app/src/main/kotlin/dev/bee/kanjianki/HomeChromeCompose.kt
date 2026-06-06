@file:JvmName("MainActivityHomeChromeCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy

internal fun homeActionButtonTestTag(label: String): String = "home-action-button-$label"

internal fun homeSectionActionButtonTestTag(label: String): String = "home-section-action-button-$label"

internal fun homeFullWidthHomeButtonTestTag(label: String): String = "home-full-width-home-button-$label"

internal fun homeActionModels(home: MainActivityHome): List<HomeActionModel> {
    return buildList {
        add(HomeActionModel(HomeTextCopy.browseActionLabel(), R.drawable.ic_book_24) { home.renderBrowseKanji("") })
        add(HomeActionModel(HomeTextCopy.recentMistakesTitle(), R.drawable.ic_trending_24, home::renderRecentMistakes))
        add(HomeActionModel(HomeTextCopy.statsActionLabel(), R.drawable.ic_stats_24, home::renderStats))
        add(HomeActionModel(HomeTextCopy.gamesActionLabel(), R.drawable.ic_game_24, home::renderGames))
        add(HomeActionModel(MainActivityBase.NAV_SETTINGS, R.drawable.ic_settings_24, home::renderSettings))
    }
}

@Composable
fun HomeActionGrid(actions: List<HomeActionModel>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowActions.forEach { action ->
                    HomeActionButton(
                        action = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowActions.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun HomeActionButton(action: HomeActionModel, modifier: Modifier = Modifier) {
    KaniOutlinedButton(
        label = action.label,
        modifier = modifier.testTag(homeActionButtonTestTag(action.label)),
        minHeightDp = 58,
        textSizeSp = 15,
        onClick = action.onClick
    )
}

@Composable
fun HomeSectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = KaniUiTokens.Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onAction != null) {
                        Modifier.clickable(role = Role.Button) {
                            onAction.invoke()
                        }
                    } else {
                        Modifier
                    }
                )
        )
        if (actionLabel != null && onAction != null) {
            KaniOutlinedButton(
                label = actionLabel,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(104.dp)
                    .testTag(homeSectionActionButtonTestTag(actionLabel)),
                minHeightDp = 42,
                textSizeSp = 14,
                onClick = onAction
            )
        }
    }
}

@Composable
fun HomeFullWidthHomeButton(
    label: String,
    onClick: () -> Unit
) {
    KaniOutlinedButton(
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeFullWidthHomeButtonTestTag(label)),
        minHeightDp = 56,
        onClick = onClick
    )
}
