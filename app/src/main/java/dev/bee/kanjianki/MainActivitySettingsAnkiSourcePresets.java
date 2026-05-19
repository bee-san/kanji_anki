package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourcePresets {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourcePresets(MainActivitySettings activity) {
        this.activity = activity;
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
                        new MainActivitySettingsAnkiSourceWriter(activity)
                );
                Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show();
                activity.renderSettings();
            });
            grid.addView(button);
        }
        box.addView(grid);
    }
}
