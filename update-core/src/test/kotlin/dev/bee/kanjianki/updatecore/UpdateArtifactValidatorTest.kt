package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateArtifactValidatorTest {
    @Test
    fun checksumValidationRejectsInvalidExpectedDigestBeforeComparingActual() {
        val result = UpdateArtifactValidator.validateChecksum("not a sha", DIGEST_A)

        assertFalse(result.ok())
        assertFalse(result.ok)
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message())
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message)
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
            "\n$DIGEST_A\t",
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
            expectedPackageName = "dev.bee.kanjianki",
            currentVersion = "0.4.3",
            releaseTag = "v0.4.4",
            archivePackageName = "dev.bee.other",
            archiveVersion = "0.4.4",
        )

        assertFalse(result.ok())
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message())
    }

    @Test
    fun packageMetadataRejectsArchiveVersionThatIsNotNewerThanCurrentVersion() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            expectedPackageName = "dev.bee.kanjianki",
            currentVersion = "0.4.4",
            releaseTag = "v0.4.4",
            archivePackageName = "dev.bee.kanjianki",
            archiveVersion = "0.4.4",
        )

        assertFalse(result.ok())
        assertEquals("APK version 0.4.4 is not newer than 0.4.4.", result.message())
    }

    @Test
    fun packageMetadataRejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            expectedPackageName = "dev.bee.kanjianki",
            currentVersion = "0.4.3",
            releaseTag = "v0.4.5",
            archivePackageName = "dev.bee.kanjianki",
            archiveVersion = "0.4.4",
        )

        assertFalse(result.ok())
        assertEquals("APK version 0.4.4 does not match release v0.4.5.", result.message())
    }

    @Test
    fun packageMetadataAllowsMissingReleaseTagWhenArchiveIsNewerAndTrusted() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            expectedPackageName = "dev.bee.kanjianki",
            currentVersion = "0.4.3",
            releaseTag = null,
            archivePackageName = "dev.bee.kanjianki",
            archiveVersion = "0.4.4",
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
            expectedPackageName = "dev.bee.kanjianki",
            currentVersion = "0.4.3",
            releaseTag = "v0.4.4",
            archivePackageName = archivePackageName,
            archiveVersion = archiveVersion,
        )

        assertFalse(result.ok())
        assertEquals("APK metadata could not be read. Install blocked.", result.message())
    }

    private companion object {
        const val DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
