package dev.bee.kanjianki.update

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ApkContentProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var updatesDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        if (::updatesDir.isInitialized) {
            updatesDir.deleteRecursively()
        }
        if (::context.isInitialized) {
            File(context.cacheDir, "outside.apk").delete()
        }
    }

    @Test
    fun opensCachedApkReadOnly() {
        val apk = File(updatesDir, "kani-test.apk")
        FileOutputStream(apk).use { output ->
            output.write(byteArrayOf(1, 2, 3))
        }

        requireNotNull(
            context.contentResolver.openFileDescriptor(
                ApkContentProvider.uriFor(context, apk.name),
                "r",
            ),
        ).use { descriptor ->
            assertEquals(3L, descriptor.statSize)
        }
    }

    @Test
    fun providerReportsApkMimeTypeAndNoMutableOperations() {
        val uri = ApkContentProvider.uriFor(context, "kani-test.apk")

        assertEquals("application/vnd.android.package-archive", context.contentResolver.getType(uri))
        assertNull(context.contentResolver.query(uri, null, null, null, null))
        assertNull(context.contentResolver.insert(uri, ContentValues()))
        assertEquals(0, context.contentResolver.delete(uri, null, null))
        assertEquals(0, context.contentResolver.update(uri, ContentValues(), null, null))
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsWriteMode() {
        context.contentResolver.openFileDescriptor(ApkContentProvider.uriFor(context, "kani-test.apk"), "w")
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsMissingCachedApk() {
        context.contentResolver.openFileDescriptor(ApkContentProvider.uriFor(context, "missing.apk"), "r")
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsOpenWhenProviderHasNoAttachedContext() {
        ApkContentProvider().openFile(Uri.parse("content://dev.bee.kanjianki.apk/kani.apk"), "r")
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsUriWithoutFileName() {
        context.contentResolver.openFileDescriptor(Uri.parse("content://${context.packageName}.apk"), "r")
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsDirectoryInsideUpdatesCache() {
        val directory = File(updatesDir, "nested")
        directory.mkdirs()

        context.contentResolver.openFileDescriptor(ApkContentProvider.uriFor(context, directory.name), "r")
    }

    @Test(expected = FileNotFoundException::class)
    fun rejectsPathTraversalOutsideUpdatesDirectory() {
        val outside = File(context.cacheDir, "outside.apk")
        FileOutputStream(outside).use { output ->
            output.write(byteArrayOf(4, 5, 6))
        }
        val traversal = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.apk")
            .encodedPath("%2E%2E%2Foutside.apk")
            .build()

        context.contentResolver.openFileDescriptor(traversal, "r")
    }
}
