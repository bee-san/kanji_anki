package dev.bee.kanjianki.update;

import android.content.pm.PackageInstaller;

import dev.bee.kanjianki.core.RecordsSchedulerModels;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class UpdatePolicyBehaviorTest {
    private static final String DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void selectAssetsRejectsEmptyReleaseMetadata() {
        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(null);

        assertFalse(selection.ok);
        assertEquals("Latest release metadata is empty.", selection.message);
    }

    @Test
    public void selectAssetsPairsApkWithItsExactChecksumAsset() {
        RecordsSchedulerModels.ReleaseAsset apk = new RecordsSchedulerModels.ReleaseAsset("kani-android-0.4.4.apk", "https://example/apk");
        RecordsSchedulerModels.ReleaseAsset otherChecksum = new RecordsSchedulerModels.ReleaseAsset("other.apk.sha256", "https://example/other-sha");
        RecordsSchedulerModels.ReleaseAsset checksum = new RecordsSchedulerModels.ReleaseAsset("kani-android-0.4.4.apk.sha256", "https://example/sha");
        RecordsSchedulerModels.ReleaseInfo release = new RecordsSchedulerModels.ReleaseInfo(
                "v0.4.4",
                "https://example/releases/v0.4.4",
                Arrays.asList(otherChecksum, apk, checksum)
        );

        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(release);

        assertTrue(selection.ok);
        assertSame(apk, selection.apk);
        assertSame(checksum, selection.checksum);
    }

    @Test
    public void checksumValidationRejectsInvalidExpectedDigestBeforeComparingActual() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateChecksum("not a sha", DIGEST_A);

        assertFalse(result.ok);
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message);
    }

    @Test
    public void checksumValidationRejectsMissingActualDigest() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateChecksum(DIGEST_A, null);

        assertFalse(result.ok);
        assertEquals("Checksum mismatch. Install blocked.", result.message);
    }

    @Test
    public void checksumValidationAcceptsWhitespaceAndCaseDifferences() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateChecksum(
                "  " + DIGEST_A.toUpperCase() + "  ",
                "\n" + DIGEST_A + "\t"
        );

        assertTrue(result.ok);
        assertEquals("Checksum verified.", result.message);
    }

    @Test
    public void expectedChecksumRequiresNonEmptySixtyFourHexDigest() {
        assertInvalidExpectedChecksum(null);
        assertInvalidExpectedChecksum("   ");
        assertInvalidExpectedChecksum("zzzz");

        UpdatePolicy.ValidationResult result = UpdatePolicy.validateExpectedChecksum(DIGEST_B.toUpperCase());

        assertTrue(result.ok);
        assertEquals("Checksum digest found.", result.message);
    }

    @Test
    public void packageMetadataRequiresPackageNameAndVersionFromArchive() {
        assertUnreadableArchiveMetadata(null, "0.4.4");
        assertUnreadableArchiveMetadata("", "0.4.4");
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", null);
        assertUnreadableArchiveMetadata("dev.bee.kanjianki", "");
    }

    @Test
    public void packageMetadataRejectsArchiveVersionThatIsNotNewerThanCurrentVersion() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.4",
                "v0.4.4",
                "dev.bee.kanjianki",
                "0.4.4"
        );

        assertFalse(result.ok);
        assertEquals("APK version 0.4.4 is not newer than 0.4.4.", result.message);
    }

    @Test
    public void packageMetadataAllowsMissingReleaseTagWhenArchiveIsNewerAndTrusted() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                null,
                "dev.bee.kanjianki",
                "0.4.4"
        );

        assertTrue(result.ok);
        assertEquals("APK metadata verified.", result.message);
    }

    @Test
    public void installerSuccessStatusMapsToFinishedCallback() {
        UpdatePolicy.InstallCallback mapped = UpdatePolicy.mapInstallStatus(UpdatePolicy.STATUS_SUCCESS, "ignored");

        assertFalse(mapped.pendingUserAction);
        assertTrue(mapped.success);
        assertEquals("Install finished.", mapped.message);
    }

    @Test
    public void installerStatusConstantsMatchAndroidPackageInstaller() {
        assertEquals(PackageInstaller.STATUS_SUCCESS, UpdatePolicy.STATUS_SUCCESS);
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, UpdatePolicy.STATUS_PENDING_USER_ACTION);
    }

    @Test
    public void installerFailureMessageIncludesTrimmedInstallerDetails() {
        UpdatePolicy.InstallCallback mapped = UpdatePolicy.mapInstallStatus(12, "  blocked by policy  ");

        assertFalse(mapped.pendingUserAction);
        assertFalse(mapped.success);
        assertEquals("Install failed: blocked by policy.", mapped.message);
    }

    @Test
    public void installerFailureMessageHandlesMissingInstallerDetails() {
        UpdatePolicy.InstallCallback nullMessage = UpdatePolicy.mapInstallStatus(12, null);
        UpdatePolicy.InstallCallback blankMessage = UpdatePolicy.mapInstallStatus(13, "  ");

        assertEquals("Install failed.", nullMessage.message);
        assertEquals("Install failed.", blankMessage.message);
    }

    @Test
    public void readableMessageHandlesNullAndBlankMessages() {
        assertEquals("unknown error", GitHubUpdater.readableMessage(null));
        assertEquals("IllegalStateException", GitHubUpdater.readableMessage(new IllegalStateException("   ")));
    }

    private static void assertInvalidExpectedChecksum(String expected) {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateExpectedChecksum(expected);

        assertFalse(result.ok);
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message);
    }

    private static void assertUnreadableArchiveMetadata(String archivePackageName, String archiveVersion) {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.3",
                "v0.4.4",
                archivePackageName,
                archiveVersion
        );

        assertFalse(result.ok);
        assertEquals("APK metadata could not be read. Install blocked.", result.message);
    }
}
