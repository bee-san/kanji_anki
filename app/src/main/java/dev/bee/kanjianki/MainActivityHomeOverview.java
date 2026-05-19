package dev.bee.kanjianki;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;

final class MainActivityHomeOverview {
    private final MainActivityHome home;

    MainActivityHomeOverview(MainActivityHome home) {
        this.home = home;
    }

    View homeHeader() {
        return MainActivityHomeOverviewCompose.homeHeaderView(home);
    }

    View homeMetricRow(LocalStore.SyncStatus sync, AnkiDroidGateway.ProviderStatus provider, StudyStatsStore.StudyStreak streak, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        LinearLayout row = new MainActivityUiSupport.EqualHeightRow(home);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(metricCard(
                R.drawable.ic_sync_24,
                home.TEAL,
                HomeTextCopy.syncMetricLabel(),
                HomeTextCopy.homeSyncValue(sync == null ? null : sync.finishedAt),
                HomeTextCopy.syncMetricStatus(provider.canSync && sync != null && "success".equals(sync.status)),
                home::confirmSync
        ));
        row.addView(metricCard(
                R.drawable.ic_flame_24,
                home.streakAccent(streak),
                HomeTextCopy.streakMetricLabel(),
                HomeTextCopy.streakHeadline(streak == null ? 0 : streak.currentDays),
                HomeTextCopy.streakMetricBody(streak != null && streak.studiedToday, streak == null ? 0 : streak.bestDays),
                null
        ));
        row.addView(metricCard(
                R.drawable.ic_target_24,
                home.CORAL,
                HomeTextCopy.focusMetricLabel(),
                HomeTextCopy.focusHeadline(plan),
                null,
                null
        ));
        return row;
    }

    View metricCard(int iconRes, int accent, String label, String value, String body, Runnable action) {
        LinearLayout card = home.panelBox(Color.WHITE, home.softened(accent));
        card.setPadding(home.dp(11), home.dp(11), home.dp(11), home.dp(11));
        card.setGravity(Gravity.TOP);
        card.setMinimumHeight(home.dp(136));
        ImageView icon = new ImageView(home);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(home.dp(22), home.dp(22));
        iconLp.setMargins(0, 0, 0, home.dp(5));
        card.addView(icon, iconLp);

        TextView labelText = home.text(label, 12, accent, true);
        labelText.setIncludeFontPadding(false);
        labelText.setSingleLine(true);
        card.addView(labelText);

        TextView valueText = home.text(value, 14, home.INK, true);
        valueText.setIncludeFontPadding(false);
        valueText.setSingleLine(false);
        valueText.setMaxLines(2);
        valueText.setPadding(0, home.dp(5), 0, home.dp(2));
        card.addView(valueText);

        if (body != null && !body.isEmpty()) {
            TextView bodyText = home.text(StudyTextCopy.compact(body, 18), 11, home.MUTED, false);
            bodyText.setIncludeFontPadding(false);
            bodyText.setSingleLine(true);
            bodyText.setPadding(0, home.dp(3), 0, 0);
            card.addView(bodyText);
        }
        if (action != null) {
            card.setClickable(true);
            card.setOnClickListener(new RunnableClickListener(action));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(home.dp(4), 0, home.dp(4), 0);
        card.setLayoutParams(lp);
        return card;
    }

    View homeStudyCta() {
        FrameLayout button = new FrameLayout(home);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(255, 116, 156), Color.rgb(255, 58, 112) }
        );
        background.setCornerRadius(home.dp(24));
        background.setStroke(home.dp(2), Color.rgb(255, 190, 214));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(home.dp(24));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(38, 255, 255, 255)),
                background,
                mask
        ));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(MainActivityBase.LABEL_STUDY_NOW);
        button.setMinimumHeight(home.dp(94));
        button.setElevation(home.dp(9));
        button.setTranslationZ(home.dp(2));
        button.setClipToOutline(true);

        LinearLayout copy = new LinearLayout(home);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = home.text(MainActivityBase.LABEL_STUDY_NOW, 26, Color.WHITE, true);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(0);
        copy.addView(title);
        TextView support = home.text(HomeTextCopy.studySupportText(), 13, Color.rgb(255, 245, 250), true);
        support.setIncludeFontPadding(false);
        support.setSingleLine(true);
        support.setPadding(0, home.dp(5), 0, 0);
        copy.addView(support);
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(-1, -1);
        copyLp.setMargins(home.dp(26), 0, home.dp(92), 0);
        button.addView(copy, copyLp);

        FrameLayout arrowChip = new FrameLayout(home);
        arrowChip.setBackground(home.panel(Color.WHITE, Color.WHITE, home.dp(25)));
        arrowChip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView arrow = new ImageView(home);
        arrow.setImageResource(R.drawable.ic_arrow_forward_24);
        arrow.setColorFilter(home.CORAL);
        FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(home.dp(24), home.dp(24), Gravity.CENTER);
        arrowChip.addView(arrow, arrowLp);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(home.dp(50), home.dp(50), Gravity.END | Gravity.CENTER_VERTICAL);
        chipLp.setMargins(0, 0, home.dp(22), 0);
        button.addView(arrowChip, chipLp);

        ImageView topSparkle = decorativeSparkle(Color.WHITE, 18);
        FrameLayout.LayoutParams topSparkleLp = new FrameLayout.LayoutParams(home.dp(18), home.dp(18), Gravity.TOP | Gravity.END);
        topSparkleLp.setMargins(0, home.dp(10), home.dp(78), 0);
        button.addView(topSparkle, topSparkleLp);

        ImageView bottomSparkle = decorativeSparkle(MainActivityBase.GOLD, 14);
        FrameLayout.LayoutParams bottomSparkleLp = new FrameLayout.LayoutParams(home.dp(14), home.dp(14), Gravity.BOTTOM | Gravity.START);
        bottomSparkleLp.setMargins(home.dp(15), 0, 0, home.dp(14));
        button.addView(bottomSparkle, bottomSparkleLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, home.dp(94));
        lp.setMargins(0, home.dp(20), 0, home.dp(16));
        button.setLayoutParams(lp);
        return button;
    }

    ImageView decorativeSparkle(int tint, int sizeDp) {
        ImageView sparkle = new ImageView(home);
        sparkle.setImageResource(R.drawable.ic_sparkle_24);
        sparkle.setColorFilter(tint);
        sparkle.setAlpha(0.9f);
        sparkle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sparkle.setMaxWidth(home.dp(sizeDp));
        sparkle.setMaxHeight(home.dp(sizeDp));
        return sparkle;
    }
}
