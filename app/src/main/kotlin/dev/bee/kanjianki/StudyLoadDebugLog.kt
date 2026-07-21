package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Debug-only file sink for the study-load timing probes.
 *
 * The [studyLoadDebug]/[withStudyLoadProbe] helpers already write to logcat, but capturing logcat
 * off a physical device is fiddly. This mirrors the same lines into a plain-text file in the app's
 * internal (app-private) files dir. The Settings "Share debug log" action copies a snapshot into a
 * dedicated FileProvider-backed cache directory; the provider cannot address the live files dir.
 * Internal storage is used deliberately so the log is not world-readable via external storage.
 *
 * Writes happen on a single background thread so logging never adds main-thread work to the very
 * path we are measuring. No-ops entirely in release builds.
 */
internal object StudyLoadDebugLog {
    private const val LOG_FILE_NAME = "kani-study-debug.log"
    private const val MAX_BYTES = 1_000_000L
    private const val TRIM_KEEP_BYTES = 500_000

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
        val wallTimeMillis = System.currentTimeMillis()
        val uptime = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        writer.execute {
            val file = logFile ?: return@execute
            val stamp = runCatching { timestampFormat.format(Date(wallTimeMillis)) }.getOrDefault("")
            runCatching { appendLine(file, "$stamp [+${uptime}ms] $message") }
        }
    }

    private fun appendLine(file: File, line: String) {
        file.appendText(line + "\n", Charsets.UTF_8)
        trimIfOversized(file)
    }

    private fun trimIfOversized(file: File) {
        trimUtf8LogTailIfOversized(
            file,
            maxBytes = MAX_BYTES,
            keepBytes = TRIM_KEEP_BYTES,
            marker = "==== older study-debug entries trimmed ====",
        )
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
     * Queues a share-sheet snapshot behind pending log writes, then invokes [onPrepared] on the
     * writer thread. Snapshot copying never blocks the UI and includes every write accepted before
     * this call.
     */
    fun prepareShareIntent(context: Context, onPrepared: (Intent?) -> Unit) {
        if (!BuildConfig.DEBUG) {
            onPrepared(null)
            return
        }
        val appContext = context.applicationContext
        writer.execute {
            val file = resolveLogFile(appContext)
            val intent = if (!file.isFile || file.length() == 0L) {
                null
            } else {
                DebugLogShare.buildIntent(appContext, file, "Kani study debug log")
            }
            runCatching { onPrepared(intent) }
        }
    }

    private fun resolveLogFile(context: Context): File {
        logFile?.let { return it }
        return File(context.applicationContext.filesDir, LOG_FILE_NAME)
    }

    internal fun drainForTests(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        val drained = CountDownLatch(1)
        writer.execute { drained.countDown() }
        return drained.await(timeout, unit)
    }

    internal fun resetForTests(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        val reset = CountDownLatch(1)
        writer.execute {
            logFile = null
            reset.countDown()
        }
        return reset.await(timeout, unit)
    }
}
