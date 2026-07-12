package dev.bee.kanjianki.updatecore

/**
 * Certificates extracted from Android package metadata together with the
 * relationship Android verified between them.
 *
 * [Kind.CURRENT_SIGNERS] is an unordered set of certificates that all sign the
 * package now. [Kind.VERIFIED_HISTORY] is a proof-of-rotation history returned
 * by Android for a package with a single current signer.
 */
class SigningCertificateInfo private constructor(
    @JvmField val kind: Kind,
    @JvmField val certificates: List<ByteArray>,
) {
    enum class Kind {
        CURRENT_SIGNERS,
        VERIFIED_HISTORY,
    }

    fun isEmpty(): Boolean = certificates.isEmpty()

    companion object {
        @JvmStatic
        fun currentSigners(certificates: List<ByteArray>?): SigningCertificateInfo {
            return SigningCertificateInfo(Kind.CURRENT_SIGNERS, copyCertificates(certificates))
        }

        @JvmStatic
        fun verifiedHistory(certificates: List<ByteArray>?): SigningCertificateInfo {
            return SigningCertificateInfo(Kind.VERIFIED_HISTORY, copyCertificates(certificates))
        }

        @JvmStatic
        fun unavailable(): SigningCertificateInfo = currentSigners(emptyList())

        private fun copyCertificates(certificates: List<ByteArray>?): List<ByteArray> {
            return certificates.orEmpty().map { it.copyOf() }
        }
    }
}

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
     * Verify the downloaded APK's signer identity without treating concurrent
     * signers as a certificate-rotation lineage.
     *
     * Concurrent current signers must match exactly. A forward extension is allowed
     * only when Android identified both inputs as verified proof-of-rotation
     * histories. Android's package installer remains the final cryptographic verifier.
     */
    @JvmStatic
    fun validateSigningCertificates(
        currentSigning: SigningCertificateInfo?,
        archiveSigning: SigningCertificateInfo?,
    ): ValidationResult {
        if (currentSigning == null || currentSigning.isEmpty()) {
            return ValidationResult.failure("Could not read the running app's signing certificate. Install blocked.")
        }
        if (archiveSigning == null || archiveSigning.isEmpty()) {
            return ValidationResult.failure("Could not read the update's signing certificate. Install blocked.")
        }
        val currentSet = currentSigning.certificates.mapTo(HashSet()) { it.toHex() }
        val archiveSet = archiveSigning.certificates.mapTo(HashSet()) { it.toHex() }
        val matches = when {
            currentSigning.kind == SigningCertificateInfo.Kind.CURRENT_SIGNERS &&
                archiveSigning.kind == SigningCertificateInfo.Kind.CURRENT_SIGNERS -> archiveSet == currentSet
            currentSigning.kind == SigningCertificateInfo.Kind.VERIFIED_HISTORY &&
                archiveSigning.kind == SigningCertificateInfo.Kind.VERIFIED_HISTORY -> archiveSet.containsAll(currentSet)
            else -> false
        }
        if (!matches) {
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
