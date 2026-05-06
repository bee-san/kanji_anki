package dev.bee.kanjianki;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintPolicy;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StudyRating;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingRatingMapper;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.update.AutoUpdateScheduler;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    public static final String EXTRA_OPEN_UPDATE = "dev.bee.kanjianki.extra.OPEN_UPDATE";
    private static final int REQUEST_POST_NOTIFICATIONS = 704;
    private static final int BG = Color.rgb(255, 247, 251);
    private static final int INK = Color.rgb(45, 22, 53);
    private static final int MUTED = Color.rgb(108, 86, 116);
    private static final int CORAL = Color.rgb(255, 76, 118);
    private static final int TEAL = Color.rgb(0, 174, 181);
    private static final int GOLD = Color.rgb(255, 214, 64);
    private static final int BLUE = Color.rgb(110, 92, 230);
    private static final long DAY_MILLIS = 86_400_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final HintProgression hintProgression = new HintProgression();
    private final WritingRatingMapper writingRatingMapper = new WritingRatingMapper();
    private LocalStore store;
    private AnkiDroidGateway gateway;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout studyActionBar;
    private Records.StudySession activeSession;
    private DrawingPadView drawingPad;
    private TextView studyStatus;
    private TextView resultStatus;
    private Button checkWritingButton;
    private Button downloadModelButton;
    private Button manualOverrideButton;
    private Button nextAfterPassButton;
    private Button practiceWithGuideButton;
    private Button replayButton;
    private Button hintButton;
    private View studyAnswerPanel;
    private WritingAnalysis activeAnalysis;
    private boolean checkingWriting;
    private boolean writingModelDownloaded;
    private boolean writingModelStatusKnown;
    private boolean continueAllKanjiSession;
    private int hintsUsed;
    private int currentPracticeLevel;
    private HintState currentHintState = HintState.initial();
    private Map<String, StrokeGuide> strokeGuides;
    private WritingRecognizer writingRecognizer;
    private LocalStore.ReminderSettings pendingReminderSettings;
    private static AnkiDroidGateway ankiDroidGatewayForTests;
    private static WritingRecognizer writingRecognizerForTests;
    private static Boolean installPermissionForTests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        gateway = ankiDroidGatewayForTests == null ? new AnkiDroidGateway(this) : ankiDroidGatewayForTests;
        requestAnkiPermissionIfNeeded();
        ReminderScheduler.schedule(this);
        AutoSyncScheduler.schedule(this);
        AutoUpdateScheduler.schedule(this);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (writingRecognizer != null && writingRecognizer != writingRecognizerForTests) {
            writingRecognizer.close();
        }
        if (store != null) {
            store.close();
        }
        super.onDestroy();
    }

    public static void setWritingRecognizerForTests(WritingRecognizer recognizer) {
        writingRecognizerForTests = recognizer;
    }

    public static void setAnkiDroidGatewayForTests(AnkiDroidGateway gateway) {
        ankiDroidGatewayForTests = gateway;
    }

    public static void setInstallPermissionForTests(Boolean allowed) {
        installPermissionForTests = allowed;
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) {
            renderUpdate();
        } else {
            renderHome();
        }
    }

    private void requestAnkiPermissionIfNeeded() {
        AnkiDroidGateway.ProviderStatus status = gateway.status();
        if (status.permission != null && !status.permissionGranted) {
            requestPermissions(new String[]{status.permission}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7) {
            renderHome();
        } else if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            LocalStore.ReminderSettings pending = pendingReminderSettings;
            if (granted) {
                LocalStore.ReminderSettings reminder = pending == null ? store.reminderSettings() : pending;
                store.saveReminderSettings(reminder);
                ReminderScheduler.schedule(this, reminder);
                if (ReminderScheduler.notificationsAllowed(this)) {
                    Toast.makeText(this, "Reminder saved for around " + reminder.displayTime() + ".", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Reminder saved, but Android notifications are off.", Toast.LENGTH_LONG).show();
                }
            } else {
                LocalStore.ReminderSettings fallback = pending == null ? store.reminderSettings() : pending;
                store.saveReminderSettings(new LocalStore.ReminderSettings(false, fallback.hour, fallback.minute));
                ReminderScheduler.cancel(this);
                Toast.makeText(this, "Notifications are off, so reminders are disabled.", Toast.LENGTH_LONG).show();
            }
            pendingReminderSettings = null;
            renderSettings();
        }
    }

    private void base(String selected) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        studyActionBar = new LinearLayout(this);
        studyActionBar.setOrientation(LinearLayout.VERTICAL);
        studyActionBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        studyActionBar.setBackground(panel(Color.WHITE, Color.rgb(246, 202, 225), 0));
        studyActionBar.setVisibility(View.GONE);
        root.addView(studyActionBar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout nav = nav(selected, 0);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(78)));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(18), dp(18) + top, dp(18), dp(18));
            nav.setPadding(dp(10), dp(8), dp(10), dp(8) + bottom);
            ViewGroup.LayoutParams navParams = nav.getLayoutParams();
            navParams.height = dp(78) + bottom;
            nav.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private LinearLayout nav(String selected, int navigationInset) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8) + navigationInset);
        nav.setBackgroundColor(INK);
        nav.addView(navButton("Home", selected.equals("home"), this::renderHome));
        nav.addView(navButton("Study", selected.equals("study"), this::renderStudy));
        nav.addView(navButton("Stats", selected.equals("stats"), this::renderStats));
        nav.addView(navButton("Settings", selected.equals("settings"), this::renderSettings));
        return nav;
    }

    private Button navButton(String label, boolean active, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(active ? INK : Color.WHITE);
        button.setBackground(panel(active ? GOLD : Color.TRANSPARENT, active ? GOLD : Color.rgb(70, 70, 70), dp(18)));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void renderHome() {
        base("home");
        TextView title = text("Kani", 42, INK, true);
        title.setLetterSpacing(0);
        content.addView(title);
        content.addView(text("Recognise and write the kanji your Kiku reviews keep exposing.", 18, MUTED, false));
        addSpace(18);

        LocalStore.SyncStatus sync = store.latestSync();
        AnkiDroidGateway.ProviderStatus provider = gateway.status();
        LinearLayout hero = band(provider.canSync ? TEAL : CORAL);
        hero.addView(text(provider.canSync ? "Ready to sync" : "AnkiDroid needs attention", 26, Color.WHITE, true));
        hero.addView(text(provider.message, 16, Color.WHITE, false));
        if (sync != null) {
            hero.addView(text(sync.headline(), 16, Color.WHITE, false));
            if (!sync.removalMessage.isEmpty()) {
                hero.addView(text(sync.removalMessage, 14, Color.WHITE, false));
            }
        } else {
            hero.addView(text("Sync once to find the kanji your Anki reviews keep exposing.", 16, Color.WHITE, false));
        }
        hero.addView(text("Study starts with recall, then uses writing when a problem kanji needs repair.", 14, Color.WHITE, false));
        content.addView(hero);
        addSpace(18);

        long now = System.currentTimeMillis();
        List<Records.DashboardRow> rows = store.dashboardRows();
        List<Records.StudyItem> homeItems = studyQueue(rows, now, false);
        Records.AdaptiveLoadPlan homePlan = rows.isEmpty() ? null : adaptivePlan(rows, homeItems, now);
        content.addView(streakPanel(store.studyStreak(now)));
        addSpace(10);
        if (homePlan != null) {
            content.addView(adaptiveFocusPanel(homePlan));
            addSpace(10);
        }

        if (rows.isEmpty()) {
            Button syncButton = primaryButton("Sync AnkiDroid", TEAL);
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
        } else {
            Button studyButton = primaryButton("Study now", CORAL);
            studyButton.setOnClickListener(v -> startFocusedStudy());
            content.addView(studyButton);

            Button syncAgainButton = secondaryButton("Sync again");
            syncAgainButton.setOnClickListener(v -> confirmSync());
            content.addView(syncAgainButton);
        }

        addSpace(16);
        if (rows.isEmpty()) {
            emptyState("No kanji queued yet", "After the first sync, this screen shows the kanji that need focused recall and writing practice.");
        } else {
            List<QueueEntry> entries = queuedEntries(rows, homeItems, now, homePlan);
            content.addView(sectionTitle("Adaptive focus queue"));
            if (entries.isEmpty()) {
                emptyState("No active practice yet", "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.");
            }
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                content.addView(queueRowView(entries.get(i), now));
            }
        }
    }

    private LinearLayout streakPanel(LocalStore.StudyStreak streak) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(255, 219, 103));
        box.addView(text("Study streak", 22, INK, true));
        box.addView(text(streakHeadline(streak), 25, streak.currentDays > 0 ? CORAL : MUTED, true));
        box.addView(text(streakBody(streak), 15, MUTED, false));
        return box;
    }

    private LinearLayout adaptiveFocusPanel(Records.AdaptiveLoadPlan plan) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text("Today's Pareto focus", 22, INK, true));
        String headline = plan.allKanjiMode
                ? "All current problem kanji"
                : plan.remaining + " left / " + plan.target;
        box.addView(text(headline, 25, plan.remaining > 0 ? CORAL : TEAL, true));
        box.addView(text(plan.status, 15, MUTED, false));
        return box;
    }

    private String streakHeadline(LocalStore.StudyStreak streak) {
        if (streak.currentDays <= 0) {
            return "No streak yet";
        }
        return streak.currentDays + "-day streak";
    }

    private String streakBody(LocalStore.StudyStreak streak) {
        String best = streak.bestDays > 0 ? " Best: " + streakDayCount(streak.bestDays) + "." : "";
        if (streak.currentDays <= 0) {
            if (streak.lastStudyAtMillis <= 0L) {
                return "Study one problem kanji to start." + best;
            }
            return "Start a new streak today. Last studied " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(streak.lastStudyAtMillis)) + "." + best;
        }
        if (streak.studiedToday) {
            return "Streak logged today. " + countText(streak.reviewsToday, "writing review today", "writing reviews today") + "." + best;
        }
        return "Study one problem kanji today to keep it alive." + best;
    }

    private String streakDayCount(int days) {
        return days + " " + (days == 1 ? "day" : "days");
    }

    private void confirmSync() {
        new AlertDialog.Builder(this)
                .setTitle("Sync AnkiDroid?")
                .setMessage("Kani will read your Kiku cards, copy problem kanji into writing practice, and mark imported suspended cards as archived after they are stored safely.")
                .setPositiveButton("Sync cards", (dialog, which) -> runSync())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runSync() {
        base("home");
        content.addView(text("Finding problem kanji", 34, INK, true));
        content.addView(text("Reading Kiku cards, saving missed kanji locally, and updating archived suspended cards when AnkiDroid allows it.", 17, MUTED, false));
        io.execute(() -> {
            ManualSyncEngine.SyncResult result = new ManualSyncEngine(this, store, gateway, settings()).run();
            if (result.success) {
                store.activateAutoSyncAfterFirstSuccess();
                AutoSyncScheduler.schedule(this);
            }
            main.post(() -> {
                renderSyncResult(result);
            });
        });
    }

    private void renderSyncResult(ManualSyncEngine.SyncResult result) {
        base("home");
        if (result.skipped) {
            content.addView(text("Sync already running", 34, INK, true));
            LinearLayout info = band(BLUE);
            info.addView(text(result.message == null || result.message.isEmpty() ? "Kani is already reading AnkiDroid." : result.message, 17, Color.WHITE, false));
            content.addView(info);
            Button home = secondaryButton("Back home");
            home.setOnClickListener(v -> renderHome());
            content.addView(home);
        } else if (result.success) {
            content.addView(text("Sync complete", 34, INK, true));
            LinearLayout summary = band(TEAL);
            long now = System.currentTimeMillis();
            List<Records.DashboardRow> rows = store.dashboardRows();
            List<Records.StudyItem> items = store.studyItems();
            Records.AdaptiveLoadPlan plan = adaptivePlan(rows, items, now);
            List<QueueEntry> entries = queuedEntries(rows, items, now, plan);
            summary.addView(text(countText(entries.size(), "kanji ready to study", "kanji ready to study"), 24, Color.WHITE, true));
            summary.addView(text(countText(result.dashboardRows, "candidate found from Kiku", "candidates found from Kiku") + ". " + adaptiveFocusText(plan) + ".", 16, Color.WHITE, false));
            if (!result.adaptiveSummary.isEmpty()) {
                summary.addView(text(result.adaptiveSummary, 15, Color.WHITE, false));
            }
            if (result.importedSuspendedKanji > 0) {
                summary.addView(text(countText(result.importedSuspendedKanji, "new archived suspended kanji added", "new archived suspended kanji added"), 15, Color.WHITE, false));
            }
            if (result.message != null && !result.message.isEmpty()) {
                summary.addView(text(result.message, 14, Color.WHITE, false));
            }
            content.addView(summary);
            if (result.dashboardRows > 0) {
                Button study = primaryButton("Study now", CORAL);
                study.setOnClickListener(v -> startFocusedStudy());
                content.addView(study);
            }
            Button home = secondaryButton("Back home");
            home.setOnClickListener(v -> renderHome());
            content.addView(home);
        } else {
            content.addView(text("Sync needs attention", 34, INK, true));
            LinearLayout error = band(CORAL);
            error.addView(text("Could not read AnkiDroid", 24, Color.WHITE, true));
            error.addView(text(result.message == null || result.message.isEmpty() ? "Try again after checking AnkiDroid permissions." : result.message, 16, Color.WHITE, false));
            content.addView(error);
            Button retry = primaryButton("Try sync again", TEAL);
            retry.setOnClickListener(v -> confirmSync());
            content.addView(retry);
            Button home = secondaryButton("Back home");
            home.setOnClickListener(v -> renderHome());
            content.addView(home);
        }
    }

    private void renderStats() {
        base("stats");
        content.addView(text("Stats", 34, INK, true));
        content.addView(text("Kani does not replace Anki. It repairs weak kanji from your Anki reviews, then steps back when sync shows enough support.", 16, MUTED, false));
        addSpace(10);

        long now = System.currentTimeMillis();
        List<Records.DashboardRow> rows = store.dashboardRows();
        List<Records.StudyItem> items = store.studyItems();
        Records.AdaptiveLoadPlan plan = adaptivePlan(rows, items, now);
        List<QueueEntry> entries = queuedEntries(rows, items, now, plan);
        LocalStore.SyncStatus sync = store.latestSync();
        LocalStore.StudyImpactStats impact = store.studyImpactStats();
        Records.ReviewStats week = store.reviewStatsSince(now - 7 * DAY_MILLIS);
        LocalStore.StudyStreak streak = store.studyStreak(now);

        content.addView(ankiImpactPanel(sync, rows));
        content.addView(sectionTitle("Learning"));
        content.addView(statPanel(
                "Kani writing",
                impact.totalReviews == 0 ? "No writing reviews yet" : countText(impact.totalReviews, "writing review", "writing reviews"),
                countText(impact.distinctReviewedKanji, "kanji studied", "kanji studied") + ". " + countText(impact.manualOverrides, "manual override", "manual overrides") + ".",
                CORAL
        ));
        content.addView(statPanel(
                "Recall quality",
                automaticPassText(impact),
                recallQualityBody(impact),
                BLUE
        ));
        content.addView(statPanel(
                "Daily rhythm",
                streakHeadline(streak),
                streak.studiedToday
                        ? countText(streak.reviewsToday, "writing review today", "writing reviews today") + ". Best: " + streakDayCount(streak.bestDays) + "."
                        : "Study one problem kanji today to keep it alive.",
                GOLD
        ));

        content.addView(sectionTitle("Anki Bridge"));
        int due = dueEntryCount(entries, now);
        int matureSupport = matureSupportCount(rows);
        int retired = retiredItemCount(items);
        content.addView(statPanel(
                "Now practicing",
                countText(entries.size(), "active kanji", "active kanji"),
                countText(due, "due now", "due now") + ". " + adaptiveFocusText(plan) + ".",
                TEAL
        ));
        content.addView(statPanel(
                "Retired back to Anki",
                countText(retired, "kanji resting in Kani", "kanji resting in Kani"),
                countText(matureSupport, "mature Anki support link", "mature Anki support links") + ". Retired means Kani can stop drilling when synced Anki evidence has caught up.",
                BLUE
        ));
        content.addView(statPanel(
                "Last 7 days",
                week.total == 0 ? "No reviews this week" : countText(week.total, "writing review", "writing reviews"),
                weeklyReviewBody(week),
                CORAL
        ));

        LinearLayout meaning = band(GOLD);
        meaning.addView(text("What this means", 24, INK, true));
        meaning.addView(text(statsMeaning(sync, rows, impact, matureSupport, retired), 16, INK, false));
        content.addView(meaning);
    }

    private LinearLayout ankiImpactPanel(LocalStore.SyncStatus sync, List<Records.DashboardRow> rows) {
        LinearLayout box = band(TEAL);
        box.addView(text("Anki impact", 26, Color.WHITE, true));
        if (sync == null) {
            box.addView(text("Sync AnkiDroid to connect Kani stats to your Kiku cards.", 16, Color.WHITE, false));
            box.addView(text("After sync, this page shows which problem kanji came from Anki, which misses became writing practice, and where Anki has mature support.", 15, Color.WHITE, false));
            return box;
        }
        box.addView(text(countText(rows.size(), "problem kanji found from AnkiDroid", "problem kanji found from AnkiDroid"), 22, Color.WHITE, true));
        box.addView(text(countText(activeEvidenceCount(rows), "active Anki example link", "active Anki example links") + " and " + countText(suspendedEvidenceCount(rows), "suspended miss link", "suspended miss links") + " explain why they are here.", 15, Color.WHITE, false));
        box.addView(text(latestSyncText(sync), 14, Color.WHITE, false));
        return box;
    }

    private LinearLayout statPanel(String title, String value, String body, int stroke) {
        LinearLayout box = panelBox(Color.WHITE, stroke);
        box.addView(text(title, 18, MUTED, true));
        box.addView(text(value, 25, INK, true));
        box.addView(text(body, 15, MUTED, false));
        return box;
    }

    private String automaticPassText(LocalStore.StudyImpactStats impact) {
        if (impact.writingRequired == 0) {
            return "No automatic checks yet";
        }
        return Math.round(100.0 * impact.writingPassed / impact.writingRequired) + "% automatic pass rate";
    }

    private String recallQualityBody(LocalStore.StudyImpactStats impact) {
        if (impact.totalReviews == 0) {
            return "Misses caught during handwriting checks will show here after you study.";
        }
        return countText(impact.writingFailed, "miss caught", "misses caught") + " before they could stay vague in Anki.";
    }

    private String weeklyReviewBody(Records.ReviewStats week) {
        if (week.total == 0) {
            return "Recent misses and passes will show here after you study.";
        }
        return countText(week.again, "miss", "misses") + ", " + countText(week.hard, "shaky pass", "shaky passes") + ", " + countText(week.good + week.easy, "solid pass", "solid passes") + ".";
    }

    private String latestSyncText(LocalStore.SyncStatus sync) {
        if (!"success".equals(sync.status)) {
            return "Latest sync is blocked: " + sync.errorMessage;
        }
        String when = sync.finishedAt > 0L
                ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(sync.finishedAt))
                : "latest sync";
        return "Latest sync " + when + ": "
                + countText(sync.activeCards, "active Anki card checked", "active Anki cards checked")
                + ", " + countText(sync.suspendedCards, "suspended card archived", "suspended cards archived") + ".";
    }

    private String statsMeaning(LocalStore.SyncStatus sync, List<Records.DashboardRow> rows, LocalStore.StudyImpactStats impact, int matureSupport, int retired) {
        if (sync == null || rows.isEmpty()) {
            return "Start with an AnkiDroid sync. Kani will only count kanji that came from your own Kiku cards, so these stats stay tied to the deck you actually review.";
        }
        if (impact.totalReviews == 0) {
            return "Kani has found problem kanji in Anki. Study one and this page will start showing writing reviews, caught misses, and whether Anki later has enough mature support to let that kanji rest.";
        }
        if (retired > 0 || matureSupport > 0) {
            return "You are turning Anki pain points into focused writing reps. Mature Anki support and resting Kani items are the strongest signs that the bridge is working.";
        }
        return "You are writing kanji that came from Anki misses or weak support. Keep syncing after Anki reviews; Kani will watch for mature support and stop drilling kanji that Anki has recovered.";
    }

    private int activeEvidenceCount(List<Records.DashboardRow> rows) {
        int total = 0;
        for (Records.DashboardRow row : rows) {
            total += row.activeExampleCount;
        }
        return total;
    }

    private int suspendedEvidenceCount(List<Records.DashboardRow> rows) {
        int total = 0;
        for (Records.DashboardRow row : rows) {
            total += row.suspendedExampleCount;
        }
        return total;
    }

    private int matureSupportCount(List<Records.DashboardRow> rows) {
        int total = 0;
        for (Records.DashboardRow row : rows) {
            total += row.matureSupportCount;
        }
        return total;
    }

    private int retiredItemCount(List<Records.StudyItem> items) {
        int total = 0;
        for (Records.StudyItem item : items) {
            if ("retired".equals(item.state)) {
                total++;
            }
        }
        return total;
    }

    private List<Records.StudyItem> studyQueue(List<Records.DashboardRow> rows, long now, boolean persist) {
        return studyQueue(rows, now, persist, null);
    }

    private List<Records.StudyItem> studyQueue(List<Records.DashboardRow> rows, long now, boolean persist, Records.AdaptiveLoadPlan plan) {
        List<Records.StudyItem> currentItems = store.studyItems();
        if (!persist) {
            return currentItems;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        Records.AdaptiveLoadPlan effectivePlan = plan == null ? adaptivePlan(rows, currentItems, now) : plan;
        List<Records.StudyItem> seeded = scheduler.seedQueue(rows, currentItems, settings(), now, startOfDay(now), effectivePlan);
        store.replaceStudyItems(seeded);
        return seeded;
    }

    private List<QueueEntry> queuedEntries(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        return queuedEntries(rows, items, now, null);
    }

    private List<QueueEntry> queuedEntries(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now, Records.AdaptiveLoadPlan plan) {
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        Map<String, Integer> focusOrder = new HashMap<>();
        if (plan != null) {
            for (int i = 0; i < plan.focusKanji.size(); i++) {
                focusOrder.put(plan.focusKanji.get(i), i);
            }
        }
        List<QueueEntry> entries = new ArrayList<>();
        for (Records.StudyItem item : items) {
            if ("retired".equals(item.state)) {
                continue;
            }
            Records.DashboardRow row = rowByKanji.get(item.kanji);
            if (row != null) {
                entries.add(new QueueEntry(row, item));
            }
        }
        entries.sort(Comparator
                .comparingInt((QueueEntry entry) -> focusOrder.getOrDefault(entry.row.kanji, Integer.MAX_VALUE))
                .thenComparingInt((QueueEntry entry) -> entry.item.dueAtMillis <= now ? 0 : 1)
                .thenComparingInt(entry -> stateRank(entry.item.state))
                .thenComparingLong(entry -> entry.item.dueAtMillis)
                .thenComparingInt(entry -> -entry.row.weaknessScore)
                .thenComparing(entry -> entry.row.kanji));
        return entries;
    }

    private int dueEntryCount(List<QueueEntry> entries, long now) {
        int due = 0;
        for (QueueEntry entry : entries) {
            if (entry.item.dueAtMillis <= now) {
                due++;
            }
        }
        return due;
    }

    private int stateRank(String state) {
        if ("learning".equals(state)) {
            return 0;
        }
        if ("review".equals(state)) {
            return 1;
        }
        if ("new".equals(state)) {
            return 2;
        }
        return 3;
    }

    private int rowColor(Records.StudyItem item, long now) {
        if (item.dueAtMillis <= now) {
            return CORAL;
        }
        if ("learning".equals(item.state)) {
            return BLUE;
        }
        return Color.rgb(246, 202, 225);
    }

    private String queueStatusText(Records.StudyItem item, long now) {
        String state = "new".equals(item.state) ? "new problem" : item.state;
        return state + " · " + dueText(item.dueAtMillis, now);
    }

    private String dueText(long dueAt, long now) {
        if (dueAt <= now) {
            return "due now";
        }
        long delta = dueAt - now;
        long minutes = Math.max(1L, delta / 60_000L);
        if (minutes < 60L) {
            return "due in " + minutes + " min";
        }
        long hours = Math.max(1L, delta / 3_600_000L);
        if (hours < 24L) {
            return "due in " + hours + " hr";
        }
        return "due " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(dueAt));
    }

    private View queueRowView(QueueEntry entry, long now) {
        Records.DashboardRow row = entry.row;
        Records.StudyItem item = entry.item;
        LinearLayout box = panelBox(Color.WHITE, rowColor(item, now));
        box.setOnClickListener(v -> renderDetail(row.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView kanji = text(row.kanji, 44, INK, true);
        kanji.setGravity(Gravity.CENTER);
        top.addView(kanji, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(rowMeaning(row), 19, INK, true));
        copy.addView(text(queueStatusText(item, now), 14, MUTED, false));
        copy.addView(text(sourceEvidenceText(row), 14, INK, true));
        copy.addView(text(row.reasonText, 14, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(item.dueAtMillis <= now ? "due now" : "resting", item.dueAtMillis <= now ? CORAL : BLUE));
        chips.addView(chip(item.state, TEAL));
        chips.addView(chip(recognitionStageLabel(item), BLUE));
        if (item.writingRemediationPending) {
            chips.addView(chip("writing repair", CORAL));
        } else if (item.consecutiveFailedRecognitionDays > 0) {
            chips.addView(chip("miss days " + item.consecutiveFailedRecognitionDays, BLUE));
        }
        box.addView(chips);
        return box;
    }

    private String recognitionStageLabel(Records.StudyItem item) {
        switch (Math.max(0, Math.min(2, item.recognitionStage))) {
            case 1:
                return "font -> meaning";
            case 2:
                return "word -> reading";
            default:
                return "kanji -> meaning";
        }
    }

    private String sourceEvidenceText(Records.DashboardRow row) {
        String active = "";
        String suspended = "";
        for (Records.Example example : row.examples) {
            if (active.isEmpty() && "active".equals(example.sourceType)) {
                active = example.expression;
            } else if (suspended.isEmpty() && "suspended".equals(example.sourceType)) {
                suspended = example.expression;
            }
        }
        if (!active.isEmpty() && !suspended.isEmpty()) {
            return "From " + active + " · missed " + suspended;
        }
        if (!active.isEmpty()) {
            return "From " + active;
        }
        if (!suspended.isEmpty()) {
            return "Missed " + suspended;
        }
        return "From your AnkiDroid sync";
    }

    private void renderDetail(String kanji) {
        base("home");
        Records.KanjiRecoveryTimeline timeline = store.timelineForKanji(kanji);
        Records.DashboardRow row = timeline.currentRow;
        if (row == null && timeline.currentStudyItem == null && timeline.events.isEmpty()) {
            emptyState("Kanji not found", "This row may have disappeared after a sync.");
            return;
        }
        String displayKanji = row == null ? kanji : row.kanji;
        TextView glyph = text(displayKanji, 92, INK, true);
        glyph.setGravity(Gravity.CENTER);
        content.addView(glyph);
        if (row == null) {
            content.addView(text("Historical recovery", 25, INK, true));
        } else {
            content.addView(text(rowMeaning(row), 25, INK, true));
            content.addView(text(row.reading, 20, TEAL, true));
        }
        addSpace(10);
        LinearLayout why = band(BLUE);
        why.addView(text("Why it is here", 22, Color.WHITE, true));
        if (row == null) {
            why.addView(text("This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history.", 17, Color.WHITE, false));
        } else {
            why.addView(text(row.reasonText, 17, Color.WHITE, false));
            why.addView(text("Anki browser: " + row.browserSearch, 14, Color.WHITE, false));
        }
        content.addView(why);
        if (row != null) {
            Button practice = primaryButton("Review this now", CORAL);
            practice.setOnClickListener(v -> renderStudyForKanji(row.kanji));
            content.addView(practice);
            Button copy = secondaryButton("Copy Anki search");
            copy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Anki search", row.browserSearch));
                if (v instanceof Button) {
                    ((Button) v).setText(R.string.copied_anki_search);
                }
                Toast.makeText(this, "Search copied", Toast.LENGTH_SHORT).show();
            });
            content.addView(copy);
        }
        addSpace(12);
        addRecoveryTimeline(timeline);
        if (row != null) {
            addSpace(12);
            content.addView(sectionTitle("Examples"));
            for (Records.Example example : row.examples) {
                content.addView(exampleView(example));
            }
        }
    }

    private void addRecoveryTimeline(Records.KanjiRecoveryTimeline timeline) {
        content.addView(sectionTitle("Recovery timeline"));
        content.addView(timelineStatusCard(timeline));
        if (timeline.events.isEmpty()) {
            content.addView(text("Timeline will fill in after the next sync or review.", 15, MUTED, false));
            return;
        }
        for (Records.KanjiTimelineEvent event : timeline.events) {
            content.addView(timelineEventView(event));
        }
    }

    private View timelineStatusCard(Records.KanjiRecoveryTimeline timeline) {
        int color = timelineStatusColor(timeline);
        LinearLayout box = panelBox(Color.WHITE, color);
        box.addView(text(timelineStatusText(timeline), 20, INK, true));
        Records.DashboardRow row = timeline.currentRow;
        if (row != null) {
            box.addView(text(String.format(Locale.ROOT, "Mature support %d / target %d", row.matureSupportCount, settings().matureSupportThreshold), 15, MUTED, false));
        } else {
            box.addView(text("No active Anki evidence in the latest local sync.", 15, MUTED, false));
        }
        return box;
    }

    private String timelineStatusText(Records.KanjiRecoveryTimeline timeline) {
        Records.StudyItem item = timeline.currentStudyItem;
        if (item != null && "retired".equals(item.state)) {
            return "Retired by Anki support";
        }
        if (item != null && item.dueAtMillis > System.currentTimeMillis()) {
            return "Resting until review";
        }
        if (timeline.currentRow == null) {
            return "Retired by Anki support";
        }
        return "Active repair";
    }

    private int timelineStatusColor(Records.KanjiRecoveryTimeline timeline) {
        Records.StudyItem item = timeline.currentStudyItem;
        if (item != null && "retired".equals(item.state)) {
            return TEAL;
        }
        if (item != null && item.dueAtMillis > System.currentTimeMillis()) {
            return BLUE;
        }
        return CORAL;
    }

    private View timelineEventView(Records.KanjiTimelineEvent event) {
        LinearLayout box = panelBox(Color.WHITE, timelineEventColor(event.eventType));
        box.addView(text(timelineDate(event.occurredAtMillis), 13, MUTED, false));
        box.addView(text(event.title, 18, INK, true));
        if (!event.detail.isEmpty()) {
            box.addView(text(event.detail, 15, MUTED, false));
        }
        String source = timelineSourceLine(event);
        if (!source.isEmpty()) {
            box.addView(text(source, 14, INK, true));
        }
        return box;
    }

    private int timelineEventColor(String eventType) {
        if ("review_failed".equals(eventType) || "support_dropped".equals(eventType) || "reopened".equals(eventType)) {
            return CORAL;
        }
        if ("review_passed".equals(eventType) || "support_improved".equals(eventType) || "retired".equals(eventType)) {
            return TEAL;
        }
        return BLUE;
    }

    private String timelineDate(long occurredAt) {
        if (occurredAt <= 0L) {
            return "Unknown time";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(occurredAt));
    }

    private String timelineSourceLine(Records.KanjiTimelineEvent event) {
        if (event.sourceExpression.isEmpty()) {
            return "";
        }
        if (event.sourceReading.isEmpty()) {
            return "Source: " + event.sourceExpression;
        }
        return "Source: " + event.sourceExpression + "  " + event.sourceReading;
    }

    private View exampleView(Records.Example example) {
        int color = "suspended".equals(example.sourceType) ? CORAL : TEAL;
        LinearLayout box = panelBox(Color.WHITE, color);
        box.addView(chip(example.sourceType.toUpperCase(Locale.ROOT), color));
        box.addView(text(example.expression + (example.reading.isEmpty() ? "" : "  " + example.reading), 22, INK, true));
        if (!example.sentence.isEmpty()) {
            box.addView(text(example.sentence, 16, MUTED, false));
        }
        if (!example.meaning.isEmpty()) {
            box.addView(text(cleanLearnerText(example.meaning, example.meaning, 120), 15, MUTED, false));
        }
        return box;
    }

    private View learningPanel(Records.StudySession session) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.addView(text("Reference", 19, INK, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 72, INK, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            details.addView(text("Meaning: " + rowMeaning(session.row), 16, INK, true));
            if (!session.row.reading.isEmpty()) {
                details.addView(text("Reading: " + session.row.reading, 15, TEAL, true));
            }
            Records.Example example = firstExample(session.row);
            if (example != null) {
                details.addView(text("Example: " + example.expression + (example.reading.isEmpty() ? "" : "  " + example.reading), 15, INK, true));
                if (!example.meaning.isEmpty()) {
                    details.addView(text(cleanLearnerText(example.meaning, "", 80), 13, MUTED, false));
                }
            }
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        box.addView(text("Trace it below, then check.", 13, MUTED, false));
        return box;
    }

    private Records.Example firstExample(Records.DashboardRow row) {
        if (row == null || row.examples.isEmpty()) {
            return null;
        }
        for (Records.Example example : row.examples) {
            if ("active".equals(example.sourceType)) {
                return example;
            }
        }
        return row.examples.get(0);
    }

    private void renderStudy() {
        base("study");
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            content.addView(text("Study practice", 34, INK, true));
            emptyState("Nothing to study yet", "Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = System.currentTimeMillis();
        List<Records.StudyItem> beforeSeed = store.studyItems();
        Records.AdaptiveLoadPlan plan = adaptivePlan(rows, beforeSeed, now);
        List<Records.StudyItem> seeded = studyQueue(rows, now, true, plan);
        Records.AdaptiveLoadPlan seededPlan = adaptivePlan(rows, seeded, now);
        Set<String> focus = continueAllKanjiSession || seededPlan.allKanjiMode
                ? null
                : new HashSet<>(seededPlan.focusKanji);
        activeSession = scheduler.nextSession(seeded, rows, now, focus);
        if (activeSession == null) {
            if (!continueAllKanjiSession && seededPlan.focusComplete()) {
                renderFocusDone(seededPlan);
                return;
            }
            content.addView(text("Nothing due now", 34, INK, true));
            content.addView(text("Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.", 18, MUTED, false));
            Button back = primaryButton("Back home", TEAL);
            back.setOnClickListener(v -> renderHome());
            content.addView(back);
            return;
        }
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private void renderFocusDone(Records.AdaptiveLoadPlan plan) {
        content.addView(text("Today's focus done", 34, INK, true));
        content.addView(text("Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji.", 18, MUTED, false));
        LinearLayout summary = band(TEAL);
        summary.addView(text("Today's Pareto focus: 0 left / " + plan.target, 22, Color.WHITE, true));
        summary.addView(text(plan.status, 15, Color.WHITE, false));
        content.addView(summary);
        Button keepGoing = primaryButton("Continue all kanji", CORAL);
        keepGoing.setOnClickListener(v -> {
            continueAllKanjiSession = true;
            renderStudy();
        });
        content.addView(keepGoing);
        Button back = secondaryButton("Back home");
        back.setOnClickListener(v -> {
            continueAllKanjiSession = false;
            renderHome();
        });
        content.addView(back);
    }

    private void startFocusedStudy() {
        continueAllKanjiSession = false;
        renderStudy();
    }

    private void renderStudyForKanji(String kanji) {
        base("study");
        List<Records.DashboardRow> rows = store.dashboardRows();
        Records.DashboardRow row = findRow(rows, kanji);
        if (row == null) {
            content.addView(text("Study practice", 34, INK, true));
            emptyState("Kanji not available", "This row may have changed after sync.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = System.currentTimeMillis();
        List<Records.StudyItem> seeded = studyQueue(rows, now, true);
        Records.StudyItem item = findStudyItem(seeded, kanji);
        if (item == null) {
            item = new Records.StudyItem(
                    kanji,
                    "new",
                    now,
                    0.4,
                    5.0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    now
            );
        }
        String token = item.activeToken == null || item.activeToken.isEmpty()
                ? item.kanji + "-" + UUID.randomUUID()
                : item.activeToken;
        String taskType = taskTypeForStudyItem(item);
        activeSession = new Records.StudySession(
                item.withToken(token),
                row,
                token,
                taskType,
                item.writingRemediationPending,
                row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning
        );
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private String taskTypeForStudyItem(Records.StudyItem item) {
        if (item.writingRemediationPending) {
            return "writing_remediation";
        }
        switch (Math.max(0, Math.min(2, item.recognitionStage))) {
            case 1:
                return "font_meaning";
            case 2:
                return "word_reading";
            default:
                return "kanji_meaning";
        }
    }

    private void renderSession(Records.StudySession session) {
        if (session.writingRequired) {
            renderWritingSession(session);
        } else {
            renderFlashcardSession(session);
        }
    }

    private void renderFlashcardSession(Records.StudySession session) {
        content.removeAllViews();
        activeAnalysis = null;
        checkingWriting = false;
        hintsUsed = 0;
        setHintState(HintState.initial());
        drawingPad = null;

        content.addView(text(flashcardTitle(session), 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (isFontRecognitionTask(session)) {
            stage.addView(text("Recognise the shape across fonts, then reveal the Anki clue.", 15, Color.WHITE, false));
        } else if (isWordReadingTask(session)) {
            stage.addView(text("Read the source word before revealing the answer.", 15, Color.WHITE, false));
        } else {
            stage.addView(text("Name the meaning before revealing the answer.", 15, Color.WHITE, false));
        }
        content.addView(stage);

        content.addView(flashcardPromptPanel(session));
        studyAnswerPanel = flashcardAnswerPanel(session);
        studyAnswerPanel.setVisibility(View.GONE);
        content.addView(studyAnswerPanel);

        buildFlashcardActionBar(false);
    }

    private void renderWritingSession(Records.StudySession session) {
        content.removeAllViews();
        activeAnalysis = null;
        checkingWriting = false;
        hintsUsed = 0;
        setHintState(initialHintState(session));

        content.addView(text("Draw this kanji", 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (session.row != null) {
            if (isRecallTask(session)) {
                stage.addView(text("Prompt: " + sessionClue(session), 17, Color.WHITE, false));
                if (!session.row.reading.isEmpty()) {
                    stage.addView(text("Reading: " + session.row.reading, 15, Color.WHITE, false));
                }
                stage.addView(text("Write the kanji from this prompt. The answer stays hidden until you check.", 15, Color.WHITE, false));
            } else if ("writing_remediation".equals(session.taskType)) {
                stage.addView(text("Recognition has missed on multiple days. Write it once with the guide before returning to recognition.", 15, Color.WHITE, false));
            } else {
                stage.addView(text("Learn it from the reference, trace it, then check.", 15, Color.WHITE, false));
            }
        } else {
            stage.addView(text(session.prompt, 17, Color.WHITE, false));
        }
        content.addView(stage);
        studyAnswerPanel = learningPanel(session);
        content.addView(studyAnswerPanel);

        content.addView(sectionTitle("Writing"));
        StrokeGuide guide = strokeGuide(session.item.kanji);
        studyStatus = text(guideLabel(currentHintState, guide), 16, MUTED, false);
        content.addView(studyStatus);
        drawingPad = new DrawingPadView(this);
        drawingPad.setTarget(session.item.kanji);
        drawingPad.setInkEditListener(this::handleDrawingEdited);
        drawingPad.setGuide(guide, currentHintState, false);
        content.addView(drawingPad, new LinearLayout.LayoutParams(-1, studyPadHeight()));

        buildStudyActionBar();
        updateResultActions();
        refreshWritingModelStatus();
    }

    private String flashcardTitle(Records.StudySession session) {
        if (isWordReadingTask(session)) {
            return "Read this word";
        }
        return isFontRecognitionTask(session) ? "Recognise this kanji" : "Name this kanji";
    }

    private View flashcardPromptPanel(Records.StudySession session) {
        LinearLayout box = panelBox(Color.WHITE, TEAL);
        box.addView(text("Front", 19, INK, true));
        if (isFontRecognitionTask(session)) {
            box.addView(fontVariantRow(session.item.kanji));
            box.addView(text("What does it mean in your Anki deck?", 15, MUTED, false));
            return box;
        }
        if (isWordReadingTask(session)) {
            box.addView(text(wordPrompt(session), 34, INK, true));
            box.addView(text("What is the reading?", 15, MUTED, false));
            return box;
        }
        TextView glyph = text(session.item.kanji, 84, INK, true);
        glyph.setGravity(Gravity.CENTER);
        box.addView(glyph, new LinearLayout.LayoutParams(-1, dp(118)));
        box.addView(text("What does it mean?", 15, MUTED, false));
        box.addView(text("Answer hidden until reveal.", 14, MUTED, false));
        return box;
    }

    private View fontVariantRow(String kanji) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(fontVariantTile(kanji, "Print", Typeface.SERIF), new LinearLayout.LayoutParams(0, dp(120), 1));
        row.addView(fontVariantTile(kanji, "Sans", Typeface.DEFAULT), new LinearLayout.LayoutParams(0, dp(120), 1));
        row.addView(fontVariantTile(kanji, "Block", Typeface.MONOSPACE), new LinearLayout.LayoutParams(0, dp(120), 1));
        return row;
    }

    private View fontVariantTile(String kanji, String label, Typeface typeface) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setBackground(panel(Color.rgb(255, 247, 251), Color.rgb(246, 202, 225), dp(8)));
        TextView glyph = text(kanji, 50, INK, true);
        glyph.setTypeface(typeface, Typeface.BOLD);
        glyph.setGravity(Gravity.CENTER);
        tile.addView(glyph, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView caption = text(label, 12, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        tile.addView(caption);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(120), 1);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        tile.setLayoutParams(lp);
        return tile;
    }

    private View flashcardAnswerPanel(Records.StudySession session) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.addView(text("Answer", 19, INK, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 76, INK, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            details.addView(text("Meaning: " + rowMeaning(session.row), 16, INK, true));
            if (!session.row.reading.isEmpty()) {
                details.addView(text("Reading: " + session.row.reading, 15, TEAL, true));
            }
            Records.Example example = firstExample(session.row);
            if (example != null) {
                details.addView(text("From: " + example.expression + (example.reading.isEmpty() ? "" : "  " + example.reading), 15, INK, true));
                if (!example.meaning.isEmpty()) {
                    details.addView(text(cleanLearnerText(example.meaning, "", 80), 13, MUTED, false));
                }
            }
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        box.addView(text("Misses move the ladder down. After enough missed days, Kani switches this kanji to writing repair.", 13, MUTED, false));
        return box;
    }

    private void buildFlashcardActionBar(boolean revealed) {
        if (studyActionBar == null) {
            return;
        }
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text(
                revealed
                        ? "Choose from what you knew before reveal."
                        : "Reveal first. Misses count toward writing repair only once per day.",
                15,
                MUTED,
                false
        );
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!revealed) {
            Button reveal = primaryButton("Reveal", CORAL);
            reveal.setOnClickListener(v -> {
                if (studyAnswerPanel != null) {
                    studyAnswerPanel.setVisibility(View.VISIBLE);
                }
                buildFlashcardActionBar(true);
            });
            actions.addView(reveal, new LinearLayout.LayoutParams(0, dp(62), 1));
        } else {
            Button known = primaryButton("I knew it", TEAL);
            known.setOnClickListener(v -> submitReview("good", false));
            actions.addView(known, new LinearLayout.LayoutParams(0, dp(62), 1));

            Button write = primaryButton("I missed it", CORAL);
            write.setOnClickListener(v -> submitReview("again", false));
            actions.addView(write, new LinearLayout.LayoutParams(0, dp(62), 1));
        }
        studyActionBar.addView(actions);
    }

    private void buildStudyActionBar() {
        if (studyActionBar == null) {
            return;
        }
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text("", 16, MUTED, false);
        resultStatus.setVisibility(View.GONE);
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = secondaryButton("Erase");
        clear.setOnClickListener(v -> {
            drawingPad.clear();
            activeAnalysis = null;
            setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)), MUTED);
            updateResultActions();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        hintButton = secondaryButton("Hint");
        hintButton.setOnClickListener(v -> showWritingHint());
        actions.addView(hintButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        studyActionBar.addView(actions);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        checkWritingButton = primaryButton("Check", CORAL);
        checkWritingButton.setOnClickListener(v -> checkWriting());
        primaryActions.addView(checkWritingButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        downloadModelButton = secondaryButton("Download checker");
        downloadModelButton.setOnClickListener(v -> downloadWritingModel());
        primaryActions.addView(downloadModelButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        nextAfterPassButton = primaryButton("Next", TEAL);
        nextAfterPassButton.setOnClickListener(v -> submitReview(activeAnalysis == null ? "again" : activeAnalysis.rating, false));
        primaryActions.addView(nextAfterPassButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        studyActionBar.addView(primaryActions);

        LinearLayout fallbackActions = new LinearLayout(this);
        fallbackActions.setOrientation(LinearLayout.HORIZONTAL);
        replayButton = secondaryButton("Replay");
        replayButton.setOnClickListener(v -> replayWritingAnalysis());
        fallbackActions.addView(replayButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        manualOverrideButton = secondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview("good", true));
        fallbackActions.addView(manualOverrideButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        practiceWithGuideButton = secondaryButton("Try again with full guide");
        practiceWithGuideButton.setOnClickListener(v -> {
            setHintState(HintState.initial());
            hintsUsed++;
            activeAnalysis = null;
            drawingPad.clear();
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentHintState, false);
            setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\nFresh guided try. Draw it again, then check.", MUTED);
            updateResultActions();
        });
        fallbackActions.addView(practiceWithGuideButton, new LinearLayout.LayoutParams(0, dp(56), 1));
        studyActionBar.addView(fallbackActions);
    }

    private int studyPadHeight() {
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        if (screenDp < 700) {
            return dp(220);
        }
        if (screenDp < 820) {
            return dp(235);
        }
        return dp(250);
    }

    private void checkWriting() {
        if (activeSession == null) {
            return;
        }
        if (drawingPad == null || !drawingPad.hasInk()) {
            activeAnalysis = WritingAnalysisEngine.noInk(currentHintState.level(), hintsUsed);
            showAnalysis(activeAnalysis);
            return;
        }
        if (checkingWriting) {
            return;
        }
        Records.StudySession session = activeSession;
        String token = session.token;
        String target = session.item.kanji;
        CapturedWriting captured;
        WritingSample sample;
        try {
            captured = drawingPad.capturedWriting();
            sample = drawingPad.writingSample();
        } catch (IllegalArgumentException error) {
            activeAnalysis = WritingAnalysisEngine.noInk(currentHintState.level(), hintsUsed);
            showAnalysis(activeAnalysis);
            return;
        }
        StrokeGuide guide = strokeGuide(target);
        checkingWriting = true;
        checkWritingButton.setEnabled(false);
        updateResultActions();
        setStudyStatus("Checking handwriting...", MUTED);
        WritingRecognizer recognizer = writingRecognizer();
        if (recognizer == null) {
            activeAnalysis = WritingAnalysisEngine.modelUnavailable("The handwriting checker is unavailable on this device.", currentHintState.level(), hintsUsed);
            checkingWriting = false;
            showAnalysis(activeAnalysis);
            return;
        }
        recognizer.modelStatus().whenComplete((status, statusError) -> {
            if (statusError != null || status == null || !status.downloaded) {
                main.post(() -> {
                    if (!isActiveToken(token)) {
                        return;
                    }
                    checkingWriting = false;
                    activeAnalysis = WritingAnalysisEngine.modelUnavailable("Download the handwriting checker before automatic checks.", currentHintState.level(), hintsUsed);
                    writingModelDownloaded = false;
                    writingModelStatusKnown = true;
                    showAnalysis(activeAnalysis);
                });
                return;
            }
            recognizer.recognize(captured).whenComplete((result, error) -> main.post(() -> {
                if (!isActiveToken(token)) {
                    return;
                }
                checkingWriting = false;
                if (error != null) {
                    activeAnalysis = WritingAnalysisEngine.recognitionError(error.getMessage(), currentHintState.level(), hintsUsed);
                } else {
                    activeAnalysis = WritingAnalysisEngine.analyze(target, sample, guide, candidates(result), currentHintState.level(), hintsUsed);
                }
                showAnalysis(activeAnalysis);
            }));
        });
    }

    private void submitReview(String rating, boolean override) {
        if (activeSession == null) {
            return;
        }
        boolean writingRequired = activeSession.writingRequired;
        boolean passed = !writingRequired || (activeAnalysis != null && activeAnalysis.writingPassed);
        StudyRating requestedRating = StudyRating.fromCode(rating);
        StudyRating mappedRating = writingRatingMapper.applyRequestedRating(requestedRating, writingRequired, activeAnalysis, override);
        boolean cleanWriting = activeAnalysis != null && activeAnalysis.status == WritingAnalysis.Status.PASS;
        Records.ReviewRequest request = new Records.ReviewRequest(
                activeSession.item.kanji,
                activeSession.token,
                mappedRating.code(),
                writingRequired,
                passed,
                cleanWriting,
                override,
                hintsUsed
        );
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(store.consumedTokens());
        long now = System.currentTimeMillis();
        Records.SchedulerParameters parameters = store.schedulerParameters();
        Records.ReviewResult result = scheduler.applyReview(activeSession.item, request, consumed, now, parameters, settings());
        LocalStore.StudyStreak streak = null;
        if (!result.duplicate) {
            store.saveStudyItem(result.item);
            store.saveReview(request, result.appliedRating, now);
            streak = store.studyStreak(now);
            Records.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
            if (tuned.lastAdjustedAtMillis != parameters.lastAdjustedAtMillis || tuned.lastAdjustmentReviewCount != parameters.lastAdjustmentReviewCount) {
                store.saveSchedulerParameters(tuned);
            }
        }
        Toast.makeText(this, reviewToast(result, streak), Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private HintState initialHintState(Records.StudySession session) {
        int stored = Math.max(0, Math.min(3, session.item.writingLevel));
        if ("targeted_writing".equals(session.taskType)
                || "writing_remediation".equals(session.taskType)
                || session.item.totalReviews == 0
                || session.item.learningStep == 0) {
            return HintState.fromWritingLevel(Math.min(stored, 1));
        }
        return HintState.fromWritingLevel(stored);
    }

    private void setHintState(HintState state) {
        currentHintState = state == null ? HintState.initial() : state;
        currentPracticeLevel = currentHintState.level().writingLevel();
    }

    private String guideStatusPrefix(StrokeGuide guide) {
        return guideLabel(currentHintState, guide);
    }

    private void showWritingHint() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        setHintState(hintProgression.revealNext(currentHintState, guide));
        hintsUsed++;
        activeAnalysis = null;
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(guideLabel(currentHintState, guide) + "\nHint used. One current stroke hinted; your ink stayed on the canvas.", MUTED);
        updateResultActions();
    }

    private void showAnalysis(WritingAnalysis analysis) {
        StrokeGuide guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
        if (shouldIncreaseSupportAfterAnalysis(analysis)) {
            setHintState(hintProgression.afterWriting(currentHintState, analysis));
        }
        if (drawingPad != null && activeSession != null) {
            drawingPad.setGuide(guide, currentHintState, true);
            if (canReplayAnalysis(analysis, guide)) {
                drawingPad.captureReplaySnapshot();
                drawingPad.startReplay();
            } else {
                drawingPad.clearReplaySnapshot();
            }
        }
        int color = analysis.writingPassed ? TEAL : CORAL;
        String candidates = candidateText(analysis.candidates);
        String message = analysis.message + attemptProgressText(analysis) + targetRevealText(analysis) + (candidates.isEmpty() ? "" : "\nIt saw: " + candidates);
        String diagnosis = diagnosisText(analysis);
        if (!diagnosis.isEmpty()) {
            message += "\n" + diagnosis;
        }
        setStudyStatus(guideLabel(currentHintState, guide), MUTED);
        setResultStatus(message, color);
        updateResultActions();
    }

    private void updateResultActions() {
        boolean hasResult = activeAnalysis != null;
        boolean passed = hasResult && activeAnalysis.writingPassed;
        boolean messyPass = hasResult && activeAnalysis.status == WritingAnalysis.Status.CLOSE;
        boolean submittable = activeAnalysis != null && canSubmitAnalysis(activeAnalysis);
        if (checkWritingButton != null) {
            checkWritingButton.setVisibility(!passed || messyPass ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(!checkingWriting);
            checkWritingButton.setText(checkingWriting ? "Checking..." : (messyPass ? "Try cleaner" : "Check"));
            checkWritingButton.setOnClickListener(messyPass ? v -> startCleanerRetry() : v -> checkWriting());
        }
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(writingModelStatusKnown && writingModelDownloaded ? View.GONE : View.VISIBLE);
        }
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(submittable ? View.VISIBLE : View.GONE);
            if (submittable) {
                nextAfterPassButton.setText(nextReviewButtonText(activeAnalysis));
            }
        }
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(hasResult && canManualOverride(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(hasResult && !passed && canPracticeAfterAnalysis(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (replayButton != null) {
            StrokeGuide guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
            replayButton.setVisibility(hasResult && drawingPad != null && drawingPad.hasReplaySnapshot() && canReplayAnalysis(activeAnalysis, guide) ? View.VISIBLE : View.GONE);
        }
        if (hintButton != null) {
            hintButton.setVisibility(!passed && canRevealMoreHelp() ? View.VISIBLE : View.GONE);
            hintButton.setText(currentPracticeLevel == 3 ? "Hint" : "More help");
        }
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(shouldShowLearningPanel(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (resultStatus != null && !hasResult) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    private boolean shouldShowLearningPanel(WritingAnalysis analysis) {
        if (activeSession != null && isRecallTask(activeSession)) {
            return analysis != null && analysis.status != WritingAnalysis.Status.NO_INK && !analysis.writingPassed;
        }
        if (analysis == null || analysis.status == WritingAnalysis.Status.NO_INK) {
            return activeSession != null && isTeachingTask(activeSession) && currentPracticeLevel < 3;
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private boolean isTeachingTask(Records.StudySession session) {
        if (session == null) {
            return false;
        }
        return "context_writing".equals(session.taskType)
                || "guided_writing".equals(session.taskType)
                || "writing_remediation".equals(session.taskType)
                || ("targeted_writing".equals(session.taskType) && session.item.learningStep < 2);
    }

    private boolean canRevealMoreHelp() {
        if (activeSession == null || currentHintState == null || currentHintState.level() == HintLevel.TRACE) {
            return false;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        return guide == null || guide.isEmpty() || currentHintState.level() == HintLevel.OUTLINE || currentHintState.revealedStrokeCount() < guide.strokeCount();
    }

    private boolean shouldIncreaseSupportAfterAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case WRONG:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private void startCleanerRetry() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        activeAnalysis = null;
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        drawingPad.clear();
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(guideLabel(currentHintState, guide) + "\nTry cleaner. Keep the same help level and draw it carefully once more.", MUTED);
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
        updateResultActions();
    }

    private void replayWritingAnalysis() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        if (canReplayAnalysis(activeAnalysis, guide)) {
            drawingPad.setGuide(guide, currentHintState, true);
            drawingPad.startReplay();
        }
    }

    private void handleDrawingEdited() {
        if (checkingWriting || activeAnalysis == null || activeSession == null || drawingPad == null) {
            return;
        }
        activeAnalysis = null;
        drawingPad.clearReplaySnapshot();
        setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\nUpdated ink. Check again when ready.", MUTED);
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
        updateResultActions();
    }

    private boolean canReplayAnalysis(WritingAnalysis analysis, StrokeGuide guide) {
        if (analysis == null
                || drawingPad == null
                || !drawingPad.hasInk()
                || guide == null
                || guide.isEmpty()
                || analysis.strokeOrder == null
                || analysis.strokeOrder.missingGuide) {
            return false;
        }
        switch (analysis.status) {
            case NO_INK:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    private String diagnosisText(WritingAnalysis analysis) {
        if (!canShowDiagnosis(analysis)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (StrokeDiagnosis.Entry entry : analysis.strokeOrder.diagnosis.entries) {
            String line = diagnosisLine(entry);
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private boolean canShowDiagnosis(WritingAnalysis analysis) {
        if (analysis == null
                || analysis.strokeOrder == null
                || analysis.strokeOrder.missingGuide
                || analysis.strokeOrder.diagnosis.isEmpty()) {
            return false;
        }
        switch (analysis.status) {
            case NO_INK:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    private String diagnosisLine(StrokeDiagnosis.Entry entry) {
        switch (entry.label) {
            case WRONG_ORDER:
                return "Stroke " + entry.strokeNumber + ": likely wrong order";
            case WRONG_DIRECTION:
                return "Stroke " + entry.strokeNumber + ": likely wrong direction";
            case MISSING_STROKE:
                return "Stroke " + entry.strokeNumber + ": may be missing";
            case ROUGH_SHAPE:
                return "Stroke " + entry.strokeNumber + ": shape looks rough";
            case RECOGNIZED_BUT_MESSY:
                return "Recognized, but the stroke path was messy";
            default:
                return "";
        }
    }

    private boolean isRecallTask(Records.StudySession session) {
        if (session == null) {
            return false;
        }
        return "blind_writing".equals(session.taskType) || "sampled_handwriting".equals(session.taskType);
    }

    private boolean isFontRecognitionTask(Records.StudySession session) {
        return session != null && ("font_meaning".equals(session.taskType) || "font_recognition".equals(session.taskType));
    }

    private boolean isWordReadingTask(Records.StudySession session) {
        return session != null && "word_reading".equals(session.taskType);
    }

    private boolean canSubmitAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private boolean canManualOverride(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case WRONG:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private boolean canPracticeAfterAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case WRONG:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private void setStudyStatus(String value, int color) {
        if (studyStatus != null) {
            studyStatus.setText(value);
            studyStatus.setTextColor(color);
        }
        if (resultStatus != null && activeAnalysis == null) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    private void setResultStatus(String value, int color) {
        if (resultStatus != null) {
            resultStatus.setText(value);
            resultStatus.setTextColor(color);
            resultStatus.setVisibility(View.VISIBLE);
        }
    }

    private void refreshWritingModelStatus() {
        writingModelStatusKnown = false;
        writingModelDownloaded = false;
        updateResultActions();
        String token = activeSession == null ? null : activeSession.token;
        WritingRecognizer recognizer = writingRecognizer();
        if (recognizer == null) {
            writingModelStatusKnown = true;
            setStudyStatus(guideStatusPrefix(strokeGuide(activeSession.item.kanji)) + "\nAutomatic handwriting checks are unavailable on this device.", CORAL);
            updateResultActions();
            return;
        }
        recognizer.modelStatus().whenComplete((status, error) -> main.post(() -> {
            if (token == null || !isActiveToken(token)) {
                return;
            }
            writingModelStatusKnown = true;
            writingModelDownloaded = error == null && status != null && status.downloaded;
            updateResultActions();
            if (activeAnalysis != null || checkingWriting) {
                return;
            }
            if (error != null || status == null) {
                setStudyStatus(guideStatusPrefix(strokeGuide(activeSession.item.kanji)) + "\nUnable to read handwriting checker status.", CORAL);
            } else if (!status.downloaded) {
                setStudyStatus(guideStatusPrefix(strokeGuide(activeSession.item.kanji)) + "\nDownload the handwriting checker before automatic checks.", CORAL);
            } else {
                setStudyStatus(guideStatusPrefix(strokeGuide(activeSession.item.kanji)) + "\nHandwriting checker ready.", MUTED);
            }
        }));
    }

    private void downloadWritingModel() {
        String token = activeSession == null ? null : activeSession.token;
        WritingRecognizer recognizer = writingRecognizer();
        if (recognizer == null) {
            setStudyStatus("The handwriting checker is unavailable on this device.", CORAL);
            return;
        }
        setStudyStatus("Downloading handwriting checker...", MUTED);
        recognizer.downloadModel().whenComplete((status, error) -> main.post(() -> {
            if (token != null && !isActiveToken(token)) {
                return;
            }
            if (error != null) {
                writingModelStatusKnown = true;
                writingModelDownloaded = false;
                setStudyStatus("Handwriting checker download failed: " + error.getMessage(), CORAL);
            } else {
                writingModelStatusKnown = true;
                writingModelDownloaded = true;
                setStudyStatus("Handwriting checker ready.", TEAL);
            }
            updateResultActions();
        }));
    }

    private boolean isActiveToken(String token) {
        return activeSession != null && activeSession.token.equals(token);
    }

    private String reviewToast(Records.ReviewResult result, LocalStore.StudyStreak streak) {
        if (result.duplicate) {
            return "Already saved.";
        }
        String streakText = streak == null || streak.currentDays <= 0 ? "" : " " + streakHeadline(streak) + ".";
        if (result.item.writingRemediationPending) {
            return "Saved. Writing repair is next for this kanji." + streakText;
        }
        if ("again".equals(result.appliedRating)) {
            return "Saved. This kanji will come back soon." + streakText;
        }
        return "Saved." + streakText;
    }

    private WritingRecognizer writingRecognizer() {
        if (writingRecognizerForTests != null) {
            return writingRecognizerForTests;
        }
        if (writingRecognizer != null) {
            return writingRecognizer;
        }
        try {
            writingRecognizer = new MlKitJapaneseWritingRecognizer(io);
            return writingRecognizer;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private List<RecognitionCandidate> candidates(WritingRecognizer.RecognitionResult result) {
        List<RecognitionCandidate> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (WritingRecognizer.Candidate candidate : result.candidates) {
            out.add(new RecognitionCandidate(candidate.text, candidate.score));
        }
        return out;
    }

    private Records.DashboardRow findRow(List<Records.DashboardRow> rows, String kanji) {
        for (Records.DashboardRow row : rows) {
            if (row.kanji.equals(kanji)) {
                return row;
            }
        }
        return null;
    }

    private Records.StudyItem findStudyItem(List<Records.StudyItem> items, String kanji) {
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        return null;
    }

    private String candidateText(List<RecognitionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            values.add(candidates.get(i).text);
        }
        return String.join(", ", values);
    }

    private StrokeGuide strokeGuide(String kanji) {
        if (strokeGuides == null) {
            strokeGuides = loadStrokeGuides();
        }
        return strokeGuides.get(kanji);
    }

    private Map<String, StrokeGuide> loadStrokeGuides() {
        try (InputStream in = getResources().openRawResource(R.raw.kanji_strokes);
             InputStreamReader reader = new InputStreamReader(in)) {
            return StrokeGuideParser.parse(reader);
        } catch (Exception error) {
            return new HashMap<>();
        }
    }

    private void renderUpdate() {
        base("settings");
        content.addView(text("GitHub updater", 34, INK, true));
        content.addView(text("Current version " + BuildConfig.VERSION_NAME + ". Checks GitHub Releases, verifies the APK, and asks Android to install it.", 16, MUTED, false));
        content.addView(autoUpdatePanel("Automatic updates"));

        Button button = primaryButton("Check for update", BLUE);
        button.setOnClickListener(v -> runUpdate(false));
        content.addView(button);
    }

    private LinearLayout autoUpdatePanel(String title) {
        LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
        boolean canInstall = canInstallUpdates();
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        box.addView(text(title, 23, INK, true));
        box.addView(text(status.enabled ? "On: checks about once a day" : "Off", 18, status.enabled ? TEAL : MUTED, true));
        box.addView(text("Last check: " + autoUpdateLastCheckText(status), 15, MUTED, false));
        box.addView(text("Last result: " + status.lastResult, 15, MUTED, false));
        box.addView(text("Install permission: " + (canInstall ? "Ready" : "Missing"), 15, canInstall ? TEAL : CORAL, true));

        if (status.hasPendingUpdate()) {
            box.addView(text("Verified APK ready: " + versionText(status.lastVersion), 18, CORAL, true));
            String pending = status.pendingMessage.isEmpty() ? "Android needs confirmation before Kani can replace itself." : status.pendingMessage;
            box.addView(text(pending, 15, MUTED, false));
            if (canInstall) {
                Button install = primaryButton("Install verified update", CORAL);
                install.setOnClickListener(v -> runUpdate(true));
                box.addView(install);
            }
        }

        if (!canInstall) {
            Button permission = secondaryButton("Set up app installs");
            permission.setOnClickListener(v -> startActivity(GitHubUpdater.installPermissionIntent(this)));
            box.addView(permission);
        }

        Button toggle = secondaryButton(status.enabled ? "Turn off automatic updates" : "Turn on automatic updates");
        toggle.setOnClickListener(v -> {
            store.saveAutoUpdateEnabled(!status.enabled);
            if (status.enabled) {
                AutoUpdateScheduler.cancel(this);
                Toast.makeText(this, "Automatic updates turned off.", Toast.LENGTH_SHORT).show();
            } else {
                AutoUpdateScheduler.schedule(this);
                Toast.makeText(this, "Automatic updates turned on.", Toast.LENGTH_SHORT).show();
            }
            renderUpdate();
        });
        box.addView(toggle);
        return box;
    }

    private String autoUpdateLastCheckText(LocalStore.AutoUpdateStatus status) {
        if (status.lastCheckAtMillis <= 0L) {
            return "not yet";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(status.lastCheckAtMillis));
    }

    private String versionText(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "unknown version";
        }
        return version.replaceFirst("^v", "");
    }

    private boolean canInstallUpdates() {
        if (installPermissionForTests != null) {
            return installPermissionForTests;
        }
        return getPackageManager().canRequestPackageInstalls();
    }

    private void renderSettings() {
        base("settings");
        Records.Settings current = settings();
        content.addView(text("Settings", 34, INK, true));
        content.addView(text("Tune which Kiku cards become writing practice and when Kani reminds you.", 16, MUTED, false));
        addSpace(12);

        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.addView(text("Rarity cutoff", 23, INK, true));
        box.addView(text("Suspended cards are imported only when the kanji is rarer than this rank. Lower ranks are common. Default: 3000.", 15, MUTED, false));
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", current.suspendedRankCutoff));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        for (int value : new int[]{1000, 2000, 3000, 4000}) {
            Button preset = secondaryButton(String.format(Locale.ROOT, "%d", value));
            preset.setOnClickListener(v -> input.setText(String.format(Locale.ROOT, "%d", value)));
            quick.addView(preset, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        box.addView(quick);

        Button save = primaryButton("Save cutoff", TEAL);
        save.setOnClickListener(v -> {
            int value;
            try {
                value = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException error) {
                Toast.makeText(this, "Enter a numeric rank cutoff.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (value < 1 || value > 20000) {
                Toast.makeText(this, "Use a cutoff from 1 to 20000.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.putIntSetting("suspended_rank_cutoff", value);
            Toast.makeText(this, "Cutoff saved. Sync again to rebuild practice.", Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        content.addView(box);

        content.addView(workloadSettingsPanel());
        content.addView(writingTriggerSettingsPanel(current));
        content.addView(reminderSettingsPanel());
        content.addView(autoSyncSettingsPanel());
        content.addView(updateSettingsPanel());

        LinearLayout mapping = band(BLUE);
        mapping.addView(text("Kiku fields used for clues", 22, Color.WHITE, true));
        mapping.addView(text("Expression -> kanji source\nExpressionReading -> reading\nMainDefinition -> meaning\nSentence -> context\nFrequency/FreqSort -> collection metadata", 15, Color.WHITE, false));
        content.addView(mapping);

        LinearLayout attribution = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        attribution.addView(text("Stroke data", 22, INK, true));
        attribution.addView(text(kanjiVgAttribution(), 14, MUTED, false));
        content.addView(attribution);
    }

    private LinearLayout workloadSettingsPanel() {
        int current = store.adaptiveLoadWorkPercent();
        final int[] selected = new int[]{current};
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text("Daily workload", 23, INK, true));
        TextView status = text(workloadStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Kani picks a small focus set from your real problem kanji. This changes how much it admits today, not Anki's schedule.", 15, MUTED, false));

        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = AdaptiveLoadPlanner.snapWorkloadPercent(progress);
                status.setText(workloadStatusText(selected[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(selected[0]);
            }
        });
        box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : new String[]{"Very little", "Pareto", "Balanced", "More", "All kanji"}) {
            TextView item = text(label, 11, MUTED, false);
            item.setGravity(Gravity.CENTER);
            labels.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        box.addView(labels);

        Button save = primaryButton("Save workload", TEAL);
        save.setOnClickListener(v -> {
            store.saveAdaptiveLoadWorkPercent(selected[0]);
            Toast.makeText(this, "Workload saved. Study uses the new adaptive focus.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private LinearLayout writingTriggerSettingsPanel(Records.Settings current) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        box.addView(text("Writing repair trigger", 23, INK, true));
        box.addView(text("Kani starts writing only after this many separate missed recognition days for the same kanji.", 15, MUTED, false));
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", current.writingTriggerMissDays));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        for (int value : new int[]{2, 3, 4}) {
            Button preset = secondaryButton(value + " days");
            preset.setOnClickListener(v -> input.setText(String.format(Locale.ROOT, "%d", value)));
            quick.addView(preset, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        box.addView(quick);

        Button save = primaryButton("Save writing trigger", TEAL);
        save.setOnClickListener(v -> {
            int value;
            try {
                value = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException error) {
                Toast.makeText(this, "Enter a number of missed days.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (value < 1 || value > 14) {
                Toast.makeText(this, "Use 1 to 14 missed days.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.putIntSetting("writing_trigger_miss_days", value);
            Toast.makeText(this, "Writing trigger saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private LinearLayout reminderSettingsPanel() {
        LocalStore.ReminderSettings reminder = store.reminderSettings();
        boolean notificationsAllowed = ReminderScheduler.notificationsAllowed(this);
        boolean blocked = reminder.enabled && !notificationsAllowed;
        int[] selectedHour = new int[]{reminder.hour};
        int[] selectedMinute = new int[]{reminder.minute};

        LinearLayout box = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        box.addView(text("Daily reminder", 23, INK, true));
        box.addView(text(reminderStatus(reminder, blocked), 17, blocked ? CORAL : reminder.enabled ? TEAL : MUTED, true));
        box.addView(text("Kani can nudge you once a day to study active problem kanji. Reminder timing is approximate because Android may batch background work.", 15, MUTED, false));

        Button time = secondaryButton(reminderTimeButtonLabel(selectedHour[0], selectedMinute[0]));
        time.setOnClickListener(v -> new TimePickerDialog(
                this,
                (view, hour, minute) -> {
                    selectedHour[0] = hour;
                    selectedMinute[0] = minute;
                    time.setText(reminderTimeButtonLabel(hour, minute));
                },
                selectedHour[0],
                selectedMinute[0],
                true
        ).show());
        box.addView(time);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        addReminderPreset(quick, "Morning", 8, 0, selectedHour, selectedMinute, time);
        addReminderPreset(quick, "Lunch", 12, 30, selectedHour, selectedMinute, time);
        addReminderPreset(quick, "Evening", 19, 0, selectedHour, selectedMinute, time);
        addReminderPreset(quick, "Night", 21, 0, selectedHour, selectedMinute, time);
        box.addView(quick);

        Button save = primaryButton(reminder.enabled ? "Save reminder" : "Enable reminder", TEAL);
        save.setOnClickListener(v -> saveReminderFromSelection(selectedHour[0], selectedMinute[0], true));
        box.addView(save);
        if (reminder.enabled) {
            Button off = secondaryButton("Turn off reminder");
            off.setOnClickListener(v -> {
                store.saveReminderSettings(new LocalStore.ReminderSettings(false, reminder.hour, reminder.minute));
                ReminderScheduler.cancel(this);
                Toast.makeText(this, "Reminder turned off.", Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(off);
        }
        if (blocked) {
            box.addView(text("Android notifications are off for Kani, so this reminder cannot appear yet.", 14, CORAL, false));
            Button notificationSettings = secondaryButton("Open notification settings");
            notificationSettings.setOnClickListener(v -> openNotificationSettings());
            box.addView(notificationSettings);
        } else if (!ReminderScheduler.hasRuntimeNotificationPermission(this)) {
            box.addView(text("Android will ask for notification permission before turning this on.", 14, CORAL, false));
        }
        return box;
    }

    private LinearLayout autoSyncSettingsPanel() {
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text("Daily Anki sync", 23, INK, true));
        box.addView(text(autoSyncStatus(auto), 17, auto.enabled ? TEAL : MUTED, true));
        box.addView(text(autoSyncDetail(auto), 15, MUTED, false));
        if (auto.configured) {
            if (auto.enabled) {
                Button off = secondaryButton("Turn off daily sync");
                off.setOnClickListener(v -> {
                    store.setAutoSyncEnabled(false);
                    AutoSyncScheduler.cancel(this);
                    Toast.makeText(this, "Daily Anki sync turned off.", Toast.LENGTH_SHORT).show();
                    renderSettings();
                });
                box.addView(off);
            } else {
                Button on = primaryButton("Turn on daily sync", TEAL);
                on.setOnClickListener(v -> {
                    store.setAutoSyncEnabled(true);
                    AutoSyncScheduler.schedule(this);
                    Toast.makeText(this, "Daily Anki sync turned on.", Toast.LENGTH_SHORT).show();
                    renderSettings();
                });
                box.addView(on);
            }
        }
        return box;
    }

    private String autoSyncStatus(LocalStore.AutoSyncSettings auto) {
        if (!auto.configured) {
            return "Starts after first successful sync";
        }
        if (auto.enabled) {
            return "On around " + auto.displayTime();
        }
        return "Off";
    }

    private String autoSyncDetail(LocalStore.AutoSyncSettings auto) {
        if (!auto.configured) {
            return "Manual sync once, then Kani will keep itself refreshed once per day.";
        }
        List<String> details = new ArrayList<>();
        if (auto.lastSuccessAt > 0L) {
            details.add("Last auto success " + shortDateTime(auto.lastSuccessAt));
        }
        if (auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt) {
            details.add("Last auto attempt " + shortDateTime(auto.lastAttemptAt));
        }
        if (auto.enabled && auto.nextRunAt > 0L) {
            details.add("Next scheduled " + shortDateTime(auto.nextRunAt));
        }
        if (details.isEmpty()) {
            return auto.enabled
                    ? "Scheduled once per local day. Android may batch the exact time."
                    : "Daily background sync is paused.";
        }
        return String.join(". ", details) + ".";
    }

    private String shortDateTime(long millis) {
        Date date = new Date(millis);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }

    private String workloadStatusText(int percent) {
        int snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent);
        String label = AdaptiveLoadPlanner.workloadLabel(snapped);
        if (snapped >= 100) {
            return label + ": all current problem kanji";
        }
        return label + ": up to " + AdaptiveLoadPlanner.targetCeiling(snapped) + " kanji";
    }

    private LinearLayout updateSettingsPanel() {
        LinearLayout box = autoUpdatePanel("App updates");
        Button update = primaryButton("Open updater", BLUE);
        update.setOnClickListener(v -> renderUpdate());
        box.addView(update);
        return box;
    }

    private String reminderStatus(LocalStore.ReminderSettings reminder, boolean blocked) {
        if (blocked) {
            return "Blocked: notifications off";
        }
        if (reminder.enabled) {
            return "Daily around " + reminder.displayTime();
        }
        return "Off";
    }

    private String reminderTime(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private String reminderTimeButtonLabel(int hour, int minute) {
        return String.format(Locale.ROOT, "Reminder time: %02d:%02d", hour, minute);
    }

    private void addReminderPreset(LinearLayout row, String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        Button preset = secondaryButton(label + " " + reminderTime(hour, minute));
        preset.setTextSize(13);
        preset.setOnClickListener(v -> {
            selectedHour[0] = hour;
            selectedMinute[0] = minute;
            timeButton.setText(reminderTimeButtonLabel(hour, minute));
        });
        row.addView(preset, new LinearLayout.LayoutParams(0, dp(54), 1));
    }

    private void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        LocalStore.ReminderSettings reminder = new LocalStore.ReminderSettings(enabled, hour, minute);
        if (!enabled) {
            store.saveReminderSettings(reminder);
            ReminderScheduler.cancel(this);
            Toast.makeText(this, "Reminder turned off.", Toast.LENGTH_SHORT).show();
            renderSettings();
            return;
        }
        ReminderScheduler.ensureNotificationChannel(this);
        if (!ReminderScheduler.hasRuntimeNotificationPermission(this)) {
            pendingReminderSettings = reminder;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            return;
        }
        store.saveReminderSettings(reminder);
        ReminderScheduler.schedule(this, reminder);
        if (ReminderScheduler.notificationsAllowed(this)) {
            Toast.makeText(this, "Reminder saved for around " + reminder.displayTime() + ".", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Reminder saved, but Android notifications are off.", Toast.LENGTH_LONG).show();
        }
        renderSettings();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private String kanjiVgAttribution() {
        try (InputStream in = getResources().openRawResource(R.raw.kanjivg_attribution);
             InputStreamReader reader = new InputStreamReader(in)) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                out.append(buffer, 0, read);
            }
            return out.toString().trim();
        } catch (Exception error) {
            return "KanjiVG stroke data, CC BY-SA 3.0.";
        }
    }

    private void runUpdate(boolean cachedPending) {
        base("settings");
        content.addView(text(cachedPending ? "Starting installer" : "Checking release", 32, INK, true));
        content.addView(text(cachedPending ? "Using the verified APK already cached by Kani." : "Downloading metadata and verifying assets.", 16, MUTED, false));
        io.execute(() -> {
            GitHubUpdater updater = new GitHubUpdater(this);
            GitHubUpdater.UpdateResult result = cachedPending
                    ? updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
                    : updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);
            main.post(() -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                if (result.intent != null) {
                    startActivity(result.intent);
                }
                renderUpdate();
            });
        });
    }

    private Records.Settings settings() {
        return SyncSettings.fromStore(store);
    }

    private Records.AdaptiveLoadPlan adaptivePlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        return new AdaptiveLoadPlanner().plan(
                rows,
                items,
                store.reviewStatsSince(now - 7 * DAY_MILLIS),
                store.studyStreak(now).currentDays,
                store.studiedKanjiSince(startOfDay(now)),
                store.adaptiveLoadWorkPercent(),
                now,
                settings()
        );
    }

    private LinearLayout band(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.setBackground(panel(color, color, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout panelBox(int fill, int stroke) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(panel(fill, stroke, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        box.setLayoutParams(lp);
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value == null ? "" : value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        text.setLineSpacing(0, 1.05f);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 22, INK, true);
        title.setPadding(0, dp(12), 0, dp(6));
        return title;
    }

    private TextView chip(String value, int color) {
        TextView chip = text(value, 13, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(panel(color, color, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(7), dp(7), dp(2));
        chip.setLayoutParams(lp);
        return chip;
    }

    private Button primaryButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(19);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(panel(color, color, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(lp);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setBackground(panel(Color.WHITE, Color.rgb(238, 189, 218), dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    private GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void emptyState(String title, String body) {
        LinearLayout empty = band(GOLD);
        empty.addView(text(title, 24, INK, true));
        empty.addView(text(body, 16, INK, false));
        content.addView(empty);
    }

    private String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private String rowMeaning(Records.DashboardRow row) {
        return cleanLearnerText(row.primaryMeaning, row.reasonCode, 72);
    }

    private String sessionClue(Records.StudySession session) {
        String raw = session.row == null || session.row.primaryMeaning.isEmpty()
                ? session.prompt
                : session.row.primaryMeaning;
        return cleanLearnerText(raw, session.prompt, 96);
    }

    private String wordPrompt(Records.StudySession session) {
        Records.Example example = session == null ? null : firstExample(session.row);
        if (example != null && !example.expression.isEmpty()) {
            return example.expression;
        }
        return session == null ? "" : session.item.kanji;
    }

    private String cleanLearnerText(String raw, String fallback, int maxChars) {
        String value = raw == null ? "" : raw;
        value = value.replaceAll("\\[[0-9]{4}-[0-9]{2}-[0-9]{2}\\]", " ");
        value = value.replaceAll("(?i)\\bJMdict\\s*\\[[^\\]]*\\]\\s*", " ");
        value = value.replaceAll("(?i)\\bJitendex\\.org\\s*", " ");
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        boolean changed = true;
        while (changed && value.startsWith("(")) {
            changed = false;
            int end = value.indexOf(')');
            if (end > 0 && end < 140) {
                String metadata = value.substring(1, end).toLowerCase(Locale.ROOT);
                if (metadata.contains("jitendex") || metadata.contains("priority") || metadata.contains("form")) {
                    value = value.substring(end + 1).trim();
                    changed = true;
                }
            }
        }
        value = value.replaceAll("^\\d+\\.\\s*", "");
        value = value.replaceAll("(?i)^(5-dan|godan)\\s+(intransitive|transitive)\\s+", "");
        value = value.replaceAll("(?i)^(ichidan|suru|na-adjective|i-adjective)\\s+", "");
        value = value.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            value = fallback == null || fallback.isEmpty() ? "Collection clue" : fallback;
        }
        return compact(value, maxChars);
    }

    private String compact(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        int cut = value.lastIndexOf(' ', maxChars - 3);
        if (cut < 32) {
            cut = maxChars - 3;
        }
        return value.substring(0, cut).trim() + "...";
    }

    private void addSpace(int dp) {
        SpaceView space = new SpaceView(this);
        content.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private String labelForTask(String task) {
        if ("targeted_flashcard".equals(task)) {
            return "Focused recall";
        }
        if ("kanji_meaning".equals(task)) {
            return "Kanji -> meaning";
        }
        if ("font_meaning".equals(task)) {
            return "Font -> meaning";
        }
        if ("word_reading".equals(task)) {
            return "Word -> reading";
        }
        if ("writing_remediation".equals(task)) {
            return "Writing repair";
        }
        if ("meaning_flashcard".equals(task)) {
            return "Quick recall";
        }
        if ("font_recognition".equals(task)) {
            return "Font check";
        }
        if ("repair_writing".equals(task)) {
            return "Write to repair";
        }
        if ("targeted_writing".equals(task)) {
            return "Focused practice";
        }
        if ("context_writing".equals(task)) {
            return "New problem kanji";
        }
        if ("guided_writing".equals(task)) {
            return "Guided review";
        }
        if ("blind_writing".equals(task)) {
            return "Memory check";
        }
        if ("confusable_recognition".equals(task)) {
            return "Learn the shape";
        }
        if ("sampled_handwriting".equals(task)) {
            return "Memory check";
        }
        return "Study";
    }

    private String adaptiveFocusText(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Adaptive focus is waiting for sync";
        }
        if (plan.allKanjiMode) {
            return "Adaptive focus is set to all current problem kanji";
        }
        return "Today's adaptive focus: " + plan.remaining + " left / " + plan.target;
    }

    private String guideLabel(int level, StrokeGuide guide) {
        return guideLabel(HintState.fromWritingLevel(level), guide);
    }

    private String guideLabel(HintState state, StrokeGuide guide) {
        HintLevel level = state == null ? HintLevel.TRACE : state.level();
        boolean hasGuide = guide != null && !guide.isEmpty();
        if (!hasGuide) {
            if (level == HintLevel.BLIND) {
                return "Write from memory, then check. No numbered stroke guide is bundled for this kanji yet.";
            }
            return "No numbered stroke guide is bundled for this kanji yet. Use the reference, draw it, then check. Stroke-order feedback will be limited.";
        }
        switch (level) {
            case TRACE:
                return "Trace the numbered strokes, then check. This is a learning attempt.";
            case OUTLINE:
                return "Copy the faint outline; the current stroke is emphasized.";
            case MINIMAL:
                return "Write with only the current stroke hinted, then check.";
            case BLIND:
            default:
                return "Write from memory, then check. Use Hint if you are stuck.";
        }
    }

    private String nextReviewButtonText(WritingAnalysis analysis) {
        if (analysis != null && !analysis.writingPassed) {
            return "Save miss";
        }
        if (analysis != null && analysis.status == WritingAnalysis.Status.CLOSE) {
            return "Save hard";
        }
        return "Next card";
    }

    private String attemptProgressText(WritingAnalysis analysis) {
        if (analysis == null) {
            return "";
        }
        if (analysis.status == WritingAnalysis.Status.PASS && analysis.hintsUsed() == 0) {
            HintState next = hintProgression.afterWriting(HintState.fromWritingLevel(analysis.hintLevel().writingLevel()), analysis);
            if (next.level() != analysis.hintLevel()) {
                return "\nNext writing review will have less help: " + stageLabel(next.level()) + ".";
            }
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.";
        }
        if (shouldIncreaseSupportAfterAnalysis(analysis) && activeSession != null) {
            HintState next = hintProgression.afterWriting(HintState.fromWritingLevel(activeSession.item.writingLevel), analysis);
            if (next.level() != HintLevel.fromWritingLevel(activeSession.item.writingLevel)) {
                return "\nNext try will use more support: " + stageLabel(next.level()) + ".";
            }
        }
        return "";
    }

    private String stageLabel(HintLevel level) {
        switch (level) {
            case TRACE:
                return "Trace";
            case OUTLINE:
                return "Outline";
            case MINIMAL:
                return "Minimal";
            case BLIND:
            default:
                return "Blind";
        }
    }

    private String targetRevealText(WritingAnalysis analysis) {
        if (activeSession == null || analysis == null) {
            return "";
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
            case MODEL_UNAVAILABLE:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return "\nTarget: " + activeSession.item.kanji;
            default:
                return "";
        }
    }

    private long startOfDay(long now) {
        return now - (now % 86_400_000L);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SpaceView extends View {
        private SpaceView(Context context) {
            super(context);
        }
    }

    private static final class QueueEntry {
        final Records.DashboardRow row;
        final Records.StudyItem item;

        QueueEntry(Records.DashboardRow row, Records.StudyItem item) {
            this.row = row;
            this.item = item;
        }
    }

    public static final class DrawingPadView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint replayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Path> paths = new ArrayList<>();
        private final List<List<CapturedStroke.Point>> committedStrokes = new ArrayList<>();
        private final List<List<CapturedStroke.Point>> replayStrokes = new ArrayList<>();
        private final List<CapturedStroke.Point> currentPoints = new ArrayList<>();
        private Path current;
        private StrokeGuide guide;
        private int guideLevel = 3;
        private HintState guideState = HintState.fromWritingLevel(3);
        private boolean revealGuide;
        private boolean replayOverlayVisible;
        private long replayStartedAtMillis;
        private Runnable inkEditListener;
        private String target = "";
        private int activePointerId = -1;
        private static final long REPLAY_DURATION_MILLIS = 950L;

        public DrawingPadView(Context context) {
            super(context);
            setBackgroundColor(Color.WHITE);
            paint.setColor(INK);
            paint.setStrokeWidth(12f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            grid.setColor(Color.rgb(244, 199, 225));
            grid.setStrokeWidth(2f);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeCap(Paint.Cap.ROUND);
            guidePaint.setStrokeJoin(Paint.Join.ROUND);
            markerPaint.setStyle(Paint.Style.FILL);
            markerText.setTextAlign(Paint.Align.CENTER);
            markerText.setTypeface(Typeface.DEFAULT_BOLD);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(5f);
            outlinePaint.setTextAlign(Paint.Align.CENTER);
            outlinePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            replayPaint.setColor(BLUE);
            replayPaint.setStrokeWidth(13f);
            replayPaint.setStyle(Paint.Style.STROKE);
            replayPaint.setStrokeCap(Paint.Cap.ROUND);
            replayPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public boolean hasInk() {
            return !committedStrokes.isEmpty();
        }

        public void clear() {
            paths.clear();
            committedStrokes.clear();
            replayStrokes.clear();
            currentPoints.clear();
            current = null;
            activePointerId = -1;
            stopReplay();
            invalidate();
        }

        public void setTarget(String target) {
            this.target = target == null ? "" : target;
        }

        public void setInkEditListener(Runnable listener) {
            this.inkEditListener = listener;
        }

        public void setGuide(StrokeGuide guide, int level, boolean revealGuide) {
            setGuide(guide, HintState.fromWritingLevel(level), revealGuide);
        }

        public void setGuide(StrokeGuide guide, HintState state, boolean revealGuide) {
            this.guide = guide;
            this.guideState = state == null ? HintState.fromWritingLevel(3) : state;
            this.guideLevel = guideState.level().writingLevel();
            this.revealGuide = revealGuide;
            if (!revealGuide || guide == null || guide.isEmpty()) {
                replayOverlayVisible = false;
                replayStartedAtMillis = 0L;
            }
            invalidate();
        }

        public void startReplay() {
            if (replayStrokes.isEmpty()) {
                return;
            }
            replayOverlayVisible = true;
            replayStartedAtMillis = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }

        public void stopReplay() {
            replayOverlayVisible = false;
            replayStartedAtMillis = 0L;
            invalidate();
        }

        public void captureReplaySnapshot() {
            replayStrokes.clear();
            for (List<CapturedStroke.Point> stroke : committedStrokes) {
                replayStrokes.add(new ArrayList<>(stroke));
            }
        }

        public void clearReplaySnapshot() {
            replayStrokes.clear();
            stopReplay();
        }

        public boolean hasReplaySnapshot() {
            return !replayStrokes.isEmpty();
        }

        public boolean isReplayOverlayVisibleForTests() {
            return replayOverlayVisible;
        }

        public CapturedWriting capturedWriting() {
            List<CapturedStroke> strokes = new ArrayList<>();
            for (List<CapturedStroke.Point> points : committedStrokes) {
                strokes.add(new CapturedStroke(points));
            }
            return new CapturedWriting(strokes, (float) getWidth(), (float) getHeight(), "");
        }

        public WritingSample writingSample() {
            List<InkStroke> strokes = new ArrayList<>();
            for (List<CapturedStroke.Point> points : committedStrokes) {
                List<InkPoint> inkPoints = new ArrayList<>();
                for (CapturedStroke.Point point : points) {
                    inkPoints.add(new InkPoint(point.x, point.y, point.timestampMillis == null ? 0L : point.timestampMillis));
                }
                strokes.add(new InkStroke(inkPoints));
            }
            return new WritingSample(strokes, (float) getWidth(), (float) getHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            canvas.drawLine(w / 2f, 0, w / 2f, h, grid);
            canvas.drawLine(0, h / 2f, w, h / 2f, grid);
            canvas.drawLine(0, h * 0.72f, w, h * 0.72f, grid);
            drawGuide(canvas, w, h);
            if (replayOverlayVisible) {
                float progress = replayProgress();
                drawReplayStrokes(canvas, progress);
                if (progress < 1f) {
                    postInvalidateOnAnimation();
                }
            } else {
                for (Path path : paths) {
                    canvas.drawPath(path, paint);
                }
            }
            if (current != null) {
                canvas.drawPath(current, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    performClick();
                    stopReplay();
                    if (inkEditListener != null) {
                        inkEditListener.run();
                    }
                    requestParentIntercept(false);
                    activePointerId = event.getPointerId(0);
                    current = new Path();
                    current.moveTo(event.getX(0), event.getY(0));
                    currentPoints.clear();
                    appendPoint(event.getX(0), event.getY(0), event.getEventTime(), false);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        requestParentIntercept(false);
                        int pointerIndex = activePointerIndex(event);
                        if (pointerIndex < 0) {
                            return true;
                        }
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            appendPoint(event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), event.getHistoricalEventTime(i), true);
                        }
                        appendPoint(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), true);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (current != null && event.getPointerId(event.getActionIndex()) == activePointerId) {
                        finishStroke(event, event.getActionIndex());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) {
                        int pointerIndex = activePointerIndex(event);
                        finishStroke(event, pointerIndex < 0 ? 0 : pointerIndex);
                    }
                    requestParentIntercept(true);
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void requestParentIntercept(boolean allow) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(!allow);
            }
        }

        private int activePointerIndex(MotionEvent event) {
            if (activePointerId < 0) {
                return event.getPointerCount() == 0 ? -1 : 0;
            }
            return event.findPointerIndex(activePointerId);
        }

        private void finishStroke(MotionEvent event, int pointerIndex) {
            if (pointerIndex >= 0 && pointerIndex < event.getPointerCount()) {
                appendPoint(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), true);
            }
            if (!currentPoints.isEmpty()) {
                paths.add(current);
                committedStrokes.add(new ArrayList<>(currentPoints));
            }
            currentPoints.clear();
            current = null;
            activePointerId = -1;
            invalidate();
        }

        private void appendPoint(float x, float y, long timestamp, boolean drawLine) {
            CapturedStroke.Point last = currentPoints.isEmpty() ? null : currentPoints.get(currentPoints.size() - 1);
            if (last != null && Math.abs(last.x - x) < 0.5f && Math.abs(last.y - y) < 0.5f) {
                return;
            }
            currentPoints.add(new CapturedStroke.Point(x, y, timestamp));
            if (drawLine && current != null) {
                current.lineTo(x, y);
            }
        }

        private void drawGuide(Canvas canvas, float width, float height) {
            if (guide != null && !guide.isEmpty()) {
                List<HintPolicy.StrokeHint> hints = HintPolicy.hintsFor(guide, guideState, committedStrokes.size(), revealGuide);
                for (HintPolicy.StrokeHint hint : hints) {
                    if (!hint.visible || hint.stroke.points.size() < 2) {
                        continue;
                    }
                    Path path = new Path();
                    InkPoint first = hint.stroke.points.get(0);
                    path.moveTo(first.x * width, first.y * height);
                    for (int i = 1; i < hint.stroke.points.size(); i++) {
                        InkPoint point = hint.stroke.points.get(i);
                        path.lineTo(point.x * width, point.y * height);
                    }
                    guidePaint.setColor(hint.current ? CORAL : Color.rgb(111, 74, 39));
                    guidePaint.setAlpha(Math.round((hint.current ? 220 : 160) * hint.alpha));
                    guidePaint.setStrokeWidth(hint.current ? 14f : 9f);
                    canvas.drawPath(path, guidePaint);
                    drawStartMarker(canvas, first.x * width, first.y * height, hint.strokeIndex + 1, hint.current, hint.numberVisible);
                }
            } else if ((guideLevel < 3 || revealGuide) && !target.isEmpty()) {
                outlinePaint.setColor(Color.argb(revealGuide ? 120 : 72, 111, 74, 39));
                outlinePaint.setTextSize(Math.min(width, height) * 0.62f);
                Rect bounds = new Rect();
                outlinePaint.getTextBounds(target, 0, target.length(), bounds);
                Path outline = new Path();
                outlinePaint.getTextPath(target, 0, target.length(), width / 2f - bounds.exactCenterX(), height * 0.68f, outline);
                canvas.drawPath(outline, outlinePaint);
            }
        }

        private float replayProgress() {
            if (!replayOverlayVisible || replayStartedAtMillis <= 0L) {
                return 1f;
            }
            long elapsed = Math.max(0L, SystemClock.uptimeMillis() - replayStartedAtMillis);
            if (elapsed >= REPLAY_DURATION_MILLIS) {
                return 1f;
            }
            return Math.max(0f, Math.min(1f, elapsed / (float) REPLAY_DURATION_MILLIS));
        }

        private void drawReplayStrokes(Canvas canvas, float progress) {
            if (replayStrokes.isEmpty()) {
                return;
            }
            float position = Math.max(0f, Math.min(1f, progress)) * replayStrokes.size();
            int fullStrokeCount = Math.min(replayStrokes.size(), (int) Math.floor(position));
            for (int i = 0; i < replayStrokes.size(); i++) {
                float strokeProgress;
                if (i < fullStrokeCount) {
                    strokeProgress = 1f;
                } else if (i == fullStrokeCount) {
                    strokeProgress = position - fullStrokeCount;
                } else {
                    break;
                }
                drawReplayStroke(canvas, replayStrokes.get(i), strokeProgress);
            }
        }

        private void drawReplayStroke(Canvas canvas, List<CapturedStroke.Point> points, float progress) {
            if (points.isEmpty()) {
                return;
            }
            CapturedStroke.Point first = points.get(0);
            if (points.size() == 1 || progress <= 0.001f) {
                canvas.drawCircle(first.x, first.y, replayPaint.getStrokeWidth() / 2f, replayPaint);
                return;
            }
            float segmentPosition = Math.max(0f, Math.min(1f, progress)) * (points.size() - 1);
            int lastWholeSegment = Math.min(points.size() - 1, (int) Math.floor(segmentPosition));
            Path path = new Path();
            path.moveTo(first.x, first.y);
            for (int i = 1; i <= lastWholeSegment; i++) {
                CapturedStroke.Point point = points.get(i);
                path.lineTo(point.x, point.y);
            }
            if (lastWholeSegment < points.size() - 1) {
                CapturedStroke.Point from = points.get(lastWholeSegment);
                CapturedStroke.Point to = points.get(lastWholeSegment + 1);
                float localProgress = segmentPosition - lastWholeSegment;
                path.lineTo(
                        from.x + ((to.x - from.x) * localProgress),
                        from.y + ((to.y - from.y) * localProgress)
                );
            }
            canvas.drawPath(path, replayPaint);
        }

        private void drawStartMarker(Canvas canvas, float x, float y, int number, boolean active, boolean numberVisible) {
            markerPaint.setColor(Color.argb(230, 255, 255, 255));
            canvas.drawCircle(x, y, 17f, markerPaint);
            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(3f);
            markerPaint.setColor(active ? CORAL : Color.rgb(111, 74, 39));
            canvas.drawCircle(x, y, 17f, markerPaint);
            markerPaint.setStyle(Paint.Style.FILL);
            if (!numberVisible) {
                canvas.drawCircle(x, y, active ? 5f : 3.5f, markerPaint);
                return;
            }
            markerText.setTextSize(18f);
            markerText.setColor(active ? CORAL : Color.rgb(111, 74, 39));
            canvas.drawText(Integer.toString(number), x, y - (markerText.descent() + markerText.ascent()) / 2f, markerText);
        }
    }
}
