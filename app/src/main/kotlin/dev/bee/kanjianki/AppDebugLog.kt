package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Release-safe, user-toggleable diagnostic log ("Debug log" in Settings > Automation).
 *
 * Unlike [StudyLoadDebugLog] (debug builds only), this sink works in release builds but is OFF by
 * default and gated behind the persisted `debug_log_enabled` setting. When off, [log] is a single
 * atomic read and returns immediately, so instrumented call sites add no measurable work. When on,
 * callers only capture timestamps; formatting and file I/O run on a dedicated daemon writer
 * thread, never on the thread being observed.
 *
 * The log lives in the app's internal (app-private) files dir so it is not world-readable; it is
 * handed to the Android share sheet through the `.debuglog` FileProvider. The file survives
 * process restarts and toggling the switch off, so a captured problem can still be shared later.
 * It is trimmed to its tail when it grows past [MAX_BYTES].
 */
internal object AppDebugLog {
    private const val LOG_FILE_NAME = "kani-debug.log"
    private const val MAX_BYTES = 2_000_000L
    private const val TRIM_KEEP_BYTES = 1_000_000

    private const val STATE_OFF = 0
    private const val STATE_PENDING = 1
    private const val STATE_ON = 2

    private val state = AtomicInteger(STATE_OFF)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kani-debug-log").apply { isDaemon = true }
    }

    /** Confined to the writer thread. SimpleDateFormat is not thread-safe. */
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    /**
     * Resolves the persisted toggle on the writer thread so process start never blocks on the
     * settings database. Events logged before resolution finishes are queued behind the resolve
     * task on the same single writer thread, so they are kept when the toggle turns out to be on
     * and dropped when it is off.
     */
    fun init(context: Context) {
        val resolvedContext = context.applicationContext
        appContext = resolvedContext
        if (!state.compareAndSet(STATE_OFF, STATE_PENDING)) {
            return
        }
        val startLine = timestampedOnCaller(appStartLine())
        writer.execute {
            val enabled = runCatching { readEnabledSetting(resolvedContext) }.getOrDefault(false)
            if (state.compareAndSet(STATE_PENDING, if (enabled) STATE_ON else STATE_OFF) && enabled) {
                append(startLine)
            }
        }
    }

    /**
     * Flips capturing on or off after the settings toggle persists. Session start/stop marker
     * lines are written unconditionally so the shared file shows exactly when capture ran.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        if (enabled) {
            state.set(STATE_ON)
            val header = timestampedOnCaller("==== debug log enabled ${appStartLine()} ====")
            writer.execute { append(header) }
        } else {
            val footer = timestampedOnCaller("==== debug log disabled ====")
            state.set(STATE_OFF)
            writer.execute { append(footer) }
        }
    }

    /**
     * True when call sites should build log messages at all. Used to skip string formatting on
     * hot paths; [log] re-checks internally so gating is optional for cold call sites.
     */
    fun isCapturing(): Boolean = state.get() != STATE_OFF

    /** Records one timestamped event line. Cheap no-op (one atomic read) while the log is off. */
    fun log(message: String) {
        val callState = state.get()
        if (callState == STATE_OFF) {
            return
        }
        val line = timestampedOnCaller(message)
        if (callState == STATE_ON) {
            writer.execute { append(line) }
        } else {
            // Captured before init resolved the persisted toggle: the resolve task was queued
            // first on this same single thread, so state is final by the time this runs.
            writer.execute {
                if (state.get() == STATE_ON) {
                    append(line)
                }
            }
        }
    }

    /** Records an error event with the exception type, message, and stack trace. */
    fun logError(event: String, error: Throwable) {
        if (!isCapturing()) {
            return
        }
        val stack = runCatching {
            val stackWriter = StringWriter()
            error.printStackTrace(PrintWriter(stackWriter))
            stackWriter.toString().trimEnd()
        }.getOrDefault(error.javaClass.name)
        log("error $event ${error.javaClass.name}: ${error.message}\n$stack")
    }

    /** True when a non-empty debug log exists and can be shared, regardless of the toggle. */
    fun hasLog(context: Context): Boolean {
        val file = resolveLogFile(context)
        return file.isFile && file.length() > 0
    }

    /**
     * Builds a share-sheet [Intent] for the debug log via the `.debuglog` FileProvider, or null
     * when there is nothing to share. Works while the toggle is off so a captured problem can be
     * shared after recording stops. Callers wrap it in [Intent.createChooser] and start it.
     */
    fun buildShareIntent(context: Context): Intent? {
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
            putExtra(Intent.EXTRA_SUBJECT, "Kani debug log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Reads the persisted toggle without creating the database: fresh installs (no database yet)
     * resolve to off with a single file stat instead of forcing schema creation at process start.
     */
    private fun readEnabledSetting(context: Context): Boolean {
        if (!context.getDatabasePath(LocalStoreSchema.DB_NAME).exists()) {
            return false
        }
        return LocalStore(context).use { it.debugLogEnabled() }
    }

    /** Captures wall + uptime clocks on the caller thread; formatting happens on the writer. */
    private fun timestampedOnCaller(message: String): PendingLine {
        val wallMillis = System.currentTimeMillis()
        val uptimeMillis = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        return PendingLine(wallMillis, uptimeMillis, message)
    }

    /** Writer-thread only: formats and appends one line, then enforces the size cap. */
    private fun append(line: PendingLine) {
        val file = File((appContext ?: return).filesDir, LOG_FILE_NAME)
        runCatching {
            val stamp = timestampFormat.format(Date(line.wallMillis))
            file.appendText("$stamp [+${line.uptimeMillis}ms] ${line.message}\n", Charsets.UTF_8)
            trimIfOversized(file)
        }
    }

    /** Keeps the newest [TRIM_KEEP_BYTES] once the file passes [MAX_BYTES], on line boundaries. */
    private fun trimIfOversized(file: File) {
        if (file.length() <= MAX_BYTES) {
            return
        }
        val text = file.readText(Charsets.UTF_8)
        var start = text.length - TRIM_KEEP_BYTES
        if (start < 0) {
            start = 0
        }
        val boundary = text.indexOf('\n', start)
        val tail = if (boundary in start until text.length - 1) text.substring(boundary + 1) else text.substring(start)
        file.writeText("==== older debug log entries trimmed ====\n$tail", Charsets.UTF_8)
    }

    private fun appStartLine(): String {
        val sdk = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0)
        val model = runCatching { "${Build.MANUFACTURER} ${Build.MODEL}" }.getOrDefault("unknown")
        return "app version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) sdk=$sdk device=$model"
    }

    private fun resolveLogFile(context: Context): File {
        return File(context.applicationContext.filesDir, LOG_FILE_NAME)
    }

    /**
     * Test hook: drains queued writes and resets the singleton so Robolectric tests (which share
     * one JVM and static state) start from a known-off state.
     */
    fun resetForTests() {
        val drained = CountDownLatch(1)
        writer.execute { drained.countDown() }
        runCatching { drained.await(5, TimeUnit.SECONDS) }
        state.set(STATE_OFF)
        appContext = null
    }

    private class PendingLine(
        @JvmField val wallMillis: Long,
        @JvmField val uptimeMillis: Long,
        @JvmField val message: String,
    )
}
