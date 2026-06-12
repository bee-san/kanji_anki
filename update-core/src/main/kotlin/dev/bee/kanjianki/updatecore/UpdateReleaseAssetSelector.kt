package dev.bee.kanjianki.updatecore

import java.util.Locale

object UpdateReleaseAssetSelector {
    private const val JAPANESE_LANGUAGE = "ja"

    const val EMPTY_METADATA_MESSAGE = "Latest release metadata is empty."
    const val MISSING_APK_MESSAGE = "Latest release has no APK asset."
    const val MISSING_CHECKSUM_MESSAGE = "Latest release has no SHA-256 checksum asset."

    @JvmStatic
    fun selectAssets(release: GitHubReleaseMetadata?): AssetSelection {
        if (release == null) {
            return AssetSelection.failure(emptyMetadataMessage())
        }
        val apk = apkAsset(release)
            ?: return AssetSelection.failure(missingApkMessage())
        val checksum = checksumAssetFor(release, apk.name())
            ?: return AssetSelection.failure(missingChecksumMessage())
        return AssetSelection.success(apk, checksum)
    }

    @JvmStatic
    fun emptyMetadataMessage(): String = localizedText(
        EMPTY_METADATA_MESSAGE,
        "最新リリースのメタデータが空です。",
    )

    @JvmStatic
    fun missingApkMessage(): String = localizedText(
        MISSING_APK_MESSAGE,
        "最新リリースにAPKアセットがありません。",
    )

    @JvmStatic
    fun missingChecksumMessage(): String = localizedText(
        MISSING_CHECKSUM_MESSAGE,
        "最新リリースにSHA-256チェックサムのアセットがありません。",
    )

    private fun apkAsset(release: GitHubReleaseMetadata): GitHubReleaseMetadata.ReleaseAsset? {
        return release.assets().firstOrNull { asset -> asset.name().endsWith(".apk") }
    }

    private fun checksumAssetFor(
        release: GitHubReleaseMetadata,
        apkName: String,
    ): GitHubReleaseMetadata.ReleaseAsset? {
        return release.assets().firstOrNull { asset -> asset.name() == "$apkName.sha256" }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english

    class AssetSelection private constructor(
        @JvmField val ok: Boolean,
        @JvmField val message: String,
        @JvmField val apk: GitHubReleaseMetadata.ReleaseAsset?,
        @JvmField val checksum: GitHubReleaseMetadata.ReleaseAsset?,
    ) {
        fun ok(): Boolean = ok
        fun message(): String = message
        fun apk(): GitHubReleaseMetadata.ReleaseAsset = requireNotNull(apk)
        fun checksum(): GitHubReleaseMetadata.ReleaseAsset = requireNotNull(checksum)

        companion object {
            fun success(
                apk: GitHubReleaseMetadata.ReleaseAsset,
                checksum: GitHubReleaseMetadata.ReleaseAsset,
            ): AssetSelection {
                return AssetSelection(true, "", apk, checksum)
            }

            fun failure(message: String): AssetSelection {
                return AssetSelection(false, message, null, null)
            }
        }
    }
}
