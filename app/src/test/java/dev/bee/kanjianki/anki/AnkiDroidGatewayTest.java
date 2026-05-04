package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.Records;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class AnkiDroidGatewayTest {
    @Test
    public void selectRequiredFieldsDropsBulkyUnusedKikuFields() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        String largeGlossary = repeat("media-glossary-entry", 4000);

        Map<String, String> fields = AnkiDroidGateway.selectRequiredFields(
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
                settings
        );

        assertEquals(settings.requiredFields().size(), fields.size());
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

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
