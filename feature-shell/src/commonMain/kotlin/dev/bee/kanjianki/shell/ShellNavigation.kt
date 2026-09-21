package dev.bee.kanjianki.shell

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.feature.shell.generated.resources.Res
import dev.bee.kanjianki.feature.shell.generated.resources.ic_home_24
import dev.bee.kanjianki.feature.shell.generated.resources.ic_settings_24
import dev.bee.kanjianki.feature.shell.generated.resources.ic_stats_24
import dev.bee.kanjianki.feature.shell.generated.resources.ic_study_24
import dev.bee.kanjianki.ui.KaniTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The test tag for one tab, in either placement.
 *
 * The `kani-nav-<route>` shape is the Android host's, unchanged: the existing
 * instrumentation tests address tabs by it, and renaming them would break those
 * tests for no product gain. The rail keeps its `-rail` suffix for the same
 * reason.
 */
fun shellTabTestTag(tab: KaniTab): String = "kani-nav-${tab.route}"

fun shellRailTabTestTag(tab: KaniTab): String = "${shellTabTestTag(tab)}-rail"

const val SHELL_BOTTOM_NAV_TEST_TAG: String = "kani-bottom-nav"
const val SHELL_NAV_RAIL_TEST_TAG: String = "kani-navigation-rail"
const val SHELL_NAV_BADGE_TEST_TAG: String = "kani-nav-badge"

/** Caps the badge so a large count cannot widen the tab past its neighbors. */
fun shellBadgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()

private fun KaniTab.icon(): DrawableResource = when (this) {
    KaniTab.HOME -> Res.drawable.ic_home_24
    KaniTab.STUDY -> Res.drawable.ic_study_24
    KaniTab.STATS -> Res.drawable.ic_stats_24
    KaniTab.SETTINGS -> Res.drawable.ic_settings_24
}

@Composable
internal fun ShellBottomNavigation(
    selectedTab: KaniTab,
    studyBadgeCount: Int,
    copy: ShellCopy,
    stackRows: Boolean,
    onSelect: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag(SHELL_BOTTOM_NAV_TEST_TAG),
        shape = RoundedCornerShape(24.dp),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
        shadowElevation = 3.dp,
    ) {
        val tabs = KaniTab.entries
        if (stackRows) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.chunked(2).forEach { rowTabs ->
                    ShellBottomNavigationRow(
                        tabs = rowTabs,
                        selectedTab = selectedTab,
                        studyBadgeCount = studyBadgeCount,
                        copy = copy,
                        onSelect = onSelect,
                    )
                }
            }
        } else {
            ShellBottomNavigationRow(
                tabs = tabs,
                selectedTab = selectedTab,
                studyBadgeCount = studyBadgeCount,
                copy = copy,
                onSelect = onSelect,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ShellBottomNavigationRow(
    tabs: List<KaniTab>,
    selectedTab: KaniTab,
    studyBadgeCount: Int,
    copy: ShellCopy,
    onSelect: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            ShellTab(
                tab = tab,
                selected = tab == selectedTab,
                badgeCount = if (tab == KaniTab.STUDY) studyBadgeCount else 0,
                copy = copy,
                testTag = shellTabTestTag(tab),
                shape = RoundedCornerShape(18.dp),
                labelSizeSp = 12,
                fillLabelWidth = true,
                contentPadding = PaddingSpec(horizontal = 0.dp, vertical = 6.dp),
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ShellNavigationRail(
    selectedTab: KaniTab,
    studyBadgeCount: Int,
    copy: ShellCopy,
    onSelect: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 8.dp)
            .testTag(SHELL_NAV_RAIL_TEST_TAG),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KaniTab.entries.forEach { tab ->
                ShellTab(
                    tab = tab,
                    selected = tab == selectedTab,
                    badgeCount = if (tab == KaniTab.STUDY) studyBadgeCount else 0,
                    copy = copy,
                    testTag = shellRailTabTestTag(tab),
                    shape = RoundedCornerShape(14.dp),
                    labelSizeSp = 11,
                    fillLabelWidth = false,
                    contentPadding = PaddingSpec(horizontal = 12.dp, vertical = 8.dp),
                    onSelect = onSelect,
                )
            }
        }
    }
}

/** Inset for a tab's contents, which differs between the bar and the rail. */
private data class PaddingSpec(
    val horizontal: Dp,
    val vertical: Dp,
)

/**
 * One tab, shared by the bar and the rail.
 *
 * The Android host had two near-identical item composables, one per placement,
 * which is how the badge came to be positioned by the same three magic numbers in
 * two places. The differences that are real — tag, corner radius, label size,
 * padding — are parameters; everything else, including the 56dp minimum touch
 * target and the tab semantics, is shared.
 */
@Composable
private fun ShellTab(
    tab: KaniTab,
    selected: Boolean,
    badgeCount: Int,
    copy: ShellCopy,
    testTag: String,
    shape: RoundedCornerShape,
    labelSizeSp: Int,
    fillLabelWidth: Boolean,
    contentPadding: PaddingSpec,
    onSelect: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KaniTheme.colors
    val contentColor = if (selected) colors.primary else colors.muted
    val description = copy.tabDescription(tab, selected)
    Surface(
        // Re-selecting the current tab is suppressed here rather than in the
        // reducer's `selectTab`, which treats it as a no-op: the difference
        // matters for a nested route, where the reducer *should* return to the
        // tab root but a tap on the already-highlighted tab should not look
        // clickable at all.
        onClick = { if (!selected) onSelect(KaniAction.Navigation.SelectTab(tab)) },
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag(testTag)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = description
            },
        shape = shape,
        color = if (selected) colors.pill else colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(
                    horizontal = contentPadding.horizontal,
                    vertical = contentPadding.vertical,
                ),
            ) {
                Box {
                    Icon(
                        painter = painterResource(tab.icon()),
                        // The tab's own semantics already carry the label and
                        // selected state; describing the icon too makes a screen
                        // reader say it twice.
                        contentDescription = null,
                        tint = contentColor,
                    )
                    if (badgeCount > 0) {
                        ShellNavigationBadge(
                            label = shellBadgeLabel(badgeCount),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 10.dp, y = (-4).dp),
                        )
                    }
                }
                Text(
                    text = copy.tabLabel(tab),
                    modifier = if (fillLabelWidth) Modifier.fillMaxWidth() else Modifier,
                    color = contentColor,
                    fontSize = labelSizeSp.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShellNavigationBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag(SHELL_NAV_BADGE_TEST_TAG),
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
        )
    }
}
