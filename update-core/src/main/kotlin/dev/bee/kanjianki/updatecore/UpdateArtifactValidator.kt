package dev.bee.kanjianki.updatecore

object UpdateArtifactValidator {
    @JvmStatic
    fun validateChecksum(expected: String?, actual: String?): ValidationResult {
        val expectedResult = validateExpectedChecksum(expected)
        if (!expectedResult.ok) {
            return expectedResult
        }
        val normalizedExpected = expected.orEmpty().trim()
        if (actual == null || !normalizedExpected.equals(actual.trim(), ignoreCase = true)) {
            return ValidationResult.failure("Checksum mismatch. Install blocked.")
        }
        return ValidationResult.success("Checksum verified.")
    }

    @JvmStatic
    fun validateExpectedChecksum(expected: String?): ValidationResult {
        if (!Sha256Digest.isDigest(expected)) {
            return ValidationResult.failure("Checksum asset does not contain a SHA-256 digest.")
        }
        return ValidationResult.success("Checksum digest found.")
    }

    @JvmStatic
    fun validatePackageMetadata(
        expectedPackageName: String?,
        currentVersion: String?,
        releaseTag: String?,
        archivePackageName: String?,
        archiveVersion: String?,
    ): ValidationResult {
        if (archivePackageName.isNullOrEmpty() || archiveVersion.isNullOrEmpty()) {
            return ValidationResult.failure("APK metadata could not be read. Install blocked.")
        }
        if (archivePackageName != expectedPackageName) {
            return ValidationResult.failure("APK package name is $archivePackageName, expected $expectedPackageName.")
        }
        if (!ReleaseVersion.isNewerSemver(currentVersion, archiveVersion)) {
            return ValidationResult.failure("APK version $archiveVersion is not newer than $currentVersion.")
        }
        val normalizedRelease = normalizeVersion(releaseTag)
        if (normalizedRelease.isNotEmpty() && normalizedRelease != normalizeVersion(archiveVersion)) {
            return ValidationResult.failure("APK version $archiveVersion does not match release $releaseTag.")
        }
        return ValidationResult.success("APK metadata verified.")
    }

    private fun normalizeVersion(version: String?): String {
        return version?.trim()?.removePrefix("v").orEmpty()
    }

    class ValidationResult private constructor(
        @JvmField val ok: Boolean,
        @JvmField val message: String,
    ) {
        fun ok(): Boolean = ok
        fun message(): String = message

        companion object {
            fun success(message: String): ValidationResult {
                return ValidationResult(true, message)
            }

            fun failure(message: String): ValidationResult {
                return ValidationResult(false, message)
            }
        }
    }
}
