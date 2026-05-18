package dev.bee.kanjianki.update;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.updatecore.UpdateArtifactValidator;

final class UpdatePolicy {
    static final int STATUS_SUCCESS = 0;
    static final int STATUS_PENDING_USER_ACTION = -1;

    private UpdatePolicy() {
    }

    static AssetSelection selectAssets(RecordsSchedulerModels.ReleaseInfo release) {
        if (release == null) {
            return AssetSelection.failure("Latest release metadata is empty.");
        }
        RecordsSchedulerModels.ReleaseAsset apk = release.apkAsset();
        if (apk == null) {
            return AssetSelection.failure("Latest release has no APK asset.");
        }
        RecordsSchedulerModels.ReleaseAsset checksum = release.checksumAssetFor(apk.name);
        if (checksum == null) {
            return AssetSelection.failure("Latest release has no SHA-256 checksum asset.");
        }
        return AssetSelection.success(apk, checksum);
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
        if (status == STATUS_SUCCESS) {
            return new InstallCallback(false, true, "Install finished.");
        }
        if (status == STATUS_PENDING_USER_ACTION) {
            return new InstallCallback(true, false, "Android needs confirmation to finish installing.");
        }
        String suffix = message == null || message.trim().isEmpty() ? "" : ": " + message.trim();
        return new InstallCallback(false, false, "Install failed" + suffix + ".");
    }

    static boolean shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource source) {
        return source == GitHubUpdater.UpdateSource.MANUAL || source == GitHubUpdater.UpdateSource.CACHED;
    }

    private static ValidationResult validationResult(UpdateArtifactValidator.ValidationResult result) {
        return result.ok ? ValidationResult.success(result.message) : ValidationResult.failure(result.message);
    }

    static final class AssetSelection {
        final boolean ok;
        final String message;
        final RecordsSchedulerModels.ReleaseAsset apk;
        final RecordsSchedulerModels.ReleaseAsset checksum;

        private AssetSelection(boolean ok, String message, RecordsSchedulerModels.ReleaseAsset apk, RecordsSchedulerModels.ReleaseAsset checksum) {
            this.ok = ok;
            this.message = message;
            this.apk = apk;
            this.checksum = checksum;
        }

        private static AssetSelection success(RecordsSchedulerModels.ReleaseAsset apk, RecordsSchedulerModels.ReleaseAsset checksum) {
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
