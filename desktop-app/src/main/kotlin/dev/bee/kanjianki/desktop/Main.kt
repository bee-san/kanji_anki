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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.nio.file.Path

internal const val FOUNDATION_TITLE = "Kani desktop foundation"
internal const val SMOKE_READY_MARKER = "KANI_DESKTOP_SMOKE_READY"

internal data class DesktopLaunchOptions(
    val smokeTest: Boolean,
    val temporaryData: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): DesktopLaunchOptions {
            val arguments = args.toList()
            val unknownArguments = arguments.filterNot(ALLOWED_ARGUMENTS::contains)
            require(unknownArguments.isEmpty()) {
                "Unknown desktop arguments: ${unknownArguments.joinToString()}"
            }
            require(arguments.distinct().size == arguments.size) {
                "Desktop arguments may be supplied only once"
            }

            val smokeTest = "--smoke-test" in arguments
            val temporaryData = "--temporary-data" in arguments
            require(smokeTest == temporaryData) {
                "--smoke-test and --temporary-data must be supplied together"
            }
            return DesktopLaunchOptions(
                smokeTest = smokeTest,
                temporaryData = temporaryData,
            )
        }

        private val ALLOWED_ARGUMENTS = setOf(
            "--smoke-test",
            "--temporary-data",
        )
    }
}

internal data class DesktopDataSession(
    val root: Path,
    val deleteAfterLaunch: Boolean,
)

fun main(args: Array<String>) {
    runDesktop(DesktopLaunchOptions.parse(args))
}

internal fun runDesktop(
    options: DesktopLaunchOptions,
    normalDataRoot: () -> Path = ::defaultDesktopDataRoot,
    temporaryDataRoot: () -> Path = {
        Files.createTempDirectory("kani-desktop-smoke-")
    },
    windowRunner: (Path, Boolean) -> Unit = ::openFoundationWindow,
) {
    val dataSession = selectDataSession(
        options = options,
        normalDataRoot = normalDataRoot,
        temporaryDataRoot = temporaryDataRoot,
    )
    try {
        windowRunner(dataSession.root, options.smokeTest)
    } finally {
        if (dataSession.deleteAfterLaunch) {
            deleteTemporaryDataRoot(dataSession.root)
        }
    }
}

internal fun deleteTemporaryDataRoot(dataRoot: Path) {
    check(dataRoot.toFile().deleteRecursively()) {
        "Failed to delete temporary desktop data root"
    }
}

internal fun selectDataSession(
    options: DesktopLaunchOptions,
    normalDataRoot: () -> Path,
    temporaryDataRoot: () -> Path,
): DesktopDataSession {
    return if (options.temporaryData) {
        DesktopDataSession(
            root = temporaryDataRoot(),
            deleteAfterLaunch = true,
        )
    } else {
        DesktopDataSession(
            root = normalDataRoot(),
            deleteAfterLaunch = false,
        )
    }
}

internal fun defaultDesktopDataRoot(): Path {
    return Path.of(
        requireNotNull(System.getProperty("user.home")) {
            "The desktop profile requires the user.home system property"
        },
        ".kani",
    )
}

private fun openFoundationWindow(dataRoot: Path, smokeTest: Boolean) {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = FOUNDATION_TITLE,
            state = rememberWindowState(width = 760.dp, height = 480.dp),
        ) {
            KaniDesktopFoundation()
            if (smokeTest) {
                LaunchedEffect(dataRoot) {
                    withFrameNanos { }
                    Files.writeString(
                        dataRoot.resolve("smoke-rendered"),
                        "$FOUNDATION_TITLE\n",
                    )
                    // Compose Desktop terminates the application process when
                    // exitApplication runs, so clean up before requesting exit
                    // rather than relying only on runDesktop's finally block.
                    deleteTemporaryDataRoot(dataRoot)
                    println("$SMOKE_READY_MARKER temporary_data=true")
                    exitApplication()
                }
            }
        }
    }
}

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
