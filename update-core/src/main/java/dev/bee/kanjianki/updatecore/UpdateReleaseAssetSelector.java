package dev.bee.kanjianki.updatecore;

import java.util.Objects;

public final class UpdateReleaseAssetSelector {
    private UpdateReleaseAssetSelector() {
    }

    public static AssetSelection selectAssets(GitHubReleaseMetadata release) {
        if (release == null) {
            return AssetSelection.failure("Latest release metadata is empty.");
        }
        GitHubReleaseMetadata.ReleaseAsset apk = apkAsset(release);
        if (apk == null) {
            return AssetSelection.failure("Latest release has no APK asset.");
        }
        GitHubReleaseMetadata.ReleaseAsset checksum = checksumAssetFor(release, apk.name());
        if (checksum == null) {
            return AssetSelection.failure("Latest release has no SHA-256 checksum asset.");
        }
        return AssetSelection.success(apk, checksum);
    }

    private static GitHubReleaseMetadata.ReleaseAsset apkAsset(GitHubReleaseMetadata release) {
        for (GitHubReleaseMetadata.ReleaseAsset asset : release.assets()) {
            if (asset.name().endsWith(".apk")) {
                return asset;
            }
        }
        return null;
    }

    private static GitHubReleaseMetadata.ReleaseAsset checksumAssetFor(GitHubReleaseMetadata release, String apkName) {
        for (GitHubReleaseMetadata.ReleaseAsset asset : release.assets()) {
            if (Objects.equals(asset.name(), apkName + ".sha256")) {
                return asset;
            }
        }
        return null;
    }

    public static final class AssetSelection {
        private final boolean ok;
        private final String message;
        private final GitHubReleaseMetadata.ReleaseAsset apk;
        private final GitHubReleaseMetadata.ReleaseAsset checksum;

        private AssetSelection(
                boolean ok,
                String message,
                GitHubReleaseMetadata.ReleaseAsset apk,
                GitHubReleaseMetadata.ReleaseAsset checksum
        ) {
            this.ok = ok;
            this.message = message;
            this.apk = apk;
            this.checksum = checksum;
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message;
        }

        public GitHubReleaseMetadata.ReleaseAsset apk() {
            return apk;
        }

        public GitHubReleaseMetadata.ReleaseAsset checksum() {
            return checksum;
        }

        private static AssetSelection success(
                GitHubReleaseMetadata.ReleaseAsset apk,
                GitHubReleaseMetadata.ReleaseAsset checksum
        ) {
            return new AssetSelection(true, "", apk, checksum);
        }

        private static AssetSelection failure(String message) {
            return new AssetSelection(false, message, null, null);
        }
    }
}
