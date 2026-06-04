package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseMetadataParserTest {
    @Test
    fun parsesLatestReleaseAssets() {
        val json = """{"tag_name":"v0.3.1","html_url":"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.1","assets":[{"name":"kani-android-0.3.1.apk","browser_download_url":"https://example/apk"},{"name":"kani-android-0.3.1.apk.sha256","browser_download_url":"https://example/sha"}]}"""

        val info = GitHubReleaseMetadataParser.parseLatest(json)

        assertEquals("v0.3.1", info.tagName())
        assertEquals("kani-android-0.3.1.apk", info.assets().get(0).name())
        assertEquals("https://example/sha", info.assets().get(1).downloadUrl())
    }

    @Test
    fun parsesRealGitHubNestedAssetObjects() {
        val json = """{"tag_name":"v0.3.2","html_url":"https://github.com/bee-san/kanji_anki/releases/tag/v0.3.2","assets":[{"url":"https://api.github.com/repos/bee-san/kanji_anki/releases/assets/1","id":1,"node_id":"RA_test","name":"kani-android-0.3.2.apk","label":null,"uploader":{"login":"bee-san","id":2,"site_admin":false},"content_type":"application/vnd.android.package-archive","state":"uploaded","size":732006,"digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","browser_download_url":"https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk"},{"name":"kani-android-0.3.2.apk.sha256","uploader":{"login":"bee-san"},"browser_download_url":"https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk.sha256"}]}"""

        val info = GitHubReleaseMetadataParser.parseLatest(json)

        assertEquals("v0.3.2", info.tagName())
        assertEquals("https://github.com/bee-san/kanji_anki/releases/tag/v0.3.2", info.htmlUrl())
        assertEquals("kani-android-0.3.2.apk", info.assets().get(0).name())
        assertEquals(
            "https://github.com/bee-san/kanji_anki/releases/download/v0.3.2/kani-android-0.3.2.apk.sha256",
            info.assets().get(1).downloadUrl()
        )
    }

    @Test
    fun malformedReleaseJsonFallsBackToEmptyValues() {
        val empty = GitHubReleaseMetadataParser.parseLatest(null)

        assertEquals("", empty.tagName())
        assertEquals("", empty.htmlUrl())
        assertTrue(empty.assets().isEmpty())

        val malformed = GitHubReleaseMetadataParser.parseLatest(
            """{"tag_name":123,"html_url" false,"assets":"not-an-array","ignored":[{"name":"missing-url"},{"browser_download_url":"missing-name"}]}"""
        )

        assertEquals("", malformed.tagName())
        assertEquals("", malformed.htmlUrl())
        assertTrue(malformed.assets().isEmpty())
    }

    @Test
    fun parserHandlesEscapesBrokenUnicodeAndUnclosedStrings() {
        val escaped = GitHubReleaseMetadataParser.parseLatest(
            """{"tag_name":"v0.4.0","html_url":"${escapedHtmlUrl()}","assets":[{"name":"kani.apk","browser_download_url":"https:\\/\\/example\\/kani.apk"}]}"""
        )

        assertEquals("v0.4.0", escaped.tagName())
        assertTrue(escaped.htmlUrl().contains("unicode拉"))
        assertTrue(escaped.htmlUrl().contains("bad\\uZZZZ"))
        assertTrue(escaped.htmlUrl().contains("short\\u12"))

        val unclosed = GitHubReleaseMetadataParser.parseLatest("""{"tag_name":"v0.4.1""")

        assertEquals("v0.4.1", unclosed.tagName())
        assertTrue(unclosed.assets().isEmpty())
    }

    @Test
    fun parserSkipsNestedStringsWhileFindingKeysAndObjects() {
        val info = GitHubReleaseMetadataParser.parseLatest(
            """{"assets":[{"name":"first.apk","browser_download_url":"https://example/first","meta":{"note":"brace } in string"}},{"name":"second.apk.sha256","browser_download_url":"https://example/second"}],"tag_name":"v0.4.2","html_url":"https://example/release"}"""
        )

        assertEquals("v0.4.2", info.tagName())
        assertEquals("first.apk", info.assets().get(0).name())
        assertEquals("https://example/second", info.assets().get(1).downloadUrl())
    }

    @Test
    fun parserHandlesMissingValuesUnclosedArraysAndUnknownEscapes() {
        val missingValues = GitHubReleaseMetadataParser.parseLatest(
            """{"tag_name":   ,"assets":   }"""
        )
        val missingAtEnd = GitHubReleaseMetadataParser.parseLatest(
            """{"tag_name":   """
        )
        val missingArrayAtEnd = GitHubReleaseMetadataParser.parseLatest(
            """{"assets":   """
        )
        val unclosedArray = GitHubReleaseMetadataParser.parseLatest(
            """{"assets":[{"name":"dangling.apk","browser_download_url":"https://example/dangling"}"""
        )
        val nestedArray = GitHubReleaseMetadataParser.parseLatest(
            """{"assets":[[],{"name":"nested.apk","browser_download_url":"https://example/nested"}]}"""
        )
        val emptyAssets = GitHubReleaseMetadataParser.parseLatest(
            """{"assets":[]}"""
        )
        val skippedAssets = GitHubReleaseMetadataParser.parseLatest(
            """{"tag_name":"v0.4.3","html_url":"unknown\qescape","assets":[{"name":"missing-url"},{"browser_download_url":"missing-name"}]}"""
        )
        val noColonBeforeEnd = GitHubReleaseMetadataParser.parseLatest("""{"tag_name"   """)
        val trailingBackslashAndShortUnicode = GitHubReleaseMetadataParser.parseLatest(
            """{"html_url":"${trailingBackslashAndShortUnicodeHtmlUrl()}"}"""
        )
        val terminalBackslash = GitHubReleaseMetadataParser.parseLatest("{" + "\"html_url\":\"trail" + "\\")
        val strayObjectClose = GitHubReleaseMetadataParser.parseLatest("""{"assets":[}]}""")

        assertEquals("", missingValues.tagName())
        assertEquals("", missingAtEnd.tagName())
        assertTrue(missingArrayAtEnd.assets().isEmpty())
        assertTrue(unclosedArray.assets().isEmpty())
        assertEquals("nested.apk", nestedArray.assets().get(0).name())
        assertTrue(emptyAssets.assets().isEmpty())
        assertEquals("unknownqescape", skippedAssets.htmlUrl())
        assertTrue(skippedAssets.assets().isEmpty())
        assertEquals("", noColonBeforeEnd.tagName())
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl().contains("trail\\"))
        assertTrue(trailingBackslashAndShortUnicode.htmlUrl().contains("short\\u"))
        assertEquals("trail\\", terminalBackslash.htmlUrl())
        assertTrue(strayObjectClose.assets().isEmpty())
    }

    @Test
    fun objectValueScannerIgnoresStrayClosingBrace() {
        val values = GitHubReleaseMetadataParser.objectValues("}")

        assertTrue(values.isEmpty())
    }

    private fun escapedHtmlUrl(): String = buildString {
        append("quote\\\" slash\\\\/ backslash\\\\\\\\ backspace\\b form\\f newline\\n return\\r tab\\t unicode\\")
        append("u62c9 bad\\")
        append("uZZZZ short\\")
        append("u12")
    }

    private fun trailingBackslashAndShortUnicodeHtmlUrl(): String = buildString {
        append("trail\\\\ short\\")
        append("u12")
    }
}
