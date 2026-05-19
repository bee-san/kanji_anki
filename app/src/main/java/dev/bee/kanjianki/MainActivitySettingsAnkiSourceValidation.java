package dev.bee.kanjianki;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.Collections;
import java.util.List;

final class MainActivitySettingsAnkiSourceValidation {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceValidation(MainActivitySettings activity) {
        this.activity = activity;
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

    int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    double parseDecimalInput(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }
}
