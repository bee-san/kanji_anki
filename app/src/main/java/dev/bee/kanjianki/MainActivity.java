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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.TextUtil;
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
import dev.bee.kanjianki.sync.SyncProgress;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.update.AutoUpdateScheduler;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Collections;
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
import java.util.concurrent.ThreadLocalRandom;

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
    private static final int BLUSH = Color.rgb(255, 239, 246);
    private static final int PINK_STROKE = Color.rgb(255, 174, 204);
    private static final int LILAC = Color.rgb(118, 72, 255);
    private static final long DAY_MILLIS = 86_400_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final HintProgression hintProgression = new HintProgression();
    private final WritingRatingMapper writingRatingMapper = new WritingRatingMapper();
    private LocalStore store;
    private AnkiDroidGateway gateway;
    private CollectionGateway syncGateway;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout studyActionBar;
    private Records.StudySession activeSession;
    private Records.LearningRepeat activeLearningRepeat;
    private Records.SimilarKanjiChoiceCard activeSimilarChoice;
    private Records.SimilarKanjiWritingRepair activeSimilarRepair;
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
    private View flashcardGestureArea;
    private WritingAnalysis activeAnalysis;
    private boolean checkingWriting;
    private boolean flashcardAnswerRevealed;
    private boolean flashcardTouchTracking;
    private boolean writingModelDownloaded;
    private boolean writingModelStatusKnown;
    private boolean continueAllKanjiSession;
    private int hintsUsed;
    private int currentPracticeLevel;
    private float flashcardTouchStartX;
    private float flashcardTouchStartY;
    private HintState currentHintState = HintState.initial();
    private Map<String, StrokeGuide> strokeGuides;
    private WritingRecognizer writingRecognizer;
    private LocalStore.ReminderSettings pendingReminderSettings;
    private static AnkiDroidGateway ankiDroidGatewayForTests;
    private static CollectionGateway collectionGatewayForTests;
    private static WritingRecognizer writingRecognizerForTests;
    private static Boolean installPermissionForTests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        gateway = ankiDroidGatewayForTests == null ? new AnkiDroidGateway(this) : ankiDroidGatewayForTests;
        syncGateway = collectionGatewayForTests == null ? gateway : collectionGatewayForTests;
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

    public static void setCollectionGatewayForTests(CollectionGateway gateway) {
        collectionGatewayForTests = gateway;
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleFlashcardGesture(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void base(String selected) {
        flashcardGestureArea = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        styleSystemBars();
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
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(96)));
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
            nav.setPadding(dp(18), dp(10), dp(18), dp(10) + bottom);
            ViewGroup.LayoutParams navParams = nav.getLayoutParams();
            navParams.height = dp(96) + bottom;
            nav.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void styleSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private LinearLayout nav(String selected, int navigationInset) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(18), dp(10), dp(18), dp(10) + navigationInset);
        nav.setBackground(panel(Color.WHITE, Color.rgb(244, 219, 231), dp(34)));
        nav.setElevation(dp(8));
        nav.addView(navButton("Home", R.drawable.ic_home_24, selected.equals("home"), this::renderHome));
        nav.addView(navButton("Study", R.drawable.ic_study_24, selected.equals("study"), this::renderStudy));
        nav.addView(navButton("Stats", R.drawable.ic_stats_24, selected.equals("stats"), this::renderStats));
        return nav;
    }

    private LinearLayout navButton(String label, int iconRes, boolean active, Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(active ? Color.rgb(245, 166, 0) : MUTED);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(23), dp(23));
        iconLp.setMargins(0, 0, dp(7), 0);
        button.addView(icon, iconLp);
        TextView text = text(label, 15, active ? INK : MUTED, true);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        button.setBackground(panel(active ? Color.rgb(255, 248, 220) : Color.TRANSPARENT, active ? Color.rgb(255, 248, 220) : Color.TRANSPARENT, dp(28)));
        button.setClickable(true);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void renderHome() {
        base("home");
        long now = System.currentTimeMillis();
        LocalStore.SyncStatus sync = store.latestSync();
        LocalStore.StudyStreak streak = store.studyStreak(now);
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        List<Records.StudyItem> homeItems = studyQueue(rows, now, false);
        Records.AdaptiveLoadPlan homePlan = rows.isEmpty() ? null : adaptivePlan(rows, homeItems, now);
        List<QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : queuedEntries(rows, homeItems, now, homePlan);
        AnkiDroidGateway.ProviderStatus provider = gateway.status();

        content.addView(homeHeader());
        addSpace(12);
        content.addView(homeMetricRow(sync, provider, streak, homePlan));
        addSpace(14);

        if (rows.isEmpty()) {
            Button syncButton = primaryButton("Sync AnkiDroid", CORAL);
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
        } else {
            Button studyButton = homeStudyButton();
            studyButton.setOnClickListener(v -> startFocusedStudy());
            content.addView(studyButton);

        }
        content.addView(homeActionRow());

        addSpace(16);
        content.addView(homeSectionHeader("Focus queue", rows.isEmpty() ? null : "View all", rows.isEmpty() ? null : this::renderFocusQueue));
        if (rows.isEmpty()) {
            emptyState("No kanji queued yet", "After the first sync, this screen shows the kanji that need focused recall and writing practice.");
        } else {
            if (entries.isEmpty()) {
                emptyState("No active practice yet", "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.");
            }
            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                content.addView(queueRowView(entries.get(i), now));
            }
        }
    }

    private View homeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Kani", 54, INK, true);
        title.setLetterSpacing(0);
        copy.addView(title);
        copy.addView(text("Your AnkiDroid companion app to cure kanji blindness", 18, MUTED, true));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        KaniMascotView mascot = new KaniMascotView(this);
        LinearLayout.LayoutParams mascotLp = new LinearLayout.LayoutParams(dp(128), dp(118));
        mascotLp.setMargins(dp(8), 0, 0, 0);
        header.addView(mascot, mascotLp);
        return header;
    }

    private View homeMetricRow(LocalStore.SyncStatus sync, AnkiDroidGateway.ProviderStatus provider, LocalStore.StudyStreak streak, Records.AdaptiveLoadPlan plan) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(metricCard(
                R.drawable.ic_sync_24,
                TEAL,
                "Last sync",
                homeSyncValue(sync),
                provider.canSync && sync != null && "success".equals(sync.status) ? "Up to date" : "Tap to sync",
                this::confirmSync
        ));
        row.addView(metricCard(
                R.drawable.ic_flame_24,
                Color.rgb(247, 159, 0),
                "Study streak",
                streakHeadline(streak),
                streak.bestDays > 0 ? "Best: " + streakDayCount(streak.bestDays) : "Start today",
                null
        ));
        row.addView(metricCard(
                R.drawable.ic_target_24,
                CORAL,
                "Today's focus",
                focusHeadline(plan),
                focusMetricBody(plan),
                null
        ));
        return row;
    }

    private View metricCard(int iconRes, int accent, String label, String value, String body, Runnable action) {
        LinearLayout card = panelBox(Color.WHITE, softened(accent));
        card.setPadding(dp(12), dp(14), dp(12), dp(14));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconLp.setMargins(0, 0, 0, dp(8));
        card.addView(icon, iconLp);
        card.addView(text(label, 14, accent, true));
        card.addView(text(value, 16, INK, true));
        card.addView(text(compact(body, 24), 12, MUTED, false));
        if (action != null) {
            card.setClickable(true);
            card.setOnClickListener(v -> action.run());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(124), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private Button homeStudyButton() {
        Button button = primaryButton("Study now", CORAL);
        button.setTextSize(24);
        button.setMinHeight(dp(80));
        button.setElevation(dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(80));
        lp.setMargins(0, dp(16), 0, dp(14));
        button.setLayoutParams(lp);
        return button;
    }

    private View homeActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(pillButton("Browse Kanji", R.drawable.ic_book_24, this::renderBrowseKanji));
        row.addView(pillButton("Recent mistakes", R.drawable.ic_trending_24, this::renderRecentMistakes));
        row.addView(pillButton("Settings", R.drawable.ic_settings_24, this::renderSettings));
        return row;
    }

    private void renderBrowseKanji() {
        renderBrowseKanji("");
    }

    private String homeSyncValue(LocalStore.SyncStatus sync) {
        if (sync == null) {
            return "Never synced";
        }
        return sentenceCase(humanSyncTime(sync.finishedAt));
    }

    private String sentenceCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String focusHeadline(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Waiting";
        }
        if (plan.allKanjiMode) {
            return "All current";
        }
        return plan.remaining + " left / " + plan.target;
    }

    private String focusMetricBody(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Sync first";
        }
        if (plan.allKanjiMode) {
            return "All kanji";
        }
        return plan.target <= 5 ? "Small Pareto" : "Adaptive Pareto";
    }

    private View homeSectionHeader(String title, String actionLabel, Runnable action) {
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

    private View pillButton(String label, int iconRes, Runnable action) {
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1);
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    private void renderFocusQueue() {
        base("home");
        long now = System.currentTimeMillis();
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        List<Records.StudyItem> items = studyQueue(rows, now, false);
        Records.AdaptiveLoadPlan plan = rows.isEmpty() ? null : adaptivePlan(rows, items, now);
        List<QueueEntry> entries = rows.isEmpty() ? new ArrayList<>() : queuedEntries(rows, items, now, plan);

        content.addView(homeSectionHeader("Focus queue", "Home", this::renderHome));
        content.addView(text(adaptiveFocusText(plan), 16, MUTED, false));
        addSpace(8);
        if (rows.isEmpty()) {
            emptyState("No kanji queued yet", "Sync AnkiDroid first to build a focus queue.");
            Button syncButton = primaryButton("Sync AnkiDroid", CORAL);
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
            return;
        }
        if (entries.isEmpty()) {
            emptyState("No active practice yet", "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.");
            return;
        }
        for (QueueEntry entry : entries) {
            content.addView(queueRowView(entry, now));
        }
    }

    private void renderRecentMistakes() {
        base("home");
        content.addView(homeSectionHeader("Recent mistakes", "Home", this::renderHome));
        List<LocalStore.RecentMistake> mistakes = store.recentMistakes(12);
        if (mistakes.isEmpty()) {
            emptyState("No recent mistakes yet", "Missed and hard reviews will show here after you study.");
            return;
        }
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        for (LocalStore.RecentMistake mistake : mistakes) {
            content.addView(recentMistakeRow(mistake, findRow(rows, mistake.kanji)));
        }
    }

    private View recentMistakeRow(LocalStore.RecentMistake mistake, Records.DashboardRow row) {
        LinearLayout box = panelBox(Color.WHITE, PINK_STROKE);
        box.setOnClickListener(v -> renderDetail(mistake.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView kanji = kanjiTile(mistake.kanji, dp(70), 42);
        top.addView(kanji);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(row == null ? "Recent review miss" : rowMeaning(row), 19, INK, true));
        copy.addView(text("Rated " + mistake.rating + " on " + timelineDate(mistake.reviewedAtMillis), 14, MUTED, false));
        if (row != null) {
            copy.addView(text(sourceEvidenceText(row), 14, INK, true));
        }
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(12), 0, dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(text(">", 34, CORAL, true));
        box.addView(top);
        return box;
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
        box.addView(text("Today's focus", 22, INK, true));
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

    private String humanSyncTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "date unknown";
        }
        Date date = new Date(timestampMillis);
        Calendar then = Calendar.getInstance();
        then.setTime(date);
        Calendar now = Calendar.getInstance();
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        if (sameLocalDay(then, now)) {
            return "today at " + timeFormat.format(date);
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameLocalDay(then, now)) {
            return "yesterday at " + timeFormat.format(date);
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }

    private boolean sameLocalDay(Calendar left, Calendar right) {
        return left.get(Calendar.ERA) == right.get(Calendar.ERA)
                && left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
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
        content.addView(text("Syncing AnkiDroid", 34, INK, true));
        TextView stage = text("Finding note type", 22, INK, true);
        content.addView(stage);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setContentDescription("Sync progress");
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(12));
        progressParams.setMargins(0, dp(12), 0, dp(12));
        content.addView(progress, progressParams);
        TextView count = text("Reading collection details.", 17, MUTED, false);
        TextView rate = text("", 15, MUTED, false);
        content.addView(count);
        content.addView(rate);
        ManualSyncProgressView progressView = new ManualSyncProgressView(stage, progress, count, rate);
        io.execute(() -> {
            ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                    this,
                    store,
                    syncGateway,
                    settings(),
                    update -> main.post(() -> progressView.render(update))
            ).run();
            if (result.success) {
                store.activateAutoSyncAfterFirstSuccess();
                AutoSyncScheduler.schedule(this);
            }
            main.post(() -> {
                renderSyncResult(result);
            });
        });
    }

    private final class ManualSyncProgressView {
        private final TextView stage;
        private final ProgressBar progressBar;
        private final TextView count;
        private final TextView rate;
        private long scanStartedAt;
        private int lastScannedCards = -1;
        private int lastTotalCards = -1;

        private ManualSyncProgressView(TextView stage, ProgressBar progressBar, TextView count, TextView rate) {
            this.stage = stage;
            this.progressBar = progressBar;
            this.count = count;
            this.rate = rate;
        }

        private void render(SyncProgress progress) {
            stage.setText(syncStageText(progress.stage));
            if (progress.totalKnown()) {
                lastScannedCards = progress.scannedCards;
                lastTotalCards = progress.totalCards;
                if (progress.stage == SyncProgress.Stage.SCANNING_CARDS && scanStartedAt <= 0L) {
                    scanStartedAt = SystemClock.elapsedRealtime();
                }
            }
            if (lastTotalCards >= 0) {
                renderKnownTotal(progress);
                return;
            }
            progressBar.setIndeterminate(true);
            count.setText(syncStageBody(progress.stage));
            rate.setText("");
            progressBar.setContentDescription("Sync progress: " + syncStageText(progress.stage));
        }

        private void renderKnownTotal(SyncProgress progress) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(1000);
            int value = lastTotalCards == 0
                    ? 1000
                    : Math.min(1000, Math.max(0, Math.round((lastScannedCards * 1000f) / lastTotalCards)));
            progressBar.setProgress(value);
            String cardText = lastScannedCards + " / " + lastTotalCards + " cards scanned";
            count.setText(cardText);
            rate.setText(scanRateText(progress.stage));
            progressBar.setContentDescription("Sync progress: " + cardText);
        }

        private String scanRateText(SyncProgress.Stage stage) {
            if (stage != SyncProgress.Stage.SCANNING_CARDS) {
                return lastScannedCards >= lastTotalCards ? "Card scan finished." : "";
            }
            if (lastScannedCards <= 0 || scanStartedAt <= 0L) {
                return "Scanning cards.";
            }
            long elapsedMillis = Math.max(1L, SystemClock.elapsedRealtime() - scanStartedAt);
            double perSecond = lastScannedCards * 1000.0 / elapsedMillis;
            String rateText = String.format(Locale.US, perSecond >= 10.0 ? "%.0f cards/sec" : "%.1f cards/sec", perSecond);
            int remaining = Math.max(0, lastTotalCards - lastScannedCards);
            if (remaining == 0) {
                return rateText + " - finishing up";
            }
            if (lastScannedCards >= 3 && elapsedMillis >= 1000L && perSecond > 0.01) {
                long etaMillis = Math.round((remaining / perSecond) * 1000.0);
                return rateText + " - about " + shortDuration(etaMillis) + " left";
            }
            return rateText + " - estimating time left";
        }
    }

    private String syncStageText(SyncProgress.Stage stage) {
        if (stage == SyncProgress.Stage.FINDING_NOTE_TYPE) {
            return "Finding note type";
        }
        if (stage == SyncProgress.Stage.READING_NOTES) {
            return "Reading notes";
        }
        if (stage == SyncProgress.Stage.SCANNING_CARDS) {
            return "Scanning cards";
        }
        if (stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE) {
            return "Building practice queue";
        }
        if (stage == SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Archiving imported suspended cards";
        }
        return "Syncing cards";
    }

    private String syncStageBody(SyncProgress.Stage stage) {
        if (stage == SyncProgress.Stage.FINDING_NOTE_TYPE) {
            return "Checking collection shape.";
        }
        if (stage == SyncProgress.Stage.READING_NOTES) {
            return "Reading notes before the card total is known.";
        }
        if (stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE) {
            return "Saving the practice queue.";
        }
        if (stage == SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Updating archived suspended cards.";
        }
        return "Preparing card scan.";
    }

    private String shortDuration(long millis) {
        long seconds = Math.max(1L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return seconds + " sec";
        }
        long minutes = Math.max(1L, Math.round(seconds / 60.0));
        if (minutes < 60L) {
            return minutes + " min";
        }
        long hours = Math.max(1L, Math.round(minutes / 60.0));
        return hours + " hr";
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
            List<Records.DashboardRow> rows = store.activeDashboardRows();
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
        List<Records.DashboardRow> rows = store.activeDashboardRows();
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
        BridgeScheduler scheduler = new BridgeScheduler();
        for (Records.StudyItem item : scheduler.activeQueueItems(items, rows, now, null)) {
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
        LinearLayout box = panelBox(Color.WHITE, softened(rowColor(item, now)));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setOnClickListener(v -> renderDetail(row.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(kanjiTile(row.kanji, dp(90), 52));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(rowMeaning(row), 19, INK, true));
        copy.addView(text(sourceEvidenceText(row), 14, INK, true));
        copy.addView(text(compact(queueCardBody(row), 72), 14, MUTED, false));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(14), 0, dp(6), 0);
        top.addView(copy, copyLp);
        top.addView(text(">", 34, CORAL, true));
        box.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(recognitionStageLabel(item), BLUE));
        if (item.writingRemediationPending) {
            chips.addView(chip("writing repair", CORAL));
        } else if (!item.suppressedByTaskType.isEmpty()) {
            chips.addView(chip("lower prompts hidden", TEAL));
        } else if (item.consecutiveFailedRecognitionDays > 0) {
            chips.addView(chip("miss days " + item.consecutiveFailedRecognitionDays, BLUE));
        }
        box.addView(chips);
        return box;
    }

    private String queueCardBody(Records.DashboardRow row) {
        if (row.reasonText == null || row.reasonText.isEmpty()) {
            return "Needs focused kanji practice.";
        }
        String normalized = row.reasonText.toLowerCase(Locale.ROOT);
        if (normalized.contains("similar-kanji") || normalized.contains("similar kanji") || normalized.contains("similar choice")) {
            return "Shape mix-up made this a writing-practice target.";
        }
        return row.reasonText;
    }

    private TextView kanjiTile(String value, int sizePx, int textSp) {
        TextView kanji = text(value, textSp, INK, true);
        kanji.setGravity(Gravity.CENTER);
        kanji.setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD);
        kanji.setBackground(panel(BLUSH, BLUSH, dp(10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        kanji.setLayoutParams(lp);
        return kanji;
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
        renderDetail(kanji, false);
    }

    private void renderBrowseKanji(String query) {
        base("home");
        content.addView(text("Browse Kanji", 34, INK, true));
        content.addView(text("Local kanji from synced Kani data and study history.", 16, MUTED, false));
        addSpace(10);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(query == null ? "" : query);
        search.setHint("Search kanji, meaning, reading, or examples");
        search.setTextSize(18);
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(58)));

        Button submit = primaryButton("Search", TEAL);
        submit.setOnClickListener(v -> renderBrowseKanji(search.getText().toString()));
        content.addView(submit);

        List<Records.KanjiInventoryItem> items = store.searchKanjiInventory(query);
        content.addView(sectionTitle(items.isEmpty() ? "No matches" : countText(items.size(), "kanji", "kanji")));
        if (items.isEmpty()) {
            emptyState("No local kanji found", "Sync AnkiDroid first, or try a different search.");
            return;
        }
        for (Records.KanjiInventoryItem item : items) {
            content.addView(browseKanjiRow(item, query == null ? "" : query));
        }
    }

    private View browseKanjiRow(Records.KanjiInventoryItem item, String query) {
        LinearLayout box = panelBox(Color.WHITE, item.suspended ? CORAL : TEAL);
        box.setOnClickListener(v -> renderDetail(item.kanji, true));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView glyph = text(item.kanji, 44, INK, true);
        glyph.setGravity(Gravity.CENTER);
        top.addView(glyph, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(item.primaryMeaning.isEmpty() ? "Meaning not stored yet" : item.primaryMeaning, 19, INK, true));
        if (!item.readings.isEmpty()) {
            copy.addView(text(item.readings, 14, TEAL, true));
        }
        copy.addView(text(countText(item.sourceCount, "local source", "local sources") + " · " + countText(item.exampleCount, "example", "examples"), 14, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        if (item.suspended) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip("SUSPENDED", CORAL));
            box.addView(chips);
        }
        return box;
    }

    private void renderDetail(String kanji, boolean fromBrowse) {
        base("home");
        Records.KanjiRecoveryTimeline timeline = store.timelineForKanji(kanji);
        Records.DashboardRow row = timeline.currentRow;
        Records.KanjiInventoryItem inventory = timeline.inventoryItem;
        if (inventory == null && row == null && timeline.currentStudyItem == null && timeline.events.isEmpty()) {
            emptyState("Kanji not found", "This row may have disappeared after a sync.");
            return;
        }
        String displayKanji = row == null ? (inventory == null ? kanji : inventory.kanji) : row.kanji;
        TextView glyph = text(displayKanji, 92, INK, true);
        glyph.setGravity(Gravity.CENTER);
        content.addView(glyph);
        if (fromBrowse) {
            Button back = secondaryButton("Back to Browse Kanji");
            back.setOnClickListener(v -> renderBrowseKanji(""));
            content.addView(back);
        }
        boolean suspended = inventory != null && inventory.suspended;
        if (suspended) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip("SUSPENDED", CORAL));
            content.addView(chips);
        }
        if (row == null) {
            content.addView(text(inventory == null || inventory.primaryMeaning.isEmpty() ? "Historical recovery" : inventory.primaryMeaning, 25, INK, true));
            if (inventory != null && !inventory.readings.isEmpty()) {
                content.addView(text(inventory.readings, 20, TEAL, true));
            }
        } else {
            content.addView(text(rowMeaning(row), 25, INK, true));
            content.addView(text(row.reading, 20, TEAL, true));
        }
        addSpace(10);
        LinearLayout why = band(BLUE);
        why.addView(text("Why it is here", 22, Color.WHITE, true));
        if (row == null) {
            why.addView(text("This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history.", 17, Color.WHITE, false));
            if (inventory != null && !inventory.browserSearch.isEmpty()) {
                why.addView(text("Anki browser: " + inventory.browserSearch, 14, Color.WHITE, false));
            }
        } else {
            why.addView(text(row.reasonText, 17, Color.WHITE, false));
            why.addView(text("Anki browser: " + row.browserSearch, 14, Color.WHITE, false));
        }
        content.addView(why);
        if (inventory != null) {
            content.addView(localInventoryPanel(inventory));
        }
        if (row != null && !suspended) {
            Button practice = primaryButton("Review this now", CORAL);
            practice.setOnClickListener(v -> renderStudyForKanji(row.kanji));
            content.addView(practice);
        }
        if (inventory != null && !inventory.browserSearch.isEmpty()) {
            Button copy = secondaryButton("Copy Anki search");
            copy.setOnClickListener(v -> copyAnkiSearch(inventory.browserSearch, v));
            content.addView(copy);
        }
        Button suspend = secondaryButton(suspended ? "Unsuspend locally" : "Suspend locally");
        suspend.setOnClickListener(v -> {
            store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis());
            Toast.makeText(this, suspended ? "Kanji unsuspended." : "Kanji suspended locally.", Toast.LENGTH_SHORT).show();
            renderDetail(displayKanji, fromBrowse);
        });
        content.addView(suspend);
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

    private View localInventoryPanel(Records.KanjiInventoryItem inventory) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text("Local inventory", 19, INK, true));
        box.addView(text(countText(inventory.sourceCount, "source note/card", "source notes/cards") + " · " + countText(inventory.exampleCount, "stored example", "stored examples"), 15, MUTED, false));
        if (!inventory.browserSearch.isEmpty()) {
            box.addView(text("Search: " + inventory.browserSearch, 14, MUTED, false));
        }
        if (inventory.lastSeenAtMillis > 0L) {
            box.addView(text("Last seen locally " + shortDateTime(inventory.lastSeenAtMillis), 14, MUTED, false));
        }
        return box;
    }

    private void copyAnkiSearch(String browserSearch, View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Anki search", browserSearch));
        if (v instanceof Button) {
            ((Button) v).setText(R.string.copied_anki_search);
        }
        Toast.makeText(this, "Search copied", Toast.LENGTH_SHORT).show();
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
            Records.Example example = exampleForSession(session);
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

    private Records.Example wordReadingExample(Records.DashboardRow row) {
        if (row == null || row.examples.isEmpty()) {
            return null;
        }
        Records.Example active = null;
        for (Records.Example example : row.examples) {
            if ("suspended".equals(example.sourceType)) {
                return example;
            }
            if (active == null && "active".equals(example.sourceType)) {
                active = example;
            }
        }
        return active == null ? row.examples.get(0) : active;
    }

    private Records.Example exampleForSession(Records.StudySession session) {
        if (isWordReadingTask(session)) {
            return wordReadingExample(session.row);
        }
        return firstExample(session == null ? null : session.row);
    }

    private void renderStudy() {
        base("study");
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        long now = System.currentTimeMillis();
        Records.SimilarKanjiWritingRepair repair = store.nextDueSimilarWritingRepair(now);
        if (repair != null) {
            renderSimilarWritingRepair(repair, now);
            return;
        }
        if (rows.isEmpty()) {
            Records.SimilarKanjiChoiceCard inventoryChoice = store.nextDueInventorySimilarChoice(Collections.emptySet(), now);
            if (inventoryChoice != null) {
                renderSimilarChoice(inventoryChoice);
                return;
            }
            content.addView(text("Study practice", 34, INK, true));
            emptyState("Nothing to study yet", "Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.StudyItem> beforeSeed = store.studyItems();
        Records.AdaptiveLoadPlan plan = adaptivePlan(rows, beforeSeed, now);
        List<Records.StudyItem> seeded = studyQueue(rows, now, true, plan);
        Records.AdaptiveLoadPlan seededPlan = adaptivePlan(rows, seeded, now);
        Set<String> focus = continueAllKanjiSession || seededPlan.allKanjiMode
                ? null
                : new HashSet<>(seededPlan.focusKanji);
        Records.LearningRepeat repeat = nextDueLearningRepeat(rows, seeded, now);
        if (repeat != null) {
            renderLearningRepeat(repeat, rows, seeded, now);
            return;
        }
        activeLearningRepeat = null;
        activeSession = scheduler.nextSession(seeded, rows, now, focus);
        if (activeSession == null) {
            Records.SimilarKanjiChoiceCard inventoryChoice = store.nextDueInventorySimilarChoice(activeKanjiSet(rows), now);
            if (inventoryChoice != null) {
                renderSimilarChoice(inventoryChoice);
                return;
            }
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
        Records.SimilarKanjiChoiceCard gate = store.dueSimilarChoiceForActiveTarget(activeSession.item.kanji, now);
        if (gate != null) {
            renderSimilarChoice(gate);
            return;
        }
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private Set<String> activeKanjiSet(List<Records.DashboardRow> rows) {
        Set<String> out = new HashSet<>();
        for (Records.DashboardRow row : rows) {
            out.add(row.kanji);
        }
        return out;
    }

    private Records.LearningRepeat nextDueLearningRepeat(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        for (Records.LearningRepeat repeat : store.dueLearningRepeats(now)) {
            Records.DashboardRow row = findRow(rows, repeat.kanji);
            Records.StudyItem item = findStudyItem(items, repeat);
            if (row == null || item == null || "retired".equals(item.state)) {
                store.clearLearningRepeat(repeat);
                continue;
            }
            if ("writing_remediation".equals(repeat.taskType) && !item.writingRemediationPending) {
                store.clearLearningRepeat(repeat);
                continue;
            }
            return repeat;
        }
        return null;
    }

    private void renderLearningRepeat(Records.LearningRepeat repeat, List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        Records.DashboardRow row = findRow(rows, repeat.kanji);
        Records.StudyItem item = findStudyItem(items, repeat);
        if (row == null || item == null) {
            store.clearLearningRepeat(repeat);
            renderStudy();
            return;
        }
        String token = repeat.activeToken.isEmpty() ? repeat.kanji + "-repeat-" + UUID.randomUUID() : repeat.activeToken;
        activeLearningRepeat = repeat.withToken(token, now);
        store.saveLearningRepeat(activeLearningRepeat);
        activeSession = new Records.StudySession(
                item.withToken(token),
                row,
                token,
                repeat.taskType,
                "writing_remediation".equals(repeat.taskType),
                row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning
        );
        renderSession(activeSession);
    }

    private void renderFocusDone(Records.AdaptiveLoadPlan plan) {
        content.addView(text("Today's focus done", 34, INK, true));
        content.addView(text("Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji.", 18, MUTED, false));
        LinearLayout summary = band(TEAL);
        summary.addView(text("Today's focus: 0 left / " + plan.target, 22, Color.WHITE, true));
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
        List<Records.DashboardRow> rows = store.activeDashboardRows();
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
        Records.SimilarKanjiChoiceCard gate = store.dueSimilarChoiceForActiveTarget(item.kanji, now);
        if (gate != null) {
            renderSimilarChoice(gate);
            return;
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
        activeLearningRepeat = null;
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

    private void renderSimilarChoice(Records.SimilarKanjiChoiceCard card) {
        content.removeAllViews();
        activeSession = null;
        activeLearningRepeat = null;
        activeSimilarRepair = null;
        activeSimilarChoice = card;
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardGestureArea = null;
        drawingPad = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

        content.addView(text("Choose the kanji", 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text("Similar choice", 22, Color.WHITE, true));
        stage.addView(text("Pick the kanji that matches the meaning before the normal card appears.", 15, Color.WHITE, false));
        content.addView(stage);

        LinearLayout box = panelBox(Color.WHITE, TEAL);
        box.addView(text("Which kanji means " + card.primaryMeaning + "?", 22, INK, true));
        box.addView(similarChoiceGrid(card));
        content.addView(box);
    }

    private View similarChoiceGrid(Records.SimilarKanjiChoiceCard card) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        List<String> choices = new ArrayList<>(card.choices);
        Collections.shuffle(choices);
        LinearLayout row = null;
        for (int i = 0; i < choices.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String glyph = choices.get(i);
            Button button = primaryButton(glyph, Color.WHITE);
            button.setTextColor(INK);
            button.setTextSize(34);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(panel(Color.rgb(255, 247, 251), Color.rgb(246, 202, 225), dp(8)));
            button.setOnClickListener(v -> submitSimilarChoice(glyph));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            row.addView(button, lp);
        }
        if (choices.size() % 2 == 1 && grid.getChildCount() > 0) {
            LinearLayout lastRow = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
            SpaceView spacer = new SpaceView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            lastRow.addView(spacer, lp);
        }
        return grid;
    }

    private void submitSimilarChoice(String selectedKanji) {
        if (activeSimilarChoice == null) {
            renderStudy();
            return;
        }
        Records.SimilarKanjiChoiceResult result = store.submitSimilarChoice(activeSimilarChoice, selectedKanji, System.currentTimeMillis());
        Toast.makeText(
                this,
                result.correct ? "Correct." : "Queued similar writing repairs.",
                Toast.LENGTH_SHORT
        ).show();
        renderStudy();
    }

    private void renderSimilarWritingRepair(Records.SimilarKanjiWritingRepair repair, long now) {
        String token = repair.activeToken.isEmpty()
                ? repair.repairKanji + "-similar-repair-" + UUID.randomUUID()
                : repair.activeToken;
        activeSimilarChoice = null;
        activeSimilarRepair = repair.withToken(token, now);
        store.saveSimilarWritingRepair(activeSimilarRepair);
        Records.DashboardRow row = rowForSimilarRepair(activeSimilarRepair.repairKanji);
        Records.StudyItem item = new Records.StudyItem(
                activeSimilarRepair.repairKanji,
                "learning",
                now,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                token,
                activeSimilarRepair.createdAtMillis
        );
        activeLearningRepeat = null;
        activeSession = new Records.StudySession(
                item,
                row,
                token,
                "similar_writing",
                true,
                activeSimilarRepair.promptMeaning
        );
        renderSession(activeSession);
    }

    private Records.DashboardRow rowForSimilarRepair(String kanji) {
        Records.DashboardRow row = store.rowForKanji(kanji);
        if (row != null) {
            return row;
        }
        Records.KanjiInventoryItem item = store.inventoryItemForKanji(kanji);
        String meaning = item == null ? "" : item.primaryMeaning;
        String reading = item == null ? "" : item.readings;
        String search = item == null ? TextUtil.browserSearchForKanji(kanji, settings()) : item.browserSearch;
        return new Records.DashboardRow(
                kanji,
                null,
                meaning,
                reading,
                search,
                0,
                "similar_choice_repair",
                "Practice after a similar-kanji choice miss.",
                0,
                0,
                0,
                Collections.emptyList()
        );
    }

    private void renderSession(Records.StudySession session) {
        activeSimilarChoice = null;
        if (session == null || !"similar_writing".equals(session.taskType)) {
            activeSimilarRepair = null;
        }
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
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        hintsUsed = 0;
        setHintState(HintState.initial());
        drawingPad = null;
        flashcardGestureArea = content;

        content.addView(text(flashcardTitle(session), 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (activeLearningRepeat != null) {
            stage.addView(text(learningRepeatLine(activeLearningRepeat), 15, Color.WHITE, false));
        }
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
        flashcardGestureArea = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        hintsUsed = 0;
        setHintState(initialHintState(session));

        content.addView(text("Draw this kanji", 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (activeLearningRepeat != null) {
            stage.addView(text(learningRepeatLine(activeLearningRepeat), 15, Color.WHITE, false));
        }
        if (session.row != null) {
            if (isRecallTask(session)) {
                stage.addView(text("Prompt: " + sessionClue(session), 17, Color.WHITE, false));
                if (!session.row.reading.isEmpty()) {
                    stage.addView(text("Reading: " + session.row.reading, 15, Color.WHITE, false));
                }
                stage.addView(text("Write the kanji from this prompt. The answer stays hidden until you check.", 15, Color.WHITE, false));
            } else if ("writing_remediation".equals(session.taskType)) {
                stage.addView(text("Recognition has missed on multiple days. Write it once with the guide before returning to recognition.", 15, Color.WHITE, false));
            } else if ("similar_writing".equals(session.taskType)) {
                stage.addView(text("Write the kanji from the similar-choice miss before retrying that choice.", 15, Color.WHITE, false));
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

    private String learningRepeatLine(Records.LearningRepeat repeat) {
        Records.LearningStepSettings settings = store.learningStepSettings();
        int total = Records.LEARNING_REPEAT_REVIEW.equals(repeat.repeatType)
                ? settings.reviewStepsMinutes.size()
                : settings.newStepsMinutes.size();
        return "Learning step " + Math.min(total, repeat.stepIndex + 1) + " / " + Math.max(1, total) + ". Practice only.";
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
            box.addView(randomFontVariantCard(session.item.kanji), fontVariantCardParams());
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

    private View randomFontVariantCard(String kanji) {
        switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0:
                return fontVariantTile(kanji, "Print", Typeface.SERIF);
            case 1:
                return fontVariantTile(kanji, "Sans", Typeface.DEFAULT);
            case 2:
                return fontVariantTile(kanji, "Block", Typeface.MONOSPACE);
            case 3:
                return fontVariantTile(kanji, "Klee", fontResource(R.font.klee_one_regular, Typeface.DEFAULT));
            default:
                return fontVariantTile(kanji, "Kaisei", fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF));
        }
    }

    private LinearLayout.LayoutParams fontVariantCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(168));
        lp.setMargins(0, dp(6), 0, dp(6));
        return lp;
    }

    private View fontVariantTile(String kanji, String label, Typeface typeface) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(10), dp(8), dp(10));
        tile.setBackground(panel(Color.rgb(255, 247, 251), Color.rgb(246, 202, 225), dp(8)));
        TextView glyph = text(kanji, 92, INK, true);
        glyph.setTypeface(typeface, Typeface.BOLD);
        glyph.setGravity(Gravity.CENTER);
        tile.addView(glyph, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView caption = text(label, 12, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        tile.addView(caption);
        return tile;
    }

    private Typeface fontResource(int fontRes, Typeface fallback) {
        try {
            return getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
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
                details.addView(text("Reading: " + hiraganaReading(session.row.reading), 15, TEAL, true));
            }
            Records.Example example = exampleForSession(session);
            if (example != null) {
                details.addView(text("From: " + example.expression + (example.reading.isEmpty() ? "" : "  " + hiraganaReading(example.reading)), 15, INK, true));
                if (!example.meaning.isEmpty()) {
                    details.addView(text(cleanLearnerText(example.meaning, "", 80), 13, MUTED, false));
                }
            }
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        return box;
    }

    private String hiraganaReading(String reading) {
        StringBuilder converted = new StringBuilder(reading.length());
        for (int i = 0; i < reading.length(); i++) {
            char c = reading.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') {
                converted.append((char) (c - 0x60));
            } else {
                converted.append(c);
            }
        }
        return converted.toString();
    }

    private void buildFlashcardActionBar(boolean revealed) {
        if (studyActionBar == null) {
            return;
        }
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text("", 15, MUTED, false);
        resultStatus.setVisibility(View.GONE);
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!revealed) {
            Button reveal = primaryButton("Reveal", CORAL);
            reveal.setOnClickListener(v -> revealFlashcardAnswer());
            actions.addView(reveal, new LinearLayout.LayoutParams(0, dp(62), 1));
        } else {
            Button fail = primaryButton("Fail", CORAL);
            fail.setOnClickListener(v -> submitReview("again", false));
            actions.addView(fail, new LinearLayout.LayoutParams(0, dp(62), 1));

            Button pass = primaryButton("Pass", TEAL);
            pass.setOnClickListener(v -> submitReview("good", false));
            actions.addView(pass, new LinearLayout.LayoutParams(0, dp(62), 1));
        }
        studyActionBar.addView(actions);
    }

    private void revealFlashcardAnswer() {
        if (flashcardAnswerRevealed) {
            return;
        }
        flashcardAnswerRevealed = true;
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(View.VISIBLE);
        }
        buildFlashcardActionBar(true);
    }

    private boolean handleFlashcardGesture(MotionEvent event) {
        if (activeSession == null || activeSession.writingRequired || flashcardGestureArea == null) {
            flashcardTouchTracking = false;
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                flashcardTouchTracking = isTouchInsideView(flashcardGestureArea, event);
                if (flashcardTouchTracking) {
                    flashcardTouchStartX = event.getRawX();
                    flashcardTouchStartY = event.getRawY();
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (!flashcardTouchTracking) {
                    return false;
                }
                flashcardTouchTracking = false;
                if (!isTouchInsideView(flashcardGestureArea, event)) {
                    return false;
                }
                return handleFlashcardRelease(event);
            case MotionEvent.ACTION_CANCEL:
                flashcardTouchTracking = false;
                return false;
            default:
                return false;
        }
    }

    private boolean handleFlashcardRelease(MotionEvent event) {
        float dx = event.getRawX() - flashcardTouchStartX;
        float dy = event.getRawY() - flashcardTouchStartY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (absX <= touchSlop && absY <= touchSlop) {
            if (!flashcardAnswerRevealed) {
                revealFlashcardAnswer();
                return true;
            }
            return false;
        }
        int swipeThreshold = Math.max(dp(72), touchSlop * 6);
        if (absX >= swipeThreshold && absX > absY * 1.25f) {
            if (!flashcardAnswerRevealed) {
                return false;
            }
            submitReview(dx > 0 ? "good" : "again", false);
            return true;
        }
        return false;
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null || !view.isShown()) {
            return false;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds)) {
            return false;
        }
        return bounds.contains((int) event.getRawX(), (int) event.getRawY());
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
        if (activeSimilarRepair != null) {
            submitSimilarWritingRepair(request);
            return;
        }
        if (activeLearningRepeat != null) {
            submitLearningRepeat(request, mappedRating.code());
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(store.consumedTokens());
        long now = System.currentTimeMillis();
        Records.SchedulerParameters parameters = store.schedulerParameters();
        Records.ReviewResult result = scheduler.applyReview(activeSession.item, request, consumed, now, parameters, settings());
        LocalStore.StudyStreak streak = null;
        if (!result.duplicate) {
            String repeatType = learningRepeatTypeForReview(activeSession.item, request, result.appliedRating);
            Records.StudyItem itemToSave = repeatType == null ? result.item : deferSameDaySrsDue(result.item, now);
            store.saveStudyItem(itemToSave);
            store.saveReview(request, result.appliedRating, now);
            enqueueLearningRepeatIfNeeded(itemToSave, activeSession.taskType, repeatType, now);
            streak = store.studyStreak(now);
            Records.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
            if (tuned.lastAdjustedAtMillis != parameters.lastAdjustedAtMillis || tuned.lastAdjustmentReviewCount != parameters.lastAdjustmentReviewCount) {
                store.saveSchedulerParameters(tuned);
            }
        }
        Toast.makeText(this, reviewToast(result, streak), Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private void submitSimilarWritingRepair(Records.ReviewRequest request) {
        Records.SimilarKanjiWritingRepair repair = activeSimilarRepair;
        if (repair == null) {
            renderStudy();
            return;
        }
        boolean passed = request.manualOverride || request.writingPassed;
        boolean saved = store.finishSimilarWritingRepair(repair.id, request.token, passed, System.currentTimeMillis());
        Toast.makeText(
                this,
                !saved ? "Similar writing repair already changed." : (passed ? "Similar writing repair complete." : "Similar writing repair stays queued."),
                Toast.LENGTH_SHORT
        ).show();
        activeSimilarRepair = null;
        renderStudy();
    }

    private void submitLearningRepeat(Records.ReviewRequest request, String rating) {
        Records.LearningRepeat repeat = activeLearningRepeat;
        if (repeat == null || activeSession == null || !repeat.activeToken.equals(request.token)) {
            Toast.makeText(this, "Learning repeat already changed.", Toast.LENGTH_SHORT).show();
            renderStudy();
            return;
        }
        long now = System.currentTimeMillis();
        Records.LearningStepSettings settings = store.learningStepSettings();
        List<Integer> steps = Records.LEARNING_REPEAT_REVIEW.equals(repeat.repeatType)
                ? settings.reviewStepsMinutes
                : settings.newStepsMinutes;
        if (steps.isEmpty()) {
            store.clearLearningRepeat(repeat);
            Toast.makeText(this, "Learning repeat cleared.", Toast.LENGTH_SHORT).show();
            renderStudy();
            return;
        }
        int nextStep;
        if ("again".equals(rating)) {
            nextStep = 0;
        } else if ("hard".equals(rating)) {
            nextStep = Math.min(repeat.stepIndex, steps.size() - 1);
        } else {
            nextStep = repeat.stepIndex + 1;
            if (nextStep >= steps.size()) {
                store.clearLearningRepeat(repeat);
                activeLearningRepeat = null;
                Toast.makeText(this, "Learning repeat complete.", Toast.LENGTH_SHORT).show();
                renderStudy();
                return;
            }
        }
        long dueAt = now + steps.get(nextStep) * 60_000L;
        store.saveLearningRepeat(repeat.withStep(nextStep, dueAt, now));
        Toast.makeText(this, "Learning repeat " + dueText(dueAt, now) + ".", Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private String learningRepeatTypeForReview(Records.StudyItem originalItem, Records.ReviewRequest request, String appliedRating) {
        boolean newCard = originalItem != null && originalItem.totalReviews <= 0;
        if (newCard) {
            return "easy".equals(appliedRating) ? null : Records.LEARNING_REPEAT_NEW;
        }
        boolean failed = "again".equals(appliedRating)
                || (request.writingRequired && !request.writingPassed && !request.manualOverride);
        return failed ? Records.LEARNING_REPEAT_REVIEW : null;
    }

    private void enqueueLearningRepeatIfNeeded(Records.StudyItem item, String taskType, String repeatType, long now) {
        if (repeatType == null) {
            return;
        }
        Records.LearningStepSettings settings = store.learningStepSettings();
        List<Integer> steps = Records.LEARNING_REPEAT_REVIEW.equals(repeatType)
                ? settings.reviewStepsMinutes
                : settings.newStepsMinutes;
        if (steps.isEmpty()) {
            return;
        }
        store.enqueueLearningRepeat(item, taskType, repeatType, 0, now + steps.get(0) * 60_000L, now);
    }

    private Records.StudyItem deferSameDaySrsDue(Records.StudyItem item, long now) {
        if (item == null || item.dueAtMillis <= now || !sameLocalDay(item.dueAtMillis, now)) {
            return item;
        }
        long due = nextLocalDayStart(now);
        return new Records.StudyItem(
                item.kanji,
                item.state,
                due,
                item.stability,
                item.difficulty,
                item.totalReviews,
                item.lapses,
                item.learningStep,
                item.writingLevel,
                item.recognitionStage,
                item.consecutiveFailedRecognitionDays,
                item.lastFailedRecognitionDayMillis,
                item.writingRemediationPending,
                item.suppressedByTaskType,
                item.suppressedAtMillis,
                item.matureIntervalDays,
                item.answerSignature,
                item.activeToken,
                item.createdAtMillis,
                item.kanjiMeaningMemory,
                item.fontMeaningMemory,
                item.wordReadingMemory,
                item.writingRemediationMemory
        );
    }

    private boolean sameLocalDay(long leftMillis, long rightMillis) {
        Calendar left = Calendar.getInstance();
        left.setTimeInMillis(leftMillis);
        Calendar right = Calendar.getInstance();
        right.setTimeInMillis(rightMillis);
        return sameLocalDay(left, right);
    }

    private long nextLocalDayStart(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private HintState initialHintState(Records.StudySession session) {
        int stored = Math.max(0, Math.min(3, session.item.writingLevel));
        if ("targeted_writing".equals(session.taskType)
                || "writing_remediation".equals(session.taskType)
                || "similar_writing".equals(session.taskType)
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
                || "similar_writing".equals(session.taskType)
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

    private Records.StudyItem findStudyItem(List<Records.StudyItem> items, Records.LearningRepeat repeat) {
        if (repeat == null) {
            return null;
        }
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(repeat.kanji) && item.answerSignature.equals(repeat.answerSignature)) {
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
        content.addView(text("Tune FSRS retention, which Kiku cards enter practice, and when Kani reminds you.", 16, MUTED, false));
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
        content.addView(retentionSettingsPanel());
        content.addView(learningStepsSettingsPanel());
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
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(text("Daily workload", 23, INK, true));

        if (autoMode) {
            long now = System.currentTimeMillis();
            List<Records.DashboardRow> rows = store.activeDashboardRows();
            Records.AdaptiveLoadPlan plan = rows.isEmpty()
                    ? null
                    : adaptivePlan(rows, store.studyItems(), now);
            box.addView(text(autoWorkloadStatusText(plan), 17, TEAL, true));
            box.addView(text("Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule.", 15, MUTED, false));
            Button manual = secondaryButton("Use manual workload");
            manual.setOnClickListener(v -> {
                store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
                Toast.makeText(this, "Manual workload enabled.", Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(manual);
            return box;
        }

        TextView status = text(workloadStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule.", 15, MUTED, false));

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
            store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
            store.saveAdaptiveLoadWorkPercent(selected[0]);
            Toast.makeText(this, "Workload saved. Study uses the new adaptive focus.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        Button automatic = secondaryButton("Use automatic Pareto");
        automatic.setOnClickListener(v -> {
            store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO);
            Toast.makeText(this, "Automatic Pareto workload enabled.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(automatic);
        return box;
    }

    private LinearLayout learningStepsSettingsPanel() {
        Records.LearningStepSettings current = store.learningStepSettings();
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.addView(text("Learning steps", 23, INK, true));
        box.addView(text("New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer.", 15, MUTED, false));

        EditText newSteps = stepInput(current.newStepsText());
        EditText reviewSteps = stepInput(current.reviewStepsText());
        box.addView(text("New cards", 15, INK, true));
        box.addView(newSteps, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text("Review misses", 15, INK, true));
        box.addView(reviewSteps, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        Button ankiDefault = secondaryButton("Anki default");
        ankiDefault.setOnClickListener(v -> {
            Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.reviewStepsText());
        });
        presets.addView(ankiDefault, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button sameSteps = secondaryButton("Both 1m 10m");
        sameSteps.setOnClickListener(v -> {
            Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.newStepsText());
        });
        presets.addView(sameSteps, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(presets);

        Button save = primaryButton("Save learning steps", TEAL);
        save.setOnClickListener(v -> {
            List<Integer> parsedNew = Records.LearningStepSettings.tryParseSteps(newSteps.getText().toString());
            List<Integer> parsedReview = Records.LearningStepSettings.tryParseSteps(reviewSteps.getText().toString());
            if (parsedNew == null || parsedReview == null) {
                Toast.makeText(this, "Use steps like 1m, 10m, or 1h.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.saveLearningStepSettings(new Records.LearningStepSettings(parsedNew, parsedReview));
            Toast.makeText(this, "Learning steps saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private EditText stepInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private LinearLayout retentionSettingsPanel() {
        Records.SchedulerParameters current = store.schedulerParameters();
        final int[] selected = new int[]{retentionPercent(current.targetRetention)};
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        box.addView(text("FSRS retention", 23, INK, true));
        TextView status = text(retentionStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule.", 15, MUTED, false));

        SeekBar slider = new SeekBar(this);
        slider.setMax(17);
        slider.setProgress(selected[0] - 80);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 80 + progress;
                status.setText(retentionStatusText(selected[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        for (int value : new int[]{85, 90, 95}) {
            Button preset = secondaryButton(value + "%");
            preset.setOnClickListener(v -> {
                selected[0] = value;
                slider.setProgress(value - 80);
                status.setText(retentionStatusText(selected[0]));
            });
            quick.addView(preset, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        box.addView(quick);

        Button save = primaryButton("Save retention", TEAL);
        save.setOnClickListener(v -> {
            Records.SchedulerParameters latest = store.schedulerParameters();
            store.saveSchedulerParameters(new Records.SchedulerParameters(
                    selected[0] / 100.0,
                    latest.againMultiplier,
                    latest.hardMultiplier,
                    latest.goodMultiplier,
                    latest.easyMultiplier,
                    latest.lastAdjustedAtMillis,
                    latest.lastAdjustmentReviewCount
            ));
            Toast.makeText(this, "FSRS retention saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private int retentionPercent(double retention) {
        return Math.max(80, Math.min(97, (int) Math.round(retention * 100.0)));
    }

    private String retentionStatusText(int retentionPercent) {
        return "Desired retention: " + retentionPercent + "%";
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

    private String autoWorkloadStatusText(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Auto Pareto: waiting for problem kanji";
        }
        return "Auto Pareto: " + countText(plan.target, "kanji", "kanji") + " from today's drop-off";
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
                store.adaptiveLoadMode(),
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
        TextView chip = text(value, 13, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(panel(softened(color), color, dp(7)));
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
        button.setBackground(panel(color, color, dp(12)));
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
        button.setBackground(panel(Color.WHITE, Color.rgb(238, 189, 218), dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    private int softened(int color) {
        if (color == CORAL) {
            return Color.rgb(255, 235, 243);
        }
        if (color == TEAL) {
            return Color.rgb(230, 250, 251);
        }
        if (color == GOLD || color == Color.rgb(247, 159, 0)) {
            return Color.rgb(255, 247, 220);
        }
        if (color == BLUE || color == LILAC) {
            return Color.rgb(242, 238, 255);
        }
        return Color.rgb(248, 238, 245);
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
        Records.Example example = session == null ? null : wordReadingExample(session.row);
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
        if ("similar_writing".equals(task)) {
            return "Similar writing";
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

    private static final class KaniMascotView extends View {
        private final Bitmap logo;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect source = new Rect();
        private final RectF target = new RectF();

        private KaniMascotView(Context context) {
            super(context);
            logo = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_foreground);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (logo == null) {
                return;
            }
            int width = logo.getWidth();
            int height = logo.getHeight();
            source.set(Math.round(width * 0.14f), Math.round(height * 0.11f), Math.round(width * 0.86f), Math.round(height * 0.68f));
            target.set(0f, 0f, getWidth(), getHeight());
            canvas.drawBitmap(logo, source, target, paint);
        }
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
