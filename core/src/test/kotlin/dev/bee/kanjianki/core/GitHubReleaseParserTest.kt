package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun parsesLatestReleaseAssets() {
        val json = "{\"tag_name\":\"v0.3.1\",\"html_url\":\"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.1\",\"assets\":[{\"name\":\"kani-android-0.3.1.apk\",\"browser_download_url\":\"https://example/apk\"},{\"name\":\"kani-android-0.3.1.apk.sha256\",\"browser_download_url\":\"https://example/sha\"}]}"

        val info = GitHubReleaseParser.parseLatest(json)
        val apkAsset = info.apkAsset()!!
        val checksumAsset = info.checksumAssetFor(apkAsset.name)!!

        assertEquals("v0.3.1", info.tagName)
        assertEquals("kani-android-0.3.1.apk", apkAsset.name)
        assertEquals("https://example/sha", checksumAsset.downloadUrl)
    }

    @Test
    fun parsesRealGitHubNestedAssetObjects() {
        val json = (
            "{" +
                "\"tag_name\":\"v0.3.2\"," +
                "\"html_url\":\"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.2\"," +
                "\"assets\":[" +
                "{" +
                "\"url\":\"https://api.github.com/repos/bee-san/kanji_anki/releases/assets/1\"," +
                "\"id\":1," +
                "\"node_id\":\"RA_test\"," +
                "\"name\":\"kani-android-0.3.2.apk\"," +
                "\"label\":null," +
                "\"uploader\":{\"login\":\"bee-san\",\"id\":2,\"site_admin\":false}," +
                "\"content_type\":\"application/vnd.android.package-archive\"," +
                "\"state\":\"uploaded\"," +
                "\"size\":732006," +
                "\"digest\":\"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"," +
                "\"browser_download_url\":\"https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk\"" +
                "}," +
                "{" +
                "\"name\":\"kani-android-0.3.2.apk.sha256\"," +
                "\"uploader\":{\"login\":\"bee-san\"}," +
                "\"browser_download_url\":\"https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk.sha256\"" +
                "}" +
                "]}"
        )

        val info = GitHubReleaseParser.parseLatest(json)
        val apkAsset = info.apkAsset()!!
        val checksumAsset = info.checksumAssetFor(apkAsset.name)!!

        assertEquals("v0.3.2", info.tagName)
        assertEquals("https://github.com/bee-san/kanji_anki/releases/tag/v0.3.2", info.htmlUrl)
        assertEquals("kani-android-0.3.2.apk", apkAsset.name)
        assertEquals(
            "https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk.sha256",
            checksumAsset.downloadUrl
        )
    }

    @Test
    fun comparesSemverTags() {
        assertTrue(GitHubReleaseParser.isNewerSemver("0.3.0", "v0.3.1"))
        assertTrue(GitHubReleaseParser.isNewerSemver("0.3.9", "v0.4.0"))
        assertTrue(GitHubReleaseParser.isNewerSemver("v0.9.9", "v1.0.0"))
        assertFalse(GitHubReleaseParser.isNewerSemver("0.3.1", "v0.3.1"))
        assertFalse(GitHubReleaseParser.isNewerSemver("0.4.0", "v0.3.9"))
        assertFalse(GitHubReleaseParser.isNewerSemver(null, null))
        assertFalse(GitHubReleaseParser.isNewerSemver("not-a-version", "also-bad"))
    }

    @Test
    fun extractsChecksumDigest() {
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            GitHubReleaseParser.parseSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk")
        )
        assertEquals("", GitHubReleaseParser.parseSha256(null))
        assertEquals("", GitHubReleaseParser.parseSha256("not a digest"))
        assertTrue(GitHubReleaseParser.isSha256Digest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertFalse(GitHubReleaseParser.isSha256Digest(null))
        assertFalse(GitHubReleaseParser.isSha256Digest("not a digest"))
    }

    @Test
    fun checksumAssetMustMatchApkName() {
        val info = RecordsSchedulerModels.ReleaseInfo(
            "v0.3.3",
            "https://example/release",
            listOf(
                RecordsSchedulerModels.ReleaseAsset("kani-android-0.3.3.apk", "https://example/apk"),
                RecordsSchedulerModels.ReleaseAsset("other.apk.sha256", "https://example/sha")
            )
        )

        assertNull(info.checksumAssetFor("kani-android-0.3.3.apk"))
    }

    @Test
    fun malformedReleaseJsonFallsBackToEmptyValues() {
        val empty = GitHubReleaseParser.parseLatest(null)

        assertEquals("", empty.tagName)
        assertEquals("", empty.htmlUrl)
        assertTrue(empty.assets.isEmpty())

        val malformed = GitHubReleaseParser.parseLatest(
            "{" +
                "\"tag_name\":123," +
                "\"html_url\" false," +
                "\"assets\":\"not-an-array\"," +
                "\"ignored\":[{\"name\":\"missing-url\"},{\"browser_download_url\":\"missing-name\"}]" +
                "}"
        )

        assertEquals("", malformed.tagName)
        assertEquals("", malformed.htmlUrl)
        assertTrue(malformed.assets.isEmpty())
    }

    @Test
    fun parserHandlesEscapesBrokenUnicodeAndUnclosedStrings() {
        val escaped = GitHubReleaseParser.parseLatest(
            "{" +
                "\"tag_name\":\"v0.4.0\"," +
                "\"html_url\":\"quote\\\" slash/ backslash\\\\ backspace\\b form\\f newline\\n return\\r tab\\t unicode\\" +
                "u62c9 bad\\" +
                "uZZZZ short\\" +
                "u12\"," +
                "\"assets\":[{\"name\":\"kani.apk\",\"browser_download_url\":\"https://example/kani.apk\"}]" +
                "}"
        )

        assertEquals("v0.4.0", escaped.tagName)
        assertTrue(escaped.htmlUrl!!.contains("unicode拉"))
        assertTrue(escaped.htmlUrl.contains("bad\\uZZZZ"))
        assertTrue(escaped.htmlUrl.contains("short\\u12"))

        val unclosed = GitHubReleaseParser.parseLatest("{\"tag_name\":\"v0.4.1")

        assertEquals("v0.4.1", unclosed.tagName)
        assertTrue(unclosed.assets.isEmpty())
    }

    @Test
    fun parserSkipsNestedStringsWhileFindingKeysAndObjects() {
        val info = GitHubReleaseParser.parseLatest(
            "{" +
                "\"assets\":[" +
                "{\"name\":\"first.apk\",\"browser_download_url\":\"https://example/first\",\"meta\":{\"note\":\"brace } in string\"}}," +
                "{\"name\":\"second.apk.sha256\",\"browser_download_url\":\"https://example/second\"}" +
                "]," +
                "\"tag_name\":\"v0.4.2\"," +
                "\"html_url\":\"https://example/release\"" +
                "}"
        )

        val apkAsset = info.apkAsset()!!
        val checksumAsset = info.checksumAssetFor("second.apk")!!

        assertEquals("v0.4.2", info.tagName)
        assertEquals("first.apk", apkAsset.name)
        assertEquals("https://example/second", checksumAsset.downloadUrl)
    }

    @Test
    fun parserHandlesMissingValuesUnclosedArraysAndUnknownEscapes() {
        val missingValues = GitHubReleaseParser.parseLatest("{\"tag_name\":   ,\"assets\":   }")
        val missingAtEnd = GitHubReleaseParser.parseLatest("{\"tag_name\":   ")
        val missingArrayAtEnd = GitHubReleaseParser.parseLatest("{\"assets\":   ")
        val unclosedArray = GitHubReleaseParser.parseLatest("{\"assets\":[{\"name\":\"dangling.apk\",\"browser_download_url\":\"https://example/dangling\"}")
        val nestedArray = GitHubReleaseParser.parseLatest("{\"assets\":[[],{\"name\":\"nested.apk\",\"browser_download_url\":\"https://example/nested\"}]}")
        val emptyAssets = GitHubReleaseParser.parseLatest("{\"assets\":[]}")
        val skippedAssets = GitHubReleaseParser.parseLatest(
            "{" +
                "\"tag_name\":\"v0.4.3\"," +
                "\"html_url\":\"unknown\\qescape\"," +
                "\"assets\":[{\"name\":\"missing-url\"},{\"browser_download_url\":\"missing-name\"}]" +
                "}"
        )
        val noColonBeforeEnd = GitHubReleaseParser.parseLatest("{\"tag_name\"   ")
        val trailingBackslashAndShortUnicode = GitHubReleaseParser.parseLatest(
            "{\"html_url\":\"trail\\\\ short\\" +
                "u\"}"
        )
        val terminalBackslash = GitHubReleaseParser.parseLatest("{\"html_url\":\"trail" + "\\")
        val strayObjectClose = GitHubReleaseParser.parseLatest("{\"assets\":[}]}")

        assertEquals("", missingValues.tagName)
        assertEquals("", missingAtEnd.tagName)
        assertTrue(missingArrayAtEnd.assets.isEmpty())
        assertTrue(unclosedArray.assets.isEmpty())
        assertEquals("nested.apk", nestedArray.apkAsset()!!.name)
        assertTrue(emptyAssets.assets.isEmpty())
        assertEquals("unknownqescape", skippedAssets.htmlUrl)
        assertTrue(skippedAssets.assets.isEmpty())
        assertEquals("", noColonBeforeEnd.tagName)
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl!!.contains("trail\\"))
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl.contains("short\\u"))
        assertEquals("trail\\", terminalBackslash.htmlUrl)
        assertTrue(strayObjectClose.assets.isEmpty())
    }
}
