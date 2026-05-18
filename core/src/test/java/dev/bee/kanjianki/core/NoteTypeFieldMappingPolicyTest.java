package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class NoteTypeFieldMappingPolicyTest {
    @Test
    public void guessesKnownKikuAndCommonFieldNames() {
        NoteTypeFieldMappingPolicy.FieldGuesses guesses = NoteTypeFieldMappingPolicy.guessFields(
                Arrays.asList("Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
        );

        assertEquals("Japanese", guesses.expression);
        assertEquals("Kana", guesses.reading);
        assertEquals("Definition", guesses.meaning);
        assertEquals("Context", guesses.sentence);
        assertEquals("Freq", guesses.frequency);
        assertEquals("FrequencySort", guesses.frequencySort);
    }

    @Test
    public void fallsBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        NoteTypeFieldMappingPolicy.FieldGuesses guesses = NoteTypeFieldMappingPolicy.guessFields(
                Arrays.asList("表", "裏")
        );

        assertEquals("表", guesses.expression);
        assertEquals("裏", guesses.meaning);
        assertEquals("", guesses.reading);
        assertEquals("", guesses.sentence);
        assertEquals("", guesses.frequency);
        assertEquals("", guesses.frequencySort);
    }

    @Test
    public void leavesMappingsBlankWhenNoteTypeHasNoFields() {
        NoteTypeFieldMappingPolicy.FieldGuesses guesses = NoteTypeFieldMappingPolicy.guessFields(Collections.emptyList());

        assertEquals("", guesses.expression);
        assertEquals("", guesses.reading);
        assertEquals("", guesses.meaning);
        assertEquals("", guesses.sentence);
        assertEquals("", guesses.frequency);
        assertEquals("", guesses.frequencySort);
    }

    @Test
    public void matchingFieldPrefersConfiguredCandidateCaseInsensitively() {
        List<String> fields = Arrays.asList("Front", "sentence", "expression", "Meaning");

        assertEquals(
                "expression",
                NoteTypeFieldMappingPolicy.firstMatchingField(fields, "Expression", "Front", "Japanese")
        );
    }

    @Test
    public void matchingFieldTreatsNullCollectionsAndValuesAsNoMatch() {
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(null, "Expression", "Meaning"));
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(Arrays.asList(null, "Front"), (String) null));
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(Arrays.asList("Front", "Back"), "Expression"));
    }

    @Test
    public void noteTypeChoicesNormalizeAndLabelFieldCounts() {
        NoteTypeFieldMappingPolicy.NoteTypeChoice blank = NoteTypeFieldMappingPolicy.choice(null, null);
        NoteTypeFieldMappingPolicy.NoteTypeChoice basic = NoteTypeFieldMappingPolicy.choice("Basic", Arrays.asList("Front", "Back"));

        assertEquals("", blank.name());
        assertEquals(0, blank.fields().size());
        assertEquals(" (0 fields)", NoteTypeFieldMappingPolicy.label(blank));
        assertEquals("Basic (2 fields)", NoteTypeFieldMappingPolicy.label(basic));
        assertArrayEquals(
                new String[]{" (0 fields)", "Basic (2 fields)"},
                NoteTypeFieldMappingPolicy.labels(Arrays.asList(blank, basic))
        );
        assertArrayEquals(new String[0], NoteTypeFieldMappingPolicy.labels(null));
    }
}
