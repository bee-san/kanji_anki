package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class UpdateReleaseAssetSelectorTest {
    @Test
    public void selectAssetsRejectsEmptyReleaseMetadata() {
        UpdateReleaseAssetSelector.AssetSelection selection = UpdateReleaseAssetSelector.selectAssets(null);

        assertFalse(selection.ok());
        assertEquals("Latest release metadata is empty.", selection.message());
    }

    @Test
    public void selectAssetsRejectsReleaseWithoutApkAsset() {
        GitHubReleaseMetadata release = new GitHubReleaseMetadata(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Collections.singletonList(asset("kani-android-0.4.3.apk.sha256", "https://example/sha"))
        );

        UpdateReleaseAssetSelector.AssetSelection selection = UpdateReleaseAssetSelector.selectAssets(release);

        assertFalse(selection.ok());
        assertEquals("Latest release has no APK asset.", selection.message());
    }

    @Test
    public void selectAssetsRejectsReleaseWithoutMatchingChecksumAsset() {
        GitHubReleaseMetadata release = new GitHubReleaseMetadata(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Arrays.asList(
                        asset("kani-android-0.4.3.apk", "https://example/apk"),
                        asset("other.apk.sha256", "https://example/sha")
                )
        );

        UpdateReleaseAssetSelector.AssetSelection selection = UpdateReleaseAssetSelector.selectAssets(release);

        assertFalse(selection.ok());
        assertEquals("Latest release has no SHA-256 checksum asset.", selection.message());
    }

    @Test
    public void selectAssetsPairsApkWithItsExactChecksumAsset() {
        GitHubReleaseMetadata.ReleaseAsset apk = asset("kani-android-0.4.4.apk", "https://example/apk");
        GitHubReleaseMetadata.ReleaseAsset otherChecksum = asset("other.apk.sha256", "https://example/other-sha");
        GitHubReleaseMetadata.ReleaseAsset checksum = asset("kani-android-0.4.4.apk.sha256", "https://example/sha");
        GitHubReleaseMetadata release = new GitHubReleaseMetadata(
                "v0.4.4",
                "https://example/releases/v0.4.4",
                Arrays.asList(otherChecksum, apk, checksum)
        );

        UpdateReleaseAssetSelector.AssetSelection selection = UpdateReleaseAssetSelector.selectAssets(release);

        assertTrue(selection.ok());
        assertSame(apk, selection.apk());
        assertSame(checksum, selection.checksum());
    }

    private static GitHubReleaseMetadata.ReleaseAsset asset(String name, String url) {
        return new GitHubReleaseMetadata.ReleaseAsset(name, url);
    }
}
