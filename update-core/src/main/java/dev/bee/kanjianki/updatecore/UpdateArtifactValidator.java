package dev.bee.kanjianki.updatecore;

public final class UpdateArtifactValidator {
    private UpdateArtifactValidator() {
    }

    public static ValidationResult validateChecksum(String expected, String actual) {
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

    public static ValidationResult validateExpectedChecksum(String expected) {
        if (!Sha256Digest.isDigest(expected)) {
            return ValidationResult.failure("Checksum asset does not contain a SHA-256 digest.");
        }
        return ValidationResult.success("Checksum digest found.");
    }

    public static ValidationResult validatePackageMetadata(
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
        if (!ReleaseVersion.isNewerSemver(currentVersion, archiveVersion)) {
            return ValidationResult.failure("APK version " + archiveVersion + " is not newer than " + currentVersion + ".");
        }
        String normalizedRelease = normalizeVersion(releaseTag);
        if (!normalizedRelease.isEmpty() && !normalizedRelease.equals(normalizeVersion(archiveVersion))) {
            return ValidationResult.failure("APK version " + archiveVersion + " does not match release " + releaseTag + ".");
        }
        return ValidationResult.success("APK metadata verified.");
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    public static final class ValidationResult {
        private final boolean ok;
        private final String message;

        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message;
        }

        private static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }

        private static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }
}
