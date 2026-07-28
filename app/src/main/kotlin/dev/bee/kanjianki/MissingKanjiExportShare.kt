package dev.bee.kanjianki

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiCsv
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal object MissingKanjiExportShare {
    data class PreparedExport(
        val intent: Intent,
        val csvResult: MissingKanjiCsv.Result,
        val fileName: String,
    )

    @Synchronized
    fun prepare(
        context: Context,
        candidates: Iterable<MissingKanjiCandidate>,
        range: MissingKanjiFrequencyRange,
        nowMillis: Long = System.currentTimeMillis(),
    ): PreparedExport? {
        val appContext = context.applicationContext
        val directory = prepareDirectory(appContext) ?: return null
        val temporary = runCatching {
            Files.createTempFile(directory, ".pending-", ".tmp")
        }.getOrNull() ?: return null
        val fileName = fileName(range, nowMillis)
        val finalPath = directory.resolve("${UUID.randomUUID()}-$fileName")
        val csvResult = try {
            Files.newBufferedWriter(temporary, Charsets.UTF_8).use { writer ->
                MissingKanjiCsv.write(candidates, writer)
            }
        } catch (_: Exception) {
            runCatching { Files.deleteIfExists(temporary) }
            return null
        }
        if (csvResult.exportedCount == 0 || !publish(temporary, finalPath)) {
            runCatching { Files.deleteIfExists(temporary) }
            runCatching { Files.deleteIfExists(finalPath) }
            return null
        }

        val authority = "${appContext.packageName}.missingkanji"
        pruneOldExports(appContext, authority, directory, finalPath)
        val uri = runCatching {
            exportUri(appContext, authority, finalPath)
        }.getOrNull() ?: run {
            runCatching { Files.deleteIfExists(finalPath) }
            return null
        }
        val subject = "Kani Missing Kanji"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MissingKanjiCsv.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, MissingKanjiTextCopy.csvImportInstructions())
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return PreparedExport(intent, csvResult, fileName)
    }

    private fun prepareDirectory(context: Context): Path? {
        val cacheDirectory = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return null
        val directory = File(cacheDirectory, DIRECTORY_NAME)
        val path = directory.toPath()
        val ready = runCatching {
            if (Files.exists(path, NOFOLLOW_LINKS)) {
                Files.isDirectory(path, NOFOLLOW_LINKS)
            } else {
                Files.createDirectory(path)
                true
            }
        }.getOrDefault(false)
        if (!ready || runCatching { directory.canonicalFile.parentFile }.getOrNull() != cacheDirectory) {
            return null
        }
        return path.takeIf(::removeUnexpectedEntries)
    }

    private fun removeUnexpectedEntries(directory: Path): Boolean {
        return runCatching {
            Files.newDirectoryStream(directory).use { entries ->
                for (entry in entries) {
                    val valid = EXPORT_FILE.matches(entry.fileName.toString()) &&
                        Files.isRegularFile(entry, NOFOLLOW_LINKS)
                    if (!valid) {
                        Files.delete(entry)
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun publish(temporary: Path, destination: Path): Boolean {
        return try {
            Files.move(temporary, destination, ATOMIC_MOVE)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            runCatching { Files.move(temporary, destination) }.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    private fun pruneOldExports(
        context: Context,
        authority: String,
        directory: Path,
        protectedExport: Path,
    ) {
        val exports = runCatching {
            Files.newDirectoryStream(directory).use { entries ->
                entries
                    .filter { entry ->
                        EXPORT_FILE.matches(entry.fileName.toString()) &&
                            Files.isRegularFile(entry, NOFOLLOW_LINKS)
                    }
                    .sortedBy { entry -> Files.getLastModifiedTime(entry, NOFOLLOW_LINKS).toMillis() }
            }
        }.getOrDefault(emptyList())
        val purgeCount = (exports.size - MAX_EXPORTS).coerceAtLeast(0)
        exports.filterNot { path -> path == protectedExport }
            .take(purgeCount)
            .forEach { stale ->
                runCatching {
                    val uri = exportUri(context, authority, stale)
                    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { Files.deleteIfExists(stale) }
            }
    }

    private fun exportUri(context: Context, authority: String, path: Path): Uri {
        val physicalName = path.fileName.toString()
        val match = EXPORT_FILE.matchEntire(physicalName)
            ?: throw IllegalArgumentException("Unexpected Missing Kanji export filename.")
        return FileProvider.getUriForFile(
            context,
            authority,
            path.toFile(),
            match.groupValues[1],
        )
    }

    private fun fileName(range: MissingKanjiFrequencyRange, nowMillis: Long): String {
        val date = FILE_DATE.format(
            Instant.ofEpochMilli(nowMillis.coerceAtLeast(0L)).atZone(ZoneOffset.UTC),
        )
        val unranked = if (range.includeUnranked) "-with-unranked" else ""
        return "kani-missing-kanji-${range.minimumRank}-${range.maximumRank}$unranked-$date.csv"
            .lowercase(Locale.ROOT)
    }

    private const val DIRECTORY_NAME = "missing-kanji-exports"
    private const val MAX_EXPORTS = 8
    private val FILE_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
    private val EXPORT_FILE = Regex(
        "[0-9a-f-]{36}-(kani-missing-kanji-[0-9]+-[0-9]+" +
            "(?:-with-unranked)?-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.csv)",
    )
}
