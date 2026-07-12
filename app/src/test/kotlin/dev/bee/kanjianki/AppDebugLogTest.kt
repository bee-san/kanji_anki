package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.data.LocalStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDebugLogTest {
    private lateinit var context: Context

    private fun logFile(): File = File(context.applicationContext.filesDir, "kani-debug.log")

    @Before
    fun setUp() {
        // KaniApplication.onCreate already ran init for this test's fresh application; reset the
        // shared singleton (drains the writer thread) so every test starts from a known-off state.
        AppDebugLog.resetForTests()
        clearFileProviderPathStrategyCache()
        context = ApplicationProvider.getApplicationContext()
        logFile().delete()
    }

    @After
    fun tearDown() {
        AppDebugLog.resetForTests()
        logFile().delete()
    }

    @Test
    fun logIsDroppedWhileOff() {
        AppDebugLog.log("should never be written")
        AppDebugLog.resetForTests() // drains the writer queue

        assertFalse(AppDebugLog.isCapturing())
        assertFalse(logFile().exists())
        assertFalse(AppDebugLog.hasLog(context))
    }

    @Test
    fun setEnabledCapturesTimestampedLines() {
        AppDebugLog.setEnabled(context, true)
        AppDebugLog.log("sync start trigger=manual")

        val text = awaitFileContaining("sync start trigger=manual")
        assertTrue(AppDebugLog.isCapturing())
        assertTrue("session header written", text.contains("==== debug log enabled"))
        assertTrue("app version recorded", text.contains("version=${BuildConfig.VERSION_NAME}"))
        assertTrue(
            "lines carry wall timestamp and uptime",
            Regex("""\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \[\+\d+ms] sync start trigger=manual""")
                .containsMatchIn(text),
        )
    }

    @Test
    fun initResolvesPersistedToggleAndKeepsEarlyEvents() {
        LocalStore(context).use { it.saveDebugLogEnabled(true) }
        AppDebugLog.resetForTests()

        AppDebugLog.init(context)
        // Logged before the writer thread resolves the persisted toggle; must still be captured.
        AppDebugLog.log("early startup event")

        val text = awaitFileContaining("early startup event")
        assertTrue("app-start line written on resolve", text.contains("version=${BuildConfig.VERSION_NAME}"))
        assertTrue(AppDebugLog.isCapturing())
    }

    @Test
    fun initResolvesOffWhenSettingUnset() {
        LocalStore(context).use { it.saveDebugLogEnabled(false) }
        AppDebugLog.resetForTests()

        AppDebugLog.init(context)
        AppDebugLog.log("event while disabled")
        AppDebugLog.resetForTests() // drains the writer queue

        assertFalse(AppDebugLog.isCapturing())
        assertFalse(logFile().exists())
    }

    @Test
    fun disableKeepsCapturedLogForSharing() {
        AppDebugLog.setEnabled(context, true)
        AppDebugLog.log("captured while on")
        awaitFileContaining("captured while on")

        AppDebugLog.setEnabled(context, false)
        val text = awaitFileContaining("==== debug log disabled ====")
        AppDebugLog.log("dropped while off")
        AppDebugLog.resetForTests() // drains the writer queue

        assertFalse(AppDebugLog.isCapturing())
        assertTrue(text.contains("captured while on"))
        assertFalse(logFile().readText().contains("dropped while off"))
        assertTrue("log stays shareable while off", AppDebugLog.hasLog(context))
        assertNotNull(AppDebugLog.buildShareIntent(context))
    }

    @Test
    fun logErrorRecordsExceptionTypeMessageAndStack() {
        AppDebugLog.setEnabled(context, true)
        AppDebugLog.logError("sync failed", IllegalStateException("boom"))

        val text = awaitFileContaining("sync failed")
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("boom"))
        assertTrue("stack trace included", text.contains("at "))
    }

    @Test
    fun settingsSnapshotLogsStorageLoadAndFirstCacheHit() {
        AppDebugLog.setEnabled(context, true)
        LocalStore(context).use { store ->
            store.getIntSetting("snapshot-log-missing", 1)
            store.getIntSetting("snapshot-log-missing", 1)
        }
        AppDebugLog.resetForTests()

        val text = logFile().readText()
        assertTrue(text.contains("settings snapshot source=storage"))
        assertTrue(text.contains("settings snapshot source=cache"))
    }

    @Test
    fun homeAndStudyPlanPhaseHelpersEmitReleaseSafeTimings() {
        AppDebugLog.setEnabled(context, true)

        val homeResult = homeLoadPhase("study-items", { value -> "rows=$value" }) { 3 }
        val planResult = studyPlanPhase("planner-compute") { "ready" }
        AppDebugLog.resetForTests()

        assertEquals(3, homeResult)
        assertEquals("ready", planResult)
        val text = logFile().readText()
        assertTrue(text.contains("home phase=study-items duration_ms="))
        assertTrue(text.contains("rows=3"))
        assertTrue(text.contains("study-plan phase=planner-compute duration_ms="))
    }

    @Test
    fun hasLogFalseAndShareIntentNullWithoutFile() {
        assertFalse(AppDebugLog.hasLog(context))
        assertNull(AppDebugLog.buildShareIntent(context))
    }

    @Test
    fun buildShareIntentReturnsSendIntentWhenLogPresent() {
        AppDebugLog.setEnabled(context, true)
        AppDebugLog.log("something worth sharing")
        awaitFileContaining("something worth sharing")

        val intent = AppDebugLog.buildShareIntent(context)
        assertNotNull("share intent built once log exists", intent)
        assertEquals(Intent.ACTION_SEND, intent?.action)
        assertEquals("text/plain", intent?.type)
        val stream = intent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
        }
        assertNotNull(stream)
        assertTrue(
            "grants read permission",
            (intent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    /**
     * Writes run on a single background thread, so poll the internal log file until it contains
     * the expected marker (or fail after a bounded wait). Returns the full file contents.
     */
    private fun awaitFileContaining(marker: String): String {
        val file = logFile()
        repeat(100) {
            if (file.isFile) {
                val text = file.readText()
                if (text.contains(marker)) {
                    return text
                }
            }
            Thread.sleep(20)
        }
        throw AssertionError("log file never contained \"$marker\"; contents=${file.takeIf { it.isFile }?.readText()}")
    }
}
