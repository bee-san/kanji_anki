package dev.bee.kanjianki.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay

internal fun openFoundationWindow(
    dataRoot: Path,
    smokeTest: Boolean,
): DesktopWindowResult {
    val smokeSentinel = dataRoot.resolve("smoke-rendered")
    application(exitProcessOnExit = false) {
        var showWindow by remember { mutableStateOf(true) }
        if (showWindow) {
            Window(
                onCloseRequest = {
                    showWindow = false
                },
                title = FOUNDATION_TITLE,
                state = rememberWindowState(width = 760.dp, height = 480.dp),
            ) {
                KaniDesktopFoundation()
                if (smokeTest) {
                    LaunchedEffect(dataRoot) {
                        repeat(SMOKE_RENDER_FRAME_COUNT) {
                            withFrameNanos { }
                        }
                        delay(SMOKE_SETTLE_MILLIS)
                        Files.writeString(
                            smokeSentinel,
                            "$FOUNDATION_TITLE\n",
                        )
                        showWindow = false
                    }
                }
            }
        } else {
            LaunchedEffect(Unit) {
                exitApplication()
            }
        }
    }
    return if (
        smokeTest &&
        Files.isRegularFile(smokeSentinel) &&
        Files.readString(smokeSentinel) == "$FOUNDATION_TITLE\n"
    ) {
        DesktopWindowResult.SMOKE_RENDERED
    } else {
        DesktopWindowResult.CLOSED
    }
}

private const val SMOKE_RENDER_FRAME_COUNT = 3
private const val SMOKE_SETTLE_MILLIS = 250L

@Composable
internal fun KaniDesktopFoundation() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Kani",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = FOUNDATION_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
