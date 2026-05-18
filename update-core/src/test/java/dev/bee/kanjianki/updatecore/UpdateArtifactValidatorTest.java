package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateArtifactValidatorTest {
    private static final String DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void checksumValidationRejectsInvalidExpectedDigestBeforeComparingActual() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validateChecksum("not a sha", DIGEST_A);

        assertFalse(result.ok());
        assertFalse(result.ok);
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message());
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message);
    }

    @Test
    public void checksumValidationRejectsMissingActualDigest() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validateChecksum(DIGEST_A, null);

        assertFalse(result.ok());
        assertEquals("Checksum mismatch. Install blocked.", result.message());
    }

    @Test
    public void checksumValidationAcceptsWhitespaceAndCaseDifferences() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validateChecksum(
                "  " + DIGEST_A.toUpperCase() + "  ",
                "\n" + DIGEST_A + "\t"
        );

        assertTrue(result.ok());
        assertEquals("Checksum verified.", result.message());
    }

    @Test
    public void expectedChecksumRequiresNonEmptySixtyFourHexDigest() {
        assertInvalidExpectedChecksum(null);
        assertInvalidExpectedChecksum("   ");
        assertInvalidExpectedChecksum("zzzz");

        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validateExpectedChecksum(DIGEST_B.toUpperCase());

        assertTrue(result.ok());
        assertEquals("Checksum digest found.", result.message());
    }

    @Test
    public void packageMetadataRequiresPackageNameAndVersionFromArchive() {
        assertUnreadableArchiveMetadata(null, "0.4.4");
        assertUnreadableArchiveMetadata("", "0.4.4");
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", null);
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", "");
    }

    @Test
    public void packageMetadataRejectsWrongPackageName() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                "v0.4.4",
                "dev.bee.other",
                "0.4.4"
        );

        assertFalse(result.ok());
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message());
    }

    @Test
    public void packageMetadataRejectsArchiveVersionThatIsNotNewerThanCurrentVersion() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.4",
                "v0.4.4",
                "dev.bee.kanjianki",
                "0.4.4"
        );

        assertFalse(result.ok());
        assertEquals("APK version 0.4.4 is not newer than 0.4.4.", result.message());
    }

    @Test
    public void packageMetadataRejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                "v0.4.5",
                "dev.bee.kanjianki",
                "0.4.4"
        );

        assertFalse(result.ok());
        assertEquals("APK version 0.4.4 does not match release v0.4.5.", result.message());
    }

    @Test
    public void packageMetadataAllowsMissingReleaseTagWhenArchiveIsNewerAndTrusted() {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                null,
                "dev.bee.kanjianki",
                "0.4.4"
        );

        assertTrue(result.ok());
        assertEquals("APK metadata verified.", result.message());
    }

    private static void assertInvalidExpectedChecksum(String expected) {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validateExpectedChecksum(expected);

        assertFalse(result.ok());
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message());
    }

    private static void assertUnreadableArchiveMetadata(String archivePackageName, String archiveVersion) {
        UpdateArtifactValidator.ValidationResult result = UpdateArtifactValidator.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                "v0.4.4",
                archivePackageName,
                archiveVersion
        );

        assertFalse(result.ok());
        assertEquals("APK metadata could not be read. Install blocked.", result.message());
    }
}
