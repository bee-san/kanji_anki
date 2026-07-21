package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        assertTrue("shared log writer reset", StudyLoadDebugLog.resetForTests())
        logFile().delete()
    }

    @After
    fun tearDown() {
        assertTrue("shared log writer reset", StudyLoadDebugLog.resetForTests())
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
        logFile().delete()
        assertFalse(StudyLoadDebugLog.hasLog(context))
    }

    @Test
    fun buildShareIntentNullWhenFileMissing() {
        logFile().delete()
        assertNull(awaitShareIntent())
    }

    @Test
    fun buildShareIntentReturnsSendIntentWhenLogPresent() {
        StudyLoadDebugLog.init(context)
        StudyLoadDebugLog.log("stage=renderStudy.total duration_ms=42")

        val intent = awaitShareIntent()
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
        val sharedText = stream?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
        }
        assertTrue("queued line is present in the snapshot", sharedText?.contains("renderStudy.total") == true)
    }

    @Test
    fun runtimeSizeCapKeepsNewestStudyLogTail() {
        StudyLoadDebugLog.init(context)
        assertNotNull(awaitShareIntent())
        logFile().writeText("覚え方\n".repeat(120_000), Charsets.UTF_8)

        StudyLoadDebugLog.log("newest-line-after-cap")

        val text = awaitFileContaining("==== older study-debug entries trimmed ====")
        assertTrue(text.startsWith("==== older study-debug entries trimmed ===="))
        assertTrue(text.contains("newest-line-after-cap"))
        assertTrue(logFile().length() < 600_000L)
    }

    private fun awaitShareIntent(): Intent? {
        val ready = CountDownLatch(1)
        var intent: Intent? = null
        StudyLoadDebugLog.prepareShareIntent(context) { prepared ->
            intent = prepared
            ready.countDown()
        }
        assertTrue("share callback completed", ready.await(5, TimeUnit.SECONDS))
        return intent
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
