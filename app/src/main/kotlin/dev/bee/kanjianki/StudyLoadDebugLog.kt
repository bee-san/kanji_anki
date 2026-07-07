package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Debug-only file sink for the study-load timing probes.
 *
 * The [studyLoadDebug]/[withStudyLoadProbe] helpers already write to logcat, but capturing logcat
 * off a physical device is fiddly. This mirrors the same lines into a plain-text file in the app's
 * internal (app-private) files dir, which is handed to the share sheet through the `.debuglog`
 * FileProvider by the Settings "Share debug log" action. Internal storage is used deliberately so
 * the log is not world-readable via external storage.
 *
 * Writes happen on a single background thread so logging never adds main-thread work to the very
 * path we are measuring. No-ops entirely in release builds.
 */
internal object StudyLoadDebugLog {
    private const val LOG_FILE_NAME = "kani-study-debug.log"
    private const val MAX_BYTES = 1_000_000L

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kani-study-debug-log").apply { isDaemon = true }
    }
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                val file = File(appContext.filesDir, LOG_FILE_NAME)
                // Start a fresh log each process launch, but keep it if it is still small so we do
                // not lose context across a quick relaunch.
                if (file.exists() && file.length() > MAX_BYTES && !file.delete()) {
                    file.writeText("", Charsets.UTF_8)
                }
                logFile = file
                appendLine(file, "==== study-debug log opened path=${file.absolutePath} ====")
            }
        }
    }

    fun log(message: String) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val stamp = runCatching { timestampFormat.format(Date()) }.getOrDefault("")
        val uptime = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        writer.execute {
            val file = logFile ?: return@execute
            runCatching { appendLine(file, "$stamp [+${uptime}ms] $message") }
        }
    }

    private fun appendLine(file: File, line: String) {
        file.appendText(line + "\n", Charsets.UTF_8)
    }

    /** True when a non-empty debug log exists and can be shared. */
    fun hasLog(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }
        val file = resolveLogFile(context)
        return file.isFile && file.length() > 0
    }

    /**
     * Builds a share-sheet [Intent] for the debug log via the debug-only FileProvider, or null if
     * there is nothing to share. Callers wrap it in [Intent.createChooser] and start it.
     */
    fun buildShareIntent(context: Context): Intent? {
        if (!BuildConfig.DEBUG) {
            return null
        }
        val file = resolveLogFile(context)
        if (!file.isFile || file.length() == 0L) {
            return null
        }
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.debuglog", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kani study debug log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveLogFile(context: Context): File {
        logFile?.let { return it }
        return File(context.applicationContext.filesDir, LOG_FILE_NAME)
    }
}
