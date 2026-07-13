package dev.bee.kanjianki

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Creates narrowly scoped, immutable snapshots for the two user-shareable diagnostic logs.
 *
 * The live logs stay in internal files storage. Only an allow-listed regular file that resolves to
 * a direct child of [Context.getFilesDir] can be copied into the FileProvider-backed cache
 * directory. Each share receives a fresh URI, so an earlier recipient can never observe a later
 * log snapshot.
 */
internal object DebugLogShare {
    private const val SHARE_DIRECTORY_NAME = "debug-log-share"
    private const val MAX_SNAPSHOTS = 8
    private val allowedLogPrefixes = mapOf(
        "kani-debug.log" to "kani-debug",
        "kani-study-debug.log" to "kani-study-debug",
    )
    private val snapshotName = Regex("kani-(?:study-)?debug-[0-9a-f-]{36}\\.log")

    /** Serializes the two independent log writers while they use the shared cache directory. */
    @Synchronized
    fun buildIntent(context: Context, sourceFile: File, subject: String): Intent? {
        val appContext = context.applicationContext
        val prefix = allowedLogPrefixes[sourceFile.name] ?: return null
        val filesDirectory = runCatching { appContext.filesDir.canonicalFile }.getOrNull() ?: return null
        val expectedSource = File(filesDirectory, sourceFile.name)
        val canonicalSource = runCatching { sourceFile.canonicalFile }.getOrNull() ?: return null
        if (
            canonicalSource != expectedSource ||
            Files.isSymbolicLink(sourceFile.toPath()) ||
            !Files.isRegularFile(canonicalSource.toPath(), NOFOLLOW_LINKS) ||
            canonicalSource.length() == 0L
        ) {
            return null
        }

        val shareDirectory = prepareShareDirectory(appContext) ?: return null
        val temporary = runCatching {
            Files.createTempFile(shareDirectory, ".pending-", ".tmp")
        }.getOrNull() ?: return null
        val snapshot = shareDirectory.resolve("$prefix-${UUID.randomUUID()}.log")
        try {
            Files.copy(canonicalSource.toPath(), temporary, REPLACE_EXISTING)
            if (Files.size(temporary) == 0L || !moveSnapshotIntoPlace(temporary, snapshot)) {
                return null
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }

        val snapshotFile = snapshot.toFile()
        val authority = "${appContext.packageName}.debuglog"
        pruneOldSnapshots(appContext, authority, shareDirectory, snapshot)
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(appContext, authority, snapshotFile)
        }.getOrNull() ?: run {
            runCatching { Files.deleteIfExists(snapshot) }
            return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun prepareShareDirectory(context: Context): Path? {
        val cacheDirectory = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return null
        val shareDirectory = File(cacheDirectory, SHARE_DIRECTORY_NAME)
        val sharePath = shareDirectory.toPath()
        val ready = runCatching {
            if (Files.exists(sharePath, NOFOLLOW_LINKS)) {
                Files.isDirectory(sharePath, NOFOLLOW_LINKS)
            } else {
                Files.createDirectory(sharePath)
                true
            }
        }.getOrDefault(false)
        if (!ready) {
            return null
        }
        val canonicalShareDirectory = runCatching { shareDirectory.canonicalFile }.getOrNull() ?: return null
        if (canonicalShareDirectory.parentFile != cacheDirectory) {
            return null
        }
        return sharePath.takeIf { removeUnexpectedEntries(it) }
    }

    /** Deletes only direct entries; symlinks are unlinked and never traversed. */
    private fun removeUnexpectedEntries(shareDirectory: Path): Boolean {
        return runCatching {
            Files.newDirectoryStream(shareDirectory).use { entries ->
                for (entry in entries) {
                    val allowedSnapshot =
                        snapshotName.matches(entry.fileName.toString()) &&
                            Files.isRegularFile(entry, NOFOLLOW_LINKS)
                    if (!allowedSnapshot) {
                        Files.delete(entry)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun moveSnapshotIntoPlace(temporary: Path, snapshot: Path): Boolean {
        return try {
            Files.move(temporary, snapshot, ATOMIC_MOVE)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            runCatching { Files.move(temporary, snapshot) }.isSuccess
        }
    }

    private fun pruneOldSnapshots(
        context: Context,
        authority: String,
        shareDirectory: Path,
        protectedSnapshot: Path,
    ) {
        val snapshots = runCatching {
            val found = mutableListOf<Path>()
            Files.newDirectoryStream(shareDirectory).use { entries ->
                for (entry in entries) {
                    if (
                        snapshotName.matches(entry.fileName.toString()) &&
                        Files.isRegularFile(entry, NOFOLLOW_LINKS)
                    ) {
                        found.add(entry)
                    }
                }
            }
            found.sortedBy { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }
        }.getOrDefault(emptyList())
        val purgeCount = (snapshots.size - MAX_SNAPSHOTS).coerceAtLeast(0)
        snapshots.filterNot { it == protectedSnapshot }.take(purgeCount).forEach { stale ->
            runCatching {
                val staleUri = FileProvider.getUriForFile(context, authority, stale.toFile())
                context.revokeUriPermission(staleUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { Files.deleteIfExists(stale) }
        }
    }
}

/** Retains a UTF-8-safe byte tail, preferring the first complete line after the cut point. */
internal fun trimUtf8LogTailIfOversized(
    file: File,
    maxBytes: Long,
    keepBytes: Int,
    marker: String,
) {
    if (file.length() <= maxBytes) {
        return
    }
    val bytes = file.readBytes()
    val requestedStart = (bytes.size - keepBytes).coerceAtLeast(0)
    var newline = requestedStart
    while (newline < bytes.size && bytes[newline] != '\n'.code.toByte()) {
        newline += 1
    }
    var tailStart = if (newline < bytes.size - 1) newline + 1 else requestedStart
    while (tailStart < bytes.size && (bytes[tailStart].toInt() and 0xc0) == 0x80) {
        tailStart += 1
    }
    file.outputStream().buffered().use { output ->
        output.write(marker.toByteArray(Charsets.UTF_8))
        output.write('\n'.code)
        output.write(bytes, tailStart, bytes.size - tailStart)
    }
}
