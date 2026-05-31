package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionCopyTest {
    @Test
    fun dictionarySourcesIncludesGeneratedAtSourcesVersionFallbackAndNotes() {
        val kanjidic = source(
            "kanjidic2",
            "KANJIDIC2",
            "Creative Commons Attribution-ShareAlike 4.0",
            "https://example.invalid/kanjidic2.xml",
            "kanjidic2.xml",
            "2026-05-14",
            "2026-05-01",
            "ignored because database_version wins",
            "ignored because database_version wins",
            "abcd",
        )
        val jiten = source(
            "jiten",
            "",
            "  ",
            "",
            "jiten.tsv",
            "2026-05-13",
            "",
            "2026-rank",
            "",
            "efgh",
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
        assertEquals("Dictionary manifest is empty.", AttributionCopy.dictionarySources("generated", null, null))
        assertEquals("Dictionary manifest is empty.", AttributionCopy.dictionarySources("generated", emptyList(), listOf("note")))
    }

    @Test
    fun sourceFormattingFallsBackToIdAndDateOfCreationWhenVersionFieldsAreBlank() {
        val lines = mutableListOf<String>()
        AttributionCopy.appendSource(
            lines,
            source(
                "legacy-source",
                null,
                null,
                "   ",
                "\t",
                "",
                " ",
                "",
                " 2025-12-31 ",
                "",
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
                "rank-source",
                null,
                null,
                null,
                null,
                null,
                null,
                " 2026-rank ",
                "ignored",
                "",
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
                "null-heavy",
                null,
                null,
                " ",
                null,
                "",
                null,
                null,
                null,
                null,
            ),
        )

        assertEquals("null-heavy", lines.joinToString("\n").trim())
    }

    private fun source(
        id: String,
        name: String?,
        license: String?,
        upstreamUrl: String?,
        sourcePath: String?,
        fetchDate: String?,
        databaseVersion: String?,
        version: String?,
        dateOfCreation: String?,
        sourceSha256: String?,
    ) = AttributionCopy.Source(
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
