package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun comparesStrictSemverTags() {
        assertTrue(ReleaseVersion.isNewerSemver("0.3.0", "v0.3.1"))
        assertTrue(ReleaseVersion.isNewerSemver("0.3.9", "v0.4.0"))
        assertTrue(ReleaseVersion.isNewerSemver("v0.9.9", "v1.0.0"))
        assertFalse(ReleaseVersion.isNewerSemver("0.3.1", "v0.3.1"))
        assertFalse(ReleaseVersion.isNewerSemver("0.4.0", "v0.3.9"))
    }

    @Test
    fun invalidVersionsCompareAsNotNewer() {
        assertFalse(ReleaseVersion.isNewerSemver(null, null))
        assertFalse(ReleaseVersion.isNewerSemver("not-a-version", "also-bad"))
        assertFalse(ReleaseVersion.isNewerSemver("0.3.1", "0.3"))
    }

    @Test
    fun malformedCurrentVersionPreservesLegacyZeroFallback() {
        assertTrue(ReleaseVersion.isNewerSemver("not-a-version", "v0.0.1"))
    }
}
