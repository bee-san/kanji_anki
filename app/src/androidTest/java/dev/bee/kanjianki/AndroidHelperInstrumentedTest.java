package dev.bee.kanjianki;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AndroidHelperInstrumentedTest {
    @Test
    public void noteTypeChooserReadsFakeProviderThroughAndroidEntryPoint() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        List<AnkiDroidGateway.NoteType> providerTypes = gateway.noteTypes();
        List<NoteTypeFieldMappings.Choice> choices = NoteTypeFieldMappings.choicesFrom(providerTypes);
        assertFalse(choices.isEmpty());

        DirectExecutorService direct = new DirectExecutorService();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> NoteTypeFieldMappings.choose(
                    activity,
                    gateway,
                    direct,
                    new Handler(Looper.getMainLooper()),
                    newInputBundle(activity).inputs
            ));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> NoteTypeFieldMappings.choose(
                    activity,
                    AnkiDroidGateway.testProvider(activity, "dev.bee.kanjianki.no_note_type_provider"),
                    direct,
                    new Handler(Looper.getMainLooper()),
                    newInputBundle(activity).inputs
            ));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertTrue(direct.executed);
    }

    @Test
    public void noteTypeChooserSelectionAppliesConfiguredFieldGuessesToRealInputs() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InputBundle inputBundle = newInputBundle(context);
        NoteTypeFieldMappings.Inputs inputs = inputBundle.inputs;
        FakeChooserUi ui = new FakeChooserUi();

        NoteTypeFieldMappings.presentNoteTypes(
                Collections.singletonList(
                        new NoteTypeFieldMappings.Choice(
                                "Kiku Mining",
                                Arrays.asList("Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
                        )
                ),
                inputs,
                ui
        );
        ui.select(0);

        assertEquals("Kiku Mining", inputBundle.noteType.getText().toString());
        assertEquals("Japanese", inputBundle.expression.getText().toString());
        assertEquals("Kana", inputBundle.reading.getText().toString());
        assertEquals("Definition", inputBundle.meaning.getText().toString());
        assertEquals("Context", inputBundle.sentence.getText().toString());
        assertEquals("Freq", inputBundle.frequency.getText().toString());
        assertEquals("FrequencySort", inputBundle.frequencySort.getText().toString());
    }

    @Test
    public void noteTypeMappingFallsBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText expression = new EditText(context);
        EditText reading = new EditText(context);
        EditText meaning = new EditText(context);
        NoteTypeFieldMappings.Inputs inputs = new NoteTypeFieldMappings.Inputs(
                new EditText(context),
                expression,
                reading,
                meaning,
                new EditText(context),
                new EditText(context),
                new EditText(context)
        );

        NoteTypeFieldMappings.applyFieldGuesses(
                Arrays.asList("A", "B"),
                inputs
        );

        assertEquals("A", expression.getText().toString());
        assertEquals("B", meaning.getText().toString());
        assertEquals("", reading.getText().toString());
    }

    @Test
    public void noteTypeMappingLeavesEmptyFieldsWhenThereIsNothingToGuess() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText expression = new EditText(context);
        EditText meaning = new EditText(context);
        NoteTypeFieldMappings.Inputs inputs = new NoteTypeFieldMappings.Inputs(
                new EditText(context),
                expression,
                new EditText(context),
                meaning,
                new EditText(context),
                new EditText(context),
                new EditText(context)
        );

        NoteTypeFieldMappings.applyFieldGuesses(
                Collections.emptyList(),
                inputs
        );

        assertEquals("", expression.getText().toString());
        assertEquals("", meaning.getText().toString());
    }

    @Test
    public void attributionTextsReadBundledResourcesAndDictionaryManifest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String kanjiVg = AttributionTexts.kanjiVg(context);
        String dictionary = AttributionTexts.dictionarySources(context);

        assertFalse(kanjiVg.isBlank());
        assertTrue(kanjiVg.contains("KanjiVG"));
        assertFalse(dictionary.isBlank());
        assertTrue(dictionary.contains("KANJIDIC2") || dictionary.contains("Dictionary"));
        assertEquals(
                "Dictionary manifest is empty.",
                AttributionTexts.dictionarySourcesFromManifestText("{\"sources\":[]}")
        );
        assertEquals(
                "Dictionary manifest is empty.",
                AttributionTexts.dictionarySourcesFromManifestText("{}")
        );
        assertEquals(
                "kanjidic2\nVersion: 2026\nSHA-256: abc",
                AttributionTexts.dictionarySourcesFromManifestText(
                        "{\"sources\":[{\"id\":\"kanjidic2\",\"source_sha256\":\"abc\",\"database_version\":\"2026\"}]}"
                )
        );
    }

    private static final class FakeChooserUi implements NoteTypeFieldMappings.ChooserUi {
        private NoteTypeFieldMappings.Selection selection;

        @Override
        public void showShortMessage(String message) {
            // This fake only captures chooser selections.
        }

        @Override
        public void showLongMessage(String message) {
            // This fake only captures chooser selections.
        }

        @Override
        public void showNoteTypeChoices(String title, String[] labels, NoteTypeFieldMappings.Selection selection) {
            this.selection = selection;
        }

        private void select(int index) {
            selection.select(index);
        }
    }

    private static InputBundle newInputBundle(Context context) {
        EditText noteType = new EditText(context);
        EditText expression = new EditText(context);
        EditText reading = new EditText(context);
        EditText meaning = new EditText(context);
        EditText sentence = new EditText(context);
        EditText frequency = new EditText(context);
        EditText frequencySort = new EditText(context);
        return new InputBundle(
                noteType,
                expression,
                reading,
                meaning,
                sentence,
                frequency,
                frequencySort,
                new NoteTypeFieldMappings.Inputs(
                        noteType,
                        expression,
                        reading,
                        meaning,
                        sentence,
                        frequency,
                        frequencySort
                )
        );
    }

    private record InputBundle(
            EditText noteType,
            EditText expression,
            EditText reading,
            EditText meaning,
            EditText sentence,
            EditText frequency,
            EditText frequencySort,
            NoteTypeFieldMappings.Inputs inputs
    ) {
        // Fake chooser drives the selection directly through showNoteTypeChoices.
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;
        private boolean terminated;
        private boolean executed;

        @Override
        public void shutdown() {
            shutdown = true;
            terminated = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            terminated = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return terminated;
        }

        @Override
        public void execute(Runnable command) {
            executed = true;
            command.run();
        }
    }
}
