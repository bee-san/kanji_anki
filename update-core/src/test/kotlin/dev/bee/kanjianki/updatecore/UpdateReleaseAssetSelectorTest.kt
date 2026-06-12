package dev.bee.kanjianki.updatecore

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseAssetSelectorTest {
    @Test
    fun selectAssetsRejectsEmptyReleaseMetadata() {
        withLocale(Locale.ENGLISH) {
            val selection = UpdateReleaseAssetSelector.selectAssets(null)

            assertFalse(selection.ok())
            assertEquals("Latest release metadata is empty.", selection.message())
            assertEquals("Latest release metadata is empty.", UpdateReleaseAssetSelector.EMPTY_METADATA_MESSAGE)
            assertEquals("Latest release metadata is empty.", UpdateReleaseAssetSelector.emptyMetadataMessage())
        }
    }

    @Test
    fun selectAssetsRejectsReleaseWithoutApkAsset() {
        withLocale(Locale.ENGLISH) {
            val release = GitHubReleaseMetadata(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                listOf(asset("kani-android-0.4.3.apk.sha256", "https://example/sha")),
            )

            val selection = UpdateReleaseAssetSelector.selectAssets(release)

            assertFalse(selection.ok())
            assertEquals("Latest release has no APK asset.", selection.message())
            assertEquals("Latest release has no APK asset.", UpdateReleaseAssetSelector.missingApkMessage())
        }
    }

    @Test
    fun selectAssetsRejectsReleaseWithoutMatchingChecksumAsset() {
        withLocale(Locale.ENGLISH) {
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
            assertEquals(
                "Latest release has no SHA-256 checksum asset.",
                UpdateReleaseAssetSelector.missingChecksumMessage(),
            )
        }
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
        assertSame(apk, selection.apk())
        assertSame(checksum, selection.checksum())
    }

    @Test
    fun failureMessagesTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val missingMetadata = UpdateReleaseAssetSelector.selectAssets(null)
            val missingApk = UpdateReleaseAssetSelector.selectAssets(
                GitHubReleaseMetadata(
                    "v0.4.3",
                    "https://example/releases/v0.4.3",
                    listOf(asset("kani-android-0.4.3.apk.sha256", "https://example/sha")),
                ),
            )
            val missingChecksum = UpdateReleaseAssetSelector.selectAssets(
                GitHubReleaseMetadata(
                    "v0.4.3",
                    "https://example/releases/v0.4.3",
                    listOf(asset("kani-android-0.4.3.apk", "https://example/apk")),
                ),
            )

            assertFalse(missingMetadata.ok())
            assertEquals("最新リリースのメタデータが空です。", missingMetadata.message())
            assertEquals("最新リリースのメタデータが空です。", UpdateReleaseAssetSelector.emptyMetadataMessage())
            assertFalse(missingApk.ok())
            assertEquals("最新リリースにAPKアセットがありません。", missingApk.message())
            assertEquals("最新リリースにAPKアセットがありません。", UpdateReleaseAssetSelector.missingApkMessage())
            assertFalse(missingChecksum.ok())
            assertEquals("最新リリースにSHA-256チェックサムのアセットがありません。", missingChecksum.message())
            assertEquals(
                "最新リリースにSHA-256チェックサムのアセットがありません。",
                UpdateReleaseAssetSelector.missingChecksumMessage(),
            )
        }
    }

    private fun asset(name: String, url: String): GitHubReleaseMetadata.ReleaseAsset =
        GitHubReleaseMetadata.ReleaseAsset(name, url)

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
