package dev.bee.kanjianki.update

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApkContentProviderTest {
    @Test
    fun contentResolverReturnsApkMimeType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apk = createCachedApk(context)

        val uri = ApkContentProvider.uriFor(context, apk.name)
        assertEquals("application/vnd.android.package-archive", context.contentResolver.getType(uri))
    }

    @Test
    fun contentResolverOpensCachedApkReadOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apk = createCachedApk(context)

        val uri = ApkContentProvider.uriFor(context, apk.name)
        context.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
            assertNotNull(descriptor)
        }
    }

    @Test(expected = FileNotFoundException::class)
    fun contentResolverRejectsWriteMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.contentResolver.openFileDescriptor(ApkContentProvider.uriFor(context, "kani-test.apk"), "w")
    }

    @Test
    fun providerDoesNotSupportQueries() {
        val provider = ApkContentProvider()

        assertNull(provider.query(Uri.EMPTY, null, null, null, null))
    }

    @Test
    fun providerDoesNotSupportInserts() {
        val provider = ApkContentProvider()

        assertNull(provider.insert(Uri.EMPTY, null))
    }

    @Test
    fun providerDoesNotSupportDeletes() {
        val provider = ApkContentProvider()

        assertEquals(0, provider.delete(Uri.EMPTY, null, null))
    }

    @Test
    fun providerDoesNotSupportUpdates() {
        val provider = ApkContentProvider()

        assertEquals(0, provider.update(Uri.EMPTY, null, null, null))
    }

    private fun createCachedApk(context: Context): File {
        val apk = File(File(context.cacheDir, "updates"), "kani-test.apk")
        val parent = apk.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw IOException("Could not create update cache directory")
        }
        FileOutputStream(apk).use { output ->
            output.write(byteArrayOf(1, 2, 3))
        }
        return apk
    }
}
