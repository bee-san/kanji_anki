package dev.bee.kanjianki;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
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
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.TextUtil;
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
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final int BLUSH = Color.rgb(255, 239, 246);
    private static final int PINK_STROKE = Color.rgb(255, 174, 204);
    private static final int LILAC = Color.rgb(118, 72, 255);
    private static final int STUDY_BG = Color.rgb(255, 245, 250);
    private static final int STUDY_CARD = Color.rgb(255, 255, 255);
    private static final int STUDY_PANEL = Color.rgb(255, 236, 245);
    private static final int STUDY_PLUM = Color.rgb(75, 37, 82);
    private static final int STUDY_MUTED = Color.rgb(130, 96, 132);
    private static final int STUDY_PINK_DARK = Color.rgb(218, 58, 122);
    private static final int STUDY_BORDER = Color.rgb(255, 199, 222);
    private static final int STUDY_BG_SOFT = Color.rgb(255, 246, 251);
    private static final int STUDY_HERO_PANEL = Color.rgb(253, 241, 247);
    private static final int STUDY_HERO_PINK = Color.rgb(248, 45, 114);
    private static final int STUDY_HERO_PLUM = Color.rgb(33, 7, 44);
    private static final int STUDY_HERO_MUTED = Color.rgb(102, 82, 110);
    private static final long DAY_MILLIS = 86_400_000L;
    private static final String NAV_STUDY = "study";
    private static final String NAV_SETTINGS = "Settings";
    private static final String NAV_SETTINGS_ROUTE = "settings";
    private static final String LABEL_BACK_HOME = "Back home";
    private static final String LABEL_MEANING = "Meaning";
    private static final String LABEL_PRACTICE = "Practice";
    private static final String LABEL_STUDY_NOW = "Study now";
    private static final String RATING_AGAIN = "again";
    private static final String STATE_LEARNING = "learning";
    private static final String STATE_RETIRED = "retired";
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";
    private static final String TASK_FONT_MEANING = "font_meaning";

    private static final String TASK_TARGETED_WRITING = "targeted_writing";
    private static final String TASK_TYPING_MEANING = "typing_meaning";
    private static final String TASK_WORD_READING = "word_reading";

    private static final String EMPTY_ACTIVE_PRACTICE_TITLE = "No active practice yet";
    private static final String EMPTY_ACTIVE_PRACTICE_BODY = "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final HintProgression hintProgression = new HintProgression();
    private LocalStore store;
    private AnkiDroidGateway gateway;
    private LinearLayout content;
    private ScrollView contentScroll;
    private LinearLayout studyActionBar;
    private Records.StudySession activeSession;
    private Records.AdaptiveLoadPlan activeStudyPlan;
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
    private View flashcardCard;
    private View flashcardHeroPanel;
    private EditText typingAnswerInput;
    private WritingAnalysis activeAnalysis;
    private boolean checkingWriting;
    private boolean flashcardAnswerRevealed;
    private boolean flashcardTouchTracking;
    private boolean writingModelDownloaded;
    private boolean writingModelStatusKnown;
    private boolean continueAllKanjiSession;
    private int hintsUsed;
    private int currentPracticeLevel;
    private int sessionProgressCompleted;
    private int sessionProgressMax;
    private float flashcardTouchStartX;
    private float flashcardTouchStartY;
    private ActiveStudyTask activeStudyTask;
    private boolean activityPaused;
    private final Set<String> sessionCompletedTaskKeys = new HashSet<>();
    private final Set<String> sessionSeenTaskKeys = new HashSet<>();
    private HintState currentHintState = HintState.initial();
    private Map<String, StrokeGuide> strokeGuides;
    private WritingRecognizer writingRecognizer;
    private DictionaryLookup dictionaryLookup;
    private LocalStore.ReminderSettings pendingReminderSettings;
    private boolean settingsAnkiExpanded = true;
    private boolean settingsStudyExpanded;
    private boolean settingsSyncExpanded;
    private boolean settingsAppExpanded;
    private static AnkiDroidGateway ankiDroidGatewayForTests;
    private static CollectionGateway collectionGatewayForTests;
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
        DatabaseBackupScheduler.schedule(this);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onPause() {
        pauseActiveStudyTask();
        activityPaused = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityPaused = false;
        resumeActiveStudyTask();
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
            handlePostNotificationPermission(grantResults);
        }
    }

    private void handlePostNotificationPermission(int[] grantResults) {
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        LocalStore.ReminderSettings pending = pendingReminderSettings;
        if (granted) {
            saveGrantedReminderPermission(pending);
        } else {
            disableReminderAfterDeniedPermission(pending);
        }
        pendingReminderSettings = null;
        renderSettings();
    }

    private void saveGrantedReminderPermission(LocalStore.ReminderSettings pending) {
        LocalStore.ReminderSettings reminder = pending == null ? store.reminderSettings() : pending;
        store.saveReminderSettings(reminder);
        ReminderScheduler.schedule(this, reminder);
        if (ReminderScheduler.notificationsAllowed(this)) {
            Toast.makeText(this, "Reminder saved for around " + reminder.displayTime() + ".", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Reminder saved, but Android notifications are off.", Toast.LENGTH_LONG).show();
        }
    }

    private void disableReminderAfterDeniedPermission(LocalStore.ReminderSettings pending) {
        LocalStore.ReminderSettings fallback = pending == null ? store.reminderSettings() : pending;
        store.saveReminderSettings(new LocalStore.ReminderSettings(false, fallback.hour, fallback.minute));
        ReminderScheduler.cancel(this);
        Toast.makeText(this, "Notifications are off, so reminders are disabled.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleFlashcardGesture(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @SuppressWarnings("deprecation")
    private void base(String selected) {
        if (!NAV_STUDY.equals(selected)) {
            abandonActiveStudyTask();
        }
        flashcardGestureArea = null;
        flashcardCard = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        styleSystemBars();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAV_STUDY.equals(selected) ? STUDY_BG_SOFT : BG);
        setContentView(root);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));
        ScrollView scroll = new ScrollView(this);
        contentScroll = scroll;
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        studyActionBar = new LinearLayout(this);
        studyActionBar.setOrientation(LinearLayout.VERTICAL);
        studyActionBar.setPadding(dp(18), dp(10), dp(18), dp(8));
        studyActionBar.setBackgroundColor(STUDY_BG_SOFT);
        studyActionBar.setVisibility(View.GONE);
        root.addView(studyActionBar, new LinearLayout.LayoutParams(-1, -2));
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
            content.setPadding(dp(18), dp(18) + top, dp(18), dp(18) + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private void styleSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void renderHome() {
        base("home");
        long now = System.currentTimeMillis();
        LocalStore.SyncStatus sync = store.latestSync();
        StudyStatsStore.StudyStreak streak = store.studyStreak(now);
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
            View studyButton = homeStudyCta();
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
                emptyState(EMPTY_ACTIVE_PRACTICE_TITLE, EMPTY_ACTIVE_PRACTICE_BODY);
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
        TextView title = text("Kani", 48, INK, true);
        title.setLetterSpacing(0);
        copy.addView(title);
        copy.addView(text("Your AnkiDroid companion app to cure kanji blindness", 16, MUTED, true));
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

    private View homeMetricRow(LocalStore.SyncStatus sync, AnkiDroidGateway.ProviderStatus provider, StudyStatsStore.StudyStreak streak, Records.AdaptiveLoadPlan plan) {
        LinearLayout row = new EqualHeightRow(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(metricCard(
                R.drawable.ic_sync_24,
                TEAL,
                "Sync",
                homeSyncValue(sync),
                provider.canSync && sync != null && "success".equals(sync.status) ? "Up to date" : "Tap to sync",
                this::confirmSync
        ));
        row.addView(metricCard(
                R.drawable.ic_flame_24,
                streakAccent(streak),
                "Streak",
                streakHeadline(streak),
                streakMetricBody(streak),
                null
        ));
        row.addView(metricCard(
                R.drawable.ic_target_24,
                CORAL,
                "Focus",
                focusHeadline(plan),
                null,
                null
        ));
        return row;
    }

    private View metricCard(int iconRes, int accent, String label, String value, String body, Runnable action) {
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
            TextView bodyText = text(compact(body, 18), 11, MUTED, false);
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

    private View homeStudyCta() {
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
        TextView support = text("Start focused practice", 13, Color.rgb(255, 245, 250), true);
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

    private ImageView decorativeSparkle(int tint, int sizeDp) {
        ImageView sparkle = new ImageView(this);
        sparkle.setImageResource(R.drawable.ic_sparkle_24);
        sparkle.setColorFilter(tint);
        sparkle.setAlpha(0.9f);
        sparkle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sparkle.setMaxWidth(dp(sizeDp));
        sparkle.setMaxHeight(dp(sizeDp));
        return sparkle;
    }

    private View homeActionRow() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBaselineAligned(false);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setBaselineAligned(false);
        firstRow.addView(pillButton("Browse Kanji", R.drawable.ic_book_24, this::renderBrowseKanji));
        firstRow.addView(pillButton("Recent mistakes", R.drawable.ic_trending_24, this::renderRecentMistakes));
        firstRow.addView(pillButton("Stats", R.drawable.ic_stats_24, this::renderStats));
        column.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setBaselineAligned(false);
        secondRow.addView(pillButton(NAV_SETTINGS, R.drawable.ic_settings_24, this::renderSettings));
        column.addView(secondRow);

        return column;
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
        return plan.remaining + " items left / " + plan.target;
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

    private View fullWidthHomeButton() {
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
        TextView text = text("Home", 15, INK, true);
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
            emptyState(EMPTY_ACTIVE_PRACTICE_TITLE, EMPTY_ACTIVE_PRACTICE_BODY);
            return;
        }
        for (QueueEntry entry : entries) {
            content.addView(queueRowView(entry, now));
        }
    }

    private void renderRecentMistakes() {
        base("home");
        content.addView(homeSectionHeader("Recent mistakes", "Home", this::renderHome));
        List<StudyStatsStore.RecentMistake> mistakes = store.recentMistakes(12);
        if (mistakes.isEmpty()) {
            emptyState("No recent mistakes yet", "Missed and hard reviews will show here after you study.");
            return;
        }
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        for (StudyStatsStore.RecentMistake mistake : mistakes) {
            content.addView(recentMistakeRow(mistake, findRow(rows, mistake.kanji)));
        }
    }

    private View recentMistakeRow(StudyStatsStore.RecentMistake mistake, Records.DashboardRow row) {
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

    private String streakHeadline(StudyStatsStore.StudyStreak streak) {
        if (streak.currentDays <= 0) {
            return "No streak yet";
        }
        return streak.currentDays + "-day streak";
    }

    private int streakAccent(StudyStatsStore.StudyStreak streak) {
        return streak != null && streak.studiedToday ? Color.rgb(247, 159, 0) : Color.rgb(160, 160, 166);
    }

    private String streakMetricBody(StudyStatsStore.StudyStreak streak) {
        if (streak != null && streak.studiedToday) {
            return streak.bestDays > 0 ? "Best: " + streakDayCount(streak.bestDays) : "Done today";
        }
        return "Not done today";
    }

    private String humanSyncTime(long timestampMillis) {
        return UiDateText.humanSyncTime(timestampMillis);
    }

    private String streakDayCount(int days) {
        return days + " " + (days == 1 ? "day" : "days");
    }

    private void confirmSync() {
        Records.Settings current = settings();
        new AlertDialog.Builder(this)
                .setTitle("Sync AnkiDroid?")
                .setMessage("Kani will read your " + current.modelName + " cards, copy problem kanji into writing practice, and mark imported suspended cards as archived after they are stored safely.")
                .setPositiveButton("Sync cards", (dialog, which) -> runSync())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runSync() {
        base("home");
        content.addView(text("Syncing AnkiDroid", 34, INK, true));
        SyncProgressPanel progressView = new SyncProgressPanel(this);
        content.addView(progressView);
        CollectionGateway syncGateway = collectionGatewayForTests == null ? gateway : collectionGatewayForTests;
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
            main.post(() -> renderSyncResult(result));
        });
    }

    private void renderSyncResult(ManualSyncEngine.SyncResult result) {
        base("home");
        if (result.skipped) {
            renderSkippedSyncResult(result);
        } else if (result.success) {
            renderSuccessfulSyncResult(result);
        } else {
            renderFailedSyncResult(result);
        }
    }

    private void renderSkippedSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text("Sync already running", 34, INK, true));
        LinearLayout info = band(BLUE);
        info.addView(text(nonEmptyOr(result.message, "Kani is already reading AnkiDroid."), 17, Color.WHITE, false));
        content.addView(info);
        addBackHomeButton();
    }

    private void renderSuccessfulSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text("Sync complete", 34, INK, true));
        LinearLayout summary = band(TEAL);
        long now = System.currentTimeMillis();
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        List<Records.StudyItem> items = store.studyItems();
        Records.AdaptiveLoadPlan plan = adaptivePlan(rows, items, now);
        List<QueueEntry> entries = queuedEntries(rows, items, now, plan);
        summary.addView(text(countText(entries.size(), "kanji ready to study", "kanji ready to study"), 24, Color.WHITE, true));
        summary.addView(text(countText(result.dashboardRows, "candidate found from Anki", "candidates found from Anki") + ". " + adaptiveFocusText(plan) + ".", 16, Color.WHITE, false));
        addOptionalSyncSummaryLines(summary, result);
        content.addView(summary);
        if (result.dashboardRows > 0) {
            Button study = primaryButton(LABEL_STUDY_NOW, CORAL);
            study.setOnClickListener(v -> startFocusedStudy());
            content.addView(study);
        }
        addBackHomeButton();
    }

    private void addOptionalSyncSummaryLines(LinearLayout summary, ManualSyncEngine.SyncResult result) {
        if (!result.adaptiveSummary.isEmpty()) {
            summary.addView(text(result.adaptiveSummary, 15, Color.WHITE, false));
        }
        if (result.importedSuspendedKanji > 0) {
            summary.addView(text(countText(result.importedSuspendedKanji, "new archived suspended kanji added", "new archived suspended kanji added"), 15, Color.WHITE, false));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summary.addView(text(result.message, 14, Color.WHITE, false));
        }
    }

    private void renderFailedSyncResult(ManualSyncEngine.SyncResult result) {
        content.addView(text("Sync needs attention", 34, INK, true));
        LinearLayout error = band(CORAL);
        error.addView(text("Could not read AnkiDroid", 24, Color.WHITE, true));
        error.addView(text(nonEmptyOr(result.message, "Try again after checking AnkiDroid permissions."), 16, Color.WHITE, false));
        content.addView(error);
        Button retry = primaryButton("Try sync again", TEAL);
        retry.setOnClickListener(v -> confirmSync());
        content.addView(retry);
        addBackHomeButton();
    }

    private void addBackHomeButton() {
        Button home = secondaryButton(LABEL_BACK_HOME);
        home.setOnClickListener(v -> renderHome());
        content.addView(home);
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    private void renderStats() {
        base("stats");
        StudyStatsStore.KaniOutcomeStats stats = store.kaniOutcomeStats();
        StudyStatsStore.StudyTaskTimeStats studyTime = store.studyTaskTimeStats(System.currentTimeMillis());
        content.addView(fullWidthHomeButton());
        content.addView(text("Stats", 34, INK, true));
        content.addView(statsVerdictPanel(stats));
        content.addView(text("Kani does not replace Anki. It repairs weak kanji from your Anki reviews, then shows whether Anki evidence caught up afterward.", 16, MUTED, false));
        addSpace(10);

        content.addView(outcomePanel(
                "Weakness Burn-Down",
                countText(stats.weakKanjiImproved.improvedCount, "weak kanji improved", "weak kanji improved"),
                weaknessImprovementBody(stats.weakKanjiImproved),
                weaknessImprovementExamples(stats.weakKanjiImproved),
                TEAL
        ));
        content.addView(outcomePanel(
                "Anki Support Conversion",
                countText(stats.matureSupportGained.matureSupportGained, "mature card gained", "mature cards gained"),
                countText(stats.matureSupportGained.firstSupportCount, "kanji gained first mature support", "kanji gained first mature support") + ".",
                supportGainExamples(stats.matureSupportGained),
                BLUE
        ));
        content.addView(ladderHealthPanel(stats.ladderHealth));
        content.addView(studyTimePanel(studyTime));
    }

    private LinearLayout statsVerdictPanel(StudyStatsStore.KaniOutcomeStats stats) {
        boolean working = stats != null
                && (stats.weakKanjiImproved.improvedCount > 0 || stats.matureSupportGained.matureSupportGained > 0);
        boolean hasLadder = stats != null && stats.ladderHealth.totalActiveItems > 0;
        int stroke = working ? TEAL : hasLadder ? GOLD : Color.rgb(178, 178, 186);
        int background = working ? Color.rgb(238, 252, 250) : hasLadder ? Color.rgb(255, 250, 226) : Color.rgb(246, 246, 248);
        LinearLayout box = panelBox(background, stroke);
        box.addView(text(working ? "Kani is working for you" : "Kani is not currently working for you", 24, working ? TEAL : MUTED, true));
        box.addView(text(statsVerdictBody(stats, working, hasLadder), 15, working ? INK : MUTED, false));
        return box;
    }

    private String statsVerdictBody(StudyStatsStore.KaniOutcomeStats stats, boolean working, boolean hasLadder) {
        if (stats == null) {
            return "No Kani evidence is available yet. Study weak kanji, then sync AnkiDroid so this page can compare before and after.";
        }
        StudyStatsStore.LadderHealthMetric ladder = stats.ladderHealth;
        if (working) {
            List<String> signals = new ArrayList<>();
            if (stats.weakKanjiImproved.improvedCount > 0) {
                signals.add(countText(stats.weakKanjiImproved.improvedCount, "weak kanji is burning down", "weak kanji are burning down"));
            }
            if (stats.matureSupportGained.matureSupportGained > 0) {
                signals.add(countText(stats.matureSupportGained.matureSupportGained, "mature Anki card has been gained", "mature Anki cards have been gained"));
            }
            if (ladder.promotionReadyCount > 0) {
                signals.add(countText(ladder.promotionReadyCount, "review-phase item is ready to climb", "review-phase items are ready to climb"));
            }
            String body = String.join(". ", signals) + ".";
            if (ladder.demotionRiskCount > 0) {
                body += " Watch " + countText(ladder.demotionRiskCount, "review-phase item with a miss streak", "review-phase items with miss streaks") + ".";
            }
            return body;
        }
        if (hasLadder) {
            return "Kani is tracking "
                    + countText(ladder.totalActiveItems, "active kanji", "active kanji")
                    + ", but no weakness burn-down or mature Anki support conversion has landed yet. Study due reviews, then sync AnkiDroid.";
        }
        return "No before-and-after evidence yet. Do Kani reviews, then sync AnkiDroid so this page can compare weak kanji and mature support.";
    }

    private LinearLayout studyTimePanel(StudyStatsStore.StudyTaskTimeStats stats) {
        LinearLayout box = panelBox(Color.WHITE, CORAL);
        box.addView(text("Answered study time", 18, MUTED, true));
        box.addView(text("Today: " + formatStudyTime(stats.todayMillis), 24, INK, true));
        box.addView(text("Last 7 days: " + formatStudyTime(stats.lastSevenDaysMillis), 16, MUTED, false));
        box.addView(text("Answered tasks: " + stats.answeredTasks, 16, MUTED, false));
        box.addView(text("Avg / task: " + formatStudyTime(stats.averageMillisPerTask()), 16, MUTED, false));
        return box;
    }

    private LinearLayout outcomePanel(String title, String value, String body, List<String> examples, int stroke) {
        LinearLayout box = statPanel(title, value, body, stroke);
        for (String example : examples) {
            box.addView(text(example, 17, INK, true));
        }
        return box;
    }

    private LinearLayout ladderHealthPanel(StudyStatsStore.LadderHealthMetric metric) {
        LinearLayout box = statPanel(
                "Ladder Health",
                countText(metric.totalActiveItems, "active kanji on the ladder", "active kanji on the ladder"),
                ladderHealthBody(metric),
                GOLD
        );
        for (String row : ladderDistributionRows(metric)) {
            box.addView(text(row, 16, INK, false));
        }
        return box;
    }

    private String ladderHealthBody(StudyStatsStore.LadderHealthMetric metric) {
        if (metric.totalActiveItems == 0) {
            return "No active ladder items yet. Sync AnkiDroid or study imported weak kanji to fill the ladder.";
        }
        String body = countText(metric.promotionReadyCount, "promotion-ready review item", "promotion-ready review items")
                + " · "
                + countText(metric.demotionRiskCount, "demotion-risk review item", "demotion-risk review items");
        if (metric.demotionReadyCount > 0) {
            body += " · " + countText(metric.demotionReadyCount, "at the demotion threshold", "at the demotion threshold");
        }
        return body + ". Threshold: " + metric.realDueReviewsToMove + " real due reviews.";
    }

    private List<String> ladderDistributionRows(StudyStatsStore.LadderHealthMetric metric) {
        List<String> rows = new ArrayList<>();
        for (Records.LadderRung rung : Records.LadderRung.values()) {
            rows.add(ladderRungLabel(rung) + ": " + metric.countFor(rung));
        }
        return rows;
    }

    private String ladderRungLabel(Records.LadderRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> "Write kanji";
            case TYPE_MEANING -> "Type meaning";
            case SIMILAR_KANJI -> "Similar kanji";
            case KANJI_MEANING -> "Kanji meaning";
            case FONT_MEANING -> "Font meaning";
            case WORD_READING -> "Word reading";
        };
    }

    private String weaknessImprovementBody(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        if (metric.improvedCount == 0) {
            return "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync.";
        }
        return "Average weakness: "
                + formatWeakness(metric.averageBeforeWeakness)
                + " -> "
                + formatWeakness(metric.averageAfterWeakness)
                + " after Kani practice.";
    }

    private List<String> weaknessImprovementExamples(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        List<String> examples = new java.util.ArrayList<>();
        int maxExamples = Math.min(3, metric.examples.size());
        for (int i = 0; i < maxExamples; i++) {
            StudyStatsStore.KanjiImprovement example = metric.examples.get(i);
            examples.add(example.kanji + "  " + formatWeakness(example.beforeWeakness) + " -> " + formatWeakness(example.afterWeakness));
        }
        return examples;
    }

    private List<String> supportGainExamples(StudyStatsStore.MatureSupportGainedMetric metric) {
        List<String> examples = new java.util.ArrayList<>();
        for (StudyStatsStore.KanjiSupportGain example : metric.examples) {
            examples.add(example.kanji + "  " + example.beforeMatureSupport + " -> " + example.afterMatureSupport + " mature cards");
        }
        return examples;
    }

    private String formatWeakness(double weakness) {
        return String.format(java.util.Locale.ROOT, "%.2f", weakness);
    }

    private String formatStudyTime(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return seconds + " sec";
        }
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes < 60L) {
            return remainingSeconds == 0L ? minutes + " min" : minutes + " min " + remainingSeconds + " sec";
        }
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        return remainingMinutes == 0L ? hours + " hr" : hours + " hr " + remainingMinutes + " min";
    }

    private LinearLayout statPanel(String title, String value, String body, int stroke) {
        LinearLayout box = panelBox(Color.WHITE, stroke);
        box.addView(text(title, 18, MUTED, true));
        box.addView(text(value, 25, INK, true));
        box.addView(text(body, 15, MUTED, false));
        return box;
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
        seeded = store.annotateSimilarKanjiAvailability(seeded);
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

    private int stateRank(String state) {
        if (STATE_LEARNING.equals(state)) {
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
        if (STATE_LEARNING.equals(item.state)) {
            return BLUE;
        }
        return Color.rgb(246, 202, 225);
    }

    private String dueText(long dueAt, long now) {
        return UiDateText.dueText(dueAt, now);
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
        if (item.phase == Records.SchedulerPhase.RELEARNING) {
            chips.addView(chip("relearning", CORAL));
        } else if (item.phase == Records.SchedulerPhase.NEW_LEARNING && item.totalReviews > 0) {
            chips.addView(chip("learning", TEAL));
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
        switch (item.rung) {
            case WRITE_KANJI:
                return "write kanji";
            case TYPE_MEANING:
                return "type meaning";
            case SIMILAR_KANJI:
                return "similar kanji";
            case FONT_MEANING:
                return "font -> meaning";
            case WORD_READING:
                return "word -> reading";
            case KANJI_MEANING:
            default:
                return "kanji -> meaning";
        }
    }

    private String sourceEvidenceText(Records.DashboardRow row) {
        String active = "";
        String suspended = "";
        for (Records.Example example : row.examples) {
            if (active.isEmpty() && SOURCE_ACTIVE.equals(example.sourceType)) {
                active = example.expression;
            } else if (suspended.isEmpty() && SOURCE_SUSPENDED.equals(example.sourceType)) {
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
        content.addView(fullWidthHomeButton());
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
            content.addView(browseKanjiRow(item));
        }
    }

    private View browseKanjiRow(Records.KanjiInventoryItem item) {
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
            content.addView(fullWidthHomeButton());
            emptyState("Kanji not found", "This row may have disappeared after a sync.");
            return;
        }
        String displayKanji = detailDisplayKanji(kanji, row, inventory);
        addDetailHeader(displayKanji, fromBrowse);
        boolean suspended = inventory != null && inventory.suspended;
        addDetailIdentity(row, inventory, suspended);
        addSpace(10);
        content.addView(detailReasonPanel(row, inventory));
        if (inventory != null) {
            content.addView(localInventoryPanel(inventory));
        }
        addDetailActions(row, inventory, displayKanji, fromBrowse, suspended);
        addSpace(12);
        addRecoveryTimeline(timeline);
        if (row != null) {
            addDetailExamples(row);
        }
    }

    private String detailDisplayKanji(String fallback, Records.DashboardRow row, Records.KanjiInventoryItem inventory) {
        if (row != null) {
            return row.kanji;
        }
        return inventory == null ? fallback : inventory.kanji;
    }

    private void addDetailHeader(String displayKanji, boolean fromBrowse) {
        if (!fromBrowse) {
            content.addView(fullWidthHomeButton());
        }
        TextView glyph = text(displayKanji, 92, INK, true);
        glyph.setGravity(Gravity.CENTER);
        content.addView(glyph);
        if (fromBrowse) {
            Button back = secondaryButton("Back to Browse Kanji");
            back.setOnClickListener(v -> renderBrowseKanji(""));
            content.addView(back);
        }
    }

    private void addDetailIdentity(Records.DashboardRow row, Records.KanjiInventoryItem inventory, boolean suspended) {
        if (suspended) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip("SUSPENDED", CORAL));
            content.addView(chips);
        }
        if (row == null) {
            content.addView(text(inventoryTitle(inventory), 25, INK, true));
            if (inventory != null && !inventory.readings.isEmpty()) {
                content.addView(text(inventory.readings, 20, TEAL, true));
            }
        } else {
            content.addView(text(rowMeaning(row), 25, INK, true));
            content.addView(text(row.reading, 20, TEAL, true));
        }
    }

    private String inventoryTitle(Records.KanjiInventoryItem inventory) {
        if (inventory == null || inventory.primaryMeaning.isEmpty()) {
            return "Historical recovery";
        }
        return inventory.primaryMeaning;
    }

    private LinearLayout detailReasonPanel(Records.DashboardRow row, Records.KanjiInventoryItem inventory) {
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
        return why;
    }

    private void addDetailActions(Records.DashboardRow row, Records.KanjiInventoryItem inventory, String displayKanji, boolean fromBrowse, boolean suspended) {
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
    }

    private void addDetailExamples(Records.DashboardRow row) {
        addSpace(12);
        content.addView(sectionTitle("Examples"));
        for (Records.Example example : row.examples) {
            content.addView(exampleView(example));
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
        if (v instanceof Button button) {
            button.setText(R.string.copied_anki_search);
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
        if (item != null && STATE_RETIRED.equals(item.state)) {
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
        if (item != null && STATE_RETIRED.equals(item.state)) {
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
        if ("review_passed".equals(eventType) || "support_improved".equals(eventType) || STATE_RETIRED.equals(eventType)) {
            return TEAL;
        }
        return BLUE;
    }

    private String timelineDate(long occurredAt) {
        return UiDateText.timelineDate(occurredAt);
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
        int color = SOURCE_SUSPENDED.equals(example.sourceType) ? CORAL : TEAL;
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
        LinearLayout box = softInsetPanel();
        box.addView(text("Reference", 19, STUDY_PLUM, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 72, STUDY_PLUM, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            addStudyCueLines(details, session);
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
            if (SOURCE_ACTIVE.equals(example.sourceType)) {
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
            if (SOURCE_SUSPENDED.equals(example.sourceType)) {
                return example;
            }
            if (active == null && SOURCE_ACTIVE.equals(example.sourceType)) {
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
        base(NAV_STUDY);
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        long now = System.currentTimeMillis();
        activeStudyPlan = rows.isEmpty() ? null : studyPlanForMode(rows, store.studyItems(), now);
        initializeSessionProgressTarget(activeStudyPlan);
        if (studyRunAtHardCap()) {
            renderStudyRunDone(activeStudyPlan);
            return;
        }
        if (rows.isEmpty()) {
            renderEmptyStudyQueue(now);
            return;
        }
        List<Records.StudyItem> beforeSeed = store.studyItems();
        Records.AdaptiveLoadPlan plan = studyPlanForMode(rows, beforeSeed, now);
        List<Records.StudyItem> seeded = studyQueue(rows, now, true, plan);
        Records.AdaptiveLoadPlan seededPlan = studyPlanForMode(rows, seeded, now);
        activeStudyPlan = seededPlan;
        initializeSessionProgressTarget(seededPlan);
        if (studyRunAtHardCap()) {
            renderStudyRunDone(seededPlan);
            return;
        }
        activeSession = nextActiveSession(rows, seeded, seededPlan, now);
        if (activeSession == null) {
            renderNoStudySession(rows, seededPlan, now);
            return;
        }
        store.saveStudyItem(activeSession.item);
        String taskKey = sessionTaskKey(activeSession);
        registerStudyTaskShown(taskKey);
        startActiveStudyTask(taskKey, activeSession.item.kanji, activeSession.taskType, now);
        renderSession(activeSession);
    }

    private void renderEmptyStudyQueue(long now) {
        prepareStudyContent(activeStudyPlan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Study practice", 32, STUDY_PLUM, true));
        card.addView(text("Nothing to study yet", 22, STUDY_PLUM, true));
        card.addView(text("Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.", 16, STUDY_MUTED, false));
        content.addView(card);
    }

    private Records.StudySession nextActiveSession(List<Records.DashboardRow> rows, List<Records.StudyItem> seeded, Records.AdaptiveLoadPlan plan, long now) {
        Set<String> focus = continueAllKanjiSession || plan.allKanjiMode ? null : new HashSet<>(plan.focusKanji);
        return new BridgeScheduler().nextSession(seeded, rows, now, focus);
    }

    private void renderNoStudySession(List<Records.DashboardRow> rows, Records.AdaptiveLoadPlan seededPlan, long now) {
        if (!continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan);
            return;
        }
        prepareStudyContent(seededPlan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Nothing due now", 32, STUDY_PLUM, true));
        card.addView(text("Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.", 17, STUDY_MUTED, false));
        Button back = pinkPrimaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> renderHome());
        card.addView(back);
        content.addView(card);
    }

    private void renderFocusDone(Records.AdaptiveLoadPlan plan) {
        prepareStudyContent(plan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Today's focus done", 32, STUDY_PLUM, true));
        card.addView(text("Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji.", 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text("Today's focus: 0 items left / " + plan.target, 20, STUDY_PLUM, true));
        summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        card.addView(summary);
        Button keepGoing = pinkPrimaryButton("Continue all kanji");
        keepGoing.setOnClickListener(v -> {
            continueAllKanjiSession = true;
            renderStudy();
        });
        card.addView(keepGoing);
        Button back = studySecondaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> {
            continueAllKanjiSession = false;
            renderHome();
        });
        card.addView(back);
        content.addView(card);
    }

    private void renderStudyRunDone(Records.AdaptiveLoadPlan plan) {
        prepareStudyContent(plan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Today's focus done", 32, STUDY_PLUM, true));
        card.addView(text("Kani finished the Study now set. You can stop here, or explicitly continue through all current problem kanji.", 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text("Study now: " + sessionProgressCompleted + " / " + Math.max(1, sessionProgressMax), 20, STUDY_PLUM, true));
        if (plan != null && !plan.status.isEmpty()) {
            summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        }
        card.addView(summary);
        Button keepGoing = pinkPrimaryButton("Continue all kanji");
        keepGoing.setOnClickListener(v -> {
            continueAllKanjiSession = true;
            renderStudy();
        });
        card.addView(keepGoing);
        Button back = studySecondaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> {
            continueAllKanjiSession = false;
            renderHome();
        });
        card.addView(back);
        content.addView(card);
    }

    private void startFocusedStudy() {
        continueAllKanjiSession = false;
        resetStudyRunProgress();
        renderStudy();
    }

    private void renderStudyForKanji(String kanji) {
        resetStudyRunProgress();
        base(NAV_STUDY);
        List<Records.DashboardRow> rows = store.activeDashboardRows();
        long now = System.currentTimeMillis();
        activeStudyPlan = rows.isEmpty() ? null : adaptivePlan(rows, store.studyItems(), now);
        Records.DashboardRow row = findRow(rows, kanji);
        if (row == null) {
            prepareStudyContent(activeStudyPlan, false);
            LinearLayout card = softStudyCard();
            card.addView(modePill(LABEL_PRACTICE));
            card.addView(text("Study practice", 32, STUDY_PLUM, true));
            card.addView(text("Kanji not available", 22, STUDY_PLUM, true));
            card.addView(text("This row may have changed after sync.", 16, STUDY_MUTED, false));
            content.addView(card);
            return;
        }
        List<Records.StudyItem> seeded = studyQueue(rows, now, true);
        activeStudyPlan = adaptivePlan(rows, seeded, now);
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
        String token = StudyTokenFactory.studyItem(item.kanji, item.activeToken);
        String taskType = rungTaskType(item);
        activeSession = new Records.StudySession(
                item.withToken(token),
                row,
                token,
                taskType,
                item.rung == Records.LadderRung.WRITE_KANJI,
                row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning
        );
        store.saveStudyItem(activeSession.item);
        String taskKey = sessionTaskKey(activeSession);
        registerStudyTaskShown(taskKey);
        startActiveStudyTask(taskKey, activeSession.item.kanji, activeSession.taskType, now);
        renderSession(activeSession);
    }

    private String rungTaskType(Records.StudyItem item) {
        return item.rung.wireName();
    }

    private String taskTypeForStudyItem(Records.StudyItem item) {
        return item.rung.wireName();
    }

    private void renderSession(Records.StudySession session) {
        if (session.writingRequired) {
            renderWritingSession(session);
        } else if (BridgeScheduler.TASK_SIMILAR_KANJI.equals(session.taskType)) {
            renderSimilarKanjiSession(session);
        } else {
            renderFlashcardSession(session);
        }
    }

    private void renderSimilarKanjiSession(Records.StudySession session) {
        prepareStudyContent(activeStudyPlan, true);
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardGestureArea = null;
        typingAnswerInput = null;
        drawingPad = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

        List<String> choices = buildSimilarKanjiChoices(session.item.kanji);
        if (choices.size() < 2) {
            // Not enough similar kanji to show a choice — fall back to
            // standard flashcard for this card.
            renderFlashcardSession(session);
            return;
        }
        Collections.shuffle(choices);

        LinearLayout cardShell = softStudyCard();
        cardShell.addView(modePill("Recognise"));
        cardShell.addView(text("Choose the kanji", 30, STUDY_PLUM, true));
        cardShell.addView(text("Similar kanji", 16, STUDY_PINK_DARK, true));
        cardShell.addView(text("Pick the kanji that matches the meaning.", 15, STUDY_MUTED, false));
        LinearLayout box = softInsetPanel();
        String meaning = session.row != null ? session.row.primaryMeaning : "";
        box.addView(text("Which kanji means " + meaning + "?", 22, STUDY_PLUM, true));
        box.addView(similarKanjiGrid(choices, session.item.kanji));
        cardShell.addView(box);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, dp(6), 0, dp(12));
        content.addView(cardShell, cardLp);
    }

    private List<String> buildSimilarKanjiChoices(String targetKanji) {
        List<Records.SimilarKanjiPair> pairs = store.similarPairsForKanji(targetKanji);
        Set<String> choices = new LinkedHashSet<>();
        choices.add(targetKanji);
        for (Records.SimilarKanjiPair pair : pairs) {
            String other = pair.kanjiA.equals(targetKanji) ? pair.kanjiB : pair.kanjiA;
            choices.add(other);
            if (choices.size() >= 4) {
                break;
            }
        }
        return new ArrayList<>(choices);
    }

    private View similarKanjiGrid(List<String> choices, String correctKanji) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < choices.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String glyph = choices.get(i);
            Button button = studySecondaryButton(glyph);
            button.setTextColor(STUDY_PLUM);
            button.setTextSize(34);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(20)));
            button.setOnClickListener(v -> {
                boolean correct = glyph.equals(correctKanji);
                submitReview(correct ? "good" : "again", false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            if (row != null) {
                row.addView(button, lp);
            }
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

    private void renderFlashcardSession(Records.StudySession session) {
        prepareStudyContent(activeStudyPlan, true);
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        typingAnswerInput = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        drawingPad = null;
        flashcardHeroPanel = null;

        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

        LinearLayout card = recognitionHeroCard(session);
        flashcardCard = card;
        flashcardGestureArea = card;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, 0, 0, dp(14));
        content.addView(card, cardLp);
        buildFlashcardActionBar(false);
    }

    private LinearLayout recognitionHeroCard(Records.StudySession session) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(panel(Color.WHITE, Color.TRANSPARENT, dp(32)));
        card.setElevation(dp(8));
        card.setClickable(true);
        card.setFocusable(true);

        card.addView(recognitionPill(studyModeLabel(session)));

        TextView title = text(flashcardTitle(session), 21, STUDY_HERO_PLUM, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(14), 0, 0);
        card.addView(title, titleLp);

        TextView question = text(heroQuestion(session), 27, STUDY_HERO_PLUM, true);
        question.setGravity(Gravity.CENTER);
        question.setIncludeFontPadding(false);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(-1, -2);
        questionLp.setMargins(0, dp(8), 0, 0);
        card.addView(question, questionLp);

        TextView hiddenHint = text("Answer hidden until reveal", 14, STUDY_HERO_MUTED, false);
        hiddenHint.setGravity(Gravity.CENTER);
        hiddenHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(6), 0, 0);
        card.addView(hiddenHint, hintLp);

        flashcardHeroPanel = heroKanjiPanel(session);
        card.addView(flashcardHeroPanel);

        if (isTypingMeaningTask(session)) {
            TextView label = text(LABEL_MEANING, 15, STUDY_HERO_MUTED, true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
            labelLp.setMargins(0, dp(14), 0, dp(8));
            card.addView(label, labelLp);
            card.addView(typingAnswerField());
        }

        studyAnswerPanel = flashcardAnswerPanel(session);
        studyAnswerPanel.setVisibility(View.GONE);
        card.addView(studyAnswerPanel);

        return card;
    }

    private String heroQuestion(Records.StudySession session) {
        if (isWordReadingTask(session)) {
            return "What is the reading?";
        }
        return "What does this kanji mean?";
    }

    private View recognitionPill(String label) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), 0, dp(18), 0);
        pill.setMinimumHeight(dp(44));
        pill.setBackground(panel(Color.rgb(253, 239, 246), Color.TRANSPARENT, dp(24)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_eye_24);
        icon.setColorFilter(STUDY_HERO_PINK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMargins(0, 0, dp(10), 0);
        pill.addView(icon, iconLp);

        TextView text = text(label, 18, STUDY_HERO_PINK, true);
        text.setIncludeFontPadding(false);
        pill.addView(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        pill.setLayoutParams(lp);
        return pill;
    }

    private View heroKanjiPanel(Records.StudySession session) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(panel(STUDY_HERO_PANEL, STUDY_BORDER, dp(28)));
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView glyph = text(
                isWordReadingTask(session) ? wordPrompt(session) : session.item.kanji,
                isWordReadingTask(session) ? 44 : 116,
                STUDY_HERO_PLUM,
                true
        );
        if (isFontRecognitionTask(session)) {
            glyph.setTypeface(randomFontVariantTypeface(), Typeface.BOLD);
        } else {
            glyph.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        glyph.setGravity(Gravity.CENTER);
        glyph.setIncludeFontPadding(false);
        panel.addView(glyph, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(210));
        lp.setMargins(0, dp(16), 0, 0);
        panel.setLayoutParams(lp);
        return panel;
    }

    private Typeface randomFontVariantTypeface() {
        return StudyFontVariants.random(this);
    }

    private void renderWritingSession(Records.StudySession session) {
        prepareStudyContent(activeStudyPlan, false);
        activeAnalysis = null;
        checkingWriting = false;
        flashcardGestureArea = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        typingAnswerInput = null;
        hintsUsed = 0;
        setHintState(initialHintState(session));

        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Draw this kanji", 30, STUDY_PLUM, true));
        card.addView(text(labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
        if (session.row != null) {
            if (isRecallTask(session)) {
                card.addView(text("Prompt: " + sessionClue(session), 17, STUDY_PLUM, true));
                if (!session.row.reading.isEmpty()) {
                    card.addView(text("Reading: " + session.row.reading, 15, STUDY_MUTED, false));
                }
                card.addView(text("Write the kanji from this prompt. The answer stays hidden until you check.", 15, STUDY_MUTED, false));
            } else {
                card.addView(text("Learn it from the reference, trace it, then check.", 15, STUDY_MUTED, false));
            }
        } else {
            card.addView(text(session.prompt, 17, STUDY_MUTED, false));
        }
        studyAnswerPanel = learningPanel(session);
        card.addView(studyAnswerPanel);

        TextView writingTitle = sectionTitle("Writing");
        writingTitle.setTextColor(STUDY_PLUM);
        card.addView(writingTitle);
        StrokeGuide guide = strokeGuide(session.item.kanji);
        studyStatus = text(guideLabel(currentHintState, guide), 16, STUDY_MUTED, false);
        card.addView(studyStatus);
        drawingPad = new DrawingPadView(this);
        drawingPad.setTarget(session.item.kanji);
        drawingPad.setInkEditListener(this::handleDrawingEdited);
        drawingPad.setGuide(guide, currentHintState, false);
        LinearLayout padShell = softInsetPanel();
        padShell.setPadding(dp(8), dp(8), dp(8), dp(8));
        padShell.addView(drawingPad, new LinearLayout.LayoutParams(-1, studyPadHeight()));
        card.addView(padShell);
        content.addView(card);

        buildStudyActionBar();
        updateResultActions();
        refreshWritingModelStatus();
    }

    private void resetStudyRunProgress() {
        sessionProgressCompleted = 0;
        sessionProgressMax = 0;
        sessionCompletedTaskKeys.clear();
        sessionSeenTaskKeys.clear();
    }

    private void markStudyRunPassed(String kanji) {
        if (activeSession != null) {
            markStudyTaskCompleted(sessionTaskKey(activeSession));
            return;
        }
        if (kanji != null && !kanji.isEmpty()) {
            markStudyTaskCompleted("kanji:" + kanji);
        }
    }

    private void initializeSessionProgressTarget(Records.AdaptiveLoadPlan plan) {
        if (sessionProgressMax <= 0 && plan != null) {
            sessionProgressMax = Math.max(0, plan.remaining > 0 ? plan.remaining : plan.target);
        }
    }

    private boolean studyRunAtHardCap() {
        return !continueAllKanjiSession && sessionProgressMax > 0 && sessionProgressCompleted >= sessionProgressMax;
    }

    private void registerStudyTaskShown(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        sessionSeenTaskKeys.add(key);
        if (sessionProgressMax <= 0) {
            sessionProgressMax = 1;
        }
    }

    private void markStudyTaskCompleted(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        registerStudyTaskShown(key);
        if (sessionCompletedTaskKeys.add(key)) {
            sessionProgressCompleted++;
            sessionProgressMax = Math.max(sessionProgressMax, sessionProgressCompleted);
        }
    }

    private String sessionTaskKey(Records.StudySession session) {
        if (session == null) {
            return "";
        }
        return "session:" + session.taskType + ":" + session.item.kanji + ":" + session.token;
    }

    private void startActiveStudyTask(String key, String kanji, String taskType, long startedAt) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (activeStudyTask != null && key.equals(activeStudyTask.taskKey)) {
            return;
        }
        activeStudyTask = new ActiveStudyTask(key, kanji, taskType, startedAt);
        if (!activityPaused) {
            activeStudyTask.resume(SystemClock.elapsedRealtime());
        }
    }

    private void completeActiveStudyTask(String key, String outcome, long answeredAt) {
        if (activeStudyTask == null || key == null || !key.equals(activeStudyTask.taskKey)) {
            return;
        }
        long nowElapsed = SystemClock.elapsedRealtime();
        activeStudyTask.pause(nowElapsed);
        store.recordStudyTaskAnswered(
                activeStudyTask.taskKey,
                activeStudyTask.kanji,
                activeStudyTask.taskType,
                activeStudyTask.startedAtMillis,
                answeredAt,
                activeStudyTask.activeElapsedMillis,
                outcome
        );
        markStudyTaskCompleted(key);
        activeStudyTask = null;
    }

    private void pauseActiveStudyTask() {
        if (activeStudyTask != null) {
            activeStudyTask.pause(SystemClock.elapsedRealtime());
        }
    }

    private void resumeActiveStudyTask() {
        if (activeStudyTask != null) {
            activeStudyTask.resume(SystemClock.elapsedRealtime());
        }
    }

    private void abandonActiveStudyTask() {
        activeStudyTask = null;
    }

    private String flashcardTitle(Records.StudySession session) {
        if (isWordReadingTask(session)) {
            return "Read this word";
        }
        if (isTypingMeaningTask(session)) {
            return "Type the meaning";
        }
        return isFontRecognitionTask(session) ? "Recognise this kanji" : "Name this kanji";
    }

    private String studyModeLabel(Records.StudySession session) {
        if (session != null && session.writingRequired) {
            return LABEL_PRACTICE;
        }
        if (isWordReadingTask(session)) {
            return "Read";
        }
        if (isTypingMeaningTask(session)) {
            return "Type";
        }
        return "Recognise";
    }

    private View typingAnswerField() {
        typingAnswerInput = new EditText(this);
        typingAnswerInput.setSingleLine(true);
        typingAnswerInput.setTextSize(20);
        typingAnswerInput.setTextColor(STUDY_PLUM);
        typingAnswerInput.setHintTextColor(STUDY_MUTED);
        typingAnswerInput.setHint(LABEL_MEANING);
        typingAnswerInput.setPadding(dp(16), 0, dp(16), 0);
        typingAnswerInput.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(4), 0, dp(4));
        typingAnswerInput.setLayoutParams(lp);
        return typingAnswerInput;
    }

    private Typeface fontResource(int fontRes, Typeface fallback) {
        try {
            return getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private View flashcardAnswerPanel(Records.StudySession session) {
        LinearLayout box = softInsetPanel();
        box.addView(text("Answer", 19, STUDY_PLUM, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 76, STUDY_PLUM, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            addStudyCueLines(details, session);
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        return box;
    }

    private void addStudyCueLines(LinearLayout details, Records.StudySession session) {
        List<String> lines = StudyCueTexts.answerLines(
                dictionaryLookup(),
                session,
                exampleForSession(session),
                isWordReadingTask(session)
        );
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = line.startsWith("Reading:") ? STUDY_PINK_DARK : STUDY_PLUM;
            details.addView(text(line, i == 0 ? 17 : 15, color, true));
        }
    }

    private DictionaryLookup dictionaryLookup() {
        if (dictionaryLookup == null) {
            dictionaryLookup = DictionaryAssets.load(this);
        }
        return dictionaryLookup;
    }

    private void buildFlashcardActionBar(boolean revealed) {
        if (studyActionBar == null) {
            return;
        }
        styleStudyActionBarShell();
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text("", 15, STUDY_MUTED, false);
        resultStatus.setVisibility(View.GONE);
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!revealed) {
            Button reveal = pinkPrimaryButton("Reveal");
            reveal.setOnClickListener(v -> revealFlashcardAnswer());
            actions.addView(reveal, new LinearLayout.LayoutParams(0, dp(62), 1));
        } else {
            Button fail = studyFailButton("Fail");
            fail.setOnClickListener(v -> submitReview(RATING_AGAIN, false));
            LinearLayout.LayoutParams failParams = new LinearLayout.LayoutParams(0, dp(62), 1);
            failParams.setMargins(0, 0, dp(6), 0);
            actions.addView(fail, failParams);

            Button pass = pinkPrimaryButton("Pass");
            pass.setOnClickListener(v -> submitReview("good", false));
            LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(0, dp(62), 1);
            passParams.setMargins(dp(6), 0, 0, 0);
            actions.addView(pass, passParams);
        }
        studyActionBar.addView(actions);
    }

    private void revealFlashcardAnswer() {
        if (flashcardAnswerRevealed) {
            return;
        }
        if (isTypingMeaningTask(activeSession)
                && TypingAnswerMatcher.matches(
                dictionaryLookup(),
                activeSession.item.kanji,
                typingAnswerInput == null ? "" : typingAnswerInput.getText().toString(),
                collectionMeaningForSession(activeSession))) {
            Toast.makeText(this, "Typing answer accepted.", Toast.LENGTH_SHORT).show();
            submitReview("good", false);
            return;
        }
        flashcardAnswerRevealed = true;
        if (flashcardHeroPanel != null) {
            flashcardHeroPanel.setVisibility(View.GONE);
        }
        expandFlashcardForAnswer();
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(View.VISIBLE);
        }
        buildFlashcardActionBar(true);
    }

    private String collectionMeaningForSession(Records.StudySession session) {
        if (session == null || session.row == null) {
            return "";
        }
        Records.Example example = exampleForSession(session);
        if (example != null && !example.meaning.isEmpty()) {
            return example.meaning;
        }
        return session.row.primaryMeaning;
    }

    private void expandFlashcardForAnswer() {
        if (flashcardCard == null) {
            return;
        }
        int currentFullHeight = flashcardCard.getHeight();
        if (currentFullHeight > 0) {
            flashcardCard.setMinimumHeight(currentFullHeight);
        }
        ViewGroup.LayoutParams params = flashcardCard.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams linearParams) {
            linearParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            linearParams.weight = 0f;
            flashcardCard.setLayoutParams(linearParams);
            flashcardCard.requestLayout();
        }
    }

    private boolean handleFlashcardGesture(MotionEvent event) {
        if (activeSession == null || activeSession.writingRequired || flashcardGestureArea == null) {
            flashcardTouchTracking = false;
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isTypingMeaningTask(activeSession)
                        && typingAnswerInput != null
                        && isTouchInsideView(typingAnswerInput, event)) {
                    flashcardTouchTracking = false;
                    return false;
                }
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
            submitReview(dx > 0 ? "good" : RATING_AGAIN, false);
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
        styleStudyActionBarShell();
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text("", 16, STUDY_MUTED, false);
        resultStatus.setVisibility(View.GONE);
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = studySecondaryButton("Erase");
        clear.setOnClickListener(v -> {
            drawingPad.clear();
            activeAnalysis = null;
            setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)), MUTED);
            updateResultActions();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        hintButton = studySecondaryButton("Hint");
        hintButton.setOnClickListener(v -> showWritingHint());
        actions.addView(hintButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        studyActionBar.addView(actions);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        checkWritingButton = pinkPrimaryButton("Check");
        checkWritingButton.setOnClickListener(v -> checkWriting());
        primaryActions.addView(checkWritingButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        downloadModelButton = studySecondaryButton("Download checker");
        downloadModelButton.setOnClickListener(v -> downloadWritingModel());
        primaryActions.addView(downloadModelButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        nextAfterPassButton = pinkPrimaryButton("Pass");
        // Write_kanji rung exposes only Pass / Fail per the ladder contract.
        // A successful writing recognition is always treated as Good; Hard /
        // Easy grading from the recognizer is not surfaced on this rung.
        nextAfterPassButton.setOnClickListener(v -> submitReview("good", false));
        primaryActions.addView(nextAfterPassButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        studyActionBar.addView(primaryActions);

        LinearLayout fallbackActions = new LinearLayout(this);
        fallbackActions.setOrientation(LinearLayout.HORIZONTAL);
        replayButton = studySecondaryButton("Replay");
        replayButton.setOnClickListener(v -> replayWritingAnalysis());
        fallbackActions.addView(replayButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        manualOverrideButton = studySecondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview("good", true));
        fallbackActions.addView(manualOverrideButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        practiceWithGuideButton = studySecondaryButton("Try again with full guide");
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
            return dp(300);
        }
        if (screenDp < 820) {
            return dp(340);
        }
        return dp(390);
    }

    private void checkWriting() {
        if (activeSession == null) {
            return;
        }
        if (showNoInkWhenNeeded()) {
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
            showModelUnavailable("The handwriting checker is unavailable on this device.");
            return;
        }
        recognizer.modelStatus().whenComplete((status, statusError) -> {
            if (statusError != null || status == null || !status.downloaded) {
                main.post(() -> {
                    if (!isActiveToken(token)) {
                        return;
                    }
                    writingModelDownloaded = false;
                    writingModelStatusKnown = true;
                    showModelUnavailable("Download the handwriting checker before automatic checks.");
                });
                return;
            }
            recognizeWriting(recognizer, captured, sample, guide, target, token);
        });
    }

    private boolean showNoInkWhenNeeded() {
        if (drawingPad != null && drawingPad.hasInk()) {
            return false;
        }
        activeAnalysis = WritingAnalysisEngine.noInk(currentHintState.level(), hintsUsed);
        showAnalysis(activeAnalysis);
        return true;
    }

    private void showModelUnavailable(String message) {
        activeAnalysis = WritingAnalysisEngine.modelUnavailable(message, currentHintState.level(), hintsUsed);
        checkingWriting = false;
        showAnalysis(activeAnalysis);
    }

    private void recognizeWriting(WritingRecognizer recognizer, CapturedWriting captured, WritingSample sample, StrokeGuide guide, String target, String token) {
        recognizer.recognize(captured).whenComplete((result, error) -> main.post(() -> {
            if (!isActiveToken(token)) {
                return;
            }
            checkingWriting = false;
            if (error != null) {
                activeAnalysis = WritingAnalysisEngine.recognitionError(currentHintState.level(), hintsUsed);
            } else {
                activeAnalysis = WritingAnalysisEngine.analyze(target, sample, guide, candidates(result), currentHintState.level(), hintsUsed);
            }
            showAnalysis(activeAnalysis);
        }));
    }

    private void submitReview(String rating, boolean override) {
        if (activeSession == null) {
            return;
        }
        StudyReviewRequests.MappedReview mappedReview = StudyReviewRequests.from(
                activeSession,
                activeAnalysis,
                hintsUsed,
                rating,
                override
        );
        Records.ReviewRequest request = mappedReview.request();
        submitNormalReview(request);
    }

    private void submitNormalReview(Records.ReviewRequest request) {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(store.consumedTokens());
        long now = System.currentTimeMillis();
        Records.SchedulerParameters parameters = store.schedulerParameters();
        Records.ReviewResult result = scheduler.applyReview(activeSession.item, request, consumed, now, parameters, settings());
        completeActiveStudyTask(sessionTaskKey(activeSession), result.appliedRating, now);
        StudyStatsStore.StudyStreak streak = null;
        if (!result.duplicate) {
            saveAppliedReview(request, result, now);
            streak = store.studyStreak(now);
            tuneSchedulerIfNeeded(parameters, now);
        }
        Toast.makeText(this, reviewToast(result, streak), Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private void saveAppliedReview(Records.ReviewRequest request, Records.ReviewResult result, long now) {
        store.saveStudyItem(result.item);
        store.saveReview(request, result.appliedRating, now, activeSession.item, result.item);
        if (!RATING_AGAIN.equals(result.appliedRating)) {
            markStudyRunPassed(request.kanji);
        }
    }

    private void tuneSchedulerIfNeeded(Records.SchedulerParameters parameters, long now) {
        Records.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
        if (tuned.lastAdjustedAtMillis != parameters.lastAdjustedAtMillis || tuned.lastAdjustmentReviewCount != parameters.lastAdjustmentReviewCount) {
            store.saveSchedulerParameters(tuned);
        }
    }

    private boolean sameLocalDay(long leftMillis, long rightMillis) {
        return UiDateText.sameLocalDay(leftMillis, rightMillis);
    }

    private long nextLocalDayStart(long now) {
        return UiDateText.nextLocalDayStart(now);
    }

    private HintState initialHintState(Records.StudySession session) {
        int stored = Math.max(0, Math.min(3, session.item.writingLevel));
        if (TASK_TARGETED_WRITING.equals(session.taskType)
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
        StrokeGuide guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
        updateCheckWritingButton(passed, messyPass);
        updateDownloadModelButton();
        updateNextAfterPassButton(submittable);
        updateFallbackActionButtons(hasResult, passed, guide);
        updateHintAndAnswerVisibility(passed);
        if (resultStatus != null && !hasResult) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    private void updateCheckWritingButton(boolean passed, boolean messyPass) {
        if (checkWritingButton != null) {
            checkWritingButton.setVisibility(!passed || messyPass ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(!checkingWriting);
            checkWritingButton.setText(checkWritingButtonText(messyPass));
            checkWritingButton.setOnClickListener(messyPass ? v -> startCleanerRetry() : v -> checkWriting());
        }
    }

    private String checkWritingButtonText(boolean messyPass) {
        if (checkingWriting) {
            return "Checking...";
        }
        return messyPass ? "Try cleaner" : "Check";
    }

    private void updateDownloadModelButton() {
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(writingModelStatusKnown && writingModelDownloaded ? View.GONE : View.VISIBLE);
        }
    }

    private void updateNextAfterPassButton(boolean submittable) {
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(submittable ? View.VISIBLE : View.GONE);
            if (submittable) {
                nextAfterPassButton.setText(nextReviewButtonText(activeAnalysis));
            }
        }
    }

    private void updateFallbackActionButtons(boolean hasResult, boolean passed, StrokeGuide guide) {
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(hasResult && canManualOverride(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(hasResult && !passed && canPracticeAfterAnalysis(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (replayButton != null) {
            replayButton.setVisibility(hasResult && drawingPad != null && drawingPad.hasReplaySnapshot() && canReplayAnalysis(activeAnalysis, guide) ? View.VISIBLE : View.GONE);
        }
    }

    private void updateHintAndAnswerVisibility(boolean passed) {
        if (hintButton != null) {
            hintButton.setVisibility(!passed && canRevealMoreHelp() ? View.VISIBLE : View.GONE);
            hintButton.setText(currentPracticeLevel == 3 ? "Hint" : "More help");
        }
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(shouldShowLearningPanel(activeAnalysis) ? View.VISIBLE : View.GONE);
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
            case PASS, CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
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
                || (TASK_TARGETED_WRITING.equals(session.taskType) && session.item.learningStep < 2);
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
            case WRONG, NO_STROKE_DATA, RECOGNITION_ERROR:
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
            case NO_INK, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
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
            case NO_INK, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    private String diagnosisLine(StrokeDiagnosis.Entry entry) {
        switch (entry.label) {
            case WRONG_ORDER:
                return strokeDiagnosisText(entry, "likely wrong order");
            case WRONG_DIRECTION:
                return strokeDiagnosisText(entry, "likely wrong direction");
            case MISSING_STROKE:
                return strokeDiagnosisText(entry, "may be missing");
            case ROUGH_SHAPE:
                return strokeDiagnosisText(entry, "shape looks rough");
            case RECOGNIZED_BUT_MESSY:
                return "Recognized, but the stroke path was messy";
            default:
                return "";
        }
    }

    private String strokeDiagnosisText(StrokeDiagnosis.Entry entry, String label) {
        return "Stroke " + entry.strokeNumber + ": " + label;
    }

    private boolean isRecallTask(Records.StudySession session) {
        if (session == null) {
            return false;
        }
        return "blind_writing".equals(session.taskType) || "sampled_handwriting".equals(session.taskType);
    }

    private boolean isFontRecognitionTask(Records.StudySession session) {
        return session != null && (TASK_FONT_MEANING.equals(session.taskType) || "font_recognition".equals(session.taskType));
    }

    private boolean isTypingMeaningTask(Records.StudySession session) {
        return session != null && TASK_TYPING_MEANING.equals(session.taskType);
    }

    private boolean isWordReadingTask(Records.StudySession session) {
        return session != null && TASK_WORD_READING.equals(session.taskType);
    }

    private boolean canSubmitAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case PASS, CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
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
            case WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private boolean canPracticeAfterAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        return canManualOverride(analysis);
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
            setWritingModelStatusMessage(status, error);
        }));
    }

    private void setWritingModelStatusMessage(WritingRecognizer.ModelStatus status, Throwable error) {
        String prefix = guideStatusPrefix(strokeGuide(activeSession.item.kanji));
        if (error != null || status == null) {
            setStudyStatus(prefix + "\nUnable to read handwriting checker status.", CORAL);
            return;
        }
        if (!status.downloaded) {
            setStudyStatus(prefix + "\nDownload the handwriting checker before automatic checks.", CORAL);
            return;
        }
        setStudyStatus(prefix + "\nHandwriting checker ready.", MUTED);
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

    private String reviewToast(Records.ReviewResult result, StudyStatsStore.StudyStreak streak) {
        if (result.duplicate) {
            return "Already saved.";
        }
        String streakText = streak == null || streak.currentDays <= 0 ? "" : " " + streakHeadline(streak) + ".";
        if (RATING_AGAIN.equals(result.appliedRating)) {
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
            strokeGuides = StrokeGuideAssets.load(this);
        }
        return strokeGuides.get(kanji);
    }

    private void renderUpdate() {
        base(NAV_SETTINGS_ROUTE);
        content.addView(text("GitHub updater", 34, INK, true));
        content.addView(text("Current version " + BuildConfig.VERSION_NAME + ". Checks GitHub Releases, verifies the APK, and asks Android to install it.", 16, MUTED, false));
        content.addView(autoUpdatePanel("Automatic updates"));

        Button button = primaryButton("Check for update", STUDY_PINK_DARK);
        button.setOnClickListener(v -> runUpdate(false));
        content.addView(button);
    }

    private LinearLayout autoUpdatePanel(String title) {
        LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
        boolean canInstall = canInstallUpdates();
        LinearLayout box = settingsPanelBox();
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
        return UiDateText.autoUpdateLastCheckText(status.lastCheckAtMillis);
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
        base(NAV_SETTINGS_ROUTE);
        Records.Settings current = settings();
        content.addView(fullWidthHomeButton());
        content.addView(settingsHero(current, store.reminderSettings(), store.autoSyncSettings(), store.autoUpdateStatus()));
        addSpace(10);

        content.addView(settingsCategory(
                "Anki source",
                "What Kani reads from AnkiDroid, and which suspended cards become practice.",
                R.drawable.ic_book_24,
                settingsAnkiExpanded,
                () -> {
                    settingsAnkiExpanded = !settingsAnkiExpanded;
                    renderSettings();
                },
                noteTypeSettingsPanel(current),
                frequencyRangeSettingsPanel(current)
        ));
        content.addView(settingsCategory(
                "Study behavior",
                "How much appears today, how quickly repeats return, and when cards move rungs.",
                R.drawable.ic_study_24,
                settingsStudyExpanded,
                () -> {
                    settingsStudyExpanded = !settingsStudyExpanded;
                    renderSettings();
                },
                workloadSettingsPanel(),
                retentionSettingsPanel(),
                learningStepsSettingsPanel(),
                ladderThresholdSettingsPanel()
        ));
        content.addView(settingsCategory(
                "Automation",
                "Background nudges, daily AnkiDroid refreshes, and app update checks.",
                R.drawable.ic_sync_24,
                settingsSyncExpanded,
                () -> {
                    settingsSyncExpanded = !settingsSyncExpanded;
                    renderSettings();
                },
                reminderSettingsPanel(),
                autoSyncSettingsPanel(),
                updateSettingsPanel()
        ));
        content.addView(settingsCategory(
                "Reference data",
                "Offline dictionaries, frequency ranks, stroke data, fonts, and attribution.",
                R.drawable.ic_sparkle_24,
                settingsAppExpanded,
                () -> {
                    settingsAppExpanded = !settingsAppExpanded;
                    renderSettings();
                },
                dataLicenseSettingsPanel()
        ));
    }

    private View settingsHero(
            Records.Settings current,
            LocalStore.ReminderSettings reminder,
            LocalStore.AutoSyncSettings autoSync,
            LocalStore.AutoUpdateStatus autoUpdate
    ) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        hero.setBackground(panel(Color.rgb(255, 248, 252), STUDY_BORDER, dp(30)));
        hero.setElevation(dp(6));

        TextView pill = text("Settings cockpit", 13, STUDY_PINK_DARK, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        hero.addView(pill, new LinearLayout.LayoutParams(-2, -2));

        TextView title = text(NAV_SETTINGS, 34, STUDY_PLUM, true);
        title.setPadding(0, dp(12), 0, dp(4));
        hero.addView(title);
        hero.addView(text("Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.", 16, STUDY_MUTED, false));

        LinearLayout topRow = settingsStatusRow(
                settingsStatusPill("Note type", current.modelName, STUDY_PLUM),
                settingsStatusPill("Import ranks", current.suspendedRankMin + "-" + current.suspendedRankMax, TEAL)
        );
        LinearLayout bottomRow = settingsStatusRow(
                settingsStatusPill("Reminder", settingsReminderSummary(reminder), reminder.enabled ? TEAL : MUTED),
                settingsStatusPill("Daily sync", settingsAutoSyncSummary(autoSync), autoSync.enabled ? TEAL : MUTED)
        );
        hero.addView(topRow);
        hero.addView(bottomRow);
        hero.addView(settingsStatusPill("Updates", settingsUpdateSummary(autoUpdate), autoUpdate.hasPendingUpdate() ? CORAL : STUDY_PINK_DARK));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(10));
        hero.setLayoutParams(lp);
        return hero;
    }

    private LinearLayout settingsStatusRow(View first, View second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(0, -2, 1);
        firstLp.setMargins(0, 0, dp(6), 0);
        row.addView(first, firstLp);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(0, -2, 1);
        secondLp.setMargins(dp(6), 0, 0, 0);
        row.addView(second, secondLp);
        return row;
    }

    private LinearLayout settingsStatusPill(String label, String value, int valueColor) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(dp(13), dp(10), dp(13), dp(10));
        pill.setBackground(panel(Color.WHITE, Color.rgb(249, 207, 226), dp(20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        pill.setLayoutParams(lp);

        TextView labelView = text(label, 12, STUDY_MUTED, true);
        labelView.setIncludeFontPadding(false);
        pill.addView(labelView);

        TextView valueView = text(value, 17, valueColor, true);
        valueView.setSingleLine(false);
        valueView.setPadding(0, dp(3), 0, 0);
        pill.addView(valueView);
        return pill;
    }

    private String settingsReminderSummary(LocalStore.ReminderSettings reminder) {
        boolean blocked = reminder.enabled && !ReminderScheduler.notificationsAllowed(this);
        if (blocked) {
            return "Blocked";
        }
        return reminder.enabled ? reminder.displayTime() : "Off";
    }

    private String settingsAutoSyncSummary(LocalStore.AutoSyncSettings autoSync) {
        if (!autoSync.configured) {
            return "After first sync";
        }
        return autoSync.enabled ? autoSync.displayTime() : "Off";
    }

    private String settingsUpdateSummary(LocalStore.AutoUpdateStatus autoUpdate) {
        if (autoUpdate.hasPendingUpdate()) {
            return "Verified APK ready";
        }
        return autoUpdate.enabled ? "Automatic checks on" : "Manual checks";
    }

    private LinearLayout settingsCategory(
            String title,
            String summary,
            int iconRes,
            boolean expanded,
            Runnable toggle,
            View... panels
    ) {
        LinearLayout category = new LinearLayout(this);
        category.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(9));
        category.setLayoutParams(lp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(14), dp(16));
        header.setBackground(panel(expanded ? Color.WHITE : Color.rgb(255, 246, 251), STUDY_BORDER, dp(26)));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription((expanded ? "Collapse " : "Expand ") + title);
        header.setOnClickListener(v -> toggle.run());
        header.setElevation(dp(3));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(STUDY_PINK_DARK);
        icon.setBackground(panel(Color.rgb(255, 237, 246), Color.TRANSPARENT, dp(16)));
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconLp.setMargins(0, 0, dp(12), 0);
        header.addView(icon, iconLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(title, 21, STUDY_PLUM, true);
        heading.setIncludeFontPadding(false);
        copy.addView(heading);
        TextView detail = text(summary, 14, STUDY_MUTED, false);
        detail.setPadding(0, dp(4), 0, 0);
        copy.addView(detail);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView count = text(panels.length + (panels.length == 1 ? " card" : " cards"), 12, STUDY_PINK_DARK, true);
        count.setGravity(Gravity.CENTER);
        count.setIncludeFontPadding(false);
        count.setPadding(dp(9), dp(6), dp(9), dp(6));
        count.setBackground(panel(Color.rgb(255, 242, 248), STUDY_BORDER, dp(16)));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.setMargins(dp(10), 0, dp(8), 0);
        header.addView(count, countLp);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_forward_24);
        arrow.setColorFilter(STUDY_PINK_DARK);
        arrow.setRotation(expanded ? 90f : 0f);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
        category.addView(header);

        if (expanded) {
            for (View panel : panels) {
                category.addView(panel);
            }
        }
        return category;
    }

    private LinearLayout settingsPanelBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(17), dp(18), dp(18));
        box.setBackground(panel(Color.rgb(255, 253, 254), STUDY_BORDER, dp(24)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(6));
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout frequencyRangeSettingsPanel(Records.Settings current) {
        LinearLayout box = settingsPanelBox();
        final int[] selected = new int[]{current.suspendedRankMin, current.suspendedRankMax};
        box.addView(text("Frequency range", 23, INK, true));
        TextView status = text(frequencyRangeStatusText(selected[0], selected[1]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000.", 15, MUTED, false));

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout minColumn = new LinearLayout(this);
        minColumn.setOrientation(LinearLayout.VERTICAL);
        minColumn.addView(text("Min rank", 15, INK, true));
        EditText minInput = rankInput(selected[0]);
        minColumn.addView(minInput, new LinearLayout.LayoutParams(-1, dp(58)));
        inputs.addView(minColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout maxColumn = new LinearLayout(this);
        maxColumn.setOrientation(LinearLayout.VERTICAL);
        maxColumn.setPadding(dp(10), 0, 0, 0);
        maxColumn.addView(text("Max rank", 15, INK, true));
        EditText maxInput = rankInput(selected[1]);
        maxColumn.addView(maxInput, new LinearLayout.LayoutParams(-1, dp(58)));
        inputs.addView(maxColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(inputs);

        box.addView(text("Minimum rank", 14, MUTED, true));
        SeekBar minSlider = new SeekBar(this);
        box.addView(minSlider, new LinearLayout.LayoutParams(-1, dp(56)));
        box.addView(text("Maximum rank", 14, MUTED, true));
        SeekBar maxSlider = new SeekBar(this);
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, dp(56)));
        bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);

        Button save = primaryButton("Save frequency range", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int minRank;
            int maxRank;
            try {
                minRank = parseRankInput(minInput);
                maxRank = parseRankInput(maxInput);
            } catch (NumberFormatException error) {
                Toast.makeText(this, "Enter numeric ranks.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (minRank < 1 || minRank > 20000 || maxRank < 1 || maxRank > 20000) {
                Toast.makeText(this, "Use ranks from 1 to 20000.", Toast.LENGTH_SHORT).show();
                return;
            }
            int normalizedMin = Math.min(minRank, maxRank);
            int normalizedMax = Math.max(minRank, maxRank);
            store.putIntSetting("suspended_rank_min", normalizedMin);
            store.putIntSetting("suspended_rank_max", normalizedMax);
            Toast.makeText(this, "Frequency range saved. Sync again to rebuild practice.", Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private LinearLayout dataLicenseSettingsPanel() {
        LinearLayout box = settingsPanelBox();
        box.addView(text("Offline data & licenses", 23, INK, true));
        box.addView(text("One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.", 15, MUTED, false));
        Button open = secondaryButton("Open data licenses");
        open.setOnClickListener(v -> renderDataSources());
        box.addView(open);
        return box;
    }

    private void renderDataSources() {
        base(NAV_SETTINGS_ROUTE);
        content.addView(text("Data licenses", 34, INK, true));
        content.addView(text("Dictionary and stroke-order data bundled for offline study.", 16, MUTED, false));

        LinearLayout dictionary = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        dictionary.addView(text("Dictionary data", 23, INK, true));
        dictionary.addView(text(AttributionTexts.dictionarySources(this), 14, MUTED, false));
        content.addView(dictionary);

        LinearLayout stroke = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        stroke.addView(text("Stroke data", 23, INK, true));
        stroke.addView(text(AttributionTexts.kanjiVg(this), 14, MUTED, false));
        content.addView(stroke);

        LinearLayout fonts = panelBox(Color.WHITE, Color.rgb(255, 247, 220));
        fonts.addView(text("Fonts", 23, INK, true));
        fonts.addView(text(AttributionTexts.rawResourceText(this, R.raw.font_attribution), 14, MUTED, false));
        content.addView(fonts);

        Button back = secondaryButton("Back to settings");
        back.setOnClickListener(v -> renderSettings());
        content.addView(back);
    }

    private LinearLayout noteTypeSettingsPanel(Records.Settings current) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Note type & clue fields", 23, INK, true));
        box.addView(text("Using " + current.modelName, 17, TEAL, true));
        box.addView(text("Default: Kiku. This single card owns the note type and all field mapping so clue configuration is not repeated elsewhere.", 15, MUTED, false));

        EditText noteType = noteTypeInput(current.modelName);
        box.addView(noteType, new LinearLayout.LayoutParams(-1, dp(58)));
        EditText expressionField = fieldInput(current.expressionField);
        EditText readingField = fieldInput(current.readingField);
        EditText meaningField = fieldInput(current.meaningField);
        EditText sentenceField = fieldInput(current.sentenceField);
        EditText frequencyField = fieldInput(current.frequencyField);
        EditText frequencySortField = fieldInput(current.frequencySortField);
        box.addView(text("Required fields", 15, STUDY_PLUM, true));
        box.addView(text("Expression = kanji source, ExpressionReading = reading, MainDefinition = meaning, Sentence = context, Frequency/FreqSort = metadata.", 14, MUTED, false));
        addFieldMappingInput(box, "Expression field", expressionField);
        addFieldMappingInput(box, "Reading field", readingField);
        addFieldMappingInput(box, "Meaning field", meaningField);
        addFieldMappingInput(box, "Sentence field", sentenceField);
        addFieldMappingInput(box, "Frequency field", frequencyField);
        addFieldMappingInput(box, "Frequency sort field", frequencySortField);

        NoteTypeFieldMappings.Inputs fieldMappings = new NoteTypeFieldMappings.Inputs(
                noteType,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField
        );
        Button choose = secondaryButton("Choose from AnkiDroid");
        choose.setOnClickListener(v -> NoteTypeFieldMappings.choose(this, gateway, io, main, fieldMappings));
        box.addView(choose);
        Button kiku = secondaryButton("Use Kiku");
        kiku.setOnClickListener(v -> {
            noteType.setText(defaults.modelName);
            expressionField.setText(defaults.expressionField);
            readingField.setText(defaults.readingField);
            meaningField.setText(defaults.meaningField);
            sentenceField.setText(defaults.sentenceField);
            frequencyField.setText(defaults.frequencyField);
            frequencySortField.setText(defaults.frequencySortField);
        });
        box.addView(kiku);

        Button save = primaryButton("Save note type", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            String selected = noteType.getText().toString().trim();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Enter a note type name.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (expressionField.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Choose the field that contains kanji.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, selected);
            store.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, expressionField.getText().toString().trim());
            store.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, readingField.getText().toString().trim());
            store.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, meaningField.getText().toString().trim());
            store.putStringSetting(SyncSettings.SENTENCE_FIELD_SETTING_KEY, sentenceField.getText().toString().trim());
            store.putStringSetting(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, frequencyField.getText().toString().trim());
            store.putStringSetting(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, frequencySortField.getText().toString().trim());
            Toast.makeText(this, "Note type saved. Sync again to rebuild practice.", Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private EditText noteTypeInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null || value.trim().isEmpty() ? Records.Settings.kikuDefaults().modelName : value.trim());
        input.setHint(Records.Settings.kikuDefaults().modelName);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private EditText fieldInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null ? "" : value.trim());
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        box.addView(text(label, 14, INK, true));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private EditText rankInput(int value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", value));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private void bindRankSliders(
            int[] selected,
            TextView status,
            EditText minInput,
            EditText maxInput,
            SeekBar minSlider,
            SeekBar maxSlider
    ) {
        minSlider.setMax(19999);
        maxSlider.setMax(19999);
        minSlider.setProgress(rankSliderProgress(selected[0]));
        maxSlider.setProgress(rankSliderProgress(selected[1]));

        minSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = Math.min(rankFromSliderProgress(progress), selected[1]);
                minInput.setText(String.format(Locale.ROOT, "%d", selected[0]));
                status.setText(frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(rankSliderProgress(selected[0]));
            }
        });
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[1] = Math.max(rankFromSliderProgress(progress), selected[0]);
                maxInput.setText(String.format(Locale.ROOT, "%d", selected[1]));
                status.setText(frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(rankSliderProgress(selected[1]));
            }
        });
    }

    private int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    private int rankSliderProgress(int rank) {
        return Math.max(0, Math.min(19999, rank - 1));
    }

    private int rankFromSliderProgress(int progress) {
        return Math.max(1, Math.min(20000, progress + 1));
    }

    private String frequencyRangeStatusText(int minRank, int maxRank) {
        return String.format(Locale.ROOT, "Jiten ranks %d-%d", minRank, maxRank);
    }

    private LinearLayout workloadSettingsPanel() {
        int current = store.adaptiveLoadWorkPercent();
        int currentMax = store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        LinearLayout box = settingsPanelBox();
        box.addView(text("Daily workload", 23, INK, true));

        if (autoMode) {
            long now = System.currentTimeMillis();
            List<Records.DashboardRow> rows = store.activeDashboardRows();
            Records.AdaptiveLoadPlan plan = rows.isEmpty()
                    ? null
                    : adaptivePlan(rows, store.studyItems(), now);
            box.addView(text(autoWorkloadStatusText(plan), 17, TEAL, true));
            box.addView(text("Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule.", 15, MUTED, false));
            addMaxItemsControl(box, selectedMax, null, null);
            Button saveMax = primaryButton("Save maximum", STUDY_PINK_DARK);
            saveMax.setOnClickListener(v -> {
                store.saveAdaptiveLoadMaxItems(selectedMax[0]);
                Toast.makeText(this, "Pareto maximum saved.", Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(saveMax);
            Button manual = secondaryButton("Use manual workload");
            manual.setOnClickListener(v -> {
                store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
                Toast.makeText(this, "Manual workload enabled.", Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(manual);
            return box;
        }

        TextView status = text(workloadStatusText(selected[0], selectedMax[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule.", 15, MUTED, false));

        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = AdaptiveLoadPlanner.snapWorkloadPercent(progress);
                status.setText(workloadStatusText(selected[0], selectedMax[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
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

        addMaxItemsControl(box, selectedMax, status, selected);

        Button save = primaryButton("Save workload", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
            store.saveAdaptiveLoadWorkPercent(selected[0]);
            store.saveAdaptiveLoadMaxItems(selectedMax[0]);
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

    private void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        TextView maxStatus = text(maxItemsStatusText(selectedMax[0]), 17, TEAL, true);
        maxStatus.setPadding(0, dp(8), 0, 0);
        box.addView(maxStatus);

        SeekBar maxSlider = new SeekBar(this);
        maxSlider.setMax(AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedMax[0] = AdaptiveLoadPlanner.normalizeMaxItems(progress + AdaptiveLoadPlanner.MIN_MAX_ITEMS);
                maxStatus.setText(maxItemsStatusText(selectedMax[0]));
                if (workloadStatus != null && selectedWorkload != null) {
                    workloadStatus.setText(workloadStatusText(selectedWorkload[0], selectedMax[0]));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
            }
        });
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    private LinearLayout learningStepsSettingsPanel() {
        Records.LearningStepSettings current = store.learningStepSettings();
        LinearLayout box = settingsPanelBox();
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

        Button save = primaryButton("Save learning steps", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<Integer> parsedNew = Records.LearningStepSettings.tryParseSteps(newSteps.getText().toString());
            List<Integer> parsedReview = Records.LearningStepSettings.tryParseSteps(reviewSteps.getText().toString());
            if (parsedNew.isEmpty() || parsedReview.isEmpty()) {
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

    private LinearLayout ladderThresholdSettingsPanel() {
        Records.Settings current = settings();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Ladder thresholds", 23, INK, true));
        box.addView(text("Recognition rungs move only after repeated FSRS-due reviews. Learning-step repeats stay practice-only.", 15, MUTED, false));

        EditText passes = thresholdInput(current.recognitionPromotionPasses);
        EditText misses = thresholdInput(current.writingTriggerMissDays);
        box.addView(text("Passes to go up", 15, INK, true));
        box.addView(passes, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text("Misses to go down", 15, INK, true));
        box.addView(misses, new LinearLayout.LayoutParams(-1, dp(58)));

        Button defaults = secondaryButton("Use 3 and 3");
        defaults.setOnClickListener(v -> {
            passes.setText(String.format(Locale.ROOT, "%d", Records.DEFAULT_RECOGNITION_PROMOTION_PASSES));
            misses.setText(String.format(Locale.ROOT, "%d", Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS));
        });
        box.addView(defaults);

        Button save = primaryButton("Save ladder thresholds", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int passCount;
            int missCount;
            try {
                passCount = parseThresholdInput(passes);
                missCount = parseThresholdInput(misses);
            } catch (NumberFormatException error) {
                Toast.makeText(this, "Use whole numbers from 1 to 10.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (passCount < 1 || passCount > 10 || missCount < 1 || missCount > 10) {
                Toast.makeText(this, "Use whole numbers from 1 to 10.", Toast.LENGTH_SHORT).show();
                return;
            }
        store.putIntSetting(SyncSettings.RECOGNITION_PROMOTION_PASSES_SETTING_KEY, passCount);
        store.putIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, missCount);
        // The ladder state machine uses a single threshold for both promotion
        // and demotion. Persist the maximum of the two UI values so that the
        // scheduler honours the user's intent for both directions.
        store.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, Math.max(passCount, missCount));
            Toast.makeText(this, "Ladder thresholds saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    private EditText thresholdInput(int value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", Math.max(1, value)));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private int parseThresholdInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    private LinearLayout retentionSettingsPanel() {
        Records.SchedulerParameters current = store.schedulerParameters();
        final int[] selected = new int[]{retentionPercent(current.targetRetention)};
        LinearLayout box = settingsPanelBox();
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
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Drag-stop has no side effects; selected retention is already updated.
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

        Button save = primaryButton("Save retention", STUDY_PINK_DARK);
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

        LinearLayout box = settingsPanelBox();
        box.addView(text("Daily reminder", 23, INK, true));
        box.addView(text(reminderStatus(reminder, blocked), 17, reminderStatusColor(reminder, blocked), true));
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

        Button save = primaryButton(reminder.enabled ? "Save reminder" : "Enable reminder", STUDY_PINK_DARK);
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

    private int reminderStatusColor(LocalStore.ReminderSettings reminder, boolean blocked) {
        if (blocked) {
            return CORAL;
        }
        return reminder.enabled ? TEAL : MUTED;
    }

    private LinearLayout autoSyncSettingsPanel() {
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        LinearLayout box = settingsPanelBox();
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
                Button on = primaryButton("Turn on daily sync", STUDY_PINK_DARK);
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
        return UiDateText.shortDateTime(millis);
    }

    private String workloadStatusText(int percent, int maxItems) {
        int snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent);
        int normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems);
        String label = AdaptiveLoadPlanner.workloadLabel(snapped);
        if (snapped >= 100) {
            return label + ": up to " + normalizedMax + " items";
        }
        return label + ": up to " + Math.min(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax) + " items";
    }

    private String maxItemsStatusText(int maxItems) {
        return "Maximum: " + countText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems), "item", "items");
    }

    private String autoWorkloadStatusText(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Auto Pareto: waiting for problem kanji";
        }
        return "Auto Pareto: " + countText(plan.target, "item", "items") + " today";
    }

    private LinearLayout updateSettingsPanel() {
        LinearLayout box = autoUpdatePanel("App updates");
        Button update = primaryButton("Open updater", STUDY_PINK_DARK);
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

    private void runUpdate(boolean cachedPending) {
        base(NAV_SETTINGS_ROUTE);
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
                store.adaptiveLoadMaxItems(),
                now,
                settings()
        );
    }

    private Records.AdaptiveLoadPlan studyPlanForMode(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        if (continueAllKanjiSession) {
            return allCurrentProblemKanjiPlan(rows, items, now);
        }
        return adaptivePlan(rows, items, now);
    }

    private Records.AdaptiveLoadPlan allCurrentProblemKanjiPlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        List<String> focus = new ArrayList<>();
        for (Records.DashboardRow row : rows) {
            focus.add(row.kanji);
        }
        Set<String> studied = store.studiedKanjiSince(startOfDay(now));
        int remaining = 0;
        for (String kanji : focus) {
            Records.StudyItem item = findStudyItem(items, kanji);
            if (!studied.contains(kanji) || itemDueForFocus(item, now)) {
                remaining++;
            }
        }
        return new Records.AdaptiveLoadPlan(
                false,
                100,
                focus.size(),
                remaining,
                focus,
                focus.size(),
                true,
                "All current problem kanji are available today."
        );
    }

    private boolean itemDueForFocus(Records.StudyItem item, long now) {
        if (item == null || STATE_RETIRED.equals(item.state)) {
            return false;
        }
        if (STATE_LEARNING.equals(item.state)) {
            return item.dueAtMillis <= now;
        }
        return item.totalReviews > 0 && item.dueAtMillis <= now;
    }

    private void prepareStudyContent(Records.AdaptiveLoadPlan plan, boolean fillViewport) {
        activeStudyPlan = plan;
        content.removeAllViews();
        if (contentScroll != null) {
            contentScroll.setFillViewport(fillViewport);
        }
        content.addView(studyTopBar(plan));
    }

    private View studyTopBar(Records.AdaptiveLoadPlan plan) {
        initializeSessionProgressTarget(plan);
        int completed = sessionProgressCompleted;
        int target = sessionProgressMax;
        boolean activeTask = activeSession != null;
        if (activeTask && target <= completed && continueAllKanjiSession) {
            target = completed + 1;
        }
        if (activeTask) {
            target = Math.max(1, target);
        }
        int visibleCompleted = Math.max(0, Math.min(target, completed));
        float fraction = target <= 0 ? 0f : Math.max(0f, Math.min(1f, completed / (float) target));
        return new StudyTopBarView(this, visibleCompleted, target, fraction, this::renderHome, this::renderSettings);
    }

    private LinearLayout softStudyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(panel(STUDY_CARD, STUDY_BORDER, dp(26)));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView modePill(String value) {
        TextView pill = text(value, 13, STUDY_PINK_DARK, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(panel(Color.rgb(255, 239, 247), STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, dp(14));
        pill.setLayoutParams(lp);
        return pill;
    }

    private LinearLayout softInsetPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(panel(STUDY_PANEL, STUDY_BORDER, dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(10));
        panel.setLayoutParams(lp);
        return panel;
    }

    private Button pinkPrimaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(19);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if ("Reveal".equals(label)) {
            button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_eye_24, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(8));
            TextViewCompat.setCompoundDrawableTintList(button, ColorStateList.valueOf(Color.WHITE));
        }
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(255, 139, 182), STUDY_PINK_DARK }
        );
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.rgb(255, 173, 205));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(20));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(42, 255, 255, 255)),
                background,
                mask
        ));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(dp(3), dp(8), dp(3), dp(8));
        button.setLayoutParams(lp);
        return button;
    }

    private Button studySecondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(STUDY_PLUM);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    private Button studyFailButton(String label) {
        Button button = studySecondaryButton(label);
        button.setTextColor(STUDY_PINK_DARK);
        button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(18)));
        return button;
    }

    private void styleStudyActionBarShell() {
        if (studyActionBar != null) {
            studyActionBar.setPadding(dp(18), dp(10), dp(18), dp(8));
            studyActionBar.setBackgroundColor(STUDY_BG);
        }
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
        return canonicalKanjiMeaning(session == null ? "" : session.item.kanji, raw, 96);
    }

    private String canonicalKanjiMeaning(String kanji, String fallback, int maxChars) {
        DictionaryLookup.KanjiEntry entry = dictionaryLookup().lookupKanji(kanji);
        if (entry != null) {
            String meaning = StudyCueTexts.displayGlosses(entry.meanings, 2);
            if (!meaning.isEmpty()) {
                return compact(meaning, maxChars);
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars);
    }

    private String wordPrompt(Records.StudySession session) {
        Records.Example example = session == null ? null : wordReadingExample(session.row);
        if (example != null && !example.expression.isEmpty()) {
            return example.expression;
        }
        return session == null ? "" : session.item.kanji;
    }

    private String cleanLearnerText(String raw, String fallback, int maxChars) {
        return StudyCueTexts.cleanFallbackMeaning(raw, fallback, maxChars);
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
        if (task == null) {
            return "Study";
        }
        return switch (task) {
            case "targeted_flashcard" -> "Focused recall";
            case "kanji_meaning" -> "Kanji -> meaning";
            case TASK_TYPING_MEANING -> "Type the meaning";
            case TASK_FONT_MEANING -> "Font -> meaning";
            case TASK_WORD_READING -> "Word -> reading";
            case BridgeScheduler.TASK_WRITE_KANJI -> "Write kanji";
            case BridgeScheduler.TASK_SIMILAR_KANJI -> "Similar kanji";
            case "meaning_flashcard" -> "Quick recall";
            case "font_recognition" -> "Font check";
            case "repair_writing" -> "Write to repair";
            case TASK_TARGETED_WRITING -> "Focused practice";
            case "context_writing" -> "New problem kanji";
            case "guided_writing" -> "Guided review";
            case "blind_writing", "sampled_handwriting" -> "Memory check";
            case "confusable_recognition" -> "Learn the shape";
            default -> "Study";
        };
    }

    private String adaptiveFocusText(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Adaptive focus is waiting for sync";
        }
        if (plan.allKanjiMode) {
            return "Adaptive focus is set to all current problem kanji";
        }
        return "Today's adaptive focus: " + plan.remaining + " items left / " + plan.target;
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
        // The write_kanji rung is Pass-only per the ladder contract; the
        // button label does not surface Hard / Easy / miss variants even
        // when the recognizer rating is available.
        return "Pass";
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
            case PASS, CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
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

    private static final class ActiveStudyTask {
        private final String taskKey;
        private final String kanji;
        private final String taskType;
        private final long startedAtMillis;
        private long activeElapsedMillis;
        private long visibleSinceElapsedMillis;

        private ActiveStudyTask(String taskKey, String kanji, String taskType, long startedAtMillis) {
            this.taskKey = taskKey;
            this.kanji = kanji == null ? "" : kanji;
            this.taskType = taskType == null ? "" : taskType;
            this.startedAtMillis = Math.max(0L, startedAtMillis);
        }

        private void pause(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                return;
            }
            activeElapsedMillis += Math.max(0L, nowElapsedMillis - visibleSinceElapsedMillis);
            visibleSinceElapsedMillis = 0L;
        }

        private void resume(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                visibleSinceElapsedMillis = nowElapsedMillis;
            }
        }
    }

    private static final class EqualHeightRow extends LinearLayout {
        private EqualHeightRow(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int maxOuterHeight = 0;
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                maxOuterHeight = Math.max(maxOuterHeight, measuredOuterHeight(child));
            }
            if (maxOuterHeight <= 0) {
                return;
            }

            int childAreaHeight = maxOuterHeight;
            if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) {
                childAreaHeight = Math.max(0, View.MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom());
            }

            for (int i = 0; i < childCount; i++) {
                measureVisibleChild(getChildAt(i), childAreaHeight);
            }

            if (View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY) {
                setMeasuredDimension(getMeasuredWidth(), getPaddingTop() + getPaddingBottom() + maxOuterHeight);
            }
        }

        private static int measuredOuterHeight(View child) {
            int outerHeight = child.getMeasuredHeight();
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (rawLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                outerHeight += marginLp.topMargin + marginLp.bottomMargin;
            }
            return outerHeight;
        }

        private static void measureVisibleChild(View child, int childAreaHeight) {
            if (child.getVisibility() == GONE) {
                return;
            }
            int childHeight = childAreaHeight;
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (rawLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                childHeight -= marginLp.topMargin + marginLp.bottomMargin;
            }
            if (childHeight <= 0) {
                return;
            }
            child.measure(
                    View.MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            );
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

}
