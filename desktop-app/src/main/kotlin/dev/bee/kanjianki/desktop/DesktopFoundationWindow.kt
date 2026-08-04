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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.bee.kanjianki.hostpresentation.DesktopMenuBar
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.desktop.DesktopDeviceSettingsStore
import dev.bee.kanjianki.platform.desktop.DesktopLogger
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.shell.DESKTOP_MINIMUM_WINDOW_HEIGHT
import dev.bee.kanjianki.shell.DESKTOP_MINIMUM_WINDOW_WIDTH
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.delay

/**
 * Opens Kani's desktop window over a live profile.
 *
 * This is the composition root: the one place where the profile, the AnkiConnect
 * provider, the platform adapters, the presentation reducers, and the shared shell
 * all meet. [DesktopStartup] brings the profile up and tears it down around the
 * window, so the window's lifetime *is* the container's lifetime — nothing here can
 * outlive the lock, and nothing can render before a staged restore has been
 * applied.
 *
 * Two host-level facts are settled here rather than deeper down:
 *
 *  - **The logger never writes to stderr.** [DesktopLogger]'s default sink is
 *    `System.err::println`, and the installed-image smoke gate requires empty
 *    stderr. A file sink inside the profile is both quieter and more useful than a
 *    console the user never sees.
 *  - **The window has a minimum size.** [DESKTOP_MINIMUM_WINDOW_WIDTH] and its
 *    companion are the sizes the shared shell's layout tests cover; below them the
 *    navigation the shell renders has not been shown to be usable, so the window
 *    refuses to go there rather than finding out in front of a user.
 */
internal fun openFoundationWindow(
    dataRoot: Path,
    smokeTest: Boolean,
): DesktopWindowResult {
    val location = DesktopStartup.resolveProfile(dataRoot = if (smokeTest) dataRoot else null)
    val logger = DesktopLogger(
        // Read lazily and per event, so turning the setting on in Settings takes
        // effect without a relaunch.
        debugEnabled = { debugLoggingEnabled(location.profileDir) },
        sink = fileSink(location.profileDir.resolve(LOG_FILE_NAME)),
    )

    val outcome = DesktopStartup.run(location = location, logger = logger) { container ->
        runWindow(container = container, dataRoot = dataRoot, smokeTest = smokeTest)
    }

    return when (outcome) {
        is DesktopStartup.Outcome.Ran -> outcome.value
        is DesktopStartup.Outcome.Blocked -> {
            // Shown in a window, not printed. A user who double-clicked an icon
            // sees no console, and every blocked reason is something they can act
            // on. The smoke gate never reaches this: its profile is a fresh
            // temporary directory that nothing else holds.
            showStartupBlocked(outcome)
            DesktopWindowResult.CLOSED
        }
    }
}

/**
 * Runs the real window for a live [container].
 *
 * The smoke path is the same window and the same shell, with a sentinel written
 * after the first frames settle. Rendering something *else* under `--smoke-test`
 * would make the installed-image gate prove only that Compose starts, which is not
 * what it is for: the gate exists to catch a packaged image whose profile, provider
 * wiring, or resources are broken, and all three are on this path.
 */
private fun runWindow(
    container: DesktopKaniContainer,
    dataRoot: Path,
    smokeTest: Boolean,
): DesktopWindowResult {
    val smokeSentinel = dataRoot.resolve("smoke-rendered")
    application(exitProcessOnExit = false) {
        var showWindow by remember { mutableStateOf(true) }
        if (showWindow) {
            val windowState = rememberWindowState(
                width = INITIAL_WINDOW_WIDTH,
                height = INITIAL_WINDOW_HEIGHT,
                placement = WindowPlacement.Floating,
            )
            Window(
                onCloseRequest = {
                    showWindow = false
                },
                title = FOUNDATION_TITLE,
                state = windowState,
            ) {
                window.minimumSize = Dimension(
                    DESKTOP_MINIMUM_WINDOW_WIDTH.value.toInt(),
                    DESKTOP_MINIMUM_WINDOW_HEIGHT.value.toInt(),
                )
                // The menu is built inside the shell — where the shell state, the visible
                // session, the loaded keybindings, and the one dispatcher already are — and
                // handed back out to here, because `MenuBar` is window-scoped and the shell
                // is not. Held as state rather than passed down so the window renders the
                // menu without the shell knowing anything about AWT.
                var menuBar by remember { mutableStateOf<DesktopMenuBar?>(null) }
                var menuDispatch by remember { mutableStateOf<((KaniAction) -> Unit)?>(null) }
                menuBar?.let { bar ->
                    menuDispatch?.let { dispatch -> KaniMenuBar(bar = bar, dispatch = dispatch) }
                }
                KaniDesktopFoundation(
                    container = container,
                    onMenuBarChange = { bar, dispatch ->
                        menuBar = bar
                        menuDispatch = dispatch
                    },
                )
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

/**
 * A plain window naming why Kani will not start.
 *
 * Deliberately not the shell: there is no container behind it, so a shell here
 * would need every port faked, and a fake shell is exactly the "second host
 * harness" this goal is meant to avoid.
 */
private fun showStartupBlocked(blocked: DesktopStartup.Outcome.Blocked) {
    application(exitProcessOnExit = false) {
        var showWindow by remember { mutableStateOf(true) }
        if (showWindow) {
            Window(
                onCloseRequest = { showWindow = false },
                title = FOUNDATION_TITLE,
                state = rememberWindowState(width = 520.dp, height = 260.dp),
            ) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Kani cannot start",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = blocked.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(Unit) { exitApplication() }
        }
    }
}

/**
 * Appends to a log file inside the profile, silently.
 *
 * Every failure is swallowed on purpose. Logging is diagnostic, so a full disk or a
 * read-only profile must not be able to take down the app it is diagnosing — and on
 * the smoke path, a stack trace from the logger would land on the stderr the gate
 * requires to be empty.
 */
private fun fileSink(logFile: Path): (String) -> Unit = { line ->
    runCatching {
        Files.writeString(
            logFile,
            line + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )
    }
}

/**
 * Whether debug logging is on, read straight from the profile's device settings.
 *
 * Read through a short-lived store rather than the container's, because the logger
 * exists before the container does — it is what reports the container failing to
 * open. A missing or unreadable store answers "off", which is the safe default for
 * a setting whose only effect is more disk writes.
 */
private fun debugLoggingEnabled(profileDir: Path): Boolean = runCatching {
    DesktopDeviceSettingsStore
        .open(profileDir.resolve(DesktopDeviceSettingsStore.FILE_NAME))
        .snapshot()
        .read(DeviceSettingKeys.debugLogEnabled) == true
}.getOrDefault(false)

private const val SMOKE_RENDER_FRAME_COUNT = 3
private const val SMOKE_SETTLE_MILLIS = 250L
private const val LOG_FILE_NAME = "kani-desktop.log"
private val INITIAL_WINDOW_WIDTH = 1280.dp
private val INITIAL_WINDOW_HEIGHT = 800.dp

/**
 * Kani's shared shell, over a live desktop container.
 *
 * Everything user-visible comes from `:feature-shell`; this function's whole job is
 * to supply the four things the shell cannot know — the theme the user picked, the
 * host's back affordance, the effect adapters, and the destination-to-content
 * table. That table is the part Goals 194+ replace one route at a time.
 */
@Composable
internal fun KaniDesktopFoundation(
    container: DesktopKaniContainer,
    onMenuBarChange: (DesktopMenuBar, (KaniAction) -> Unit) -> Unit = { _, _ -> },
) {
    DesktopShellScaffold(container = container, onMenuBarChange = onMenuBarChange)
}
