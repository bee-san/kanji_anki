package dev.bee.kanjianki;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MainActivitySettingsAnkiSourceImportFilters {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceValidation validation;

    MainActivitySettingsAnkiSourceImportFilters(
            MainActivitySettings activity,
            MainActivitySettingsAnkiSourceInputs inputs,
            MainActivitySettingsAnkiSourceValidation validation
    ) {
        this.activity = activity;
        this.inputs = inputs;
        this.validation = validation;
    }

    View importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        CheckBox activeCards = activity.importFilterCheckBox(SettingsTextCopy.activeCardsLabel(), current.importActiveCards);
        CheckBox suspendedCards = activity.importFilterCheckBox(SettingsTextCopy.suspendedCardsLabel(), current.importSuspendedCards);
        CheckBox taggedCards = activity.importFilterCheckBox(SettingsTextCopy.taggedCardsLabel(), current.importTaggedCardsEnabled());
        CheckBox weakCards = activity.importFilterCheckBox(SettingsTextCopy.weakCardsLabel(), current.importWeakCards);
        CheckBox browserQueryCards = activity.importFilterCheckBox(SettingsTextCopy.browserQueryLabel(), current.importBrowserQueryCards);

        EditText browserQueryInput = inputs.fieldInput(current.importBrowserQuery);
        browserQueryInput.setHint(SettingsTextCopy.ankiBrowserQueryHint());
        browserQueryInput.setContentDescription(SettingsTextCopy.ankiBrowserQueryLabel());

        EditText tags = inputs.fieldInput(current.importTagsText());
        tags.setHint(SettingsTextCopy.ankiNoteTagsHint());
        tags.setContentDescription(SettingsTextCopy.ankiNoteTagsLabel());

        EditText difficultyInput = inputs.decimalInput(current.importWeakFsrsDifficultyThreshold);
        difficultyInput.setContentDescription(SettingsTextCopy.fsrsDifficultyLabel());
        EditText lapses = activity.thresholdInput(current.importWeakLapsesThreshold);
        lapses.setContentDescription(SettingsTextCopy.lapsesLabel());

        EditText minMatching = activity.thresholdInput(current.importMinMatchingCardsPerKanji);
        minMatching.setContentDescription(SettingsTextCopy.minimumMatchingCardsLabel());

        return MainActivitySettingsAnkiSourceImportFiltersCompose.importFiltersSettingsPanelView(
                activity,
                new SettingsImportFiltersPanelModel(
                        SettingsTextCopy.importFiltersTitle(),
                        SettingsTextCopy.settingsImportSummary(current),
                        SettingsTextCopy.importFiltersBody(),
                        SettingsTextCopy.presetsTitle(),
                        presetButtons(),
                        Arrays.asList(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards),
                        fieldModel(SettingsTextCopy.ankiBrowserQueryLabel(), browserQueryInput, 52),
                        fieldModel(SettingsTextCopy.ankiNoteTagsLabel(), tags, 52),
                        fieldModel(SettingsTextCopy.fsrsDifficultyLabel(), difficultyInput, 58),
                        fieldModel(SettingsTextCopy.lapsesLabel(), lapses, 58),
                        fieldModel(SettingsTextCopy.minimumMatchingCardsLabel(), minMatching, 58),
                        SettingsTextCopy.saveImportFiltersLabel(),
                        () -> saveImportFilters(
                                activeCards,
                                suspendedCards,
                                taggedCards,
                                weakCards,
                                browserQueryCards,
                                tags,
                                browserQueryInput,
                                difficultyInput,
                                lapses,
                                minMatching
                        )
                )
        );
    }

    private List<SettingsImportPresetButtonModel> presetButtons() {
        List<SettingsImportPresetButtonModel> models = new ArrayList<>();
        for (SettingsImportPreset preset : SettingsImportPreset.defaults()) {
            models.add(new SettingsImportPresetButtonModel(preset.label(), () -> applyPreset(preset)));
        }
        return models;
    }

    private SettingsImportFilterFieldModel fieldModel(String label, EditText input, int heightDp) {
        return new SettingsImportFilterFieldModel(label, input, heightDp);
    }

    private void saveImportFilters(
            CheckBox activeCards,
            CheckBox suspendedCards,
            CheckBox taggedCards,
            CheckBox weakCards,
            CheckBox browserQueryCards,
            EditText tags,
            EditText browserQueryInput,
            EditText difficultyInput,
            EditText lapses,
            EditText minMatching
    ) {
        List<String> parsedTags = RecordsBase.parseImportTags(tags.getText().toString());
        String queryText = browserQueryInput.getText().toString().trim();
        if (browserQueryCards.isChecked() && queryText.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validation.hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText)) {
            Toast.makeText(activity, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        MainActivityBase.ImportThresholds parsedThresholds = validation.readImportThresholds(difficultyInput, lapses, minMatching);
        if (parsedThresholds == null) {
            return;
        }
        SettingsWriteActions.saveImportFilters(
                new SettingsWriteActions.ImportFilterWriteRequest(
                        activeCards.isChecked(),
                        suspendedCards.isChecked(),
                        taggedCards.isChecked(),
                        String.join(" ", parsedTags),
                        weakCards.isChecked(),
                        parsedThresholds.difficulty,
                        parsedThresholds.lapseThreshold,
                        parsedThresholds.minCards,
                        browserQueryCards.isChecked(),
                        queryText
                ),
                new MainActivitySettingsAnkiSourceWriter(activity)
        );
        Toast.makeText(activity, SettingsTextCopy.importFiltersSavedToast(), Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }

    private void applyPreset(SettingsImportPreset preset) {
        SettingsWriteActions.saveImportFilters(
                new SettingsWriteActions.ImportFilterWriteRequest(
                        preset.activeCards(),
                        preset.suspendedCards(),
                        preset.taggedCards(),
                        preset.tags(),
                        preset.weakCards(),
                        preset.weakDifficulty(),
                        preset.weakLapses(),
                        preset.minMatchingCards(),
                        preset.browserQueryCards(),
                        preset.browserQuery()
                ),
                new MainActivitySettingsAnkiSourceWriter(activity)
        );
        Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }
}
