package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingExposureMediaReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parserReadsCanonicalAndCompactFieldNames() {
        val stats = ReadingExposureMediaReader.parseKanjiStats(
            """
            {
              "kanji": [
                {
                  "kanji": "読",
                  "totalCount": 10,
                  "last7DaysCount": 2,
                  "last14DaysCount": 4,
                  "last31DaysCount": 7,
                  "lastSeenAtMillis": 123
                },
                {
                  "kanji": "書",
                  "total": 8,
                  "last7": 1,
                  "last14": 3,
                  "last31": 5,
                  "lastSeen": 456
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, stats.size)
        assertEquals("読", stats[0].kanji)
        assertEquals(7, stats[0].last31DaysCount)
        assertEquals("書", stats[1].kanji)
        assertEquals(456L, stats[1].lastSeenAtMillis)
    }

    @Test
    fun readerLoadsGzippedKanjiStatsFromGenericManifest() {
        val media = temporaryFolder.newFolder("collection.media")
        File(media, ReadingExposureMediaReader.MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"${ReadingExposureMediaReader.DEFAULT_KANJI_FILE}"}""",
            Charsets.UTF_8,
        )
        gzip(
            File(media, ReadingExposureMediaReader.DEFAULT_KANJI_FILE),
            """{"kanji":[{"kanji":"見","totalCount":12,"last7DaysCount":6,"last14DaysCount":8,"last31DaysCount":9,"lastSeenAtMillis":999}]}""",
        )

        val index = ReadingExposureMediaReader(listOf(media)).read()

        val stat = index.statFor("見")
        assertEquals(12, stat?.totalCount)
        assertTrue(index.priorityBoost("見") > 0.0)
    }

    @Test
    fun readerFallsBackToLegacyKaniManifest() {
        val media = temporaryFolder.newFolder("legacy.media")
        File(media, ReadingExposureMediaReader.LEGACY_KANI_MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"${ReadingExposureMediaReader.LEGACY_KANI_KANJI_FILE}"}""",
            Charsets.UTF_8,
        )
        gzip(
            File(media, ReadingExposureMediaReader.LEGACY_KANI_KANJI_FILE),
            """{"kanji":[{"kanji":"旧","totalCount":7,"last7DaysCount":3,"last14DaysCount":4,"last31DaysCount":6,"lastSeenAtMillis":777}]}""",
        )

        val index = ReadingExposureMediaReader(listOf(media)).read()

        val stat = index.statFor("旧")
        assertEquals(7, stat?.totalCount)
        assertTrue(index.priorityBoost("旧") > 0.0)
    }

    @Test
    fun readerFallsBackToLegacyKaniManifestWhenGenericExportIsBroken() {
        val media = temporaryFolder.newFolder("mixed.media")
        File(media, ReadingExposureMediaReader.MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"missing.gz"}""",
            Charsets.UTF_8,
        )
        File(media, ReadingExposureMediaReader.LEGACY_KANI_MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"${ReadingExposureMediaReader.LEGACY_KANI_KANJI_FILE}"}""",
            Charsets.UTF_8,
        )
        gzip(
            File(media, ReadingExposureMediaReader.LEGACY_KANI_KANJI_FILE),
            """{"kanji":[{"kanji":"戻","totalCount":5,"last7DaysCount":2,"last14DaysCount":3,"last31DaysCount":4,"lastSeenAtMillis":555}]}""",
        )

        val index = ReadingExposureMediaReader(listOf(media)).read()

        assertEquals(5, index.statFor("戻")?.totalCount)
        assertTrue(index.priorityBoost("戻") > 0.0)
    }

    @Test
    fun readerReturnsEmptyIndexWhenManifestIsMissing() {
        val index = ReadingExposureMediaReader(listOf(temporaryFolder.newFolder("empty"))).read()

        assertEquals(0.0, index.priorityBoost("読"), 0.0)
    }

    @Test
    fun readerReparsesWhenBackingFileChangesButServesCacheOtherwise() {
        val media = temporaryFolder.newFolder("cache.media")
        File(media, ReadingExposureMediaReader.MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"${ReadingExposureMediaReader.DEFAULT_KANJI_FILE}"}""",
            Charsets.UTF_8,
        )
        val kanjiFile = File(media, ReadingExposureMediaReader.DEFAULT_KANJI_FILE)
        gzip(
            kanjiFile,
            """{"kanji":[{"kanji":"川","totalCount":3,"last7DaysCount":1,"last14DaysCount":1,"last31DaysCount":2,"lastSeenAtMillis":10}]}""",
        )

        val first = ReadingExposureMediaReader(listOf(media)).read()
        assertEquals(3, first.statFor("川")?.totalCount)

        // Same file → cached parse; a fresh reader instance still reflects the same content.
        val cached = ReadingExposureMediaReader(listOf(media)).read()
        assertEquals(3, cached.statFor("川")?.totalCount)

        // Rewrite with different content and a distinct mtime so the fingerprint changes.
        kanjiFile.delete()
        gzip(
            kanjiFile,
            """{"kanji":[{"kanji":"川","totalCount":99,"last7DaysCount":1,"last14DaysCount":1,"last31DaysCount":2,"lastSeenAtMillis":10}]}""",
        )
        kanjiFile.setLastModified(5_000_000_000L)

        val refreshed = ReadingExposureMediaReader(listOf(media)).read()
        assertEquals(99, refreshed.statFor("川")?.totalCount)
    }

    @Test
    fun reparsesWhenCustomManifestKanjiFileChanges() {
        val media = temporaryFolder.newFolder("custom.media")
        File(media, ReadingExposureMediaReader.MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"custom_stats.json.gz"}""",
            Charsets.UTF_8,
        )
        val custom = File(media, "custom_stats.json.gz")
        gzip(
            custom,
            """{"kanji":[{"kanji":"山","totalCount":4,"last7DaysCount":1,"last14DaysCount":1,"last31DaysCount":2,"lastSeenAtMillis":1}]}""",
        )

        val first = ReadingExposureMediaReader(listOf(media)).read()
        assertEquals(4, first.statFor("山")?.totalCount)

        // Change only the custom stats file (manifest untouched). Cache must still refresh.
        custom.delete()
        gzip(
            custom,
            """{"kanji":[{"kanji":"山","totalCount":77,"last7DaysCount":1,"last14DaysCount":1,"last31DaysCount":2,"lastSeenAtMillis":1}]}""",
        )
        custom.setLastModified(6_000_000_000L)

        val refreshed = ReadingExposureMediaReader(listOf(media)).read()
        assertEquals(77, refreshed.statFor("山")?.totalCount)
    }

    @Test
    fun debugLogSeparatesFingerprintReadParseAndCacheHitPhases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logFile = File(context.filesDir, "kani-debug.log")
        val media = temporaryFolder.newFolder("logged.media")
        File(media, ReadingExposureMediaReader.MANIFEST_FILE).writeText(
            """{"schemaVersion":1,"kanjiFile":"${ReadingExposureMediaReader.DEFAULT_KANJI_FILE}"}""",
            Charsets.UTF_8,
        )
        gzip(
            File(media, ReadingExposureMediaReader.DEFAULT_KANJI_FILE),
            """{"kanji":[{"kanji":"速","totalCount":9,"last7DaysCount":2}]}""",
        )

        try {
            AppDebugLog.resetForTests()
            logFile.delete()
            AppDebugLog.setEnabled(context, true)

            val reader = ReadingExposureMediaReader(listOf(media))
            reader.read()
            reader.read()
            AppDebugLog.resetForTests()

            val text = logFile.readText()
            assertTrue(text.contains("reading-exposure phase=fingerprint"))
            assertTrue(text.contains("reading-exposure phase=manifest-read"))
            assertTrue(text.contains("reading-exposure phase=gzip-read"))
            assertTrue(text.contains("reading-exposure phase=parse"))
            assertTrue(text.contains("reading-exposure phase=total"))
            assertTrue(text.contains("source=cache"))
        } finally {
            AppDebugLog.resetForTests()
            logFile.delete()
        }
    }

    @Test
    fun debugFailureLogIsBoundedAndOmitsExceptionPayloadAndStack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logFile = File(context.filesDir, "kani-debug.log")
        val privatePayload = "private-reading-exposure-payload-" + "x".repeat(100_000)

        try {
            AppDebugLog.resetForTests()
            logFile.delete()
            AppDebugLog.setEnabled(context, true)

            try {
                readingExposurePhase("parse") {
                    throw IllegalArgumentException(privatePayload)
                }
                fail("phase should rethrow the parser failure")
            } catch (_: IllegalArgumentException) {
                // Expected: diagnostics must not change the optional-media failure contract.
            }
            AppDebugLog.resetForTests()

            val text = logFile.readText()
            assertTrue(text.contains("reading-exposure phase=parse failed"))
            assertTrue(text.contains("error_type=illegalargumentexception"))
            assertFalse(text.contains("private-reading-exposure-payload"))
            assertFalse(text.contains("ReadingExposureMediaReaderTest.debugFailureLog"))
            assertTrue("failure diagnostics stay bounded", text.length < 2_000)
        } finally {
            AppDebugLog.resetForTests()
            logFile.delete()
        }
    }

    private fun gzip(file: File, text: String) {
        GZIPOutputStream(file.outputStream()).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
