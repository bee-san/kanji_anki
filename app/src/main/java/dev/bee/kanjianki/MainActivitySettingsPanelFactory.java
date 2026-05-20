package dev.bee.kanjianki;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.widget.CheckBox;

final class MainActivitySettingsPanelFactory {
    private final MainActivitySettings activity;

    MainActivitySettingsPanelFactory(MainActivitySettings activity) {
        this.activity = activity;
    }

    CheckBox importFilterCheckBox(String label, boolean checked) {
        CheckBox box = new CheckBox(activity);
        box.setText(label);
        box.setTextColor(activity.INK);
        box.setTextSize(17);
        box.setTypeface(Typeface.DEFAULT_BOLD);
        box.setChecked(checked);
        box.setButtonTintList(ColorStateList.valueOf(activity.STUDY_PINK_DARK));
        box.setPadding(0, activity.dp(4), 0, activity.dp(4));
        return box;
    }
}
