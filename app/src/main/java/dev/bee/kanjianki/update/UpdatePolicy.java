package dev.bee.kanjianki.update;

import dev.bee.kanjianki.updatecore.GitHubReleaseMetadata;
import dev.bee.kanjianki.updatecore.PackageInstallStatusPolicy;
import dev.bee.kanjianki.updatecore.UpdateArtifactValidator;
import dev.bee.kanjianki.updatecore.UpdateReleaseAssetSelector;

final class UpdatePolicy {
    static final int STATUS_SUCCESS = PackageInstallStatusPolicy.STATUS_SUCCESS;
    static final int STATUS_PENDING_USER_ACTION = PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION;

    private UpdatePolicy() {
    }

    static AssetSelection selectAssets(GitHubReleaseMetadata release) {
        UpdateReleaseAssetSelector.AssetSelection selection = UpdateReleaseAssetSelector.selectAssets(release);
        return selection.ok()
                ? AssetSelection.success(selection.apk(), selection.checksum())
                : AssetSelection.failure(selection.message());
    }

    static ValidationResult validateChecksum(String expected, String actual) {
        return validationResult(UpdateArtifactValidator.validateChecksum(expected, actual));
    }

    static ValidationResult validateExpectedChecksum(String expected) {
        return validationResult(UpdateArtifactValidator.validateExpectedChecksum(expected));
    }

    static ValidationResult validatePackageMetadata(
            String expectedPackageName,
            String currentVersion,
            String releaseTag,
            String archivePackageName,
            String archiveVersion
    ) {
        return validationResult(UpdateArtifactValidator.validatePackageMetadata(
                expectedPackageName,
                currentVersion,
                releaseTag,
                archivePackageName,
                archiveVersion
        ));
    }

    static InstallCallback mapInstallStatus(int status, String message) {
        PackageInstallStatusPolicy.InstallCallback mapped = PackageInstallStatusPolicy.mapInstallStatus(status, message);
        return new InstallCallback(mapped.pendingUserAction(), mapped.success(), mapped.message());
    }

    static boolean shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource source) {
        return source == GitHubUpdater.UpdateSource.MANUAL || source == GitHubUpdater.UpdateSource.CACHED;
    }

    private static ValidationResult validationResult(UpdateArtifactValidator.ValidationResult result) {
        return result.ok() ? ValidationResult.success(result.message()) : ValidationResult.failure(result.message());
    }

    static final class AssetSelection {
        final boolean ok;
        final String message;
        final GitHubReleaseMetadata.ReleaseAsset apk;
        final GitHubReleaseMetadata.ReleaseAsset checksum;

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

    static final class ValidationResult {
        final boolean ok;
        final String message;

        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        private static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }

        private static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }

    static final class InstallCallback {
        final boolean pendingUserAction;
        final boolean success;
        final String message;

        private InstallCallback(boolean pendingUserAction, boolean success, String message) {
            this.pendingUserAction = pendingUserAction;
            this.success = success;
            this.message = message;
        }
    }
}
