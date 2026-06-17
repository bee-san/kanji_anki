package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun readerReturnsEmptyIndexWhenManifestIsMissing() {
        val index = ReadingExposureMediaReader(listOf(temporaryFolder.newFolder("empty"))).read()

        assertEquals(0.0, index.priorityBoost("読"), 0.0)
    }

    private fun gzip(file: File, text: String) {
        GZIPOutputStream(file.outputStream()).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
