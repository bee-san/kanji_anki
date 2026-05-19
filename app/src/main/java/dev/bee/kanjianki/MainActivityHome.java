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
    private final MainActivityHomeScreen homeScreen = new MainActivityHomeScreen(this);
    private final MainActivityHomeOverview overview = new MainActivityHomeOverview(this);
    private final MainActivityHomeChrome chrome = new MainActivityHomeChrome(this);
    private final MainActivityHomeFocusQueue focusQueue = new MainActivityHomeFocusQueue(this);
    private final MainActivityHomeBrowseDetail browseDetail = new MainActivityHomeBrowseDetail(this);
    private final MainActivityHomeSync syncFlow = new MainActivityHomeSync(this);

    abstract void renderStats();
    abstract void renderGames();

    void renderHome() {
        homeScreen.renderHome();
    }

    View homeHeader() {
        return overview.homeHeader();
    }

    View homeMetricRow(LocalStore.SyncStatus sync, AnkiDroidGateway.ProviderStatus provider, StudyStatsStore.StudyStreak streak, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return overview.homeMetricRow(sync, provider, streak, plan);
    }

    View metricCard(int iconRes, int accent, String label, String value, String body, Runnable action) {
        return overview.metricCard(iconRes, accent, label, value, body, action);
    }

    View homeStudyCta() {
        return overview.homeStudyCta();
    }

    ImageView decorativeSparkle(int tint, int sizeDp) {
        return overview.decorativeSparkle(tint, sizeDp);
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

    void openComposeShell() {
        startActivity(ComposeShellActivity.intent(this));
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
