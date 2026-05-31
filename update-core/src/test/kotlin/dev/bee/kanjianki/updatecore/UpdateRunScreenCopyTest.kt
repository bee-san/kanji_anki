package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateRunScreenCopyTest {
    @Test
    fun cachedPendingRunPreservesInstallerCopy() {
        val copy = UpdateRunScreenCopy.forRun(true)

        assertEquals("Starting installer", copy.title())
        assertEquals("Using the verified APK already cached by Kani.", copy.body())
        assertEquals("Preparing verified APK", copy.progressLabel())
    }

    @Test
    fun manualRunPreservesReleaseCheckCopy() {
        val copy = UpdateRunScreenCopy.forRun(false)

        assertEquals("Checking release", copy.title())
        assertEquals("Downloading metadata and verifying assets.", copy.body())
        assertEquals("Checking GitHub Releases", copy.progressLabel())
    }
}
