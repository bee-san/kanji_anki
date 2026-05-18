package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubReleaseParserTest {
    @Test
    public void parsesLatestReleaseAssets() {
        String json = "{\"tag_name\":\"v0.3.1\",\"html_url\":\"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.1\",\"assets\":[{\"name\":\"kani-android-0.3.1.apk\",\"browser_download_url\":\"https://example/apk\"},{\"name\":\"kani-android-0.3.1.apk.sha256\",\"browser_download_url\":\"https://example/sha\"}]}";

        RecordsSchedulerModels.ReleaseInfo info = GitHubReleaseParser.parseLatest(json);

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

        RecordsSchedulerModels.ReleaseInfo info = GitHubReleaseParser.parseLatest(json);

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
        assertTrue(GitHubReleaseParser.isNewerSemver("v0.9.9", "v1.0.0"));
        assertFalse(GitHubReleaseParser.isNewerSemver("0.3.1", "v0.3.1"));
        assertFalse(GitHubReleaseParser.isNewerSemver("0.4.0", "v0.3.9"));
        assertFalse(GitHubReleaseParser.isNewerSemver(null, null));
        assertFalse(GitHubReleaseParser.isNewerSemver("not-a-version", "also-bad"));
    }

    @Test
    public void extractsChecksumDigest() {
        assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                GitHubReleaseParser.parseSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk")
        );
        assertEquals("", GitHubReleaseParser.parseSha256(null));
        assertEquals("", GitHubReleaseParser.parseSha256("not a digest"));
    }

    @Test
    public void checksumAssetMustMatchApkName() {
        RecordsSchedulerModels.ReleaseInfo info = new RecordsSchedulerModels.ReleaseInfo(
                "v0.3.3",
                "https://example/release",
                java.util.Arrays.asList(
                        new RecordsSchedulerModels.ReleaseAsset("kani-android-0.3.3.apk", "https://example/apk"),
                        new RecordsSchedulerModels.ReleaseAsset("other.apk.sha256", "https://example/sha")
                )
        );

        assertEquals(null, info.checksumAssetFor("kani-android-0.3.3.apk"));
    }

    @Test
    public void malformedReleaseJsonFallsBackToEmptyValues() {
        RecordsSchedulerModels.ReleaseInfo empty = GitHubReleaseParser.parseLatest(null);

        assertEquals("", empty.tagName);
        assertEquals("", empty.htmlUrl);
        assertTrue(empty.assets.isEmpty());

        RecordsSchedulerModels.ReleaseInfo malformed = GitHubReleaseParser.parseLatest(
                "{"
                        + "\"tag_name\":123,"
                        + "\"html_url\" false,"
                        + "\"assets\":\"not-an-array\","
                        + "\"ignored\":[{\"name\":\"missing-url\"},{\"browser_download_url\":\"missing-name\"}]"
                        + "}"
        );

        assertEquals("", malformed.tagName);
        assertEquals("", malformed.htmlUrl);
        assertTrue(malformed.assets.isEmpty());
    }

    @Test
    public void parserHandlesEscapesBrokenUnicodeAndUnclosedStrings() {
        RecordsSchedulerModels.ReleaseInfo escaped = GitHubReleaseParser.parseLatest(
                "{"
                        + "\"tag_name\":\"v0.4.0\","
                        + "\"html_url\":\"quote\\\" slash\\/ backslash\\\\ backspace\\b form\\f newline\\n return\\r tab\\t unicode\\"
                        + "u62c9 bad\\"
                        + "uZZZZ short\\"
                        + "u12\","
                        + "\"assets\":[{\"name\":\"kani.apk\",\"browser_download_url\":\"https:\\/\\/example\\/kani.apk\"}]"
                        + "}"
        );

        assertEquals("v0.4.0", escaped.tagName);
        assertTrue(escaped.htmlUrl.contains("unicode拉"));
        assertTrue(escaped.htmlUrl.contains("bad\\uZZZZ"));
        assertTrue(escaped.htmlUrl.contains("short\\u12"));

        RecordsSchedulerModels.ReleaseInfo unclosed = GitHubReleaseParser.parseLatest("{\"tag_name\":\"v0.4.1");

        assertEquals("v0.4.1", unclosed.tagName);
        assertTrue(unclosed.assets.isEmpty());
    }

    @Test
    public void parserSkipsNestedStringsWhileFindingKeysAndObjects() {
        RecordsSchedulerModels.ReleaseInfo info = GitHubReleaseParser.parseLatest(
                "{"
                        + "\"assets\":["
                        + "{\"name\":\"first.apk\",\"browser_download_url\":\"https://example/first\",\"meta\":{\"note\":\"brace } in string\"}},"
                        + "{\"name\":\"second.apk.sha256\",\"browser_download_url\":\"https://example/second\"}"
                        + "],"
                        + "\"tag_name\":\"v0.4.2\","
                        + "\"html_url\":\"https://example/release\""
                        + "}"
        );

        assertEquals("v0.4.2", info.tagName);
        assertEquals("first.apk", info.apkAsset().name);
        assertEquals("https://example/second", info.checksumAssetFor("second.apk").downloadUrl);
    }

    @Test
    public void parserHandlesMissingValuesUnclosedArraysAndUnknownEscapes() {
        RecordsSchedulerModels.ReleaseInfo missingValues = GitHubReleaseParser.parseLatest(
                "{\"tag_name\":   ,\"assets\":   }"
        );
        RecordsSchedulerModels.ReleaseInfo missingAtEnd = GitHubReleaseParser.parseLatest(
                "{\"tag_name\":   "
        );
        RecordsSchedulerModels.ReleaseInfo missingArrayAtEnd = GitHubReleaseParser.parseLatest(
                "{\"assets\":   "
        );
        RecordsSchedulerModels.ReleaseInfo unclosedArray = GitHubReleaseParser.parseLatest(
                "{\"assets\":[{\"name\":\"dangling.apk\",\"browser_download_url\":\"https://example/dangling\"}"
        );
        RecordsSchedulerModels.ReleaseInfo nestedArray = GitHubReleaseParser.parseLatest(
                "{\"assets\":[[],{\"name\":\"nested.apk\",\"browser_download_url\":\"https://example/nested\"}]}"
        );
        RecordsSchedulerModels.ReleaseInfo emptyAssets = GitHubReleaseParser.parseLatest(
                "{\"assets\":[]}"
        );
        RecordsSchedulerModels.ReleaseInfo skippedAssets = GitHubReleaseParser.parseLatest(
                "{"
                        + "\"tag_name\":\"v0.4.3\","
                        + "\"html_url\":\"unknown\\qescape\","
                        + "\"assets\":[{\"name\":\"missing-url\"},{\"browser_download_url\":\"missing-name\"}]"
                        + "}"
        );
        RecordsSchedulerModels.ReleaseInfo noColonBeforeEnd = GitHubReleaseParser.parseLatest("{\"tag_name\"   ");
        RecordsSchedulerModels.ReleaseInfo trailingBackslashAndShortUnicode = GitHubReleaseParser.parseLatest(
                "{\"html_url\":\"trail\\\\ short\\"
                        + "u\"}"
        );
        RecordsSchedulerModels.ReleaseInfo terminalBackslash = GitHubReleaseParser.parseLatest("{\"html_url\":\"trail" + "\\");
        RecordsSchedulerModels.ReleaseInfo strayObjectClose = GitHubReleaseParser.parseLatest("{\"assets\":[}]}");

        assertEquals("", missingValues.tagName);
        assertEquals("", missingAtEnd.tagName);
        assertTrue(missingArrayAtEnd.assets.isEmpty());
        assertTrue(unclosedArray.assets.isEmpty());
        assertEquals("nested.apk", nestedArray.apkAsset().name);
        assertTrue(emptyAssets.assets.isEmpty());
        assertEquals("unknownqescape", skippedAssets.htmlUrl);
        assertTrue(skippedAssets.assets.isEmpty());
        assertEquals("", noColonBeforeEnd.tagName);
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl.contains("trail\\"));
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl.contains("short\\u"));
        assertEquals("trail\\", terminalBackslash.htmlUrl);
        assertTrue(strayObjectClose.assets.isEmpty());
    }
}
