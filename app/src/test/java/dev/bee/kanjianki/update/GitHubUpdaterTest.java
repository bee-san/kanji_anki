package dev.bee.kanjianki.update;

import dev.bee.kanjianki.core.Records;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GitHubUpdaterTest {
    @Test
    public void readableMessageFallsBackToExceptionClassWhenMessageIsNull() {
        assertEquals("RuntimeException", GitHubUpdater.readableMessage(new RuntimeException()));
    }

    @Test
    public void readableMessageKeepsSpecificExceptionMessage() {
        assertEquals("HTTP 403", GitHubUpdater.readableMessage(new RuntimeException("HTTP 403")));
    }

    @Test
    public void rejectsReleaseWithoutApkAsset() {
        Records.ReleaseInfo release = new Records.ReleaseInfo(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Collections.singletonList(new Records.ReleaseAsset("kani-android-0.4.3.apk.sha256", "https://example/sha"))
        );

        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(release);

        assertFalse(selection.ok);
        assertEquals("Latest release has no APK asset.", selection.message);
    }

    @Test
    public void rejectsReleaseWithoutMatchingChecksumAsset() {
        Records.ReleaseInfo release = new Records.ReleaseInfo(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Arrays.asList(
                        new Records.ReleaseAsset("kani-android-0.4.3.apk", "https://example/apk"),
                        new Records.ReleaseAsset("other.apk.sha256", "https://example/sha")
                )
        );

        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(release);

        assertFalse(selection.ok);
        assertEquals("Latest release has no SHA-256 checksum asset.", selection.message);
    }

    @Test
    public void rejectsChecksumMismatch() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateChecksum(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );

        assertFalse(result.ok);
        assertEquals("Checksum mismatch. Install blocked.", result.message);
    }

    @Test
    public void acceptsExpectedPackageNameAndNewerVersion() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.3",
                "dev.bee.kanjianki",
                "0.4.3"
        );

        assertTrue(result.ok);
    }

    @Test
    public void rejectsDifferentPackageName() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.3",
                "dev.bee.other",
                "0.4.3"
        );

        assertFalse(result.ok);
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message);
    }

    @Test
    public void rejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.4",
                "dev.bee.kanjianki",
                "0.4.3"
        );

        assertFalse(result.ok);
        assertEquals("APK version 0.4.3 does not match release v0.4.4.", result.message);
    }

    @Test
    public void mapsPendingUserActionInstallerStatus() {
        UpdatePolicy.InstallCallback mapped = UpdatePolicy.mapInstallStatus(UpdatePolicy.STATUS_PENDING_USER_ACTION, "");

        assertTrue(mapped.pendingUserAction);
        assertFalse(mapped.success);
        assertEquals("Android needs confirmation to finish installing.", mapped.message);
    }
}
