package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubReleaseParserTest {
    @Test
    public void parsesLatestReleaseAssets() {
        String json = "{\"tag_name\":\"v0.3.1\",\"html_url\":\"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.1\",\"assets\":[{\"name\":\"kani-android-0.3.1.apk\",\"browser_download_url\":\"https://example/apk\"},{\"name\":\"kani-android-0.3.1.apk.sha256\",\"browser_download_url\":\"https://example/sha\"}]}";

        Records.ReleaseInfo info = GitHubReleaseParser.parseLatest(json);

        assertEquals("v0.3.1", info.tagName);
        assertEquals("kani-android-0.3.1.apk", info.apkAsset().name);
        assertEquals("https://example/sha", info.checksumAssetFor(info.apkAsset().name).downloadUrl);
    }

    @Test
    public void parsesRealGitHubNestedAssetObjects() {
        String json = "{"
                + "\"tag_name\":\"v0.3.2\","
                + "\"html_url\":\"https:\\/\\/github.com\\/bee-san\\/kanji_anki\\/releases\\/tag\\/v0.3.2\","
                + "\"assets\":["
                + "{"
                + "\"url\":\"https://api.github.com/repos/bee-san/kanji_anki/releases/assets/1\","
                + "\"id\":1,"
                + "\"node_id\":\"RA_test\","
                + "\"name\":\"kani-android-0.3.2.apk\","
                + "\"label\":null,"
                + "\"uploader\":{\"login\":\"bee-san\",\"id\":2,\"site_admin\":false},"
                + "\"content_type\":\"application/vnd.android.package-archive\","
                + "\"state\":\"uploaded\","
                + "\"size\":732006,"
                + "\"digest\":\"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\","
                + "\"browser_download_url\":\"https:\\/\\/github.com\\/bee-san\\/kanji_anki\\/releases\\/download\\/v0.3.2\\/kani-android-0.3.2.apk\""
                + "},"
                + "{"
                + "\"name\":\"kani-android-0.3.2.apk.sha256\","
                + "\"uploader\":{\"login\":\"bee-san\"},"
                + "\"browser_download_url\":\"https:\\/\\/github.com\\/bee-san\\/kanji_anki\\/releases\\/download\\/v0.3.2\\/kani-android-0.3.2.apk.sha256\""
                + "}"
                + "]}";

        Records.ReleaseInfo info = GitHubReleaseParser.parseLatest(json);

        assertEquals("v0.3.2", info.tagName);
        assertEquals("https://github.com/bee-san/kanji_anki/releases/tag/v0.3.2", info.htmlUrl);
        assertEquals("kani-android-0.3.2.apk", info.apkAsset().name);
        assertEquals(
                "https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk.sha256",
                info.checksumAssetFor(info.apkAsset().name).downloadUrl
        );
    }

    @Test
    public void comparesSemverTags() {
        assertTrue(GitHubReleaseParser.isNewerSemver("0.3.0", "v0.3.1"));
        assertTrue(GitHubReleaseParser.isNewerSemver("0.3.9", "v0.4.0"));
        assertFalse(GitHubReleaseParser.isNewerSemver("0.3.1", "v0.3.1"));
        assertFalse(GitHubReleaseParser.isNewerSemver("0.4.0", "v0.3.9"));
    }

    @Test
    public void extractsChecksumDigest() {
        assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                GitHubReleaseParser.parseSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk")
        );
    }

    @Test
    public void checksumAssetMustMatchApkName() {
        Records.ReleaseInfo info = new Records.ReleaseInfo(
                "v0.3.3",
                "https://example/release",
                java.util.Arrays.asList(
                        new Records.ReleaseAsset("kani-android-0.3.3.apk", "https://example/apk"),
                        new Records.ReleaseAsset("other.apk.sha256", "https://example/sha")
                )
        );

        assertEquals(null, info.checksumAssetFor("kani-android-0.3.3.apk"));
    }
}
