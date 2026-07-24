package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiCsv
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import java.io.File
import java.nio.file.Files
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
class MissingKanjiExportShareTest {
    private lateinit var context: Context
    private lateinit var exportDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exportDirectory = File(context.cacheDir, "missing-kanji-exports")
        exportDirectory.deleteRecursively()
        clearFileProviderPathStrategyCache()
    }

    @After
    fun tearDown() {
        if (Files.isSymbolicLink(exportDirectory.toPath())) {
            Files.deleteIfExists(exportDirectory.toPath())
        } else {
            exportDirectory.deleteRecursively()
        }
        File(context.cacheDir, "private-settings.db").delete()
    }

    @Test
    fun preparesReadOnlyUtf8CsvShareIntent() {
        val prepared = MissingKanjiExportShare.prepare(
            context = context,
            candidates = listOf(candidate("語", "language", 301)),
            range = MissingKanjiFrequencyRange(200, 400),
            nowMillis = 1_784_764_800_000L,
        )

        assertNotNull(prepared)
        val intent = prepared!!.intent
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(MissingKanjiCsv.MIME_TYPE, intent.type)
        assertEquals(
            dev.bee.kanjianki.core.MissingKanjiTextCopy.csvImportInstructions(),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        context.contentResolver.query(
            uri!!,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "kani-missing-kanji-200-400-2026-07-23.csv",
                cursor.getString(0),
            )
        }
        assertEquals(
            "\"Kanji\",\"Meaning\",\"OnReading\",\"KunReading\",\"JitenRank\",\"SourceId\"\r\n" +
                "\"語\",\"language\",\"ゴ\",\"かたる\",\"301\",\"kani-missing:語\"\r\n",
            readUri(uri),
        )
        assertEquals("kani-missing-kanji-200-400-2026-07-23.csv", prepared.fileName)
        assertEquals(1, prepared.csvResult.exportedCount)
    }

    @Test
    fun filenameContainsOnlyRangeDateAndUnrankedChoice() {
        val prepared = MissingKanjiExportShare.prepare(
            context = context,
            candidates = listOf(candidate("秘", "private deck name", null)),
            range = MissingKanjiFrequencyRange(1, 5_000, includeUnranked = true),
            nowMillis = 1_784_764_800_000L,
        )

        assertEquals(
            "kani-missing-kanji-1-5000-with-unranked-2026-07-23.csv",
            prepared?.fileName,
        )
        assertFalse(prepared!!.fileName.contains("private"))
        assertFalse(prepared.fileName.contains("秘"))
    }

    @Test
    fun keepsOnlyEightNewestExportsAndRemovesUnexpectedEntries() {
        exportDirectory.mkdirs()
        val unexpected = File(exportDirectory, "private-copy.txt").apply { writeText("secret") }
        var firstUri: Uri? = null
        repeat(9) { index ->
            val prepared = MissingKanjiExportShare.prepare(
                context = context,
                candidates = listOf(candidate("語", "language $index", 301)),
                range = MissingKanjiFrequencyRange.TOP_1000,
                nowMillis = 1_784_764_800_000L + index,
            )!!
            val uri = IntentCompat.getParcelableExtra(
                prepared.intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            if (index == 0) {
                firstUri = uri
                exportDirectory.listFiles().orEmpty().single().setLastModified(1L)
            }
        }

        assertFalse(unexpected.exists())
        assertEquals(8, exportDirectory.listFiles { file -> file.extension == "csv" }.orEmpty().size)
        assertTrue(runCatching { readUri(firstUri!!) }.isFailure)
    }

    @Test
    fun rejectsEmptyPayloadAndSymlinkedExportDirectory() {
        assertNull(
            MissingKanjiExportShare.prepare(
                context,
                emptyList(),
                MissingKanjiFrequencyRange.TOP_1000,
            ),
        )
        exportDirectory.deleteRecursively()
        val outside = File(context.cacheDir, "outside-missing-kanji").apply { mkdirs() }
        Files.createSymbolicLink(exportDirectory.toPath(), outside.toPath())
        try {
            assertNull(
                MissingKanjiExportShare.prepare(
                    context,
                    listOf(candidate("語", "language", 301)),
                    MissingKanjiFrequencyRange.TOP_1000,
                ),
            )
        } finally {
            Files.deleteIfExists(exportDirectory.toPath())
            outside.deleteRecursively()
        }
    }

    @Test
    fun providerCannotAddressOtherCacheFiles() {
        val privateFile = File(context.cacheDir, "private-settings.db").apply {
            writeText("private")
        }

        val result = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.missingkanji",
                privateFile,
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun readUri(uri: Uri): String =
        context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }

    private fun candidate(
        literal: String,
        meaning: String,
        rank: Int?,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = listOf(meaning),
        onReadings = listOf("ゴ"),
        kunReadings = listOf("かたる"),
        jitenRank = rank,
    )
}
