package dev.bee.kanjianki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.theme.KaniThemeChoice

internal val NoOpRouteScrollY: (Int) -> Unit = {}

internal fun shouldTrackRouteScroll(onScrollY: (Int) -> Unit): Boolean = onScrollY !== NoOpRouteScrollY

@Composable
internal fun MainActivityComposeRoute(
    model: MainActivityShellModel = MainActivityShellModel(),
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = NoOpRouteScrollY,
    navActions: KaniNavActions? = null,
    themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
    isSystemDarkTheme: Boolean = false,
    contentKey: Any? = null,
    content: @Composable () -> Unit,
) {
    MainActivityShellFrame(
        model = model,
        themeChoice = themeChoice,
        isSystemDarkTheme = isSystemDarkTheme,
    ) {
        MainActivityRouteContent(
            model = model,
            initialScrollY = initialScrollY,
            onScrollY = onScrollY,
            navActions = navActions,
            contentKey = contentKey,
            content = content
        )
    }
}

@Composable
internal fun MainActivityComposeRouteWithActionBar(
    model: MainActivityShellModel = MainActivityShellModel(),
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = NoOpRouteScrollY,
    navActions: KaniNavActions? = null,
    themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
    isSystemDarkTheme: Boolean = false,
    contentKey: Any? = null,
    content: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
) {
    MainActivityShellFrame(
        model = model,
        themeChoice = themeChoice,
        isSystemDarkTheme = isSystemDarkTheme,
    ) {
        MainActivityRouteContentWithActionBar(
            model = model,
            initialScrollY = initialScrollY,
            onScrollY = onScrollY,
            navActions = navActions,
            contentKey = contentKey,
            content = content,
            actionBar = actionBar,
        )
    }
}

@Composable
private fun MainActivityShellFrame(
    model: MainActivityShellModel,
    themeChoice: KaniThemeChoice,
    isSystemDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    KaniTheme(choice = themeChoice, isSystemInDarkTheme = isSystemDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main-activity-shell")
                .semantics {
                    contentDescription = StudyTextCopy.shellContentDescription(model.selectedRoute)
                }
        ) {
            content()
        }
    }
}

@Composable
internal fun MainActivityRouteContent(
    model: MainActivityShellModel,
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = NoOpRouteScrollY,
    navActions: KaniNavActions? = null,
    imeVisible: Boolean = kaniImeVisible(),
    contentKey: Any? = null,
    content: @Composable () -> Unit,
) {
    MainActivityScrollableRouteColumn(
        model = model,
        initialScrollY = initialScrollY,
        onScrollY = onScrollY,
        navActions = navActions,
        imeVisible = imeVisible,
        contentKey = contentKey,
        content = content,
    )
}

@Composable
internal fun MainActivityRouteContentWithActionBar(
    model: MainActivityShellModel,
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = NoOpRouteScrollY,
    navActions: KaniNavActions? = null,
    imeVisible: Boolean = kaniImeVisible(),
    contentKey: Any? = null,
    content: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
) {
    MainActivityScrollableRouteColumn(
        model = model,
        initialScrollY = initialScrollY,
        onScrollY = onScrollY,
        navActions = navActions,
        imeVisible = imeVisible,
        contentKey = contentKey,
        content = content,
        footerContent = actionBar,
    )
}

@Composable
private fun MainActivityScrollableRouteColumn(
    model: MainActivityShellModel,
    initialScrollY: Int,
    onScrollY: (Int) -> Unit,
    navActions: KaniNavActions?,
    imeVisible: Boolean = kaniImeVisible(),
    contentKey: Any?,
    content: @Composable () -> Unit,
    footerContent: @Composable () -> Unit = {},
) {
    val backgroundColor = if (MainActivityBase.NAV_STUDY == model.selectedRoute) {
        KaniTheme.colors.studyBg
    } else {
        KaniTheme.colors.bg
    }
    key(model.selectedRoute, initialScrollY) {
        val scrollState = rememberScrollState(initial = initialScrollY)
        // Keep the scroll container/layout node mounted across study cards, but
        // restore the route's requested position when its body identity changes.
        // The previous setContent-per-card path implicitly created a fresh state.
        LaunchedEffect(contentKey, scrollState) {
            if (scrollState.value != initialScrollY) {
                scrollState.scrollTo(initialScrollY)
            }
        }
        if (shouldTrackRouteScroll(onScrollY)) {
            LaunchedEffect(scrollState, onScrollY) {
                snapshotFlow { scrollState.value }.collect { onScrollY(it) }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(model.routeTestTag)
                .semantics {
                    contentDescription = model.routeContentDescription
                }
                .background(backgroundColor)
                .systemBarsPadding()
                .imePadding()
                .padding(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                // Route-local remember state (typing input, reveal state, card
                // animations) must not leak into the next imperative render.
                key(contentKey) {
                    content()
                }
            }
            key(contentKey) {
                footerContent()
            }
            // The bottom nav is unusable while typing and would sit on top of the
            // keyboard, stealing ~90dp from the already-shrunken content viewport
            // (the kanji prompt was getting pushed off-screen). Hide it whenever
            // the IME is open anywhere, AND for the whole unrevealed-typing-card
            // state so its disappearance never coincides with the keyboard
            // animation — it is already absent from the card's first frame and
            // toggles only at card boundaries where the content changes anyway (KB1).
            val activeStudySession = model.selectedRoute == MainActivityBase.NAV_STUDY && model.studySessionActive
            if (navActions != null && !imeVisible && !model.studyCardKeyboardResident && !activeStudySession) {
                KaniBottomNavBar(
                    selectedRoute = model.selectedRoute,
                    actions = navActions,
                    studyBadgeCount = model.studyBadgeCount,
                )
            }
        }
    }
}
