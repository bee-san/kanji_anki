package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
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
class StudyLoadDebugLogTest {
    private lateinit var context: Context

    private fun logFile(): File = File(context.applicationContext.filesDir, "kani-study-debug.log")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearFileProviderPathStrategyCache()
        // StudyLoadDebugLog keeps static state and writes on a shared background thread, so a
        // trailing write from a previous test can recreate the file. Delete until it stays gone.
        val file = logFile()
        repeat(50) {
            file.delete()
            Thread.sleep(10)
            if (!file.exists()) return@repeat
        }
        file.delete()
    }

    @After
    fun tearDown() {
        logFile().delete()
    }

    @Test
    fun initThenLogWritesLinesToInternalFile() {
        StudyLoadDebugLog.init(context)
        StudyLoadDebugLog.log("hello-stage duration_ms=1.23")

        val text = awaitFileContaining("hello-stage duration_ms=1.23")
        assertTrue("header line written", text.contains("study-debug log opened"))
    }

    @Test
    fun hasLogTrueOnceLogWritten() {
        StudyLoadDebugLog.init(context)
        StudyLoadDebugLog.log("x")
        awaitFileContaining("x")

        assertTrue(StudyLoadDebugLog.hasLog(context))
    }

    @Test
    fun hasLogFalseWhenFileMissing() {
        // hasLog resolves the internal log path and checks the file; with the file removed it must
        // report false even if a stale handle is cached from a prior test.
        deleteLogFileStably()
        assertFalse(StudyLoadDebugLog.hasLog(context))
    }

    @Test
    fun buildShareIntentNullWhenFileMissing() {
        deleteLogFileStably()
        assertNull(StudyLoadDebugLog.buildShareIntent(context))
    }

    @Test
    fun buildShareIntentReturnsSendIntentWhenLogPresent() {
        StudyLoadDebugLog.init(context)
        StudyLoadDebugLog.log("stage=renderStudy.total duration_ms=42")
        awaitFileContaining("renderStudy.total")

        val intent = StudyLoadDebugLog.buildShareIntent(context)
        assertNotNull("share intent built once log exists", intent)
        assertEquals(Intent.ACTION_SEND, intent?.action)
        assertEquals("text/plain", intent?.type)
        assertNotNull(intent?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertTrue(
            "grants read permission",
            (intent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    /** Removes the log file and keeps it gone against the shared background writer. */
    private fun deleteLogFileStably() {
        val file = logFile()
        repeat(50) {
            file.delete()
            Thread.sleep(10)
            if (!file.exists()) return
        }
    }

    /**
     * Writes run on a single background thread, so poll the internal log file until it contains the
     * expected marker (or fail after a bounded wait). Returns the full file contents.
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
