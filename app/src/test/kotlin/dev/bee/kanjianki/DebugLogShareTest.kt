package dev.bee.kanjianki

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugLogShareTest {
    private lateinit var context: Context
    private lateinit var shareDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDebugLog.resetForTests()
        File(context.filesDir, "kani-debug.log").delete()
        shareDirectory = File(context.cacheDir, "debug-log-share")
        shareDirectory.deleteRecursively()
        clearFileProviderPathStrategyCache()
    }

    @After
    fun tearDown() {
        AppDebugLog.resetForTests()
        File(context.filesDir, "kani-debug.log").delete()
        File(context.filesDir, "kani-study-debug.log").delete()
        File(context.filesDir, "private-settings.db").delete()
        File(context.cacheDir, "kani-debug.log").delete()
        if (Files.isSymbolicLink(shareDirectory.toPath())) {
            Files.deleteIfExists(shareDirectory.toPath())
        } else {
            shareDirectory.deleteRecursively()
        }
    }

    @Test
    fun allowlistedLogIsCopiedToNarrowShareCache() {
        val source = File(context.filesDir, "kani-debug.log").apply {
            writeText("diagnostic snapshot", Charsets.UTF_8)
        }
        shareDirectory.mkdirs()
        val unexpected = File(shareDirectory, "private-copy.txt").apply { writeText("secret") }

        val intent = DebugLogShare.buildIntent(context, source, "Kani debug log")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent?.action)
        assertEquals("text/plain", intent?.type)
        val uri = intent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
        }
        assertNotNull(uri)
        assertEquals(uri, intent?.clipData?.getItemAt(0)?.uri)
        assertTrue(
            "grants read permission",
            (intent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        assertEquals(0, (intent?.flags ?: 0) and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val sharedText = uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() } }
        assertEquals("diagnostic snapshot", sharedText)
        val snapshots = shareDirectory.listFiles { file -> file.extension == "log" }.orEmpty()
        assertEquals(1, snapshots.size)
        assertEquals("diagnostic snapshot", snapshots.single().readText())
        assertFalse("unexpected cache entries are removed", unexpected.exists())
    }

    @Test
    fun acceptsAllowedFileThroughCanonicalParentAlias() {
        val source = File(context.filesDir, "kani-debug.log").apply {
            writeText("diagnostic through alias", Charsets.UTF_8)
        }
        val filesAlias = File(context.cacheDir, "debug-log-files-alias")
        Files.createSymbolicLink(filesAlias.toPath(), context.filesDir.toPath())

        try {
            val intent = DebugLogShare.buildIntent(
                context,
                File(filesAlias, source.name),
                "Kani debug log",
            )

            assertNotNull(intent)
            val uri = intent?.let {
                IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
            }
            assertEquals("diagnostic through alias", uri?.let(::readUri))
        } finally {
            Files.deleteIfExists(filesAlias.toPath())
        }
    }

    @Test
    fun rejectsAllowlistedNameThroughParentAliasOutsideFilesRoot() {
        val outside = File(context.cacheDir, "outside-debug-log-files").apply { mkdirs() }
        File(outside, "kani-debug.log").writeText("not the live log")
        val outsideAlias = File(context.cacheDir, "outside-debug-log-files-alias")
        Files.createSymbolicLink(outsideAlias.toPath(), outside.toPath())

        try {
            assertNull(
                DebugLogShare.buildIntent(
                    context,
                    File(outsideAlias, "kani-debug.log"),
                    "Not a log",
                ),
            )
        } finally {
            Files.deleteIfExists(outsideAlias.toPath())
            outside.deleteRecursively()
        }
    }

    @Test
    fun sequentialSharesUseImmutableDistinctUris() {
        val source = File(context.filesDir, "kani-debug.log")
        source.writeText("first snapshot")
        val firstIntent = DebugLogShare.buildIntent(context, source, "First")
        val firstUri = firstIntent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
        }

        source.writeText("second snapshot")
        val secondIntent = DebugLogShare.buildIntent(context, source, "Second")
        val secondUri = secondIntent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
        }

        assertNotNull(firstUri)
        assertNotNull(secondUri)
        assertNotEquals(firstUri, secondUri)
        assertEquals("first snapshot", readUri(firstUri!!))
        assertEquals("second snapshot", readUri(secondUri!!))
    }

    @Test
    fun snapshotCacheKeepsOnlyEightNewestUris() {
        val source = File(context.filesDir, "kani-debug.log")
        source.writeText("snapshot 0")
        val firstIntent = DebugLogShare.buildIntent(context, source, "Snapshot 0")
        val firstUri = firstIntent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
        }
        assertNotNull(firstUri)
        assertTrue(shareDirectory.listFiles().orEmpty().single().setLastModified(1L))

        repeat(8) { index ->
            source.writeText("snapshot ${index + 1}")
            assertNotNull(DebugLogShare.buildIntent(context, source, "Snapshot ${index + 1}"))
        }

        assertEquals(8, shareDirectory.listFiles { file -> file.extension == "log" }.orEmpty().size)
        assertTrue("purged snapshot URI no longer resolves", runCatching { readUri(firstUri!!) }.isFailure)
    }

    @Test
    fun failedProviderLookupDoesNotLeakCompletedSnapshot() {
        val source = File(context.filesDir, "kani-debug.log").apply { writeText("diagnostic") }
        val invalidAuthorityContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getPackageName(): String = "${context.packageName}.missing"
        }

        assertNull(DebugLogShare.buildIntent(invalidAuthorityContext, source, "Missing provider"))
        assertTrue(shareDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rejectsPrivateFileWithDifferentName() {
        val privateFile = File(context.filesDir, "private-settings.db").apply { writeText("private") }

        assertNull(DebugLogShare.buildIntent(context, privateFile, "Not a log"))
    }

    @Test
    fun rejectsAllowlistedNameOutsideFilesRoot() {
        val lookalike = File(context.cacheDir, "kani-debug.log").apply { writeText("not the live log") }

        assertNull(DebugLogShare.buildIntent(context, lookalike, "Not the live log"))
    }

    @Test
    fun rejectsMissingAndEmptyLogs() {
        val source = File(context.filesDir, "kani-debug.log")
        assertFalse(source.exists())
        assertNull(DebugLogShare.buildIntent(context, source, "Missing"))

        source.writeText("")
        assertNull(DebugLogShare.buildIntent(context, source, "Empty"))
    }

    @Test
    fun rejectsSymbolicLinkWithAllowlistedName() {
        val privateFile = File(context.filesDir, "private-settings.db").apply { writeText("private") }
        val link = File(context.filesDir, "kani-debug.log")
        Files.createSymbolicLink(link.toPath(), privateFile.toPath())

        assertNull(DebugLogShare.buildIntent(context, link, "Symlink"))
    }

    @Test
    fun rejectsSymbolicLinkShareDirectory() {
        val source = File(context.filesDir, "kani-debug.log").apply { writeText("diagnostic") }
        val outside = File(context.cacheDir, "outside-debug-log-share").apply { mkdirs() }
        Files.createSymbolicLink(shareDirectory.toPath(), outside.toPath())

        assertNull(DebugLogShare.buildIntent(context, source, "Symlink directory"))

        Files.deleteIfExists(shareDirectory.toPath())
        outside.deleteRecursively()
    }

    @Test
    fun providerCannotAddressLivePrivateFilesDirectory() {
        val privateFile = File(context.filesDir, "private-settings.db").apply { writeText("private") }

        val result = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.debuglog", privateFile)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun readUri(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }
}
