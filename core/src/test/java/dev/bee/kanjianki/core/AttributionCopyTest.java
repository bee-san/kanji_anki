package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class AttributionCopyTest {
    @Test
    public void dictionarySourcesIncludesGeneratedAtSourcesVersionFallbackAndNotes() {
        AttributionCopy.Source kanjidic = source(
                "kanjidic2",
                "KANJIDIC2",
                "Creative Commons Attribution-ShareAlike 4.0",
                "https://example.invalid/kanjidic2.xml",
                "kanjidic2.xml",
                "2026-05-14",
                "2026-05-01",
                "ignored because database_version wins",
                "ignored because database_version wins",
                "abcd"
        );
        AttributionCopy.Source jiten = source(
                "jiten",
                "",
                "  ",
                "",
                "jiten.tsv",
                "2026-05-13",
                "",
                "2026-rank",
                "",
                "efgh"
        );

        assertEquals(
                String.join("\n", Arrays.asList(
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
                        "Rerun the generator after refreshing source exports."
                )),
                AttributionCopy.dictionarySources(
                        "2026-05-15T08:30:00Z",
                        Arrays.asList(kanjidic, jiten),
                        Arrays.asList(
                                "Dictionary updates ship as a DB, manifest, and checksum.",
                                "Rerun the generator after refreshing source exports."
                        )
                )
        );
    }

    @Test
    public void appendNotesIgnoresMissingAndEmptyNoteLists() {
        List<String> lines = new ArrayList<>();

        AttributionCopy.appendNotes(lines, null);
        AttributionCopy.appendNotes(lines, Collections.emptyList());

        assertEquals("", String.join("\n", lines));
    }

    @Test
    public void appendSourceIgnoresNull() {
        List<String> lines = new ArrayList<>();

        AttributionCopy.appendSource(lines, null);

        assertEquals("", String.join("\n", lines));
    }

    @Test
    public void emptySourcesReportEmptyManifest() {
        assertEquals("Dictionary manifest is empty.", AttributionCopy.dictionarySources("generated", null, null));
        assertEquals("Dictionary manifest is empty.", AttributionCopy.dictionarySources("generated", List.of(), List.of("note")));
    }

    @Test
    public void sourceFormattingFallsBackToIdAndDateOfCreationWhenVersionFieldsAreBlank() {
        List<String> lines = new ArrayList<>();
        AttributionCopy.appendSource(lines, source(
                "legacy-source",
                "",
                "",
                "   ",
                "\t",
                "",
                " ",
                "",
                " 2025-12-31 ",
                ""
        ));

        assertEquals(
                String.join("\n", Arrays.asList(
                        "legacy-source",
                        "Version: 2025-12-31"
                )),
                String.join("\n", lines).trim()
        );
    }

    @Test
    public void sourceFormattingUsesVersionWhenDatabaseVersionIsMissing() {
        List<String> lines = new ArrayList<>();
        AttributionCopy.appendSource(lines, source(
                "rank-source",
                "",
                "",
                "",
                "",
                "",
                null,
                " 2026-rank ",
                "ignored",
                ""
        ));

        assertEquals(
                String.join("\n", Arrays.asList(
                        "rank-source",
                        "Version: 2026-rank"
                )),
                String.join("\n", lines).trim()
        );
    }

    @Test
    public void sourceFormattingOmitsNullAndBlankOptionalValues() {
        List<String> lines = new ArrayList<>();
        AttributionCopy.appendSource(lines, source(
                "null-heavy",
                null,
                null,
                " ",
                null,
                "",
                null,
                null,
                null,
                null
        ));

        assertEquals("null-heavy", String.join("\n", lines).trim());
    }

    private static AttributionCopy.Source source(
            String id,
            String name,
            String license,
            String upstreamUrl,
            String sourcePath,
            String fetchDate,
            String databaseVersion,
            String version,
            String dateOfCreation,
            String sourceSha256
    ) {
        return new AttributionCopy.Source(
                id,
                name,
                license,
                upstreamUrl,
                sourcePath,
                fetchDate,
                databaseVersion,
                version,
                dateOfCreation,
                sourceSha256
        );
    }
}
