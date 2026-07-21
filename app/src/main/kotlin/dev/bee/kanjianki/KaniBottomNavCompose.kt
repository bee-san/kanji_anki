package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

internal fun kaniNavBadgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()

private class KaniNavItem(
    val route: String,
    val label: String,
    val iconRes: Int,
    val onClick: () -> Unit,
    val badgeCount: Int? = null,
)

@Composable
internal fun KaniBottomNavBar(
    selectedRoute: String,
    actions: KaniNavActions,
    studyBadgeCount: Int? = null,
) {
    val items = listOf(
        KaniNavItem(MainActivityBase.NAV_HOME_ROUTE, NavigationCopy.homeLabel(), R.drawable.ic_home_24, actions.onHome),
        KaniNavItem(MainActivityBase.NAV_STUDY, NavigationCopy.studyLabel(), R.drawable.ic_study_24, actions.onStudy, badgeCount = studyBadgeCount),
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
        val useTwoRows = LocalDensity.current.fontScale >= 1.5f
        if (useTwoRows) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.chunked(2).forEach { rowItems ->
                    KaniBottomNavRow(items = rowItems, selectedRoute = selectedRoute)
                }
            }
        } else {
            KaniBottomNavRow(
                items = items,
                selectedRoute = selectedRoute,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun KaniBottomNavRow(
    items: List<KaniNavItem>,
    selectedRoute: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            KaniBottomNavItem(
                item = item,
                selected = item.route == selectedRoute || (
                    item.route == MainActivityBase.NAV_SETTINGS_ROUTE &&
                        MainActivityBase.isSettingsRoute(selectedRoute)
                    ),
                modifier = Modifier.weight(1f),
            )
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
                Box {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = contentColor,
                    )
                    val badgeCount = item.badgeCount
                    if (badgeCount != null && badgeCount > 0) {
                        KaniNavBadge(
                            label = kaniNavBadgeLabel(badgeCount),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 10.dp, y = (-4).dp),
                        )
                    }
                }
                Text(
                    text = item.label,
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = TextStyle(
                        letterSpacing = 0.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
        }
    }
}

@Composable
private fun KaniNavBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("kani-nav-badge"),
        shape = RoundedCornerShape(999.dp),
        color = KaniTheme.colors.coral,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            color = KaniTheme.colors.onCoral,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}

@Composable
internal fun KaniNavigationRail(
    selectedRoute: String,
    actions: KaniNavActions,
    studyBadgeCount: Int? = null,
) {
    val items = listOf(
        KaniNavItem(MainActivityBase.NAV_HOME_ROUTE, NavigationCopy.homeLabel(), R.drawable.ic_home_24, actions.onHome),
        KaniNavItem(MainActivityBase.NAV_STUDY, NavigationCopy.studyLabel(), R.drawable.ic_study_24, actions.onStudy, badgeCount = studyBadgeCount),
        KaniNavItem(MainActivityBase.NAV_STATS_ROUTE, NavigationCopy.statsLabel(), R.drawable.ic_stats_24, actions.onStats),
        KaniNavItem(MainActivityBase.NAV_SETTINGS_ROUTE, NavigationCopy.settingsLabel(), R.drawable.ic_settings_24, actions.onSettings),
    )
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = 8.dp)
            .testTag("kani-navigation-rail"),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item ->
                val isSelected = item.route == selectedRoute || (
                    item.route == MainActivityBase.NAV_SETTINGS_ROUTE && MainActivityBase.isSettingsRoute(selectedRoute)
                )
                KaniRailItem(item = item, selected = isSelected)
            }
        }
    }
}

@Composable
private fun KaniRailItem(
    item: KaniNavItem,
    selected: Boolean,
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
        modifier = Modifier
            .heightIn(min = 56.dp)
            .testTag(kaniNavItemTestTag(item.route) + "-rail")
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = NavigationCopy.navItemContentDescription(item.label, selected)
            },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.pill else colors.surface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = contentColor,
                )
                val badgeCount = item.badgeCount
                if (badgeCount != null && badgeCount > 0) {
                    KaniNavBadge(
                        label = kaniNavBadgeLabel(badgeCount),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-4).dp),
                    )
                }
            }
            Text(
                text = item.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}
