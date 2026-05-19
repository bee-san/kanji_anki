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
        return MainActivityHomeOverviewCompose.metricCardView(home, iconRes, accent, label, value, body, action);
    }

    View homeStudyCta() {
        return MainActivityHomeOverviewCompose.homeStudyCtaView(home);
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
