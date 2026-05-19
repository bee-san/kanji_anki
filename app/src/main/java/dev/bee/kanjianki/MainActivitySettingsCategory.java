package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsCategory {
    private final MainActivitySettings activity;

    MainActivitySettingsCategory(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout settingsCategory(
            String title,
            String summary,
            int iconRes,
            boolean expanded,
            Runnable toggle,
            View... panels
    ) {
        LinearLayout category = new LinearLayout(activity);
        category.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, activity.dp(7), 0, activity.dp(9));
        category.setLayoutParams(lp);

        category.addView(
                MainActivitySettingsCategoryCompose.settingsCategoryHeaderView(
                        activity,
                        title,
                        summary,
                        iconRes,
                        expanded,
                        SettingsTextCopy.settingsCategoryPanelCount(panels.length),
                        activity.STUDY_PINK_DARK,
                        activity.STUDY_BORDER,
                        activity.STUDY_PLUM,
                        activity.STUDY_MUTED,
                        activity.STUDY_PINK_DARK,
                        SettingsTextCopy.categoryToggleDescription(expanded, title),
                        toggle
                )
        );

        if (expanded) {
            for (View panel : panels) {
                category.addView(panel);
            }
        }
        return category;
    }
}
