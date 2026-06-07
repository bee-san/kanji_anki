package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateRunScreenCopyTest {
    @Test
    fun cachedPendingRunPreservesInstallerCopy() {
        val copy = UpdateRunScreenCopy.forRun(true)

        assertEquals("Starting installer", copy.title())
        assertEquals("Preparing verified APK", copy.progressLabel())
    }

    @Test
    fun manualRunPreservesReleaseCheckCopy() {
        val copy = UpdateRunScreenCopy.forRun(false)

        assertEquals("Checking release", copy.title())
        assertEquals("Checking GitHub Releases", copy.progressLabel())
    }
}
