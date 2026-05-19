package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        this.activity = activity;
        this.frequencyRange = new MainActivitySettingsAnkiSourceFrequencyRange(activity, this);
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.noteTypeFieldsTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.noteTypeUsingText(current.modelName), 17, activity.TEAL, true));
        box.addView(activity.text(SettingsTextCopy.noteTypeFieldsBody(), 15, activity.MUTED, false));

        EditText noteType = noteTypeInput(current.modelName);
        box.addView(noteType, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        EditText expressionField = fieldInput(current.expressionField);
        EditText readingField = fieldInput(current.readingField);
        EditText meaningField = fieldInput(current.meaningField);
        EditText sentenceField = fieldInput(current.sentenceField);
        EditText frequencyField = fieldInput(current.frequencyField);
        EditText frequencySortField = fieldInput(current.frequencySortField);
        box.addView(activity.text(SettingsTextCopy.requiredFieldsTitle(), 15, activity.STUDY_PLUM, true));
        box.addView(activity.text(SettingsTextCopy.requiredFieldsBody(), 14, activity.MUTED, false));
        addFieldMappingInput(box, SettingsTextCopy.expressionFieldLabel(), expressionField);
        addFieldMappingInput(box, SettingsTextCopy.readingFieldLabel(), readingField);
        addFieldMappingInput(box, SettingsTextCopy.meaningFieldLabel(), meaningField);
        addFieldMappingInput(box, SettingsTextCopy.sentenceFieldLabel(), sentenceField);
        addFieldMappingInput(box, SettingsTextCopy.frequencyFieldLabel(), frequencyField);
        addFieldMappingInput(box, SettingsTextCopy.frequencySortFieldLabel(), frequencySortField);

        NoteTypeFieldMappings.Inputs fieldMappings = new NoteTypeFieldMappings.Inputs(
                noteType,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField
        );
        Button choose = activity.secondaryButton(SettingsTextCopy.chooseFromAnkiDroidLabel());
        choose.setOnClickListener(v -> NoteTypeFieldMappings.choose(activity, activity.gateway, activity.io, activity.main, fieldMappings));
        box.addView(choose);
        Button kiku = activity.secondaryButton(SettingsTextCopy.useKikuLabel());
        kiku.setOnClickListener(v -> {
            noteType.setText(defaults.modelName);
            expressionField.setText(defaults.expressionField);
            readingField.setText(defaults.readingField);
            meaningField.setText(defaults.meaningField);
            sentenceField.setText(defaults.sentenceField);
            frequencyField.setText(defaults.frequencyField);
            frequencySortField.setText(defaults.frequencySortField);
        });
        box.addView(kiku);

        Button save = activity.primaryButton(SettingsTextCopy.saveNoteTypeLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            String selected = noteType.getText().toString().trim();
            if (selected.isEmpty()) {
                Toast.makeText(activity, SettingsTextCopy.noteTypeRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (expressionField.getText().toString().trim().isEmpty()) {
                Toast.makeText(activity, SettingsTextCopy.expressionFieldRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsWriteActions.saveNoteTypeFields(
                    new SettingsWriteActions.NoteTypeFieldWriteRequest(
                            selected,
                            expressionField.getText().toString().trim(),
                            readingField.getText().toString().trim(),
                            meaningField.getText().toString().trim(),
                            sentenceField.getText().toString().trim(),
                            frequencyField.getText().toString().trim(),
                            frequencySortField.getText().toString().trim()
                    ),
                    activity.store::putStringSetting
            );
            Toast.makeText(activity, SettingsTextCopy.noteTypeSavedToast(), Toast.LENGTH_LONG).show();
            activity.renderSettings();
        });
        box.addView(save);
        return box;
    }

    EditText noteTypeInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null || value.trim().isEmpty() ? RecordsSyncModels.Settings.kikuDefaults().modelName : value.trim());
        input.setHint(RecordsSyncModels.Settings.kikuDefaults().modelName);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText fieldInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null ? "" : value.trim());
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        box.addView(activity.text(label, 14, activity.INK, true));
        box.addView(input, new LinearLayout.LayoutParams(-1, activity.dp(52)));
    }

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.importFiltersTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.settingsImportSummary(current), 17, activity.TEAL, true));
        box.addView(activity.text(SettingsTextCopy.importFiltersBody(), 15, activity.MUTED, false));
        addImportPresetButtons(box);

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

        EditText browserQueryInput = fieldInput(current.importBrowserQuery);
        browserQueryInput.setHint(SettingsTextCopy.ankiBrowserQueryHint());
        addFieldMappingInput(box, SettingsTextCopy.ankiBrowserQueryLabel(), browserQueryInput);

        EditText tags = fieldInput(current.importTagsText());
        tags.setHint(SettingsTextCopy.ankiNoteTagsHint());
        addFieldMappingInput(box, SettingsTextCopy.ankiNoteTagsLabel(), tags);

        LinearLayout thresholds = new LinearLayout(activity);
        thresholds.setOrientation(LinearLayout.HORIZONTAL);
        EditText difficultyInput = decimalInput(current.importWeakFsrsDifficultyThreshold);
        LinearLayout difficultyColumn = inputColumn(SettingsTextCopy.fsrsDifficultyLabel(), difficultyInput, 0);
        EditText lapses = activity.thresholdInput(current.importWeakLapsesThreshold);
        LinearLayout lapsesColumn = inputColumn(SettingsTextCopy.lapsesLabel(), lapses, activity.dp(10));
        thresholds.addView(difficultyColumn, new LinearLayout.LayoutParams(0, -2, 1));
        thresholds.addView(lapsesColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(thresholds);

        EditText minMatching = activity.thresholdInput(current.importMinMatchingCardsPerKanji);
        addFieldMappingInput(box, SettingsTextCopy.minimumMatchingCardsLabel(), minMatching);

        Button save = activity.primaryButton(SettingsTextCopy.saveImportFiltersLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<String> parsedTags = RecordsBase.parseImportTags(tags.getText().toString());
            String queryText = browserQueryInput.getText().toString().trim();
            if (browserQueryCards.isChecked() && queryText.isEmpty()) {
                Toast.makeText(activity, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText)) {
                Toast.makeText(activity, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            MainActivityBase.ImportThresholds parsedThresholds = readImportThresholds(difficultyInput, lapses, minMatching);
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

    void addImportPresetButtons(LinearLayout box) {
        box.addView(activity.text(SettingsTextCopy.presetsTitle(), 17, activity.INK, true));
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (SettingsImportPreset preset : SettingsImportPreset.defaults()) {
            Button button = activity.secondaryButton(preset.label());
            button.setOnClickListener(v -> {
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
                Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show();
                activity.renderSettings();
            });
            grid.addView(button);
        }
        box.addView(grid);
    }

    MainActivityBase.ImportThresholds readImportThresholds(EditText difficultyInput, EditText lapses, EditText minMatching) {
        double difficulty;
        int lapseThreshold;
        int minCards;
        try {
            difficulty = parseDecimalInput(difficultyInput);
            lapseThreshold = activity.parseThresholdInput(lapses);
            minCards = activity.parseThresholdInput(minMatching);
        } catch (NumberFormatException error) {
            Toast.makeText(activity, SettingsTextCopy.numericImportThresholdsToast(), Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!SettingsInputRules.validImportThresholds(difficulty, lapseThreshold, minCards)) {
            Toast.makeText(activity, SettingsTextCopy.importThresholdRangeToast(), Toast.LENGTH_SHORT).show();
            return null;
        }
        return new MainActivityBase.ImportThresholds(difficulty, lapseThreshold, minCards);
    }

    boolean hasSelectedImportSource(
            CheckBox activeCards,
            CheckBox suspendedCards,
            CheckBox taggedCards,
            CheckBox weakCards,
            CheckBox browserQueryCards,
            List<String> parsedTags,
            String queryText
    ) {
        if (activeCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null);
        }
        if (suspendedCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null);
        }
        if (weakCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null);
        }
        if (taggedCards.isChecked() && SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, parsedTags, "")) {
            return true;
        }
        return SettingsInputRules.hasSelectedImportSource(
                false,
                false,
                false,
                false,
                browserQueryCards.isChecked(),
                Collections.emptyList(),
                queryText
        );
    }

    LinearLayout inputColumn(String label, EditText input, int leftPadding) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(leftPadding, 0, 0, 0);
        column.addView(activity.text(label, 15, activity.INK, true));
        column.addView(input, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        return column;
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return frequencyRange.frequencyRangeSettingsPanel(current);
    }

    EditText rankInput(int value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", value));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText decimalInput(double value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.ROOT, "%.1f", value));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    void bindRankSliders(
            int[] selected,
            TextView status,
            EditText minInput,
            EditText maxInput,
            SeekBar minSlider,
            SeekBar maxSlider
    ) {
        minSlider.setMax(19999);
        maxSlider.setMax(19999);
        minSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
        maxSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));

        minSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = Math.min(SettingsInputRules.rankFromSliderProgress(progress), selected[1]);
                minInput.setText(String.format(Locale.ROOT, "%d", selected[0]));
                status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
            }
        });
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[1] = Math.max(SettingsInputRules.rankFromSliderProgress(progress), selected[0]);
                maxInput.setText(String.format(Locale.ROOT, "%d", selected[1]));
                status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));
            }
        });
    }

    int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    double parseDecimalInput(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }
}
