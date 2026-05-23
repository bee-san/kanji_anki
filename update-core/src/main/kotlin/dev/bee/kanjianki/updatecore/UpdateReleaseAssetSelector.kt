package dev.bee.kanjianki.updatecore

object UpdateReleaseAssetSelector {
    @JvmStatic
    fun selectAssets(release: GitHubReleaseMetadata?): AssetSelection {
        if (release == null) {
            return AssetSelection.failure("Latest release metadata is empty.")
        }
        val apk = apkAsset(release)
            ?: return AssetSelection.failure("Latest release has no APK asset.")
        val checksum = checksumAssetFor(release, apk.name())
            ?: return AssetSelection.failure("Latest release has no SHA-256 checksum asset.")
        return AssetSelection.success(apk, checksum)
    }

    private fun apkAsset(release: GitHubReleaseMetadata): GitHubReleaseMetadata.ReleaseAsset? {
        return release.assets().firstOrNull { asset -> asset.name().endsWith(".apk") }
    }

    private fun checksumAssetFor(
        release: GitHubReleaseMetadata,
        apkName: String,
    ): GitHubReleaseMetadata.ReleaseAsset? {
        return release.assets().firstOrNull { asset -> asset.name() == "$apkName.sha256" }
    }

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
