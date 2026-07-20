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

    @Test
    fun validSemverRecognisesReleaseTags() {
        assertTrue(ReleaseVersion.isValidSemver("0.4.204"))
        assertTrue(ReleaseVersion.isValidSemver("v0.4.204"))
        assertTrue(ReleaseVersion.isValidSemver("1.0.0"))
    }

    @Test
    fun validSemverRejectsUnusableTags() {
        // A captive-portal / interception read yields no usable tag_name.
        assertFalse(ReleaseVersion.isValidSemver(null))
        assertFalse(ReleaseVersion.isValidSemver(""))
        assertFalse(ReleaseVersion.isValidSemver("not-a-version"))
        assertFalse(ReleaseVersion.isValidSemver("0.4"))
        assertFalse(ReleaseVersion.isValidSemver("<!DOCTYPE html>"))
    }
}
