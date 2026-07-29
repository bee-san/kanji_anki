package dev.bee.kanjianki.platform

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

data class AppDirectories(
    val data: Path,
    val cache: Path,
    val backups: Path,
)

fun interface AppDirectoriesProvider {
    fun directories(): AppDirectories
}

enum class FilePickerPurpose {
    OPEN,
    SAVE,
}

data class FileTypeFilter(
    val description: String,
    val extensions: Set<String>,
) {
    init {
        require(description.isNotBlank()) { "file filter description must not be blank" }
        require(extensions.isNotEmpty()) { "file filter extensions must not be empty" }
        require(extensions.all(VALID_FILE_EXTENSION::matches)) {
            "file filter extensions must be lowercase alphanumeric suffixes"
        }
    }
}

data class FilePickerRequest(
    val purpose: FilePickerPurpose,
    val suggestedName: String? = null,
    val filters: List<FileTypeFilter> = emptyList(),
) {
    init {
        require(suggestedName == null || isSafeDisplayName(suggestedName)) {
            "suggested file name must be a single safe path segment"
        }
    }
}

class PlatformFileReference private constructor(
    val opaqueId: String,
    val displayName: String,
) {
    init {
        require(opaqueId.isNotBlank()) { "file reference must not be blank" }
        require(isSafeDisplayName(displayName)) {
            "display name must be a single safe path segment"
        }
    }

    override fun toString(): String =
        "PlatformFileReference(displayName=$displayName, opaqueId=[REDACTED])"

    companion object {
        @JvmStatic
        fun create(opaqueId: String, displayName: String): PlatformFileReference =
            PlatformFileReference(opaqueId, displayName)
    }
}

fun interface FilePicker {
    fun launch(
        request: FilePickerRequest,
        onResult: (PlatformFileReference?) -> Unit,
    )
}

interface PlatformFileAccess {
    fun openInput(file: PlatformFileReference): InputStream?

    fun openOutput(file: PlatformFileReference): OutputStream?
}

data class DatabaseSnapshotResult(
    val bytesWritten: Long,
) {
    init {
        require(bytesWritten > 0L) { "a successful database snapshot must not be empty" }
    }
}

fun interface DatabaseSnapshotService {
    fun createSnapshot(destination: Path): DatabaseSnapshotResult
}

private fun isSafeDisplayName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        '\u0000' !in name

private val VALID_FILE_EXTENSION = Regex("[a-z0-9][a-z0-9._+-]*")
