package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(activity.dp(16), activity.dp(16), activity.dp(14), activity.dp(16));
        header.setBackground(activity.panel(expanded ? Color.WHITE : Color.rgb(255, 246, 251), activity.STUDY_BORDER, activity.dp(26)));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription(SettingsTextCopy.categoryToggleDescription(expanded, title));
        header.setOnClickListener(v -> toggle.run());
        header.setElevation(activity.dp(3));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.STUDY_PINK_DARK);
        icon.setBackground(activity.panel(Color.rgb(255, 237, 246), Color.TRANSPARENT, activity.dp(16)));
        icon.setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40));
        iconLp.setMargins(0, 0, activity.dp(12), 0);
        header.addView(icon, iconLp);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = activity.text(title, 21, activity.STUDY_PLUM, true);
        heading.setIncludeFontPadding(false);
        copy.addView(heading);
        TextView detail = activity.text(summary, 14, activity.STUDY_MUTED, false);
        detail.setPadding(0, activity.dp(4), 0, 0);
        copy.addView(detail);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView count = activity.text(SettingsTextCopy.settingsCategoryPanelCount(panels.length), 12, activity.STUDY_PINK_DARK, true);
        count.setGravity(Gravity.CENTER);
        count.setIncludeFontPadding(false);
        count.setPadding(activity.dp(9), activity.dp(6), activity.dp(9), activity.dp(6));
        count.setBackground(activity.panel(Color.rgb(255, 242, 248), activity.STUDY_BORDER, activity.dp(16)));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.setMargins(activity.dp(10), 0, activity.dp(8), 0);
        header.addView(count, countLp);

        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_arrow_forward_24);
        arrow.setColorFilter(activity.STUDY_PINK_DARK);
        arrow.setRotation(expanded ? 90f : 0f);
        header.addView(arrow, new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)));
        category.addView(header);

        if (expanded) {
            for (View panel : panels) {
                category.addView(panel);
            }
        }
        return category;
    }
}
