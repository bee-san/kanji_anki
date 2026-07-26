package dev.bee.kanjianki.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal const val FOUNDATION_TITLE = "Kani desktop foundation"
internal const val SMOKE_READY_MARKER = "KANI_DESKTOP_SMOKE_READY"
internal const val SMOKE_READY_LINE = "$SMOKE_READY_MARKER temporary_data=true"
internal const val SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE =
    "KANI_DESKTOP_SMOKE_RESULT_FILE"
internal const val SMOKE_RENDER_API_PROPERTY = "skiko.renderApi"
internal const val LINUX_SMOKE_RENDER_API = "SOFTWARE_FAST"

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
    val options = DesktopLaunchOptions.parse(args)
    configureSmokeRenderer(options)
    runDesktop(options)
}

internal fun configureSmokeRenderer(
    options: DesktopLaunchOptions,
    propertySetter: (String, String) -> Unit = System::setProperty,
    osName: String = System.getProperty("os.name", ""),
) {
    if (options.smokeTest && osName.startsWith("Linux", ignoreCase = true)) {
        propertySetter(SMOKE_RENDER_API_PROPERTY, LINUX_SMOKE_RENDER_API)
    }
}

internal fun runDesktop(
    options: DesktopLaunchOptions,
    normalDataRoot: () -> Path = { defaultDesktopDataRoot() },
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

internal fun deleteTemporaryDataRoot(
    dataRoot: Path,
    deleteRecursively: (Path) -> Boolean = { it.toFile().deleteRecursively() },
) {
    check(deleteRecursively(dataRoot)) {
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

internal fun defaultDesktopDataRoot(
    userHome: String? = System.getProperty("user.home"),
): Path {
    return Path.of(
        requireNotNull(userHome) {
            "The desktop profile requires the user.home system property"
        },
        ".kani",
    )
}
