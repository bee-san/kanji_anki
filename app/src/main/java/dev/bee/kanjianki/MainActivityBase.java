package dev.bee.kanjianki;

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
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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

abstract class MainActivityBase extends Activity {
    public static final String EXTRA_OPEN_UPDATE = "dev.bee.kanjianki.extra.OPEN_UPDATE";
    static final int REQUEST_POST_NOTIFICATIONS = 704;
    static final String PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    static final int BG = Color.rgb(255, 247, 251);
    static final int INK = Color.rgb(45, 22, 53);
    static final int MUTED = Color.rgb(108, 86, 116);
    static final int CORAL = Color.rgb(255, 76, 118);
    static final int TEAL = Color.rgb(0, 174, 181);
    static final int GOLD = Color.rgb(255, 214, 64);
    static final int BLUE = Color.rgb(110, 92, 230);
    static final int BLUSH = Color.rgb(255, 239, 246);
    static final int PINK_STROKE = Color.rgb(255, 174, 204);
    static final int LILAC = Color.rgb(118, 72, 255);
    static final int STUDY_BG = Color.rgb(255, 245, 250);
    static final int STUDY_CARD = Color.rgb(255, 255, 255);
    static final int STUDY_PANEL = Color.rgb(255, 236, 245);
    static final int STUDY_PLUM = Color.rgb(75, 37, 82);
    static final int STUDY_MUTED = Color.rgb(130, 96, 132);
    static final int STUDY_PINK_DARK = Color.rgb(218, 58, 122);
    static final int STUDY_BORDER = Color.rgb(255, 199, 222);
    static final int STUDY_BG_SOFT = Color.rgb(255, 246, 251);
    static final int STUDY_HERO_PANEL = Color.rgb(253, 241, 247);
    static final int STUDY_HERO_PINK = Color.rgb(248, 45, 114);
    static final int STUDY_HERO_PLUM = Color.rgb(33, 7, 44);
    static final int STUDY_HERO_MUTED = Color.rgb(102, 82, 110);
    static final long DAY_MILLIS = 86_400_000L;
    static final String NAV_STUDY = "study";
    static final String NAV_SETTINGS = "Settings";
    static final String NAV_SETTINGS_ROUTE = "settings";
    static final String LABEL_BACK_HOME = "Back home";
    static final String LABEL_MEANING = "Meaning";
    static final String LABEL_PASS = "Pass";
    static final String LABEL_PRACTICE = "Practice";
    static final String LABEL_SIMILAR_KANJI = "Similar kanji";
    static final String LABEL_STUDY_NOW = "Study now";
    static final String LABEL_STUDY = "Study";
    static final String LABEL_NEW_CARDS = "New cards";
    static final String LABEL_CONTINUE_ALL_KANJI = "Continue all kanji";
    static final String RATING_AGAIN = "again";
    static final String RATING_GOOD = "good";
    static final String STATE_LEARNING = "learning";
    static final String STATE_RETIRED = "retired";
    static final String SOURCE_ACTIVE = "active";
    static final String SOURCE_SUSPENDED = "suspended";
    static final String TASK_FONT_MEANING = "font_meaning";

    static final String TASK_TARGETED_WRITING = "targeted_writing";
    static final String TASK_TYPING_MEANING = "typing_meaning";
    static final String TASK_WORD_READING = "word_reading";

