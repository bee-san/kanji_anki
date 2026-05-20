package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MainActivitySettingsAnkiSourceImportFilters {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceValidation validation;

    MainActivitySettingsAnkiSourceImportFilters(
            MainActivitySettings activity,
            MainActivitySettingsAnkiSourceValidation validation
    ) {
        this.activity = activity;
        this.validation = validation;
    }

    View importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        SettingsImportFiltersState state = new SettingsImportFiltersState(
                current.importActiveCards,
                current.importSuspendedCards,
                current.importTaggedCardsEnabled(),
                current.importWeakCards,
                current.importBrowserQueryCards,
                trimmed(current.importBrowserQuery),
                trimmed(current.importTagsText()),
                decimalText(current.importWeakFsrsDifficultyThreshold),
                thresholdText(current.importWeakLapsesThreshold),
                thresholdText(current.importMinMatchingCardsPerKanji)
        );

        return MainActivitySettingsAnkiSourceImportFiltersCompose.importFiltersSettingsPanelView(
                activity,
                new SettingsImportFiltersPanelModel(
                        SettingsTextCopy.importFiltersTitle(),
                        SettingsTextCopy.settingsImportSummary(current),
                        SettingsTextCopy.importFiltersBody(),
                        SettingsTextCopy.presetsTitle(),
                        presetButtons(),
                        state,
                        SettingsTextCopy.activeCardsLabel(),
                        SettingsTextCopy.suspendedCardsLabel(),
                        SettingsTextCopy.taggedCardsLabel(),
                        SettingsTextCopy.weakCardsLabel(),
                        SettingsTextCopy.browserQueryLabel(),
                        SettingsTextCopy.ankiBrowserQueryLabel(),
                        SettingsTextCopy.ankiBrowserQueryHint(),
                        SettingsTextCopy.ankiNoteTagsLabel(),
                        SettingsTextCopy.ankiNoteTagsHint(),
                        SettingsTextCopy.fsrsDifficultyLabel(),
                        SettingsTextCopy.lapsesLabel(),
                        SettingsTextCopy.minimumMatchingCardsLabel(),
                        SettingsTextCopy.saveImportFiltersLabel(),
                        () -> saveImportFilters(state)
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

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String decimalText(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String thresholdText(int value) {
        return Integer.toString(Math.max(1, value));
    }

    private void saveImportFilters(SettingsImportFiltersState state) {
        List<String> parsedTags = RecordsBase.parseImportTags(state.getTags());
        String queryText = state.getBrowserQuery().trim();
        if (state.getBrowserQueryCards() && queryText.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validation.hasSelectedImportSource(
                state.getActiveCards(),
                state.getSuspendedCards(),
                state.getTaggedCards(),
                state.getWeakCards(),
                state.getBrowserQueryCards(),
                parsedTags,
                queryText
        )) {
            Toast.makeText(activity, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        MainActivityBase.ImportThresholds parsedThresholds = validation.readImportThresholds(
                state.getDifficulty(),
                state.getLapses(),
                state.getMinMatching()
        );
        if (parsedThresholds == null) {
            return;
        }
        SettingsWriteActions.saveImportFilters(
                new SettingsWriteActions.ImportFilterWriteRequest(
                        state.getActiveCards(),
                        state.getSuspendedCards(),
                        state.getTaggedCards(),
                        String.join(" ", parsedTags),
                        state.getWeakCards(),
                        parsedThresholds.difficulty,
                        parsedThresholds.lapseThreshold,
                        parsedThresholds.minCards,
                        state.getBrowserQueryCards(),
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
