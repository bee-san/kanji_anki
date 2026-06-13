package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.NavigationCopy

/** Navigation callbacks supplied by the activity for the bottom bar. */
internal class KaniNavActions(
    val onHome: () -> Unit,
    val onStudy: () -> Unit,
    val onStats: () -> Unit,
    val onSettings: () -> Unit,
)

internal fun kaniNavItemTestTag(route: String): String = "kani-nav-$route"

private class KaniNavItem(
    val route: String,
    val label: String,
    val iconRes: Int,
    val onClick: () -> Unit,
)

@Composable
internal fun KaniBottomNavBar(
    selectedRoute: String,
    actions: KaniNavActions,
) {
    val items = listOf(
        KaniNavItem(MainActivityBase.NAV_HOME_ROUTE, NavigationCopy.homeLabel(), R.drawable.ic_home_24, actions.onHome),
        KaniNavItem(MainActivityBase.NAV_STUDY, NavigationCopy.studyLabel(), R.drawable.ic_study_24, actions.onStudy),
        KaniNavItem(MainActivityBase.NAV_STATS_ROUTE, NavigationCopy.statsLabel(), R.drawable.ic_stats_24, actions.onStats),
        KaniNavItem(MainActivityBase.NAV_SETTINGS_ROUTE, NavigationCopy.settingsLabel(), R.drawable.ic_settings_24, actions.onSettings),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag("kani-bottom-nav"),
        shape = RoundedCornerShape(24.dp),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                KaniBottomNavItem(
                    item = item,
                    selected = item.route == selectedRoute,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KaniBottomNavItem(
    item: KaniNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KaniTheme.colors
    val contentColor = if (selected) colors.primary else colors.muted
    Surface(
        onClick = {
            if (!selected) {
                withUiTrace("kani.button.nav-${item.route}") {
                    item.onClick()
                }
            }
        },
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag(kaniNavItemTestTag(item.route))
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = NavigationCopy.navItemContentDescription(item.label, selected)
            },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.pill else colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = contentColor,
                )
                Text(
                    text = item.label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
        }
    }
}