    static final String EMPTY_ACTIVE_PRACTICE_TITLE = "No active practice yet";
    static final String EMPTY_ACTIVE_PRACTICE_BODY = "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.";

    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final HintProgression hintProgression = new HintProgression();
    LocalStore store;
    AnkiDroidGateway gateway;
    LinearLayout content;
    ScrollView contentScroll;
    LinearLayout studyActionBar;
    Records.StudySession activeSession;
    Records.AdaptiveLoadPlan activeStudyPlan;
    DrawingPadView drawingPad;
    TextView studyStatus;
    TextView resultStatus;
    Button checkWritingButton;
    Button downloadModelButton;
    Button manualOverrideButton;
    Button nextAfterPassButton;
    Button practiceWithGuideButton;
    Button replayButton;
    Button hintButton;
    View studyAnswerPanel;
    View flashcardGestureArea;
    View flashcardCard;
    View flashcardHeroPanel;
    EditText typingAnswerInput;
    WritingAnalysis activeAnalysis;
    boolean checkingWriting;
    boolean flashcardAnswerRevealed;
    boolean flashcardTouchTracking;
    boolean writingModelDownloaded;
    boolean writingModelStatusKnown;
    boolean continueAllKanjiSession;
    final List<String> studyMoreNewCardKanji = new ArrayList<>();
    int hintsUsed;
    int currentPracticeLevel;
    int sessionProgressCompleted;
    int sessionProgressMax;
    float flashcardTouchStartX;
    float flashcardTouchStartY;
    ActiveStudyTask activeStudyTask;
    boolean activityPaused;
    final Set<String> sessionCompletedTaskKeys = new HashSet<>();
    final Set<String> sessionSeenTaskKeys = new HashSet<>();
    HintState currentHintState = HintState.initial();
    Map<String, StrokeGuide> strokeGuides;
    WritingRecognizer writingRecognizer;
    DictionaryLookup dictionaryLookup;
    LocalStore.ReminderSettings pendingReminderSettings;
    boolean settingsAnkiExpanded = true;
    boolean settingsStudyExpanded;
    boolean settingsSyncExpanded;
    boolean settingsAppExpanded;
    static AnkiDroidGateway ankiDroidGatewayForTests;
    static CollectionGateway collectionGatewayForTests;
    static WritingRecognizer writingRecognizerForTests;
    static WritingRecognizerFactory writingRecognizerFactoryForTests;
    static Boolean installPermissionForTests;
    static Boolean runtimeNotificationPermissionForTests;
    static Boolean notificationsAllowedForTests;

    interface WritingRecognizerFactory {
        WritingRecognizer create(ExecutorService executor);
    }

    abstract void renderHome();
    abstract void renderUpdate();
    abstract void renderSettings();
    abstract void renderStudy();
    abstract void startFocusedStudy();
    abstract void renderStudyForKanji(String kanji);
    abstract void pauseActiveStudyTask();
    abstract void resumeActiveStudyTask();
    abstract void abandonActiveStudyTask();
    abstract boolean handleFlashcardGesture(MotionEvent event);
    abstract String streakHeadline(StudyStatsStore.StudyStreak streak);
    abstract void initializeSessionProgressTarget(Records.AdaptiveLoadPlan plan);
    abstract DictionaryLookup dictionaryLookup();
    abstract Records.Example wordReadingExample(Records.DashboardRow row);
    abstract boolean shouldIncreaseSupportAfterAnalysis(WritingAnalysis analysis);
    abstract void clearStudyModeOverrides();
    abstract Typeface fontResource(int fontRes, Typeface fallback);
    abstract String shortDateTime(long millis);
    abstract EditText thresholdInput(int value);
    abstract int parseThresholdInput(EditText input);

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

    public static void setWritingRecognizerFactoryForTests(WritingRecognizerFactory factory) {
        writingRecognizerFactoryForTests = factory;
    }

    public static void setRuntimeNotificationPermissionForTests(Boolean granted) {
        runtimeNotificationPermissionForTests = granted;
    }

