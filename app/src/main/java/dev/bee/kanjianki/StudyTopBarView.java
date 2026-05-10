package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StudyTopBarView extends LinearLayout {
    private static final int STUDY_HERO_PLUM = 0xFF7A245D;
    private static final int STUDY_HERO_PINK = 0xFFF82D72;
    private static final int STUDY_HERO_PINK_DARK = 0xFFE62A6D;
    private static final int STUDY_HERO_TRACK = 0xFFFBDDEC;

    StudyTopBarView(Context context) {
        this(context, null);
    }

    StudyTopBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0, 0f, () -> { }, () -> { });
    }

    StudyTopBarView(Context context, int completed, int target, float fraction, Runnable closeAction, Runnable settingsAction) {
        this(context, null, completed, target, fraction, closeAction, settingsAction);
    }

    private StudyTopBarView(Context context, AttributeSet attrs, int completed, int target, float fraction, Runnable closeAction, Runnable settingsAction) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(8));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconButton(context, R.drawable.ic_close_24, "Close study", closeAction), new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView progress = text(context, completed + " / " + target, 18, STUDY_HERO_PLUM, true);
        progress.setGravity(Gravity.CENTER);
        progress.setIncludeFontPadding(false);
        center.addView(progress, new LinearLayout.LayoutParams(-1, -2));

        StudyProgressPillView progressPill = new StudyProgressPillView(context, STUDY_HERO_TRACK, STUDY_HERO_PINK);
        progressPill.setFraction(fraction);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(180), dp(7));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.setMargins(0, dp(8), 0, 0);
        center.addView(progressPill, progressLp);

        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, -2, 1);
        centerLp.setMargins(dp(10), 0, dp(10), 0);
        row.addView(center, centerLp);
        row.addView(iconButton(context, R.drawable.ic_settings_24, "Settings", settingsAction), new LinearLayout.LayoutParams(dp(56), dp(56)));
        addView(row);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(18));
        setLayoutParams(lp);
    }

    private View iconButton(Context context, int iconRes, String description, Runnable action) {
        FrameLayout button = new FrameLayout(context);
        button.setBackground(panel(Color.rgb(255, 242, 248), Color.TRANSPARENT, dp(28)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(description);
        button.setElevation(dp(3));
        button.setOnClickListener(v -> action.run());
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(STUDY_HERO_PINK_DARK);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        button.addView(icon, iconLp);
        return button;
    }

    private TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
