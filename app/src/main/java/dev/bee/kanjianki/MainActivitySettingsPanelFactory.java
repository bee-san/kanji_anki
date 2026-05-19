package dev.bee.kanjianki;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.CheckBox;
import android.widget.LinearLayout;

final class MainActivitySettingsPanelFactory {
    private final MainActivitySettings activity;

    MainActivitySettingsPanelFactory(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout settingsPanelBox() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(18), activity.dp(17), activity.dp(18), activity.dp(18));
        box.setBackground(activity.panel(Color.rgb(255, 253, 254), activity.STUDY_BORDER, activity.dp(24)));
        box.setElevation(activity.dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, activity.dp(8), 0, activity.dp(6));
        box.setLayoutParams(lp);
        return box;
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
