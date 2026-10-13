package dev.bee.kanjianki.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal const val FOUNDATION_TITLE = "Kani desktop foundation"
internal const val SMOKE_READY_MARKER = "KANI_DESKTOP_SMOKE_READY"
internal const val SMOKE_READY_LINE = "$SMOKE_READY_MARKER temporary_data=true"
internal const val SMOKE_READY_LINE_PINNED_DATA = "$SMOKE_READY_MARKER temporary_data=false"
internal const val SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE =
    "KANI_DESKTOP_SMOKE_RESULT_FILE"
internal const val SMOKE_RENDER_API_PROPERTY = "skiko.renderApi"
internal const val LINUX_SMOKE_RENDER_API = "SOFTWARE_FAST"

internal data class DesktopLaunchOptions(
    val smokeTest: Boolean,
    val temporaryData: Boolean,
    val dataRoot: Path? = null,
) {
    companion object {
        fun parse(args: Array<String>): DesktopLaunchOptions {
            val arguments = args.toList()
            val flags = arguments.filterNot { it.startsWith("$DATA_ROOT_ARGUMENT=") }
            val unknownArguments = flags.filterNot(ALLOWED_ARGUMENTS::contains)
            require(unknownArguments.isEmpty()) {
                "Unknown desktop arguments: ${unknownArguments.joinToString()}"
            }
            require(arguments.distinct().size == arguments.size) {
                "Desktop arguments may be supplied only once"
            }

            val smokeTest = "--smoke-test" in arguments
            val temporaryData = "--temporary-data" in arguments
            val dataRoot = parseDataRoot(arguments)
            require(smokeTest || dataRoot == null) {
                "$DATA_ROOT_ARGUMENT requires --smoke-test"
            }
            // Exactly one of the two data modes, because they mean opposite things about
            // what survives the run: `--temporary-data` promises the root is deleted, and
            // `--data-root` promises it is kept. Accepting both would leave the launcher
            // choosing which promise to break.
            require(smokeTest == (temporaryData || dataRoot != null)) {
                "--smoke-test requires exactly one of --temporary-data or $DATA_ROOT_ARGUMENT"
            }
            require(!(temporaryData && dataRoot != null)) {
                "--temporary-data and $DATA_ROOT_ARGUMENT are mutually exclusive"
            }
            return DesktopLaunchOptions(
                smokeTest = smokeTest,
                temporaryData = temporaryData,
                dataRoot = dataRoot,
            )
        }

        /**
         * The profile a caller pinned, or null when none was given.
         *
         * Absolute-only, and required to exist: this is how the upgrade/retention gate
         * points two different installed images at one profile. A relative path would
         * resolve against the launcher's working directory — which jpackage sets, not the
         * caller — so a typo would silently create a *second* profile and the gate would
         * "pass" having compared an empty root against itself.
         */
        private fun parseDataRoot(arguments: List<String>): Path? {
            val prefix = "$DATA_ROOT_ARGUMENT="
            val supplied = arguments.filter { it.startsWith(prefix) }
            require(supplied.size <= 1) {
                "$DATA_ROOT_ARGUMENT may be supplied only once"
            }
            val value = supplied.singleOrNull()?.removePrefix(prefix) ?: return null
            require(value.isNotBlank()) { "$DATA_ROOT_ARGUMENT must not be blank" }
            val path = Path.of(value)
            require(path.isAbsolute) { "$DATA_ROOT_ARGUMENT must be an absolute path" }
            require(Files.isDirectory(path)) {
                "$DATA_ROOT_ARGUMENT must name an existing directory: $path"
            }
            return path
        }

        private const val DATA_ROOT_ARGUMENT = "--data-root"

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
    smokeReadyReporter: (Path, Boolean) -> Unit = ::reportSmokeReady,
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
        // The marker states which data mode ran, because a reader cannot otherwise tell a
        // throwaway profile from a pinned one — and a retention gate that accepted
        // `temporary_data=true` would be verifying that data survived a root the app had
        // just deleted.
        smokeReadyReporter(smokeResultFile(), dataSession.deleteAfterLaunch)
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

internal fun reportSmokeReady(resultFile: Path, temporaryData: Boolean = true) {
    val line = if (temporaryData) SMOKE_READY_LINE else SMOKE_READY_LINE_PINNED_DATA
    Files.writeString(
        resultFile,
        "$line\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )
    println(line)
    System.out.flush()
}

internal fun selectDataSession(
    options: DesktopLaunchOptions,
    normalDataRoot: () -> Path,
    temporaryDataRoot: () -> Path,
): DesktopDataSession {
    return when {
        options.temporaryData -> DesktopDataSession(
            root = temporaryDataRoot(),
            deleteAfterLaunch = true,
        )
        // A pinned root is never deleted. That is the whole point: the upgrade gate runs
        // one image, then a second image over the same profile, and asks whether the
        // first run's data is still there. Deleting it would make the question vacuous.
        options.dataRoot != null -> DesktopDataSession(
            root = options.dataRoot,
            deleteAfterLaunch = false,
        )
        else -> DesktopDataSession(
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
