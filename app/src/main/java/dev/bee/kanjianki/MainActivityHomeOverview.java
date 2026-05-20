package dev.bee.kanjianki;

import android.view.View;
import android.widget.ImageView;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
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
        return MainActivityHomeOverviewCompose.homeMetricRowView(home, sync, provider, streak, plan);
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
