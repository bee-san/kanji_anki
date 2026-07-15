package dev.bee.kanjianki.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniVersioningTest {
    @Test
    fun parsesTagAndCalculatesCanonicalAndroidCode() {
        val version = KaniVersioning.parse("v0.4.193")

        assertEquals("0.4.193", version.versionName)
        assertEquals(4_193, version.versionCode)
    }

    @Test
    fun supportsAllThreeComponentsInVersionCode() {
        val version = KaniVersioning.parse("12.34.56")

        assertEquals(12_034_056, version.versionCode)
    }

    @Test
    fun rejectsMalformedVersionsAndOversizedComponents() {
        listOf("1.2", "1.2.3.4", "release-v1.2.3", "1.-2.3", "v0.04.194").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { KaniVersioning.parse(value) }
        }
        listOf("1000.0.1", "1.1000.1", "1.0.1000").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { KaniVersioning.parse(value) }
        }
    }

    @Test
    fun validatesAndroidVersionCodeRange() {
        assertEquals(ANDROID_MAX_VERSION_CODE, KaniVersioning.parseVersionCode("2100000000"))
        assertThrows(IllegalArgumentException::class.java) { KaniVersioning.parseVersionCode("0") }
        assertThrows(IllegalArgumentException::class.java) { KaniVersioning.parseVersionCode("2100000001") }
        assertThrows(IllegalArgumentException::class.java) { KaniVersioning.parseVersionCode("not-a-number") }
    }

    @Test
    fun releaseTagIsAuthoritativeAndConsistentOverridesRemainSupported() {
        val resolved = KaniVersioning.resolve(
            releaseTag = "v0.4.193",
            versionNameOverride = "0.4.193",
            versionCodeOverride = "4193",
            latestReachableGitTag = { error("Git must not be queried for explicit release metadata") },
            fallbackVersionName = "0.4.33",
            fallbackVersionCode = "4033",
        )

        assertEquals(KaniVersionSource.RELEASE_TAG, resolved.source)
        assertEquals("0.4.193", resolved.version.versionName)
        assertEquals(4_193, resolved.version.versionCode)
    }

    @Test
    fun derivesCodeFromVersionNameOverride() {
        val resolved = KaniVersioning.resolve(
            releaseTag = null,
            versionNameOverride = "2.7.9",
            versionCodeOverride = null,
            latestReachableGitTag = { error("Git must not be queried for a name override") },
            fallbackVersionName = "0.4.33",
            fallbackVersionCode = "4033",
        )

        assertEquals(KaniVersionSource.OVERRIDE, resolved.source)
        assertEquals(2_007_009, resolved.version.versionCode)
    }

    @Test
    fun usesLatestReachableGitTagBeforeCatalogFallback() {
        val resolved = KaniVersioning.resolve(
            releaseTag = null,
            versionNameOverride = null,
            versionCodeOverride = null,
            latestReachableGitTag = { "v0.4.193" },
            fallbackVersionName = "0.4.33",
            fallbackVersionCode = "4033",
        )

        assertEquals(KaniVersionSource.GIT_TAG, resolved.source)
        assertEquals("0.4.193", resolved.version.versionName)
        assertEquals(4_193, resolved.version.versionCode)
    }

    @Test
    fun usesAndChecksCatalogFallbackOutsideGitCheckout() {
        val resolved = KaniVersioning.resolve(
            releaseTag = null,
            versionNameOverride = null,
            versionCodeOverride = null,
            latestReachableGitTag = { null },
            fallbackVersionName = "0.4.33",
            fallbackVersionCode = "4033",
        )

        assertEquals(KaniVersionSource.CATALOG_FALLBACK, resolved.source)
        assertEquals(4_033, resolved.version.versionCode)
    }

    @Test
    fun rejectsConflictingNamesAndCodes() {
        assertThrows(IllegalArgumentException::class.java) {
            KaniVersioning.resolve(
                releaseTag = "v0.4.193",
                versionNameOverride = "0.4.192",
                versionCodeOverride = null,
                latestReachableGitTag = { null },
                fallbackVersionName = "0.4.33",
                fallbackVersionCode = "4033",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            KaniVersioning.resolve(
                releaseTag = null,
                versionNameOverride = "0.4.193",
                versionCodeOverride = "4192",
                latestReachableGitTag = { null },
                fallbackVersionName = "0.4.33",
                fallbackVersionCode = "4033",
            )
        }
    }

    @Test
    fun signingPolicyTargetsArtifactsWithoutBlockingDebugOrAnalysis() {
        assertTrue(KaniReleaseTaskPolicy.requiresSigning("packageRelease"))
        assertTrue(KaniReleaseTaskPolicy.requiresSigning("packageReleaseBundle"))
        assertTrue(KaniReleaseTaskPolicy.requiresSigning("packageReleaseUniversalApk"))
        assertTrue(KaniReleaseTaskPolicy.requiresSigning("signReleaseBundle"))
        assertFalse(KaniReleaseTaskPolicy.requiresSigning("packageDebug"))
        assertFalse(KaniReleaseTaskPolicy.requiresSigning("packageMinifiedSmoke"))
        assertFalse(KaniReleaseTaskPolicy.requiresSigning("packageReleaseResources"))
        assertFalse(KaniReleaseTaskPolicy.requiresSigning("compileReleaseKotlin"))
        assertFalse(KaniReleaseTaskPolicy.requiresSigning("lintRelease"))
    }
}
