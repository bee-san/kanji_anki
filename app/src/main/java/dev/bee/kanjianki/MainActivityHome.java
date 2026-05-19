package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
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
import android.widget.Toast;
import androidx.core.widget.TextViewCompat;

import dev.bee.kanjianki.backup.DatabaseBackupScheduler;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
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
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncSettings;
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
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
        } else {
            View studyButton = homeStudyCta();
            studyButton.setOnClickListener(v -> startFocusedStudy());
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
            card.setOnClickListener(v -> action.run());
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
        List<View> actions = new ArrayList<>();
        actions.add(pillButton(HomeTextCopy.browseActionLabel(), R.drawable.ic_book_24, () -> renderBrowseKanji("")));
        actions.add(pillButton(HomeTextCopy.recentMistakesTitle(), R.drawable.ic_trending_24, this::renderRecentMistakes));
        actions.add(pillButton(HomeTextCopy.statsActionLabel(), R.drawable.ic_stats_24, this::renderStats));
        actions.add(pillButton(HomeTextCopy.gamesActionLabel(), R.drawable.ic_game_24, this::renderGames));
        actions.add(pillButton(NAV_SETTINGS, R.drawable.ic_settings_24, this::renderSettings));
        return twoColumnGrid(actions);
    }

    View homeSectionHeader(String title, String actionLabel, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = sectionTitle(title);
        heading.setPadding(0, dp(8), 0, dp(8));
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if (actionLabel != null && action != null) {
            TextView link = text(actionLabel + " >", 15, CORAL, true);
            link.setGravity(Gravity.CENTER_VERTICAL);
            link.setPadding(dp(12), dp(8), 0, dp(8));
            link.setOnClickListener(v -> action.run());
            row.addView(link, new LinearLayout.LayoutParams(-2, -2));
        }
        return row;
    }

    View pillButton(String label, int iconRes, Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(INK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMargins(0, 0, dp(7), 0);
        button.addView(icon, iconLp);
        TextView text = text(label, 13, INK, true);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(false);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        button.setBackground(panel(Color.WHITE, Color.rgb(235, 214, 228), dp(22)));
        button.setClickable(true);
        button.setOnClickListener(v -> action.run());
        button.setMinimumHeight(dp(62));
        return button;
    }

    View fullWidthHomeButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_home_24);
        icon.setColorFilter(INK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMargins(0, 0, dp(8), 0);
        button.addView(icon, iconLp);
        TextView text = text(HomeTextCopy.homeLabel(), 15, INK, true);
        text.setGravity(Gravity.CENTER);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        button.setBackground(panel(Color.WHITE, Color.rgb(235, 214, 228), dp(22)));
        button.setClickable(true);
        button.setOnClickListener(v -> renderHome());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(lp);
        return button;
    }

    void renderFocusQueue() {
        base("home");
        long now = System.currentTimeMillis();
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> items = studyQueue(rows, now, false, null);
        RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty() ? null : adaptivePlan(rows, items, now);
        List<QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : queuedEntries(rows, items, now, plan);

        content.addView(homeSectionHeader(HomeTextCopy.focusQueueTitle(), HomeTextCopy.homeLabel(), this::renderHome));
        content.addView(text(AdaptiveFocusCopy.adaptiveFocusText(plan), 16, MUTED, false));
        addSpace(8);
        if (rows.isEmpty()) {
            emptyState(HomeTextCopy.noKanjiQueuedTitle(), HomeTextCopy.focusQueueNoKanjiQueuedBody());
            Button syncButton = primaryButton(HomeTextCopy.syncAnkiDroidLabel(), CORAL);
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
            return;
        }
        if (entries.isEmpty()) {
            emptyState(EMPTY_ACTIVE_PRACTICE_TITLE, EMPTY_ACTIVE_PRACTICE_BODY);
            return;
        }
        for (QueueEntry entry : entries) {
            content.addView(queueRowView(entry, now));
        }
    }

    void renderRecentMistakes() {
        base("home");
        content.addView(homeSectionHeader(HomeTextCopy.recentMistakesTitle(), HomeTextCopy.homeLabel(), this::renderHome));
        List<StudyStatsStore.RecentMistake> mistakes = store.recentMistakes(12);
        if (mistakes.isEmpty()) {
            emptyState(HomeTextCopy.noRecentMistakesTitle(), HomeTextCopy.noRecentMistakesBody());
            return;
        }
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        for (StudyStatsStore.RecentMistake mistake : mistakes) {
            content.addView(recentMistakeRow(mistake, findRow(rows, mistake.kanji)));
        }
    }

    View recentMistakeRow(StudyStatsStore.RecentMistake mistake, RecordsImportModels.DashboardRow row) {
        LinearLayout box = panelBox(Color.WHITE, PINK_STROKE);
        box.setOnClickListener(v -> renderDetail(mistake.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView kanji = kanjiTile(mistake.kanji, dp(70), 42);
        top.addView(kanji);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(HomeTextCopy.recentMistakeTitle(row == null ? "" : StudyTextCopy.rowMeaning(row)), 19, INK, true));
        copy.addView(text(HomeTextCopy.recentMistakeSubtitle(mistake.rating, DateTextPolicy.timelineDate(mistake.reviewedAtMillis)), 14, MUTED, false));
        if (row != null) {
            copy.addView(text(FocusQueueCopy.sourceEvidenceText(row), 14, INK, true));
        }
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(12), 0, dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(text(">", 34, CORAL, true));
        box.addView(top);
        return box;
    }

    int streakAccent(StudyStatsStore.StudyStreak streak) {
        return streak != null && streak.studiedToday ? Color.rgb(247, 159, 0) : Color.rgb(160, 160, 166);
    }

    void confirmSync() {
        RecordsSyncModels.Settings current = settings();
        new AlertDialog.Builder(this)
                .setTitle(HomeTextCopy.syncDialogTitle())
                .setMessage(HomeTextCopy.syncDialogMessage(current))
                .setPositiveButton(HomeTextCopy.syncDialogPositiveLabel(), (dialog, which) -> runSync())
                .setNegativeButton(HomeTextCopy.cancelLabel(), null)
                .show();
    }

    void runSync() {
        base("home");
        content.addView(text(HomeTextCopy.syncingTitle(), 34, INK, true));
        SyncProgressPanel progressView = new SyncProgressPanel(this);
        content.addView(progressView);
        CollectionGateway syncGateway = collectionGatewayForTests == null ? gateway : collectionGatewayForTests;
        ManualSyncCoordinator coordinator = new ManualSyncCoordinator(
                io,
                main::post,
                progress -> new ManualSyncEngine(
                        this,
                        store,
                        syncGateway,
                        settings(),
                        progress
                ).run(),
                () -> {
                    store.activateAutoSyncAfterFirstSuccess();
                    AutoSyncScheduler.schedule(this);
                },
                this::renderSyncResult
        );
        coordinator.start(update -> main.post(() -> progressView.render(update)));
    }

    void renderSyncResult(ManualSyncEngine.SyncResult result) {
        base("home");
        if (result.skipped) {
            renderSkippedSyncResult(result);
        } else if (result.success) {
            renderSuccessfulSyncResult(result);
        } else {
            renderFailedSyncResult(result);
        }
    }

    void renderSkippedSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text(HomeTextCopy.syncAlreadyRunningTitle(), 34, INK, true));
        LinearLayout info = band(BLUE);
        info.addView(text(result.message == null || result.message.isEmpty() ? HomeTextCopy.syncAlreadyRunningFallback() : result.message, 17, Color.WHITE, false));
        content.addView(info);
        Button home = secondaryButton(LABEL_BACK_HOME);
        home.setOnClickListener(v -> renderHome());
        content.addView(home);
    }

    void renderSuccessfulSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text(HomeTextCopy.syncCompleteTitle(), 34, INK, true));
        LinearLayout summary = band(TEAL);
        long now = System.currentTimeMillis();
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> items = store.studyItems();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = adaptivePlan(rows, items, now);
        List<QueueEntry> entries = queuedEntries(rows, items, now, plan);
        summary.addView(text(HomeTextCopy.syncReadyCountText(entries.size()), 24, Color.WHITE, true));
        summary.addView(text(HomeTextCopy.syncCandidateSummary(result.dashboardRows, AdaptiveFocusCopy.adaptiveFocusText(plan)), 16, Color.WHITE, false));
        if (!result.adaptiveSummary.isEmpty()) {
            summary.addView(text(result.adaptiveSummary, 15, Color.WHITE, false));
        }
        if (result.importedSuspendedKanji > 0) {
            summary.addView(text(HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji), 15, Color.WHITE, false));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summary.addView(text(result.message, 14, Color.WHITE, false));
        }
        content.addView(summary);
        if (result.dashboardRows > 0) {
            Button study = primaryButton(LABEL_STUDY_NOW, CORAL);
            study.setOnClickListener(v -> startFocusedStudy());
            content.addView(study);
        }
        Button home = secondaryButton(LABEL_BACK_HOME);
        home.setOnClickListener(v -> renderHome());
        content.addView(home);
    }

    void renderFailedSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text(HomeTextCopy.syncNeedsAttentionTitle(), 34, INK, true));
        LinearLayout error = band(CORAL);
        error.addView(text(HomeTextCopy.syncReadErrorTitle(), 24, Color.WHITE, true));
        error.addView(text(result.message == null || result.message.isEmpty() ? HomeTextCopy.syncFailureFallback() : result.message, 16, Color.WHITE, false));
        content.addView(error);
        Button retry = primaryButton(HomeTextCopy.trySyncAgainLabel(), TEAL);
        retry.setOnClickListener(v -> confirmSync());
        content.addView(retry);
        Button home = secondaryButton(LABEL_BACK_HOME);
        home.setOnClickListener(v -> renderHome());
        content.addView(home);
    }

    long studyAheadMillis() {
        return store.studyAheadMinutes() * 60_000L;
    }

    String nonEmptyOr(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    List<RecordsStudyModels.StudyItem> studyQueue(List<RecordsImportModels.DashboardRow> rows, long now, boolean persist, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        BridgeScheduler scheduler = new BridgeScheduler();
        return HomeStudyQueueActions.studyQueue(new HomeStudyQueueActions.StudyQueueRequest(
                rows,
                now,
                persist,
                plan,
                store::studyItems,
                this::settings,
                this::startOfDay,
                this::studyLadderSettings,
                this::adaptivePlan,
                scheduler::seedQueue,
                new MainActivityHomeStudyItemsWriter(this)
        ));
    }

    List<QueueEntry> queuedEntries(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now, RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        List<FocusQueuePolicy.QueueEntry> coreEntries = FocusQueuePolicy.queuedEntries(rows, items, now, store.studyAheadMinutes() * 60_000L, plan, studyLadderSettings());
        List<QueueEntry> entries = new ArrayList<>(coreEntries.size());
        for (FocusQueuePolicy.QueueEntry entry : coreEntries) {
            entries.add(new QueueEntry(entry.row, entry.item));
        }
        return entries;
    }

    List<QueueEntry> queuedEntries(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return queuedEntries(rows, items, now, null);
    }

    int rowColor(RecordsStudyModels.StudyItem item, long now) {
        FocusQueuePolicy.QueueTone tone = FocusQueuePolicy.rowTone(item, now);
        if (tone == FocusQueuePolicy.QueueTone.DUE) {
            return CORAL;
        }
        if (tone == FocusQueuePolicy.QueueTone.LEARNING) {
            return BLUE;
        }
        return Color.rgb(246, 202, 225);
    }

    View queueRowView(QueueEntry entry, long now) {
        RecordsImportModels.DashboardRow row = entry.row;
        RecordsStudyModels.StudyItem item = entry.item;
        LinearLayout box = panelBox(Color.WHITE, softened(rowColor(item, now)));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setOnClickListener(v -> renderDetail(row.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(kanjiTile(row.kanji, dp(90), 52));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(StudyTextCopy.rowMeaning(row), 19, INK, true));
        copy.addView(text(FocusQueueCopy.sourceEvidenceText(row), 14, INK, true));
        copy.addView(text(FocusQueueCopy.focusReasonLine(row, item, now, settings().matureSupportThreshold), 13, MUTED, false));
        copy.addView(text(StudyTextCopy.compact(FocusQueueCopy.queueCardBody(row), 72), 14, MUTED, false));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(14), 0, dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(text(">", 34, CORAL, true));
        box.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(FocusQueueCopy.recognitionStageLabel(item), BLUE));
        if (item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
            chips.addView(chip(HomeTextCopy.relearningChipLabel(), CORAL));
        } else if (item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews > 0) {
            chips.addView(chip(STATE_LEARNING, TEAL));
        }
        box.addView(chips);
        return box;
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
        renderDetail(kanji, false);
    }

    void renderBrowseKanji(String query) {
        activeBrowseQuery = query == null ? "" : query;
        base("home");
        content.addView(fullWidthHomeButton());
        content.addView(text(HomeTextCopy.browseTitle(), 34, INK, true));
        content.addView(text(HomeTextCopy.browseBody(), 16, MUTED, false));
        addSpace(10);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(query == null ? "" : query);
        search.setHint(HomeTextCopy.browseSearchHint());
        search.setTextSize(18);
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(58)));

        Button submit = primaryButton(HomeTextCopy.browseSearchButtonLabel(), TEAL);
        submit.setOnClickListener(v -> renderBrowseKanji(search.getText().toString()));
        content.addView(submit);

        List<RecordsImportModels.KanjiInventoryItem> items = store.searchKanjiInventory(query);
        content.addView(sectionTitle(HomeTextCopy.browseResultHeading(items.size())));
        if (items.isEmpty()) {
            emptyState(HomeTextCopy.browseEmptyTitle(), HomeTextCopy.browseEmptyBody());
            return;
        }
        for (RecordsImportModels.KanjiInventoryItem item : items) {
            content.addView(browseKanjiRow(item));
        }
    }

    View browseKanjiRow(RecordsImportModels.KanjiInventoryItem item) {
        LinearLayout box = panelBox(Color.WHITE, item.suspended ? CORAL : TEAL);
        box.setOnClickListener(v -> renderDetail(item.kanji, true));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView glyph = text(item.kanji, 44, INK, true);
        glyph.setGravity(Gravity.CENTER);
        top.addView(glyph, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(HomeTextCopy.browseItemMeaning(item), 19, INK, true));
        if (!item.readings.isEmpty()) {
            copy.addView(text(item.readings, 14, TEAL, true));
        }
        copy.addView(text(HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount), 14, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        if (item.suspended) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip(HomeTextCopy.suspendedChipLabel(), CORAL));
            box.addView(chips);
        }
        return box;
    }

    void renderDetail(String kanji, boolean fromBrowse) {
        renderDetail(kanji, fromBrowse, fromBrowse ? activeBrowseQuery : "");
    }

    void renderDetail(String kanji, boolean fromBrowse, String browseQuery) {
        base("home");
        RecordsStudyModels.KanjiRecoveryTimeline timeline = store.timelineForKanji(kanji);
        RecordsImportModels.DashboardRow row = timeline.currentRow;
        RecordsImportModels.KanjiInventoryItem inventory = timeline.inventoryItem;
        if (inventory == null && row == null && timeline.currentStudyItem == null && timeline.events.isEmpty()) {
            content.addView(fullWidthHomeButton());
            emptyState(HomeTextCopy.kanjiNotFoundTitle(), HomeTextCopy.kanjiNotFoundBody());
            return;
        }
        String displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory);
        addDetailHeader(displayKanji, fromBrowse, browseQuery);
        boolean suspended = inventory != null && inventory.suspended;
        addDetailIdentity(row, inventory, suspended);
        addSpace(10);
        content.addView(detailReasonPanel(row, inventory));
        if (inventory != null) {
            content.addView(localInventoryPanel(inventory));
        }
        addDetailActions(row, inventory, displayKanji, fromBrowse, browseQuery, suspended);
        addSpace(12);
        addRecoveryTimeline(timeline);
        if (row != null) {
            addDetailExamples(row);
        }
    }

    void addDetailHeader(String displayKanji, boolean fromBrowse, String browseQuery) {
        if (!fromBrowse) {
            content.addView(fullWidthHomeButton());
        }
        TextView glyph = text(displayKanji, 92, INK, true);
        glyph.setGravity(Gravity.CENTER);
        content.addView(glyph);
        if (fromBrowse) {
            Button back = secondaryButton(HomeTextCopy.backToBrowseKanjiLabel());
            back.setOnClickListener(v -> renderBrowseKanji(browseQuery));
            content.addView(back);
        }
    }

    void addDetailIdentity(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, boolean suspended) {
        if (suspended) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip(HomeTextCopy.suspendedChipLabel(), CORAL));
            content.addView(chips);
        }
        if (row == null) {
            content.addView(text(HomeTextCopy.inventoryTitle(inventory), 25, INK, true));
            if (inventory != null && !inventory.readings.isEmpty()) {
                content.addView(text(inventory.readings, 20, TEAL, true));
            }
        } else {
            content.addView(text(StudyTextCopy.rowMeaning(row), 25, INK, true));
            if (!row.reading.isEmpty()) {
                content.addView(text(row.reading, 20, TEAL, true));
            }
        }
    }

    LinearLayout detailReasonPanel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory) {
        LinearLayout why = band(BLUE);
        why.addView(text(HomeTextCopy.detailReasonTitle(), 22, Color.WHITE, true));
        if (row == null) {
            why.addView(text(HomeTextCopy.historicalReasonText(), 17, Color.WHITE, false));
            if (inventory != null && !inventory.browserSearch.isEmpty()) {
                why.addView(text(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(inventory.browserSearch, 96)), 14, Color.WHITE, false));
            }
        } else {
            why.addView(text(HomeTextCopy.activeReasonText(row), 17, Color.WHITE, false));
            if (!row.browserSearch.isEmpty()) {
                why.addView(text(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(row.browserSearch, 96)), 14, Color.WHITE, false));
            }
        }
        return why;
    }

    void addDetailActions(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, String displayKanji, boolean fromBrowse, String browseQuery, boolean suspended) {
        if (row != null && !suspended) {
            Button practice = primaryButton(HomeTextCopy.reviewNowLabel(), CORAL);
            practice.setOnClickListener(v -> renderStudyForKanji(row.kanji));
            content.addView(practice);
        }
        String browserSearch = HomeTextCopy.detailBrowserSearch(row, inventory);
        if (!browserSearch.isEmpty()) {
            Button copy = secondaryButton(HomeTextCopy.copyAnkiSearchLabel());
            copy.setOnClickListener(v -> copyAnkiSearch(browserSearch, v));
            content.addView(copy);
        }
        Button suspend = secondaryButton(HomeTextCopy.localSuspendButtonLabel(suspended));
        suspend.setOnClickListener(v -> {
            store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis());
            String toast = HomeTextCopy.localSuspendToast(suspended);
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
            renderDetail(displayKanji, fromBrowse, browseQuery);
        });
        content.addView(suspend);
    }

    void addDetailExamples(RecordsImportModels.DashboardRow row) {
        addSpace(12);
        content.addView(sectionTitle(HomeTextCopy.examplesTitle()));
        for (RecordsImportModels.Example example : row.examples) {
            content.addView(exampleView(example));
        }
    }

    View localInventoryPanel(RecordsImportModels.KanjiInventoryItem inventory) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text(HomeTextCopy.localInventoryTitle(), 19, INK, true));
        box.addView(text(HomeTextCopy.localInventorySummary(inventory.sourceCount, inventory.exampleCount), 15, MUTED, false));
        if (!inventory.browserSearch.isEmpty()) {
            box.addView(text(HomeTextCopy.localInventorySearchLine(StudyTextCopy.compact(inventory.browserSearch, 96)), 14, MUTED, false));
        }
        if (inventory.lastSeenAtMillis > 0L) {
            box.addView(text(HomeTextCopy.localInventoryLastSeenLine(inventory.lastSeenAtMillis), 14, MUTED, false));
        }
        return box;
    }

    void copyAnkiSearch(String browserSearch, View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(HomeTextCopy.ankiSearchClipLabel(), browserSearch));
        if (v instanceof Button button) {
            button.setText(R.string.copied_anki_search);
        }
        Toast.makeText(this, HomeTextCopy.ankiSearchCopiedToast(), Toast.LENGTH_SHORT).show();
    }

    void addRecoveryTimeline(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        content.addView(sectionTitle(HomeTextCopy.recoveryTimelineTitle()));
        content.addView(timelineStatusCard(timeline));
        if (timeline.events.isEmpty()) {
            content.addView(text(HomeTextCopy.timelineEmptyText(), 15, MUTED, false));
            return;
        }
        for (RecordsImportModels.KanjiTimelineEvent event : timeline.events) {
            content.addView(timelineEventView(event));
        }
    }

    View timelineStatusCard(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        int color = timelineToneColor(TimelineCopy.statusTone(timeline, System.currentTimeMillis()));
        LinearLayout box = panelBox(Color.WHITE, color);
        box.addView(text(TimelineCopy.statusText(timeline, System.currentTimeMillis()), 20, INK, true));
        RecordsImportModels.DashboardRow row = timeline.currentRow;
        if (row != null) {
            box.addView(text(HomeTextCopy.matureSupportTargetText(row.matureSupportCount, settings().matureSupportThreshold), 15, MUTED, false));
        } else {
            box.addView(text(HomeTextCopy.noActiveEvidenceText(), 15, MUTED, false));
        }
        return box;
    }

    View timelineEventView(RecordsImportModels.KanjiTimelineEvent event) {
        LinearLayout box = panelBox(Color.WHITE, timelineToneColor(TimelineCopy.eventTone(event.eventType)));
        box.addView(text(DateTextPolicy.timelineDate(event.occurredAtMillis), 13, MUTED, false));
        box.addView(text(event.title, 18, INK, true));
        if (!event.detail.isEmpty()) {
            box.addView(text(event.detail, 15, MUTED, false));
        }
        String source = TimelineCopy.sourceLine(event);
        if (!source.isEmpty()) {
            box.addView(text(source, 14, INK, true));
        }
        return box;
    }

    int timelineToneColor(TimelineCopy.Tone tone) {
        if (tone == TimelineCopy.Tone.POSITIVE) {
            return TEAL;
        }
        if (tone == TimelineCopy.Tone.WARNING) {
            return CORAL;
        }
        return BLUE;
    }

    View exampleView(RecordsImportModels.Example example) {
        int color = SOURCE_SUSPENDED.equals(example.sourceType) ? CORAL : TEAL;
        LinearLayout box = panelBox(Color.WHITE, color);
        box.addView(chip(HomeTextCopy.exampleSourceLabel(example), color));
        box.addView(text(HomeTextCopy.exampleExpressionLine(example), 22, INK, true));
        if (!example.sentence.isEmpty()) {
            box.addView(text(example.sentence, 16, MUTED, false));
        }
        String meaning = HomeTextCopy.exampleMeaningLine(example);
        if (!meaning.isEmpty()) {
            box.addView(text(meaning, 15, MUTED, false));
        }
        return box;
    }
}
