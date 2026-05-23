package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProviderNotePolicyTest {
    @Test
    public void selectRequiredFieldsDropsBulkyUnusedFields() {
        String largeGlossary = repeat("media-glossary-entry", 4000);
        List<String> required = Arrays.asList("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort");

        Map<String, String> fields = ProviderNotePolicy.selectRequiredFields(
                Arrays.asList(
                        "Expression",
                        "ExpressionReading",
                        "MainDefinition",
                        "Sentence",
                        "Frequency",
                        "FreqSort",
                        "Glossary",
                        "PitchGraph",
                        "Audio"
                ),
                Arrays.asList(
                        "確認",
                        "かくにん",
                        "confirmation",
                        "確認した。",
                        "123",
                        "123",
                        largeGlossary,
                        repeat("pitch", 3000),
                        "[sound:large.mp3]"
                ),
                required
        );

        assertEquals(required.size(), fields.size());
        assertEquals("確認", fields.get("Expression"));
        assertEquals("かくにん", fields.get("ExpressionReading"));
        assertEquals("confirmation", fields.get("MainDefinition"));
        assertEquals("確認した。", fields.get("Sentence"));
        assertEquals("123", fields.get("Frequency"));
        assertEquals("123", fields.get("FreqSort"));
        assertFalse(fields.containsKey("Glossary"));
        assertFalse(fields.containsKey("PitchGraph"));
        assertFalse(fields.containsKey("Audio"));
    }

    @Test
    public void selectRequiredFieldsUsesEmptyStringsForMissingAndShortFieldRows() {
        Map<String, String> fields = ProviderNotePolicy.selectRequiredFields(
                Arrays.asList("Expression", "ExpressionReading"),
                Collections.singletonList("確認"),
                Arrays.asList("Expression", "ExpressionReading", "MainDefinition", "Sentence")
        );

        assertEquals("確認", fields.get("Expression"));
        assertEquals("", fields.get("ExpressionReading"));
        assertEquals("", fields.get("MainDefinition"));
        assertEquals("", fields.get("Sentence"));
    }

    @Test
    public void selectRequiredFieldsSkipsBlankOptionalCustomMappings() {
        Map<String, String> fields = ProviderNotePolicy.selectRequiredFields(
                Arrays.asList("Front", "Reading", "Back", "Example", "Frequency", "FrequencySort"),
                Arrays.asList("確認", "かくにん", "confirmation", "確認した。", "123", "123"),
                Arrays.asList("Front", "Back")
        );

        assertEquals(2, fields.size());
        assertEquals("確認", fields.get("Front"));
        assertEquals("confirmation", fields.get("Back"));
        assertFalse(fields.containsKey(""));
        assertFalse(fields.containsKey("Reading"));
    }

    @Test
    public void fieldMappingPreservesEmptyAnkiFieldSlots() {
        Map<String, String> fields = ProviderNotePolicy.selectRequiredFields(
                Arrays.asList("Front", "Reading", "Back", "Example"),
                Arrays.asList("確認", "", "confirmation", ""),
                Arrays.asList("Front", "Reading", "Back")
        );

        assertEquals("確認", fields.get("Front"));
        assertEquals("", fields.get("Reading"));
        assertEquals("confirmation", fields.get("Back"));
    }

    @Test
    public void currentAndLegacyArchiveTagsAreRecognized() {
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(Arrays.asList("leech", "kani_archived")));
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(Arrays.asList("marked", "kanji_anki_archived")));
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(Arrays.asList("kani_archived", "kanji_anki_archived")));
        assertFalse(ProviderNotePolicy.isArchivedTagPresent(Collections.singletonList("marked")));
        assertFalse(ProviderNotePolicy.isArchivedTagPresent(null));
    }

    @Test
    public void browserQuerySearchKeepsConfiguredModelBoundary() {
        String search = ProviderNotePolicy.configuredBrowserQuerySearch("Kiku", "tag:Kani marked:1");

        assertEquals("note:\"Kiku\" (tag:Kani marked:1)", search);
        assertEquals("note:\"Kiku\"", ProviderNotePolicy.modelSearch("Kiku"));
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
