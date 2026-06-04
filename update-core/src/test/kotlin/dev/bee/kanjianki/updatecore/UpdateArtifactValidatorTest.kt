package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateArtifactValidatorTest {
    companion object {
        private const val DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }

    @Test
    fun checksumValidationRejectsInvalidExpectedDigestBeforeComparingActual() {
        val result = UpdateArtifactValidator.validateChecksum("not a sha", DIGEST_A)

        assertFalse(result.ok())
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message())
    }

    @Test
    fun checksumValidationRejectsMissingActualDigest() {
        val result = UpdateArtifactValidator.validateChecksum(DIGEST_A, null)

        assertFalse(result.ok())
        assertEquals("Checksum mismatch. Install blocked.", result.message())
    }

    @Test
    fun checksumValidationAcceptsWhitespaceAndCaseDifferences() {
        val result = UpdateArtifactValidator.validateChecksum(
            "  ${DIGEST_A.uppercase()}  ",
            "\n${DIGEST_A}\t"
        )

        assertTrue(result.ok())
        assertEquals("Checksum verified.", result.message())
    }

    @Test
    fun expectedChecksumRequiresNonEmptySixtyFourHexDigest() {
        assertInvalidExpectedChecksum(null)
        assertInvalidExpectedChecksum("   ")
        assertInvalidExpectedChecksum("zzzz")

        val result = UpdateArtifactValidator.validateExpectedChecksum(DIGEST_B.uppercase())

        assertTrue(result.ok())
        assertEquals("Checksum digest found.", result.message())
    }

    @Test
    fun packageMetadataRequiresPackageNameAndVersionFromArchive() {
        assertUnreadableArchiveMetadata(null, "0.4.4")
        assertUnreadableArchiveMetadata("", "0.4.4")
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", null)
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", "")
    }

    @Test
    fun packageMetadataRejectsWrongPackageName() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.4",
            "v0.4.4",
            "dev.bee.other",
            "0.4.4"
        )

        assertFalse(result.ok())
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message())
    }

    @Test
    fun packageMetadataRejectsArchiveVersionThatIsNotNewerThanCurrentVersion() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.4",
            "v0.4.4",
            "dev.bee.kanjianki",
            "0.4.4"
        )

        assertFalse(result.ok())
        assertEquals("APK version 0.4.4 is not newer than 0.4.4.", result.message())
    }

    @Test
    fun packageMetadataRejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.3",
            "v0.4.5",
            "dev.bee.kanjianki",
            "0.4.4"
        )

        assertFalse(result.ok())
        assertEquals("APK version 0.4.4 does not match release v0.4.5.", result.message())
    }

    @Test
    fun packageMetadataAllowsMissingReleaseTagWhenArchiveIsNewerAndTrusted() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.3",
            null,
            "dev.bee.kanjianki",
            "0.4.4"
        )

        assertTrue(result.ok())
        assertEquals("APK metadata verified.", result.message())
    }

    private fun assertInvalidExpectedChecksum(expected: String?) {
        val result = UpdateArtifactValidator.validateExpectedChecksum(expected)

        assertFalse(result.ok())
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message())
    }

    private fun assertUnreadableArchiveMetadata(archivePackageName: String?, archiveVersion: String?) {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.4",
            "v0.4.4",
            archivePackageName,
            archiveVersion
        )

        assertFalse(result.ok())
        assertEquals("APK metadata could not be read. Install blocked.", result.message())
    }
}
