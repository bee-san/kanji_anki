package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionCopyTest {
    @Test
    fun dictionarySourcesIncludesGeneratedAtSourcesVersionFallbackAndNotes() {
        val kanjidic = source(
            id = "kanjidic2",
            name = "KANJIDIC2",
            license = "Creative Commons Attribution-ShareAlike 4.0",
            upstreamUrl = "https://example.invalid/kanjidic2.xml",
            sourcePath = "kanjidic2.xml",
            fetchDate = "2026-05-14",
            databaseVersion = "2026-05-01",
            version = "ignored because database_version wins",
            dateOfCreation = "ignored because database_version wins",
            sourceSha256 = "abcd",
        )
        val jiten = source(
            id = "jiten",
            name = "",
            license = "  ",
            upstreamUrl = "",
            sourcePath = "jiten.tsv",
            fetchDate = "2026-05-13",
            databaseVersion = "",
            version = "2026-rank",
            dateOfCreation = "",
            sourceSha256 = "efgh",
        )

        assertEquals(
            listOf(
                "Generated: 2026-05-15T08:30:00Z",
                "",
                "KANJIDIC2",
                "License: Creative Commons Attribution-ShareAlike 4.0",
                "URL: https://example.invalid/kanjidic2.xml",
                "Source: kanjidic2.xml",
                "Fetched: 2026-05-14",
                "Version: 2026-05-01",
                "SHA-256: abcd",
                "",
                "jiten",
                "Source: jiten.tsv",
                "Fetched: 2026-05-13",
                "Version: 2026-rank",
                "SHA-256: efgh",
                "",
                "Dictionary updates ship as a DB, manifest, and checksum.",
                "Rerun the generator after refreshing source exports.",
            ).joinToString("\n"),
            AttributionCopy.dictionarySources(
                "2026-05-15T08:30:00Z",
                listOf(kanjidic, jiten),
                listOf(
                    "Dictionary updates ship as a DB, manifest, and checksum.",
                    "Rerun the generator after refreshing source exports.",
                ),
            ),
        )
    }

    @Test
    fun dictionarySourcesUsesJapaneseLabelsInJapaneseLocale() {
        withJapaneseLocale {
            val source = source(
                id = "kanjidic2",
                name = "KANJIDIC2",
                license = "Creative Commons Attribution-ShareAlike 4.0",
                upstreamUrl = "https://example.invalid/kanjidic2.xml",
                sourcePath = "kanjidic2.xml",
                fetchDate = "2026-05-14",
                databaseVersion = "2026-05-01",
                version = "ignored because database_version wins",
                dateOfCreation = "ignored because database_version wins",
                sourceSha256 = "abcd",
            )

            assertEquals(
                listOf(
                    "生成: 2026-05-15T08:30:00Z",
                    "",
                    "KANJIDIC2",
                    "ライセンス: Creative Commons Attribution-ShareAlike 4.0",
                    "URL: https://example.invalid/kanjidic2.xml",
                    "出典: kanjidic2.xml",
                    "取得日: 2026-05-14",
                    "バージョン: 2026-05-01",
                    "SHA-256: abcd",
                    "",
                    "辞書更新はDB、マニフェスト、チェックサムで配布されます。",
                ).joinToString("\n"),
                AttributionCopy.dictionarySources(
                    "2026-05-15T08:30:00Z",
                    listOf(source),
                    listOf("辞書更新はDB、マニフェスト、チェックサムで配布されます。"),
                ),
            )
        }
    }

    @Test
    fun attributionFallbacksTranslateToJapaneseLocale() {
        withJapaneseLocale {
            assertEquals(
                "EDRDGのKANJIDIC2辞書データ、Jiten順位データ、KanjiVG筆順データ。",
                AttributionCopy.dictionaryFallback(),
            )
            assertEquals("KanjiVG筆順データ、CC BY-SA 3.0。", AttributionCopy.kanjiVgFallback())
            assertEquals(
                "辞書マニフェストが空です。",
                AttributionCopy.dictionarySources("generated", null, null),
            )
        }
    }

    @Test
    fun appendNotesIgnoresMissingAndEmptyNoteLists() {
        val lines = mutableListOf<String>()

        AttributionCopy.appendNotes(lines, null)
        AttributionCopy.appendNotes(lines, emptyList())

        assertEquals("", lines.joinToString("\n"))
    }

    @Test
    fun appendSourceIgnoresNull() {
        val lines = mutableListOf<String>()

        AttributionCopy.appendSource(lines, null)

        assertEquals("", lines.joinToString("\n"))
    }

    @Test
    fun emptySourcesReportEmptyManifest() {
        assertEquals(
            "Dictionary manifest is empty.",
            AttributionCopy.dictionarySources("generated", null, null),
        )
        assertEquals(
            "Dictionary manifest is empty.",
            AttributionCopy.dictionarySources(
                "generated",
                emptyList(),
                listOf("note"),
            ),
        )
    }

    @Test
    fun sourceFormattingFallsBackToIdAndDateOfCreationWhenVersionFieldsAreBlank() {
        val lines = mutableListOf<String>()
        AttributionCopy.appendSource(
            lines,
            source(
                id = "legacy-source",
                name = "",
                license = "",
                upstreamUrl = "   ",
                sourcePath = "\t",
                fetchDate = "",
                databaseVersion = " ",
                version = "",
                dateOfCreation = " 2025-12-31 ",
                sourceSha256 = "",
            ),
        )

        assertEquals(
            listOf(
                "legacy-source",
                "Version: 2025-12-31",
            ).joinToString("\n"),
            lines.joinToString("\n").trim(),
        )
    }

    @Test
    fun sourceFormattingUsesVersionWhenDatabaseVersionIsMissing() {
        val lines = mutableListOf<String>()
        AttributionCopy.appendSource(
            lines,
            source(
                id = "rank-source",
                name = "",
                license = "",
                upstreamUrl = "",
                sourcePath = "",
                fetchDate = "",
                databaseVersion = null,
                version = " 2026-rank ",
                dateOfCreation = "ignored",
                sourceSha256 = "",
            ),
        )

        assertEquals(
            listOf(
                "rank-source",
                "Version: 2026-rank",
            ).joinToString("\n"),
            lines.joinToString("\n").trim(),
        )
    }

    @Test
    fun sourceFormattingOmitsNullAndBlankOptionalValues() {
        val lines = mutableListOf<String>()
        AttributionCopy.appendSource(
            lines,
            source(
                id = "null-heavy",
                name = null,
                license = null,
                upstreamUrl = " ",
                sourcePath = null,
                fetchDate = "",
                databaseVersion = null,
                version = null,
                dateOfCreation = null,
                sourceSha256 = null,
            ),
        )

        assertEquals("null-heavy", lines.joinToString("\n").trim())
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun source(
        id: String?,
        name: String?,
        license: String?,
        upstreamUrl: String?,
        sourcePath: String?,
        fetchDate: String?,
        databaseVersion: String?,
        version: String?,
        dateOfCreation: String?,
        sourceSha256: String?,
    ): AttributionCopy.Source =
        AttributionCopy.Source(
            id,
            name,
            license,
            upstreamUrl,
            sourcePath,
            fetchDate,
            databaseVersion,
            version,
            dateOfCreation,
            sourceSha256,
        )
}
