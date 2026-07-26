package dev.bee.kanjianki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.KaniThemeChoice

internal val NoOpRouteScrollY: (Int) -> Unit = {}

internal fun shouldTrackRouteScroll(onScrollY: (Int) -> Unit): Boolean = onScrollY !== NoOpRouteScrollY

internal enum class MainActivityRouteScrollMode {
    SHELL,
    CONTENT,
}

@Composable
internal fun MainActivityComposeRoute(
    model: MainActivityShellModel = MainActivityShellModel(),
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = NoOpRouteScrollY,
    navActions: KaniNavActions? = null,
    themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
    isSystemDarkTheme: Boolean = false,
    contentKey: Any? = null,
    scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
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
            scrollMode = scrollMode,
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
    scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
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
            scrollMode = scrollMode,
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
    scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
    content: @Composable () -> Unit,
) {
    MainActivityScrollableRouteColumn(
        model = model,
        initialScrollY = initialScrollY,
        onScrollY = onScrollY,
        navActions = navActions,
        imeVisible = imeVisible,
        contentKey = contentKey,
        scrollMode = scrollMode,
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
    scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
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
        scrollMode = scrollMode,
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
    scrollMode: MainActivityRouteScrollMode,
    content: @Composable () -> Unit,
    footerContent: @Composable () -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val saveableContentKey = saveableRouteContentKey(contentKey)
    val saveableStatePruner = remember { RouteSaveableStatePruner() }
    LaunchedEffect(saveableContentKey) {
        saveableStatePruner.activate(saveableContentKey, saveableStateHolder::removeState)
    }
    val backgroundColor = if (MainActivityBase.NAV_STUDY == model.selectedRoute) {
        KaniTheme.colors.studyBg
    } else {
        KaniTheme.colors.bg
    }
    key(model.selectedRoute, initialScrollY, scrollMode) {
        val scrollState = rememberScrollState(initial = initialScrollY)
        if (scrollMode == MainActivityRouteScrollMode.SHELL) {
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
        }
        val activeStudySession = model.selectedRoute == MainActivityBase.NAV_STUDY && model.studySessionActive
        val showNav = navActions != null && !imeVisible && !model.studyCardKeyboardResident && !activeStudySession
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag(model.routeTestTag)
                .background(backgroundColor)
                .systemBarsPadding()
                .imePadding(),
        ) {
            val isExpanded = maxWidth >= 840.dp
            if (isExpanded && showNav) {
                Row(modifier = Modifier.fillMaxSize()) {
                    KaniNavigationRail(
                        selectedRoute = model.selectedRoute,
                        actions = navActions,
                        studyBadgeCount = model.studyBadgeCount,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .weight(1f)
                                .then(
                                    if (scrollMode == MainActivityRouteScrollMode.SHELL) {
                                        Modifier.verticalScroll(scrollState)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            key(contentKey) {
                                saveableStateHolder.SaveableStateProvider(saveableContentKey) {
                                    content()
                                }
                            }
                        }
                        key(contentKey) { footerContent() }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Box(
                        modifier = Modifier
                            .let { if (isExpanded) it.widthIn(max = 640.dp).align(Alignment.CenterHorizontally) else it.fillMaxWidth() }
                            .weight(1f)
                            .then(
                                if (scrollMode == MainActivityRouteScrollMode.SHELL) {
                                    Modifier.verticalScroll(scrollState)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        key(contentKey) {
                            saveableStateHolder.SaveableStateProvider(saveableContentKey) {
                                content()
                            }
                        }
                    }
                    key(contentKey) { footerContent() }
                    if (showNav) {
                        KaniBottomNavBar(
                            selectedRoute = model.selectedRoute,
                            actions = navActions,
                            studyBadgeCount = model.studyBadgeCount,
                        )
                    }
                }
            }
        }
    }
}

internal class RouteSaveableStatePruner {
    private var activeKey: Any? = null

    fun activate(key: Any, removeState: (Any) -> Unit) {
        val departedKey = activeKey
        activeKey = key
        if (departedKey != null && departedKey != key) {
            removeState(departedKey)
        }
    }
}

private fun saveableRouteContentKey(contentKey: Any?): Any {
    return when (contentKey) {
        null -> "main-activity-route-default"
        is MainActivityRouteStateKey -> contentKey.saveableStateKey()
        else -> contentKey
    }
}
