package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.FocusQueueCopy;
import dev.bee.kanjianki.core.FocusQueuePolicy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudyTextCopy;
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
        for (StudyStatsStore.RecentMistake mistake : mistakes) {
            home.content.addView(recentMistakeRow(mistake, home.findRow(rows, mistake.kanji)));
        }
    }

    View recentMistakeRow(StudyStatsStore.RecentMistake mistake, RecordsImportModels.DashboardRow row) {
        LinearLayout box = home.panelBox(Color.WHITE, home.PINK_STROKE);
        box.setOnClickListener(new RunnableClickListener(() -> home.renderDetail(mistake.kanji)));
        LinearLayout top = new LinearLayout(home);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(home.kanjiTile(mistake.kanji, home.dp(70), 42));
        LinearLayout copy = new LinearLayout(home);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(home.text(HomeTextCopy.recentMistakeTitle(row == null ? "" : StudyTextCopy.rowMeaning(row)), 19, home.INK, true));
        copy.addView(home.text(HomeTextCopy.recentMistakeSubtitle(mistake.rating, DateTextPolicy.timelineDate(mistake.reviewedAtMillis)), 14, home.MUTED, false));
        if (row != null) {
            copy.addView(home.text(FocusQueueCopy.sourceEvidenceText(row), 14, home.INK, true));
        }
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(home.dp(12), 0, home.dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(home.text(">", 34, home.CORAL, true));
        box.addView(top);
        return box;
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
        RecordsImportModels.DashboardRow row = entry.row;
        RecordsStudyModels.StudyItem item = entry.item;
        LinearLayout box = home.panelBox(Color.WHITE, home.softened(rowColor(item, now)));
        box.setPadding(home.dp(12), home.dp(12), home.dp(12), home.dp(12));
        box.setOnClickListener(new RunnableClickListener(() -> home.renderDetail(row.kanji)));
        LinearLayout top = new LinearLayout(home);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(home.kanjiTile(row.kanji, home.dp(90), 52));
        LinearLayout copy = new LinearLayout(home);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(home.text(StudyTextCopy.rowMeaning(row), 19, home.INK, true));
        copy.addView(home.text(FocusQueueCopy.sourceEvidenceText(row), 14, home.INK, true));
        copy.addView(home.text(FocusQueueCopy.focusReasonLine(row, item, now, home.settings().matureSupportThreshold), 13, home.MUTED, false));
        copy.addView(home.text(StudyTextCopy.compact(FocusQueueCopy.queueCardBody(row), 72), 14, home.MUTED, false));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(home.dp(14), 0, home.dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(home.text(">", 34, home.CORAL, true));
        box.addView(top);
        LinearLayout chips = new LinearLayout(home);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(home.chip(FocusQueueCopy.recognitionStageLabel(item), home.BLUE));
        if (item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
            chips.addView(home.chip(HomeTextCopy.relearningChipLabel(), home.CORAL));
        } else if (item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews > 0) {
            chips.addView(home.chip(MainActivityBase.STATE_LEARNING, home.TEAL));
        }
        box.addView(chips);
        return box;
    }
}
