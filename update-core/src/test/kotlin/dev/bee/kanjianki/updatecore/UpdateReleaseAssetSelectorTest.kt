package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseAssetSelectorTest {
    @Test
    fun selectAssetsRejectsEmptyReleaseMetadata() {
        val selection = UpdateReleaseAssetSelector.selectAssets(null)

        assertFalse(selection.ok())
        assertEquals("Latest release metadata is empty.", selection.message())
    }

    @Test
    fun selectAssetsRejectsReleaseWithoutApkAsset() {
        val release = GitHubReleaseMetadata(
            "v0.4.3",
            "https://example/releases/v0.4.3",
            listOf(asset("kani-android-0.4.3.apk.sha256", "https://example/sha")),
        )

        val selection = UpdateReleaseAssetSelector.selectAssets(release)

        assertFalse(selection.ok())
        assertEquals("Latest release has no APK asset.", selection.message())
    }

    @Test
    fun selectAssetsRejectsReleaseWithoutMatchingChecksumAsset() {
        val release = GitHubReleaseMetadata(
            "v0.4.3",
            "https://example/releases/v0.4.3",
            listOf(
                asset("kani-android-0.4.3.apk", "https://example/apk"),
                asset("other.apk.sha256", "https://example/sha"),
            ),
        )

        val selection = UpdateReleaseAssetSelector.selectAssets(release)

        assertFalse(selection.ok())
        assertEquals("Latest release has no SHA-256 checksum asset.", selection.message())
    }

    @Test
    fun selectAssetsPairsApkWithItsExactChecksumAsset() {
        val apk = asset("kani-android-0.4.4.apk", "https://example/apk")
        val otherChecksum = asset("other.apk.sha256", "https://example/other-sha")
        val checksum = asset("kani-android-0.4.4.apk.sha256", "https://example/sha")
        val release = GitHubReleaseMetadata(
            "v0.4.4",
            "https://example/releases/v0.4.4",
            listOf(otherChecksum, apk, checksum),
        )

        val selection = UpdateReleaseAssetSelector.selectAssets(release)

        assertTrue(selection.ok())
        assertTrue(selection.ok)
        assertSame(apk, selection.apk())
        assertSame(apk, selection.apk)
        assertSame(checksum, selection.checksum())
        assertSame(checksum, selection.checksum)
    }

    private fun asset(name: String, url: String): GitHubReleaseMetadata.ReleaseAsset {
        return GitHubReleaseMetadata.ReleaseAsset(name, url)
    }
}
