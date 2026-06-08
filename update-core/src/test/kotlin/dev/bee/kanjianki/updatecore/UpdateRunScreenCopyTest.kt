package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateRunScreenCopyTest {
    @Test
    fun cachedPendingRunPreservesInstallerCopy() {
        val copy = UpdateRunScreenCopy.forRun(true)

        assertEquals("Preparing installer", copy.title())
        assertEquals("Verifying APK", copy.progressLabel())
    }

    @Test
    fun manualRunPreservesReleaseCheckCopy() {
        val copy = UpdateRunScreenCopy.forRun(false)

        assertEquals("Checking for updates", copy.title())
        assertEquals("Checking releases", copy.progressLabel())
    }
}
