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

    /**
     * Verify that the downloaded APK's signing lineage contains every certificate
     * trusted by the running app. Certs are passed as their raw signature bytes and
     * order does not matter.
     *
     * Requiring the archive lineage to be a superset keeps ordinary and multi-signer
     * updates fail-closed while allowing Android proof-of-rotation lineages to append
     * a new signer. A shorter or disjoint archive lineage is rejected; Android's
     * package installer remains the final cryptographic verifier of the lineage.
     */
    @JvmStatic
    fun validateSigningCertificates(
        currentCerts: List<ByteArray>?,
        archiveCerts: List<ByteArray>?,
    ): ValidationResult {
        if (currentCerts.isNullOrEmpty()) {
            return ValidationResult.failure("Could not read the running app's signing certificate. Install blocked.")
        }
        if (archiveCerts.isNullOrEmpty()) {
            return ValidationResult.failure("Could not read the update's signing certificate. Install blocked.")
        }
        val currentSet = currentCerts.mapTo(HashSet()) { it.toHex() }
        val archiveSet = archiveCerts.mapTo(HashSet()) { it.toHex() }
        if (!archiveSet.containsAll(currentSet)) {
            return ValidationResult.failure("APK signing certificate does not match the installed app. Install blocked.")
        }
        return ValidationResult.success("APK signing certificate verified.")
    }

    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (b in this) {
            builder.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            builder.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return builder.toString()
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