    public static void setNotificationsAllowedForTests(Boolean allowed) {
        notificationsAllowedForTests = allowed;
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

    void handleLaunchIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) {
            renderUpdate();
        } else {
            renderHome();
        }
    }

    void requestAnkiPermissionIfNeeded() {
        AnkiDroidGateway.ProviderStatus status = gateway.status();
        if (status.permission != null && !status.permissionGranted) {
            requestPermissions(new String[]{status.permission}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        handlePermissionResult(requestCode, grantResults);
    }

    void handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == 7) {
            renderHome();
        } else if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            handlePostNotificationPermission(grantResults);
        }
    }

    void handlePostNotificationPermission(int[] grantResults) {
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

    void saveGrantedReminderPermission(LocalStore.ReminderSettings pending) {
        LocalStore.ReminderSettings reminder = pending == null ? store.reminderSettings() : pending;
        store.saveReminderSettings(reminder);
        ReminderScheduler.schedule(this, reminder);
        if (notificationsAllowedForReminders()) {
            Toast.makeText(this, "Reminder saved for around " + reminder.displayTime() + ".", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Reminder saved, but Android notifications are off.", Toast.LENGTH_LONG).show();
        }
    }

    void disableReminderAfterDeniedPermission(LocalStore.ReminderSettings pending) {
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

    void base(String selected) {
        if (!NAV_STUDY.equals(selected)) {
            abandonActiveStudyTask();
        }
        flashcardGestureArea = null;
        flashcardCard = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAV_STUDY.equals(selected) ? STUDY_BG_SOFT : BG);
        setContentView(root);
        styleSystemBars();

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
            Insets bars = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                    .getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(dp(18), dp(18) + bars.top, dp(18), dp(18) + bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    void styleSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    boolean isActiveToken(String token) {
        return activeSession != null && activeSession.token.equals(token);
    }

    String reviewToast(Records.ReviewResult result, StudyStatsStore.StudyStreak streak) {
        if (result.duplicate) {
            return "Already saved.";
        }
        String streakText = streak == null || streak.currentDays <= 0 ? "" : " " + streakHeadline(streak) + ".";
        if (RATING_AGAIN.equals(result.appliedRating)) {
            return "Saved. This kanji will come back soon." + streakText;
        }
        return "Saved." + streakText;
    }

    WritingRecognizer writingRecognizer() {
        if (writingRecognizerForTests != null) {
            return writingRecognizerForTests;
        }
        if (writingRecognizer != null) {
            return writingRecognizer;
        }
        try {
            writingRecognizer = writingRecognizerFactoryForTests == null
                    ? new MlKitJapaneseWritingRecognizer(io)
                    : writingRecognizerFactoryForTests.create(io);
            return writingRecognizer;
        } catch (RuntimeException error) {
            return null;
        }
    }

    boolean hasRuntimeNotificationPermissionForReminder() {
        return runtimeNotificationPermissionForTests == null
                ? ReminderScheduler.hasRuntimeNotificationPermission(this)
                : runtimeNotificationPermissionForTests;
    }

    boolean notificationsAllowedForReminders() {
        return notificationsAllowedForTests == null
                ? ReminderScheduler.notificationsAllowed(this)
                : notificationsAllowedForTests;
    }

    List<RecognitionCandidate> candidates(WritingRecognizer.RecognitionResult result) {
        List<RecognitionCandidate> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (WritingRecognizer.Candidate candidate : result.candidates) {
            out.add(new RecognitionCandidate(candidate.text, candidate.score));
        }
        return out;
    }

    Records.DashboardRow findRow(List<Records.DashboardRow> rows, String kanji) {
        for (Records.DashboardRow row : rows) {
            if (row.kanji.equals(kanji)) {
                return row;
            }
        }
        return null;
    }

    Records.StudyItem findStudyItem(List<Records.StudyItem> items, String kanji) {
        for (Records.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        return null;
    }

    String candidateText(List<RecognitionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            values.add(candidates.get(i).text);
        }
        return String.join(", ", values);
    }

    StrokeGuide strokeGuide(String kanji) {
        if (strokeGuides == null) {
            strokeGuides = StrokeGuideAssets.load(this);
        }
        return strokeGuides.get(kanji);
    }

    Records.Settings settings() {
        return SyncSettings.fromStore(store);
    }

    Records.AdaptiveLoadPlan adaptivePlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
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

    Records.AdaptiveLoadPlan studyPlanForMode(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        if (!studyMoreNewCardKanji.isEmpty()) {
            return studyMoreNewCardsPlan(rows, items, now);
        }
        if (continueAllKanjiSession) {
            return allCurrentProblemKanjiPlan(rows, items, now);
        }
        return adaptivePlan(rows, items, now);
    }

    Records.AdaptiveLoadPlan studyMoreNewCardsPlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        List<String> focus = new ArrayList<>();
        for (String kanji : studyMoreNewCardKanji) {
            if (findRow(rows, kanji) != null) {
                focus.add(kanji);
            }
        }
        int remaining = 0;
        for (String kanji : focus) {
            Records.StudyItem item = findStudyItem(items, kanji);
            if (itemDueForFocus(item, now)) {
                remaining++;
            }
        }
        return new Records.AdaptiveLoadPlan(
                false,
                100,
                focus.size(),
                remaining,
                focus,
                0,
                false,
                "Custom study: " + countText(focus.size(), "extra new card", "extra new cards") + "."
        );
    }

    Records.AdaptiveLoadPlan allCurrentProblemKanjiPlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
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

    boolean itemDueForFocus(Records.StudyItem item, long now) {
        if (item == null || STATE_RETIRED.equals(item.state)) {
            return false;
        }
        if (STATE_LEARNING.equals(item.state)) {
            return item.dueAtMillis <= now;
        }
        return item.totalReviews > 0 && item.dueAtMillis <= now;
    }

    void prepareStudyContent(Records.AdaptiveLoadPlan plan, boolean fillViewport) {
        activeStudyPlan = plan;
        content.removeAllViews();
        if (contentScroll != null) {
            contentScroll.setFillViewport(fillViewport);
        }
        content.addView(studyTopBar(plan));
    }

    View studyTopBar(Records.AdaptiveLoadPlan plan) {
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

    LinearLayout softStudyCard() {
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

    TextView modePill(String value) {
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

    LinearLayout softInsetPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(panel(STUDY_PANEL, STUDY_BORDER, dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(10));
        panel.setLayoutParams(lp);
        return panel;
    }

    Button pinkPrimaryButton(String label) {
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

    Button studySecondaryButton(String label) {
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

    Button studyFailButton(String label) {
        Button button = studySecondaryButton(label);
        button.setTextColor(STUDY_PINK_DARK);
        button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(18)));
        return button;
    }

    void styleStudyActionBarShell() {
        if (studyActionBar != null) {
            studyActionBar.setPadding(dp(18), dp(10), dp(18), dp(8));
            studyActionBar.setBackgroundColor(STUDY_BG);
        }
    }

    LinearLayout band(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.setBackground(panel(color, color, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        box.setLayoutParams(lp);
        return box;
    }

    LinearLayout panelBox(int fill, int stroke) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(panel(fill, stroke, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        box.setLayoutParams(lp);
        return box;
    }

    TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value == null ? "" : value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        text.setLineSpacing(0, 1.05f);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    TextView sectionTitle(String value) {
        TextView title = text(value, 22, INK, true);
        title.setPadding(0, dp(12), 0, dp(6));
        return title;
    }

    TextView chip(String value, int color) {
        TextView chip = text(value, 13, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(panel(softened(color), color, dp(7)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(7), dp(7), dp(2));
        chip.setLayoutParams(lp);
        return chip;
    }

    Button primaryButton(String label, int color) {
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

    Button secondaryButton(String label) {
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

    int softened(int color) {
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

    GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    void emptyState(String title, String body) {
        LinearLayout empty = band(GOLD);
        empty.addView(text(title, 24, INK, true));
        empty.addView(text(body, 16, INK, false));
        content.addView(empty);
    }

    String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    String rowMeaning(Records.DashboardRow row) {
        return cleanLearnerText(row.primaryMeaning, row.reasonCode, 72);
    }

    String sessionClue(Records.StudySession session) {
        String raw = session.row == null || session.row.primaryMeaning.isEmpty()
                ? session.prompt
                : session.row.primaryMeaning;
        return canonicalKanjiMeaning(session == null ? "" : session.item.kanji, raw, 96);
    }

    String canonicalKanjiMeaning(String kanji, String fallback, int maxChars) {
        DictionaryLookup.KanjiEntry entry = dictionaryLookup().lookupKanji(kanji);
        if (entry != null) {
            String meaning = StudyCueTexts.displayGlosses(entry.meanings, 2);
            if (!meaning.isEmpty()) {
                return compact(meaning, maxChars);
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars);
    }

    String wordPrompt(Records.StudySession session) {
        Records.Example example = session == null ? null : wordReadingExample(session.row);
        if (example != null && !example.expression.isEmpty()) {
            return example.expression;
        }
        return session == null ? "" : session.item.kanji;
    }

    String cleanLearnerText(String raw, String fallback, int maxChars) {
        return StudyCueTexts.cleanFallbackMeaning(raw, fallback, maxChars);
    }

    String compact(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        int cut = value.lastIndexOf(' ', maxChars - 3);
        if (cut < 32) {
            cut = maxChars - 3;
        }
        return value.substring(0, cut).trim() + "...";
    }

    void addSpace(int dp) {
        SpaceView space = new SpaceView(this);
        content.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    String labelForTask(String task) {
        if (task == null) {
            return LABEL_STUDY;
        }
        return switch (task) {
            case "targeted_flashcard" -> "Focused recall";
            case "kanji_meaning" -> "Kanji -> meaning";
            case TASK_TYPING_MEANING, BridgeScheduler.TASK_TYPE_MEANING -> "Type the meaning";
            case TASK_FONT_MEANING -> "Font -> meaning";
            case TASK_WORD_READING -> "Word -> reading";
            case BridgeScheduler.TASK_WRITE_KANJI -> "Write kanji";
            case BridgeScheduler.TASK_SIMILAR_KANJI -> LABEL_SIMILAR_KANJI;
            case "meaning_flashcard" -> "Quick recall";
            case "font_recognition" -> "Font check";
            case "repair_writing" -> "Write to repair";
            case TASK_TARGETED_WRITING -> "Focused practice";
            case "context_writing" -> "New problem kanji";
            case "guided_writing" -> "Guided review";
            case "blind_writing", "sampled_handwriting" -> "Memory check";
            case "confusable_recognition" -> "Learn the shape";
            default -> LABEL_STUDY;
        };
    }

    String adaptiveFocusText(Records.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Adaptive focus is waiting for sync";
        }
        if (plan.allKanjiMode) {
            return "Adaptive focus is set to all current problem kanji";
        }
        return "Today's adaptive focus: " + plan.remaining + " items left / " + plan.target;
    }

    String guideLabel(int level, StrokeGuide guide) {
        return guideLabel(HintState.fromWritingLevel(level), guide);
    }

    String guideLabel(HintState state, StrokeGuide guide) {
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

    String attemptProgressText(WritingAnalysis analysis) {
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

    String stageLabel(HintLevel level) {
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

    String targetRevealText(WritingAnalysis analysis) {
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

    long startOfDay(long now) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static final class ImportThresholds {
        final double difficulty;
        final int lapseThreshold;
        final int minCards;

        ImportThresholds(double difficulty, int lapseThreshold, int minCards) {
            this.difficulty = difficulty;
            this.lapseThreshold = lapseThreshold;
            this.minCards = minCards;
        }
    }

    static final class ActiveStudyTask {
        final String taskKey;
        final String kanji;
        final String taskType;
        final long startedAtMillis;
        long activeElapsedMillis;
        long visibleSinceElapsedMillis;

        ActiveStudyTask(String taskKey, String kanji, String taskType, long startedAtMillis) {
            this.taskKey = taskKey;
            this.kanji = kanji == null ? "" : kanji;
            this.taskType = taskType == null ? "" : taskType;
            this.startedAtMillis = Math.max(0L, startedAtMillis);
        }

        void pause(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                return;
            }
            activeElapsedMillis += Math.max(0L, nowElapsedMillis - visibleSinceElapsedMillis);
            visibleSinceElapsedMillis = 0L;
        }

        void resume(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                visibleSinceElapsedMillis = nowElapsedMillis;
            }
        }
    }

    static final class EqualHeightRow extends LinearLayout {
        EqualHeightRow(Context context) {
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

        static int measuredOuterHeight(View child) {
            int outerHeight = child.getMeasuredHeight();
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (rawLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                outerHeight += marginLp.topMargin + marginLp.bottomMargin;
            }
            return outerHeight;
        }

        static void measureVisibleChild(View child, int childAreaHeight) {
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

    static final class SpaceView extends View {
        SpaceView(Context context) {
            super(context);
        }
    }

    static final class QueueEntry {
        final Records.DashboardRow row;
        final Records.StudyItem item;

        QueueEntry(Records.DashboardRow row, Records.StudyItem item) {
            this.row = row;
            this.item = item;
        }
    }

}
