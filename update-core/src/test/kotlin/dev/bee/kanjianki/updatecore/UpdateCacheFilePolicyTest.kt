package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateCacheFilePolicyTest {
    @Test
    fun safeFileNameStripsPathTraversal() {
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../release/kani-0.4.3.apk"))
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../../kani-0.4.3.apk"))
    }

    @Test
    fun safeFileNameDefaultsMissingNames() {
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(""))
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(null))
    }

    @Test
    fun staleCachedApksKeepsPendingAndFreshFiles() {
        val dir = java.nio.file.Files.createTempDirectory("kani-cache-policy").toFile()
        val now = 3_000_000_000L
        val pending = touch(File(dir, "pending.apk"), now - TimeUnit.DAYS.toMillis(30))
        val fresh = touch(File(dir, "fresh.apk"), now - TimeUnit.HOURS.toMillis(1))
        val stale = touch(File(dir, "stale.apk"), now - TimeUnit.DAYS.toMillis(30))
        touch(File(dir, "notes.txt"), now - TimeUnit.DAYS.toMillis(30))

        val staleApks = UpdateCacheFilePolicy.staleCachedApks(dir, "../pending.apk", now)

        assertEquals(1, staleApks.size)
        assertEquals(stale.canonicalFile, staleApks[0].canonicalFile)
        assertTrue(pending.isFile)
        assertTrue(fresh.isFile)
    }

    private fun touch(file: File, lastModified: Long): File {
        assertTrue(file.createNewFile())
        assertTrue(file.setLastModified(lastModified))
        return file
    }
}
