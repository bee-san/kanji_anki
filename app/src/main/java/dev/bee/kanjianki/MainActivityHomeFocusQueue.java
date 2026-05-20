package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.FocusQueuePolicy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.data.StudyStatsStore;

final class MainActivityHomeFocusQueue {
    private final MainActivityHome home;

    MainActivityHomeFocusQueue(MainActivityHome home) {
        this.home = home;
    }

    void renderFocusQueue() {
        home.base("home");
        long now = System.currentTimeMillis();
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> items = studyQueue(rows, now, false, null);
        RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty() ? null : home.adaptivePlan(rows, items, now);
        List<MainActivityBase.QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : queuedEntries(rows, items, now, plan);

        home.content.addView(home.homeSectionHeader(HomeTextCopy.focusQueueTitle(), HomeTextCopy.homeLabel(), home::renderHome));
        home.content.addView(MainActivityHomeFocusQueueCompose.homeFocusQueueContentView(home, rows, entries, now, plan));
    }

    void renderRecentMistakes() {
        home.base("home");
        home.content.addView(home.homeSectionHeader(HomeTextCopy.recentMistakesTitle(), HomeTextCopy.homeLabel(), home::renderHome));
        List<StudyStatsStore.RecentMistake> mistakes = home.store.recentMistakes(12);
        if (mistakes.isEmpty()) {
            home.emptyState(HomeTextCopy.noRecentMistakesTitle(), HomeTextCopy.noRecentMistakesBody());
            return;
        }
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        home.content.addView(MainActivityHomeFocusQueueCompose.homeRecentMistakesContentView(home, mistakes, rows));
    }

    int streakAccent(StudyStatsStore.StudyStreak streak) {
        return streak != null && streak.studiedToday ? Color.rgb(247, 159, 0) : Color.rgb(160, 160, 166);
    }

    long studyAheadMillis() {
        return home.store.studyAheadMinutes() * 60_000L;
    }

    List<RecordsStudyModels.StudyItem> studyQueue(List<RecordsImportModels.DashboardRow> rows, long now, boolean persist, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        BridgeScheduler scheduler = new BridgeScheduler();
        return HomeStudyQueueActions.studyQueue(new HomeStudyQueueActions.StudyQueueRequest(
                rows,
                now,
                persist,
                plan,
                home.store::studyItems,
                home::settings,
                home::startOfDay,
                home::studyLadderSettings,
                home::adaptivePlan,
                scheduler::seedQueue,
                new MainActivityHomeStudyItemsWriter(home)
        ));
    }

    List<MainActivityBase.QueueEntry> queuedEntries(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        List<FocusQueuePolicy.QueueEntry> coreEntries = FocusQueuePolicy.queuedEntries(rows, items, now, studyAheadMillis(), plan, home.studyLadderSettings());
        List<MainActivityBase.QueueEntry> entries = new ArrayList<>(coreEntries.size());
        for (FocusQueuePolicy.QueueEntry entry : coreEntries) {
            entries.add(new MainActivityBase.QueueEntry(entry.row, entry.item));
        }
        return entries;
    }

    int rowColor(RecordsStudyModels.StudyItem item, long now) {
        FocusQueuePolicy.QueueTone tone = FocusQueuePolicy.rowTone(item, now);
        if (tone == FocusQueuePolicy.QueueTone.DUE) {
            return home.CORAL;
        }
        if (tone == FocusQueuePolicy.QueueTone.LEARNING) {
            return home.BLUE;
        }
        return Color.rgb(246, 202, 225);
    }

    View queueRowView(MainActivityBase.QueueEntry entry, long now) {
        return MainActivityHomeFocusQueueCompose.homeFocusQueueCardView(home, entry, now);
    }
}
