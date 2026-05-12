package dev.bee.kanjianki.update;

import dev.bee.kanjianki.core.GitHubReleaseParser;
import dev.bee.kanjianki.core.Records;

import java.util.regex.Pattern;

final class UpdatePolicy {
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("(?i)[0-9a-f]{64}");
    static final int STATUS_SUCCESS = 0;
    static final int STATUS_PENDING_USER_ACTION = -1;

    private UpdatePolicy() {
    }

    static AssetSelection selectAssets(Records.ReleaseInfo release) {
        if (release == null) {
            return AssetSelection.failure("Latest release metadata is empty.");
        }
        Records.ReleaseAsset apk = release.apkAsset();
        if (apk == null) {
            return AssetSelection.failure("Latest release has no APK asset.");
        }
        Records.ReleaseAsset checksum = release.checksumAssetFor(apk.name);
        if (checksum == null) {
            return AssetSelection.failure("Latest release has no SHA-256 checksum asset.");
        }
        return AssetSelection.success(apk, checksum);
    }

    static ValidationResult validateChecksum(String expected, String actual) {
        ValidationResult expectedResult = validateExpectedChecksum(expected);
        if (!expectedResult.ok) {
            return expectedResult;
        }
        String normalizedExpected = expected.trim();
        if (actual == null || !normalizedExpected.equalsIgnoreCase(actual.trim())) {
            return ValidationResult.failure("Checksum mismatch. Install blocked.");
        }
        return ValidationResult.success("Checksum verified.");
    }

    static ValidationResult validateExpectedChecksum(String expected) {
        if (expected == null || expected.trim().isEmpty()) {
            return ValidationResult.failure("Checksum asset does not contain a SHA-256 digest.");
        }
        if (!CHECKSUM_PATTERN.matcher(expected.trim()).matches()) {
            return ValidationResult.failure("Checksum asset does not contain a SHA-256 digest.");
        }
        return ValidationResult.success("Checksum digest found.");
    }

    static ValidationResult validatePackageMetadata(
            String expectedPackageName,
            String currentVersion,
            String releaseTag,
            String archivePackageName,
            String archiveVersion
    ) {
        if (archivePackageName == null || archivePackageName.isEmpty() || archiveVersion == null || archiveVersion.isEmpty()) {
            return ValidationResult.failure("APK metadata could not be read. Install blocked.");
        }
        if (!archivePackageName.equals(expectedPackageName)) {
            return ValidationResult.failure("APK package name is " + archivePackageName + ", expected " + expectedPackageName + ".");
        }
        if (!GitHubReleaseParser.isNewerSemver(currentVersion, archiveVersion)) {
            return ValidationResult.failure("APK version " + archiveVersion + " is not newer than " + currentVersion + ".");
        }
        String normalizedRelease = normalizeVersion(releaseTag);
        if (!normalizedRelease.isEmpty() && !normalizedRelease.equals(normalizeVersion(archiveVersion))) {
            return ValidationResult.failure("APK version " + archiveVersion + " does not match release " + releaseTag + ".");
        }
        return ValidationResult.success("APK metadata verified.");
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

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    static final class AssetSelection {
        final boolean ok;
        final String message;
        final Records.ReleaseAsset apk;
        final Records.ReleaseAsset checksum;

        private AssetSelection(boolean ok, String message, Records.ReleaseAsset apk, Records.ReleaseAsset checksum) {
            this.ok = ok;
            this.message = message;
            this.apk = apk;
            this.checksum = checksum;
        }

        private static AssetSelection success(Records.ReleaseAsset apk, Records.ReleaseAsset checksum) {
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
