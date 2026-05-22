package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainActivityShell(
    legacyRoot: View,
    model: MainActivityShellModel = MainActivityShellModel(),
) {
    MainActivityShellFrame(model) {
        MainActivityRouteHost(legacyRoot = legacyRoot, model = model)
    }
}

@Composable
fun MainActivityComposeRoute(
    model: MainActivityShellModel = MainActivityShellModel(),
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    MainActivityShellFrame(model) {
        MainActivityRouteContent(
            model = model,
            initialScrollY = initialScrollY,
            onScrollY = onScrollY,
            content = content
        )
    }
}

@Composable
fun MainActivityComposeRouteWithActionBar(
    model: MainActivityShellModel = MainActivityShellModel(),
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = {},
    content: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
) {
    MainActivityShellFrame(model) {
        MainActivityRouteContentWithActionBar(
            model = model,
            initialScrollY = initialScrollY,
            onScrollY = onScrollY,
            content = content,
            actionBar = actionBar,
        )
    }
}

@Composable
private fun MainActivityShellFrame(
    model: MainActivityShellModel,
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main-activity-shell")
                .semantics {
                    contentDescription = "Kani shell ${model.selectedRoute}"
                }
        ) {
            content()
        }
    }
}

@Composable
internal fun MainActivityRouteHost(
    legacyRoot: View,
    model: MainActivityShellModel,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(model.routeTestTag)
            .semantics {
                contentDescription = model.routeContentDescription
            }
    ) {
        key(legacyRoot) {
            AndroidView(
                factory = { legacyRoot },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun MainActivityRouteContent(
    model: MainActivityShellModel,
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState(initial = initialScrollY)
    LaunchedEffect(scrollState, onScrollY) {
        snapshotFlow { scrollState.value }.collect { onScrollY(it) }
    }
    val backgroundColor = if (MainActivityBase.NAV_STUDY == model.selectedRoute) {
        MainActivityUiSupport.STUDY_BG_SOFT
    } else {
        MainActivityUiSupport.BG
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(model.routeTestTag)
            .semantics {
                contentDescription = model.routeContentDescription
            }
            .background(Color(backgroundColor))
            .systemBarsPadding()
            .padding(18.dp)
            .verticalScroll(scrollState),
    ) {
        content()
    }
}

@Composable
internal fun MainActivityRouteContentWithActionBar(
    model: MainActivityShellModel,
    initialScrollY: Int = 0,
    onScrollY: (Int) -> Unit = {},
    content: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState(initial = initialScrollY)
    LaunchedEffect(scrollState, onScrollY) {
        snapshotFlow { scrollState.value }.collect { onScrollY(it) }
    }
    val backgroundColor = if (MainActivityBase.NAV_STUDY == model.selectedRoute) {
        MainActivityUiSupport.STUDY_BG_SOFT
    } else {
        MainActivityUiSupport.BG
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(model.routeTestTag)
            .semantics {
                contentDescription = model.routeContentDescription
            }
            .background(Color(backgroundColor))
            .systemBarsPadding()
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            content()
        }
        actionBar()
    }
}
