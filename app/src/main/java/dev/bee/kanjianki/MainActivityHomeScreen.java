package dev.bee.kanjianki;

import android.view.View;
import java.util.ArrayList;
import java.util.List;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;

final class MainActivityHomeScreen {
    private final MainActivityHome home;

    MainActivityHomeScreen(MainActivityHome home) {
        this.home = home;
    }

    void renderHome() {
        home.clearStudyModeOverrides();
        home.base("home");
        long now = System.currentTimeMillis();
        LocalStore.SyncStatus sync = home.store.latestSync();
        StudyStatsStore.StudyStreak streak = home.store.studyStreak(now);
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> homeItems = home.studyQueue(rows, now, false, null);
        RecordsSchedulerModels.AdaptiveLoadPlan homePlan = rows.isEmpty() ? null : home.adaptivePlan(rows, homeItems, now);
        List<MainActivityBase.QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : home.queuedEntries(rows, homeItems, now, homePlan);
        AnkiDroidGateway.ProviderStatus provider = home.gateway.status();

        home.content.addView(home.homeHeader());
        home.addSpace(12);
        home.content.addView(home.homeMetricRow(sync, provider, streak, homePlan));
        home.addSpace(14);

        if (rows.isEmpty()) {
            home.content.addView(home.homeSyncCta());
        } else {
            View studyButton = home.homeStudyCta();
            studyButton.setOnClickListener(new RunnableClickListener(home::startFocusedStudy));
            home.content.addView(studyButton);
        }

        home.content.addView(home.homeActionRow());
        home.addSpace(16);
        home.content.addView(home.homeSectionHeader(
                HomeTextCopy.focusQueueTitle(),
                rows.isEmpty() ? null : HomeTextCopy.viewAllLabel(),
                rows.isEmpty() ? null : home::renderFocusQueue
        ));
        if (rows.isEmpty()) {
            home.emptyState(HomeTextCopy.noKanjiQueuedTitle(), HomeTextCopy.homeNoKanjiQueuedBody());
        } else {
            if (entries.isEmpty()) {
                home.emptyState(MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE, MainActivityBase.EMPTY_ACTIVE_PRACTICE_BODY);
            }
            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                home.content.addView(home.queueRowView(entries.get(i), now));
            }
        }
    }
}
