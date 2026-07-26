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
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.delay

internal const val FOUNDATION_TITLE = "Kani desktop foundation"
internal const val SMOKE_READY_MARKER = "KANI_DESKTOP_SMOKE_READY"
internal const val SMOKE_READY_LINE = "$SMOKE_READY_MARKER temporary_data=true"
internal const val SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE =
    "KANI_DESKTOP_SMOKE_RESULT_FILE"

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

internal enum class DesktopWindowResult {
    CLOSED,
    SMOKE_RENDERED,
}

fun main(args: Array<String>) {
    runDesktop(DesktopLaunchOptions.parse(args))
}

internal fun runDesktop(
    options: DesktopLaunchOptions,
    normalDataRoot: () -> Path = ::defaultDesktopDataRoot,
    temporaryDataRoot: () -> Path = ::createSmokeTemporaryDataRoot,
    windowRunner: (Path, Boolean) -> DesktopWindowResult = ::openFoundationWindow,
    smokeResultFile: () -> Path = ::smokeResultFileFromEnvironment,
    smokeReadyReporter: (Path) -> Unit = ::reportSmokeReady,
) {
    val dataSession = selectDataSession(
        options = options,
        normalDataRoot = normalDataRoot,
        temporaryDataRoot = temporaryDataRoot,
    )
    val windowResult = try {
        windowRunner(dataSession.root, options.smokeTest)
    } finally {
        if (dataSession.deleteAfterLaunch) {
            deleteTemporaryDataRoot(dataSession.root)
        }
    }
    check(!options.smokeTest || windowResult == DesktopWindowResult.SMOKE_RENDERED) {
        "Desktop smoke window closed before rendering completed"
    }
    if (options.smokeTest) {
        smokeReadyReporter(smokeResultFile())
    }
}

internal fun deleteTemporaryDataRoot(dataRoot: Path) {
    check(dataRoot.toFile().deleteRecursively()) {
        "Failed to delete temporary desktop data root"
    }
}

internal fun smokeResultFileFromEnvironment(
    environment: Map<String, String> = System.getenv(),
): Path {
    val configuredPath = requireNotNull(
        environment[SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
    ) {
        "$SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE is required in smoke mode"
    }
    require(configuredPath.isNotBlank()) {
        "$SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE must not be blank"
    }
    return Path.of(configuredPath).also { resultFile ->
        require(resultFile.isAbsolute) {
            "$SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE must be an absolute path"
        }
    }
}

internal fun createSmokeTemporaryDataRoot(
    environment: Map<String, String> = System.getenv(),
): Path {
    val resultFile = smokeResultFileFromEnvironment(environment)
    val resultDirectory = requireNotNull(resultFile.parent) {
        "$SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE must have a parent directory"
    }
    require(Files.isDirectory(resultDirectory)) {
        "smoke result parent directory does not exist: $resultDirectory"
    }
    return Files.createTempDirectory(resultDirectory, "kani-desktop-smoke-")
}

internal fun reportSmokeReady(resultFile: Path) {
    Files.writeString(
        resultFile,
        "$SMOKE_READY_LINE\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )
    println(SMOKE_READY_LINE)
    System.out.flush()
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

private fun openFoundationWindow(
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
