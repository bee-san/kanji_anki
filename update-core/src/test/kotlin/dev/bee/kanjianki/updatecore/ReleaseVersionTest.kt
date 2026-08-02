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
    fun comparesBetaVersionsUsingSemverPrereleasePrecedence() {
        assertTrue(ReleaseVersion.isNewerSemver("0.5.11", "v0.5.12-beta"))
        assertTrue(ReleaseVersion.isNewerSemver("0.5.12-beta", "v0.5.13-beta"))
        assertTrue(ReleaseVersion.isNewerSemver("0.5.12-beta", "v0.5.12"))
        assertFalse(ReleaseVersion.isNewerSemver("0.5.12-beta", "v0.5.12-beta"))
        assertFalse(ReleaseVersion.isNewerSemver("0.5.12", "v0.5.12-beta"))
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
    fun comparesNumericComponentsWithoutIntegerOverflow() {
        assertTrue(ReleaseVersion.isNewerSemver("2147483647.0.0", "v2147483648.0.0"))
        assertTrue(
            ReleaseVersion.isNewerSemver(
                "999999999999999999999.9.9",
                "v1000000000000000000000.0.0",
            ),
        )
        assertFalse(
            ReleaseVersion.isNewerSemver(
                "1000000000000000000000.0.0",
                "v999999999999999999999.9.9",
            ),
        )
        assertFalse(ReleaseVersion.isNewerSemver("00000000001.02.003", "v1.2.3"))
    }

    @Test
    fun validSemverRecognisesReleaseTags() {
        assertTrue(ReleaseVersion.isValidSemver("0.4.204"))
        assertTrue(ReleaseVersion.isValidSemver("v0.4.204"))
        assertTrue(ReleaseVersion.isValidSemver("1.0.0"))
        assertTrue(ReleaseVersion.isValidSemver("v999999999999999999999.0.0"))
        assertTrue(ReleaseVersion.isValidSemver("0.5.12-beta"))
        assertTrue(ReleaseVersion.isValidSemver("v0.5.12-beta"))
    }

    @Test
    fun validSemverRejectsUnusableTags() {
        // A captive-portal / interception read yields no usable tag_name.
        assertFalse(ReleaseVersion.isValidSemver(null))
        assertFalse(ReleaseVersion.isValidSemver(""))
        assertFalse(ReleaseVersion.isValidSemver("not-a-version"))
        assertFalse(ReleaseVersion.isValidSemver("0.4"))
        assertFalse(ReleaseVersion.isValidSemver("v0.5.12_beta"))
        assertFalse(ReleaseVersion.isValidSemver("v0.5.12-alpha"))
        assertFalse(ReleaseVersion.isValidSemver("v0.5.12-beta.1"))
        assertFalse(ReleaseVersion.isValidSemver("<!DOCTYPE html>"))
    }
}
