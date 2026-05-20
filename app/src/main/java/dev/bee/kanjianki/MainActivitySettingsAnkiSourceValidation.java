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
        return readImportThresholds(
                difficultyInput.getText().toString(),
                lapses.getText().toString(),
                minMatching.getText().toString()
        );
    }

    MainActivityBase.ImportThresholds readImportThresholds(String difficultyInput, String lapses, String minMatching) {
        double difficulty;
        int lapseThreshold;
        int minCards;
        try {
            difficulty = parseDecimalText(difficultyInput);
            lapseThreshold = parseThresholdText(lapses);
            minCards = parseThresholdText(minMatching);
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
            boolean activeCards,
            boolean suspendedCards,
            boolean taggedCards,
            boolean weakCards,
            boolean browserQueryCards,
            List<String> parsedTags,
            String queryText
    ) {
        if (activeCards) {
            return SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null);
        }
        if (suspendedCards) {
            return SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null);
        }
        if (weakCards) {
            return SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null);
        }
        if (taggedCards && SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, parsedTags, "")) {
            return true;
        }
        return SettingsInputRules.hasSelectedImportSource(
                false,
                false,
                false,
                false,
                browserQueryCards,
                Collections.emptyList(),
                queryText
        );
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
        return hasSelectedImportSource(
                activeCards != null && activeCards.isChecked(),
                suspendedCards != null && suspendedCards.isChecked(),
                taggedCards != null && taggedCards.isChecked(),
                weakCards != null && weakCards.isChecked(),
                browserQueryCards != null && browserQueryCards.isChecked(),
                parsedTags,
                queryText
        );
    }

    int parseRankText(String input) {
        return Integer.parseInt(input.trim());
    }

    double parseDecimalInput(EditText input) {
        return parseDecimalText(input.getText().toString());
    }

    private static int parseThresholdText(String input) {
        return Integer.parseInt(input.trim());
    }

    private static double parseDecimalText(String input) {
        return Double.parseDouble(input.trim());
    }
}
