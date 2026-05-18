package dev.bee.kanjianki;

import dev.bee.kanjianki.anki.AnkiDroidGateway;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoteTypeFieldMappingsTest {
    @Test
    public void chooseShowsNoNoteTypesMessageWithoutOpeningDialog() {
        FakeUi emptyUi = new FakeUi();
        NoteTypeFieldMappings.choose(() -> Collections.emptyList(), Runnable::run, Runnable::run, new FakeInputs(), emptyUi);

        assertEquals(Collections.singletonList("Reading AnkiDroid note types."), emptyUi.shortMessages);
        assertEquals(Collections.singletonList("No note types found in AnkiDroid."), emptyUi.longMessages);
        assertFalse(emptyUi.dialogShown());

        FakeUi nullUi = new FakeUi();
        NoteTypeFieldMappings.choose(() -> null, Runnable::run, Runnable::run, new FakeInputs(), nullUi);

        assertEquals(Collections.singletonList("No note types found in AnkiDroid."), nullUi.longMessages);
        assertFalse(nullUi.dialogShown());
    }

    @Test
    public void choicesFromTreatsMissingProviderModelsAsEmpty() {
        assertTrue(NoteTypeFieldMappings.choicesFrom(null).isEmpty());
        assertTrue(NoteTypeFieldMappings.choicesFrom(Collections.emptyList()).isEmpty());
    }

    @Test
    public void choicesFromCopiesProviderModelsAndFields() throws Exception {
        List<NoteTypeFieldMappings.Choice> choices = NoteTypeFieldMappings.choicesFrom(Collections.singletonList(
                providerNoteType("Mining", Arrays.asList("Front", "Back"))
        ));
        FakeInputs inputs = new FakeInputs();

        assertEquals(1, choices.size());
        assertEquals("Mining (2 fields)", NoteTypeFieldMappings.label(choices.get(0)));
        NoteTypeFieldMappings.chooseNoteType(choices.get(0), inputs);
        assertEquals("Mining", inputs.noteType);
        assertEquals("Front", inputs.expression);
        assertEquals("Back", inputs.meaning);
    }

    @Test
    public void presentNoteTypesBuildsChooserLabelsFromNamesAndFieldCounts() {
        FakeUi ui = new FakeUi();

        NoteTypeFieldMappings.presentNoteTypes(
                Arrays.asList(
                        choiceWithNullFields("Empty"),
                        choice("Kiku", "Expression"),
                        choice("Basic", "Front", "Back")
                ),
                new FakeInputs(),
                ui
        );

        assertEquals("Choose note type", ui.title);
        assertArrayEquals(new String[]{"Empty (0 fields)", "Kiku (1 field)", "Basic (2 fields)"}, ui.labels);
        assertTrue(ui.longMessages.isEmpty());
    }

    @Test
    public void choiceNormalizesNullNamesAndFieldsForLabels() {
        NoteTypeFieldMappings.Choice blank = new NoteTypeFieldMappings.Choice(null, null);

        assertEquals(" (0 fields)", NoteTypeFieldMappings.label(blank));
        assertArrayEquals(new String[]{" (0 fields)"}, NoteTypeFieldMappings.labels(Collections.singletonList(blank)));
        assertArrayEquals(new String[]{" (0 fields)"}, NoteTypeFieldMappings.labels(Collections.singletonList(null)));
    }

    @Test
    public void selectedNoteTypeFillsModelNameAndAppliesFieldGuesses() {
        FakeUi ui = new FakeUi();
        FakeInputs inputs = new FakeInputs();
        NoteTypeFieldMappings.presentNoteTypes(
                Arrays.asList(
                        choice("Wrong", "Front", "Back"),
                        choice("Kiku Mining", "Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
                ),
                inputs,
                ui
        );

        ui.select(1);

        assertEquals("Kiku Mining", inputs.noteType);
        assertEquals("Japanese", inputs.expression);
        assertEquals("Kana", inputs.reading);
        assertEquals("Definition", inputs.meaning);
        assertEquals("Context", inputs.sentence);
        assertEquals("Freq", inputs.frequency);
        assertEquals("FrequencySort", inputs.frequencySort);
    }

    @Test
    public void fieldGuessesFallBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        FakeInputs inputs = new FakeInputs();

        NoteTypeFieldMappings.applyFieldGuesses(Arrays.asList("表", "裏"), inputs);

        assertEquals("表", inputs.expression);
        assertEquals("裏", inputs.meaning);
        assertEquals("", inputs.reading);
        assertEquals("", inputs.sentence);
        assertEquals("", inputs.frequency);
        assertEquals("", inputs.frequencySort);
    }

    @Test
    public void fieldGuessesLeaveMappingsBlankWhenNoteTypeHasNoFields() {
        FakeInputs inputs = new FakeInputs();

        NoteTypeFieldMappings.applyFieldGuesses(Collections.emptyList(), inputs);

        assertEquals("", inputs.expression);
        assertEquals("", inputs.meaning);
        assertEquals("", inputs.reading);
        assertEquals("", inputs.sentence);
        assertEquals("", inputs.frequency);
        assertEquals("", inputs.frequencySort);
    }

    @Test
    public void chooseShowsGatewayExceptionMessageWithoutOpeningDialog() {
        FakeUi ui = new FakeUi();

        NoteTypeFieldMappings.choose(
                () -> {
                    throw AnkiDroidGateway.SyncFailure.retryable("Provider unavailable");
                },
                Runnable::run,
                Runnable::run,
                new FakeInputs(),
                ui
        );

        assertEquals(Collections.singletonList("Reading AnkiDroid note types."), ui.shortMessages);
        assertEquals(Collections.singletonList("Provider unavailable"), ui.longMessages);
        assertFalse(ui.dialogShown());
    }

    @Test
    public void firstMatchingFieldPrefersConfiguredCandidateWithCaseInsensitiveMatch() {
        List<String> fields = Arrays.asList("Front", "sentence", "expression", "Meaning");

        String match = NoteTypeFieldMappings.firstMatchingField(fields, "Expression", "Front", "Japanese");

        assertEquals("expression", match);
    }

    @Test
    public void firstMatchingFieldReturnsEmptyWhenNoCandidateMatches() {
        List<String> fields = Arrays.asList("Front", "Back", "Reading");

        String match = NoteTypeFieldMappings.firstMatchingField(fields, "Expression", "Meaning");

        assertEquals("", match);
    }

    @Test
    public void errorMessageFallsBackForNullAndBlankExceptionMessages() {
        String expected = "Could not read AnkiDroid note types.";

        assertEquals(expected, NoteTypeFieldMappings.errorMessage(new Exception()));
        assertEquals(expected, NoteTypeFieldMappings.errorMessage(new Exception("   ")));
    }

    @Test
    public void errorMessageUsesNonBlankExceptionMessage() {
        assertEquals("Provider unavailable", NoteTypeFieldMappings.errorMessage(new Exception("Provider unavailable")));
    }

    private static NoteTypeFieldMappings.Choice choice(String name, String... fields) {
        return new NoteTypeFieldMappings.Choice(name, Arrays.asList(fields));
    }

    private static NoteTypeFieldMappings.Choice choiceWithNullFields(String name) {
        return new NoteTypeFieldMappings.Choice(name, null);
    }

    private static dev.bee.kanjianki.anki.AnkiDroidGateway.NoteType providerNoteType(String name, List<String> fields) throws Exception {
        Constructor<dev.bee.kanjianki.anki.AnkiDroidGateway.NoteType> constructor =
                dev.bee.kanjianki.anki.AnkiDroidGateway.NoteType.class.getDeclaredConstructor(long.class, String.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(42L, name, fields);
    }

    private static final class FakeUi implements NoteTypeFieldMappings.ChooserUi {
        private final List<String> shortMessages = new ArrayList<>();
        private final List<String> longMessages = new ArrayList<>();
        private String title;
        private String[] labels;
        private NoteTypeFieldMappings.Selection selection;

        @Override
        public void showShortMessage(String message) {
            shortMessages.add(message);
        }

        @Override
        public void showLongMessage(String message) {
            longMessages.add(message);
        }

        @Override
        public void showNoteTypeChoices(String title, String[] labels, NoteTypeFieldMappings.Selection selection) {
            this.title = title;
            this.labels = labels;
            this.selection = selection;
        }

        private boolean dialogShown() {
            return labels != null;
        }

        private void select(int index) {
            selection.select(index);
        }
    }

    private static final class FakeInputs implements NoteTypeFieldMappings.FieldInputs {
        private String noteType = "";
        private String expression = "";
        private String reading = "";
        private String meaning = "";
        private String sentence = "";
        private String frequency = "";
        private String frequencySort = "";

        @Override
        public void setNoteType(String value) {
            noteType = value;
        }

        @Override
        public void setExpression(String value) {
            expression = value;
        }

        @Override
        public void setReading(String value) {
            reading = value;
        }

        @Override
        public void setMeaning(String value) {
            meaning = value;
        }

        @Override
        public void setSentence(String value) {
            sentence = value;
        }

        @Override
        public void setFrequency(String value) {
            frequency = value;
        }

        @Override
        public void setFrequencySort(String value) {
            frequencySort = value;
        }
    }
}
