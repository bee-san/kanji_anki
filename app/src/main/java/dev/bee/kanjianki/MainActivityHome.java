package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;

import dev.bee.kanjianki.backup.DatabaseBackupScheduler;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveFocusCopy;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.FocusQueueCopy;
import dev.bee.kanjianki.core.FocusQueuePolicy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TimelineCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TypingAnswerMatcher;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.DictionaryAssets;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.update.AutoUpdateScheduler;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class MainActivityHome extends MainActivityBase {
    String activeBrowseQuery = "";
    private final MainActivityHomeChrome chrome = new MainActivityHomeChrome(this);
    private final MainActivityHomeFocusQueue focusQueue = new MainActivityHomeFocusQueue(this);
    private final MainActivityHomeBrowseDetail browseDetail = new MainActivityHomeBrowseDetail(this);
    private final MainActivityHomeSync syncFlow = new MainActivityHomeSync(this);

    abstract void renderStats();
    abstract void renderGames();

    void renderHome() {
        clearStudyModeOverrides();
        base("home");
        long now = System.currentTimeMillis();
        LocalStore.SyncStatus sync = store.latestSync();
        StudyStatsStore.StudyStreak streak = store.studyStreak(now);
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> homeItems = studyQueue(rows, now, false, null);
        RecordsSchedulerModels.AdaptiveLoadPlan homePlan = rows.isEmpty() ? null : adaptivePlan(rows, homeItems, now);
        List<QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : queuedEntries(rows, homeItems, now, homePlan);
        AnkiDroidGateway.ProviderStatus provider = gateway.status();

        content.addView(homeHeader());
        addSpace(12);
        content.addView(homeMetricRow(sync, provider, streak, homePlan));
        addSpace(14);

        if (rows.isEmpty()) {
            Button syncButton = primaryButton(HomeTextCopy.syncAnkiDroidLabel(), CORAL);
            syncButton.setOnClickListener(new RunnableClickListener(this::confirmSync));
            content.addView(syncButton);
        } else {
            View studyButton = homeStudyCta();
            studyButton.setOnClickListener(new RunnableClickListener(this::startFocusedStudy));
            content.addView(studyButton);

        }
        content.addView(homeActionRow());

        addSpace(16);
        content.addView(homeSectionHeader(
                HomeTextCopy.focusQueueTitle(),
                rows.isEmpty() ? null : HomeTextCopy.viewAllLabel(),
                rows.isEmpty() ? null : this::renderFocusQueue
        ));
        if (rows.isEmpty()) {
            emptyState(HomeTextCopy.noKanjiQueuedTitle(), HomeTextCopy.homeNoKanjiQueuedBody());
        } else {
            if (entries.isEmpty()) {
                emptyState(EMPTY_ACTIVE_PRACTICE_TITLE, EMPTY_ACTIVE_PRACTICE_BODY);
            }
            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                content.addView(queueRowView(entries.get(i), now));
            }
        }
    }

    View homeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(HomeTextCopy.appTitle(), 48, INK, true);
        title.setLetterSpacing(0);
        copy.addView(title);
        copy.addView(text(HomeTextCopy.appSubtitle(), 16, MUTED, true));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.mipmap.ic_launcher_foreground);
        mascot.setAdjustViewBounds(true);
        mascot.setBackgroundColor(Color.TRANSPARENT);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams mascotLp = new LinearLayout.LayoutParams(dp(110), dp(110));
        mascotLp.setMargins(dp(10), 0, 0, 0);
        header.addView(mascot, mascotLp);
        return header;
    }

    View homeMetricRow(LocalStore.SyncStatus sync, AnkiDroidGateway.ProviderStatus provider, StudyStatsStore.StudyStreak streak, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        LinearLayout row = new EqualHeightRow(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(metricCard(
                R.drawable.ic_sync_24,
                TEAL,
                HomeTextCopy.syncMetricLabel(),
                HomeTextCopy.homeSyncValue(sync == null ? null : sync.finishedAt),
                HomeTextCopy.syncMetricStatus(provider.canSync && sync != null && "success".equals(sync.status)),
                this::confirmSync
        ));
        row.addView(metricCard(
                R.drawable.ic_flame_24,
                streakAccent(streak),
                HomeTextCopy.streakMetricLabel(),
                HomeTextCopy.streakHeadline(streak == null ? 0 : streak.currentDays),
                HomeTextCopy.streakMetricBody(streak != null && streak.studiedToday, streak == null ? 0 : streak.bestDays),
                null
        ));
        row.addView(metricCard(
                R.drawable.ic_target_24,
                CORAL,
                HomeTextCopy.focusMetricLabel(),
                HomeTextCopy.focusHeadline(plan),
                null,
                null
        ));
        return row;
    }

    View metricCard(int iconRes, int accent, String label, String value, String body, Runnable action) {
        LinearLayout card = panelBox(Color.WHITE, softened(accent));
        card.setPadding(dp(11), dp(11), dp(11), dp(11));
        card.setGravity(Gravity.TOP);
        card.setMinimumHeight(dp(136));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMargins(0, 0, 0, dp(5));
        card.addView(icon, iconLp);

        TextView labelText = text(label, 12, accent, true);
        labelText.setIncludeFontPadding(false);
        labelText.setSingleLine(true);
        card.addView(labelText);

        TextView valueText = text(value, 14, INK, true);
        valueText.setIncludeFontPadding(false);
        valueText.setSingleLine(false);
        valueText.setMaxLines(2);
        valueText.setPadding(0, dp(5), 0, dp(2));
        card.addView(valueText);

        if (body != null && !body.isEmpty()) {
            TextView bodyText = text(StudyTextCopy.compact(body, 18), 11, MUTED, false);
            bodyText.setIncludeFontPadding(false);
            bodyText.setSingleLine(true);
            bodyText.setPadding(0, dp(3), 0, 0);
            card.addView(bodyText);
        }
        if (action != null) {
            card.setClickable(true);
            card.setOnClickListener(new RunnableClickListener(action));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);
        return card;
    }

    View homeStudyCta() {
        FrameLayout button = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(255, 116, 156), Color.rgb(255, 58, 112) }
        );
        background.setCornerRadius(dp(24));
        background.setStroke(dp(2), Color.rgb(255, 190, 214));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(24));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(38, 255, 255, 255)),
                background,
                mask
        ));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(LABEL_STUDY_NOW);
        button.setMinimumHeight(dp(94));
        button.setElevation(dp(9));
        button.setTranslationZ(dp(2));
        button.setClipToOutline(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(LABEL_STUDY_NOW, 26, Color.WHITE, true);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(0);
        copy.addView(title);
        TextView support = text(HomeTextCopy.studySupportText(), 13, Color.rgb(255, 245, 250), true);
        support.setIncludeFontPadding(false);
        support.setSingleLine(true);
        support.setPadding(0, dp(5), 0, 0);
        copy.addView(support);
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(-1, -1);
        copyLp.setMargins(dp(26), 0, dp(92), 0);
        button.addView(copy, copyLp);

        FrameLayout arrowChip = new FrameLayout(this);
        arrowChip.setBackground(panel(Color.WHITE, Color.WHITE, dp(25)));
        arrowChip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_forward_24);
        arrow.setColorFilter(CORAL);
        FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        arrowChip.addView(arrow, arrowLp);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END | Gravity.CENTER_VERTICAL);
        chipLp.setMargins(0, 0, dp(22), 0);
        button.addView(arrowChip, chipLp);

        ImageView topSparkle = decorativeSparkle(Color.WHITE, 18);
        FrameLayout.LayoutParams topSparkleLp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
        topSparkleLp.setMargins(0, dp(10), dp(78), 0);
        button.addView(topSparkle, topSparkleLp);

        ImageView bottomSparkle = decorativeSparkle(GOLD, 14);
        FrameLayout.LayoutParams bottomSparkleLp = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.BOTTOM | Gravity.START);
        bottomSparkleLp.setMargins(dp(15), 0, 0, dp(14));
        button.addView(bottomSparkle, bottomSparkleLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(94));
        lp.setMargins(0, dp(20), 0, dp(16));
        button.setLayoutParams(lp);
        return button;
    }

    ImageView decorativeSparkle(int tint, int sizeDp) {
        ImageView sparkle = new ImageView(this);
        sparkle.setImageResource(R.drawable.ic_sparkle_24);
        sparkle.setColorFilter(tint);
        sparkle.setAlpha(0.9f);
        sparkle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sparkle.setMaxWidth(dp(sizeDp));
        sparkle.setMaxHeight(dp(sizeDp));
        return sparkle;
    }

    View homeActionRow() {
        return chrome.homeActionRow();
    }

    View homeSectionHeader(String title, String actionLabel, Runnable action) {
        return chrome.homeSectionHeader(title, actionLabel, action);
    }

    View pillButton(String label, int iconRes, Runnable action) {
        return chrome.pillButton(label, iconRes, action);
    }

    View fullWidthHomeButton() {
        return chrome.fullWidthHomeButton();
    }

    void renderFocusQueue() {
        focusQueue.renderFocusQueue();
    }

    void renderRecentMistakes() {
        focusQueue.renderRecentMistakes();
    }

    View recentMistakeRow(StudyStatsStore.RecentMistake mistake, RecordsImportModels.DashboardRow row) {
        return focusQueue.recentMistakeRow(mistake, row);
    }

    int streakAccent(StudyStatsStore.StudyStreak streak) {
        return focusQueue.streakAccent(streak);
    }

    void confirmSync() {
        syncFlow.confirmSync();
    }

    void runSync() {
        syncFlow.runSync();
    }

    void renderSyncResult(ManualSyncEngine.SyncResult result) {
        syncFlow.renderSyncResult(result);
    }

    void renderSkippedSyncResult(ManualSyncEngine.SyncResult result) {
        syncFlow.renderSkippedSyncResult(result);
    }

    void renderSuccessfulSyncResult(ManualSyncEngine.SyncResult result) {
        syncFlow.renderSuccessfulSyncResult(result);
    }

    void renderFailedSyncResult(ManualSyncEngine.SyncResult result) {
        syncFlow.renderFailedSyncResult(result);
    }

    long studyAheadMillis() {
        return focusQueue.studyAheadMillis();
    }

    String nonEmptyOr(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    List<RecordsStudyModels.StudyItem> studyQueue(List<RecordsImportModels.DashboardRow> rows, long now, boolean persist, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return focusQueue.studyQueue(rows, now, persist, plan);
    }

    List<QueueEntry> queuedEntries(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return focusQueue.queuedEntries(rows, items, now, plan);
    }

    List<QueueEntry> queuedEntries(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return focusQueue.queuedEntries(rows, items, now, null);
    }

    int rowColor(RecordsStudyModels.StudyItem item, long now) {
        return focusQueue.rowColor(item, now);
    }

    View queueRowView(QueueEntry entry, long now) {
        return focusQueue.queueRowView(entry, now);
    }

    TextView kanjiTile(String value, int sizePx, int textSp) {
        TextView kanji = text(value, textSp, INK, true);
        kanji.setGravity(Gravity.CENTER);
        kanji.setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD);
        kanji.setBackground(panel(BLUSH, BLUSH, dp(10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        kanji.setLayoutParams(lp);
        return kanji;
    }

    void renderDetail(String kanji) {
        browseDetail.renderDetail(kanji);
    }

    void renderBrowseKanji(String query) {
        browseDetail.renderBrowseKanji(query);
    }

    View browseKanjiRow(RecordsImportModels.KanjiInventoryItem item) {
        return browseDetail.browseKanjiRow(item);
    }

    void renderDetail(String kanji, boolean fromBrowse) {
        browseDetail.renderDetail(kanji, fromBrowse);
    }

    void renderDetail(String kanji, boolean fromBrowse, String browseQuery) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery);
    }

    void addDetailHeader(String displayKanji, boolean fromBrowse, String browseQuery) {
        browseDetail.addDetailHeader(displayKanji, fromBrowse, browseQuery);
    }

    void addDetailIdentity(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, boolean suspended) {
        browseDetail.addDetailIdentity(row, inventory, suspended);
    }

    LinearLayout detailReasonPanel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory) {
        return browseDetail.detailReasonPanel(row, inventory);
    }

    void addDetailActions(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, String displayKanji, boolean fromBrowse, String browseQuery, boolean suspended) {
        browseDetail.addDetailActions(row, inventory, displayKanji, fromBrowse, browseQuery, suspended);
    }
    void addDetailExamples(RecordsImportModels.DashboardRow row) {
        browseDetail.addDetailExamples(row);
    }

    View localInventoryPanel(RecordsImportModels.KanjiInventoryItem inventory) {
        return browseDetail.localInventoryPanel(inventory);
    }

    void copyAnkiSearch(String browserSearch, View v) {
        browseDetail.copyAnkiSearch(browserSearch, v);
    }

    void addRecoveryTimeline(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        browseDetail.addRecoveryTimeline(timeline);
    }

    View timelineStatusCard(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        return browseDetail.timelineStatusCard(timeline);
    }

    View timelineEventView(RecordsImportModels.KanjiTimelineEvent event) {
        return browseDetail.timelineEventView(event);
    }

    int timelineToneColor(TimelineCopy.Tone tone) {
        return browseDetail.timelineToneColor(tone);
    }

    View exampleView(RecordsImportModels.Example example) {
        return browseDetail.exampleView(example);
    }

}
