package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.List;

final class MainActivitySettingsAnkiSourceImportFilters {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSource source;

    MainActivitySettingsAnkiSourceImportFilters(MainActivitySettings activity, MainActivitySettingsAnkiSource source) {
        this.activity = activity;
        this.source = source;
    }

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.importFiltersTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.settingsImportSummary(current), 17, activity.TEAL, true));
        box.addView(activity.text(SettingsTextCopy.importFiltersBody(), 15, activity.MUTED, false));
        source.addImportPresetButtons(box);

        CheckBox activeCards = activity.importFilterCheckBox(SettingsTextCopy.activeCardsLabel(), current.importActiveCards);
        CheckBox suspendedCards = activity.importFilterCheckBox(SettingsTextCopy.suspendedCardsLabel(), current.importSuspendedCards);
        CheckBox taggedCards = activity.importFilterCheckBox(SettingsTextCopy.taggedCardsLabel(), current.importTaggedCardsEnabled());
        CheckBox weakCards = activity.importFilterCheckBox(SettingsTextCopy.weakCardsLabel(), current.importWeakCards);
        CheckBox browserQueryCards = activity.importFilterCheckBox(SettingsTextCopy.browserQueryLabel(), current.importBrowserQueryCards);
        box.addView(activeCards);
        box.addView(suspendedCards);
        box.addView(taggedCards);
        box.addView(weakCards);
        box.addView(browserQueryCards);

        EditText browserQueryInput = source.fieldInput(current.importBrowserQuery);
        browserQueryInput.setHint(SettingsTextCopy.ankiBrowserQueryHint());
        source.addFieldMappingInput(box, SettingsTextCopy.ankiBrowserQueryLabel(), browserQueryInput);

        EditText tags = source.fieldInput(current.importTagsText());
        tags.setHint(SettingsTextCopy.ankiNoteTagsHint());
        source.addFieldMappingInput(box, SettingsTextCopy.ankiNoteTagsLabel(), tags);

        LinearLayout thresholds = new LinearLayout(activity);
        thresholds.setOrientation(LinearLayout.HORIZONTAL);
        EditText difficultyInput = source.decimalInput(current.importWeakFsrsDifficultyThreshold);
        LinearLayout difficultyColumn = source.inputColumn(SettingsTextCopy.fsrsDifficultyLabel(), difficultyInput, 0);
        EditText lapses = activity.thresholdInput(current.importWeakLapsesThreshold);
        LinearLayout lapsesColumn = source.inputColumn(SettingsTextCopy.lapsesLabel(), lapses, activity.dp(10));
        thresholds.addView(difficultyColumn, new LinearLayout.LayoutParams(0, -2, 1));
        thresholds.addView(lapsesColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(thresholds);

        EditText minMatching = activity.thresholdInput(current.importMinMatchingCardsPerKanji);
        source.addFieldMappingInput(box, SettingsTextCopy.minimumMatchingCardsLabel(), minMatching);

        Button save = activity.primaryButton(SettingsTextCopy.saveImportFiltersLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<String> parsedTags = RecordsBase.parseImportTags(tags.getText().toString());
            String queryText = browserQueryInput.getText().toString().trim();
            if (browserQueryCards.isChecked() && queryText.isEmpty()) {
                Toast.makeText(activity, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!source.hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText)) {
                Toast.makeText(activity, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            MainActivityBase.ImportThresholds parsedThresholds = source.readImportThresholds(difficultyInput, lapses, minMatching);
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
                    new SettingsWriteActions.SettingWriter() {
                        @Override
                        public void putIntSetting(String key, int value) {
                            activity.store.putIntSetting(key, value);
                        }

                        @Override
                        public void putStringSetting(String key, String value) {
                            activity.store.putStringSetting(key, value);
                        }

                        @Override
                        public void putDoubleSetting(String key, double value) {
                            activity.store.putDoubleSetting(key, value);
                        }
                    }
            );
            Toast.makeText(activity, SettingsTextCopy.importFiltersSavedToast(), Toast.LENGTH_LONG).show();
            activity.renderSettings();
        });
        box.addView(save);
        return box;
    }
}
