package dev.bee.kanjianki.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.feature.shell.generated.resources.Res
import dev.bee.kanjianki.feature.shell.generated.resources.ic_arrow_back_24
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens
import org.jetbrains.compose.resources.painterResource

const val SHELL_ROOT_TEST_TAG: String = "kani-shell"
const val SHELL_BACK_TEST_TAG: String = "kani-shell-back"
const val SHELL_CONTENT_TEST_TAG: String = "kani-shell-content"

/** The test tag for the shell's rendered route, mirroring the Android host's. */
fun shellRouteTestTag(destination: KaniDestination): String =
    "kani-route-${destination.route}"

/**
 * Where the user's "go back" gesture comes from on this host.
 *
 * Android has a system back gesture and its shell renders no back button, so
 * adding one would change the app's appearance on a host whose visual behavior
 * Goal 193 requires preserving. A desktop window has neither, which is why the
 * shell must be able to draw one — and why this is a host decision rather than a
 * width breakpoint.
 */
enum class ShellBackAffordanceMode {
    /** The host supplies back itself (Android's system gesture). */
    SYSTEM,

    /** The shell draws a back button when [ShellState.canGoBack]. */
    IN_SHELL,
}

/**
 * Kani's product shell, for both hosts.
 *
 * Everything platform-specific is a parameter: the window size comes from
 * [BoxWithConstraints] rather than a configuration object, [fontScale] and
 * [immersion] are supplied by the host, and the four effects the shell cannot
 * perform go through [effectHandler]. What is shared is the part users recognize
 * as the app — where navigation lives, how a tab is selected, and what a loading,
 * failed, or capability-limited screen looks like.
 *
 * [content] renders the destination in [ShellState.current]. The shell
 * deliberately does not know how: routing a destination to a feature composable is
 * the host composition root's job, and a shell holding that table would depend on
 * every feature module.
 */
@Composable
fun KaniShell(
    state: ShellState,
    resolver: UiTextResolver,
    effectHandler: ShellEffectHandler,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    immersion: ShellImmersion = ShellImmersion(),
    backAffordance: ShellBackAffordanceMode = ShellBackAffordanceMode.SYSTEM,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (KaniDestination) -> Unit,
) {
    val copy = rememberShellCopy()
    // Study gets its own background, as on Android. Reading it off the tab rather
    // than the route means every Study destination matches without a list to keep
    // in step.
    val background = if (state.current.tab == KaniTab.STUDY) {
        KaniTheme.colors.studyBg
    } else {
        KaniTheme.colors.bg
    }

    ShellEffectHost(
        queue = state.effects,
        snackbarHostState = snackbarHostState,
        resolver = resolver,
        handler = effectHandler,
        dispatch = dispatch,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .testTag(SHELL_ROOT_TEST_TAG)
            // Escape is desktop's back, handled here rather than per screen so no
            // route can forget it. The shell claims the event only when going
            // back is actually possible, so a text field or dialog that wants
            // Escape for its own dismissal still receives it.
            .onPreviewKeyEvent { event ->
                val isBack = event.type == KeyEventType.KeyUp && event.key == Key.Escape
                if (isBack && state.canGoBack) {
                    dispatch(KaniAction.Navigation.Back)
                    true
                } else {
                    false
                }
            },
    ) {
        val layout = resolveShellLayout(
            windowWidth = maxWidth,
            fontScale = fontScale,
            immersion = immersion,
        )
        when (layout.placement) {
            ShellNavigationPlacement.SIDE_RAIL -> Row(modifier = Modifier.fillMaxSize()) {
                ShellNavigationRail(
                    selectedTab = state.selectedTab,
                    studyBadgeCount = state.studyBadgeCount,
                    copy = copy,
                    onSelect = dispatch,
                )
                ShellBody(
                    state = state,
                    layout = layout,
                    copy = copy,
                    backAffordance = backAffordance,
                    snackbarHostState = snackbarHostState,
                    dispatch = dispatch,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    content = content,
                )
            }

            ShellNavigationPlacement.BOTTOM_BAR,
            ShellNavigationPlacement.HIDDEN,
            -> Column(modifier = Modifier.fillMaxSize()) {
                ShellBody(
                    state = state,
                    layout = layout,
                    copy = copy,
                    backAffordance = backAffordance,
                    snackbarHostState = snackbarHostState,
                    dispatch = dispatch,
                    modifier = Modifier.weight(1f),
                    content = content,
                )
                if (layout.placement == ShellNavigationPlacement.BOTTOM_BAR) {
                    ShellBottomNavigation(
                        selectedTab = state.selectedTab,
                        studyBadgeCount = state.studyBadgeCount,
                        copy = copy,
                        stackRows = layout.stackNavigationRows,
                        onSelect = dispatch,
                        modifier = Modifier.padding(horizontal = layout.contentPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellBody(
    state: ShellState,
    layout: ShellLayout,
    copy: ShellCopy,
    backAffordance: ShellBackAffordanceMode,
    snackbarHostState: SnackbarHostState,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (KaniDestination) -> Unit,
) {
    Column(
        modifier = modifier.padding(layout.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (backAffordance == ShellBackAffordanceMode.IN_SHELL && state.canGoBack) {
                ShellBackButton(copy = copy, dispatch = dispatch)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(SHELL_CONTENT_TEST_TAG),
            ) {
                // Tagged on its own node rather than alongside the content tag:
                // `testTag` is not additive, so a second call on the same
                // modifier chain would replace the first and the Android tests
                // that address routes by tag would stop matching.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(shellRouteTestTag(state.current)),
                ) {
                    content(state.current)
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun ShellBackButton(
    copy: ShellCopy,
    dispatch: (KaniAction) -> Unit,
) {
    val description = copy.back
    IconButton(
        onClick = { dispatch(KaniAction.Navigation.Back) },
        modifier = Modifier
            .size(KaniUiTokens.MinTouchTarget)
            .testTag(SHELL_BACK_TEST_TAG)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_back_24),
            contentDescription = null,
            tint = KaniTheme.colors.ink,
        )
    }
}
