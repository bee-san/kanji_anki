package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import dev.bee.kanjianki.backup.DatabaseBackupScheduler;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveFocusCopy;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.FocusQueuePolicy;
import dev.bee.kanjianki.core.FocusedStudyPlanPolicy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.LocalDayPolicy;
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.StudyCollectionLookup;
import dev.bee.kanjianki.core.StudySessionProgressTracker;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.update.AutoUpdateScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class MainActivityBase extends MainActivityUiSupport {
    public static final String EXTRA_OPEN_UPDATE = "dev.bee.kanjianki.extra.OPEN_UPDATE";
    static final int REQUEST_POST_NOTIFICATIONS = 704;
    static final String PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
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
    static final String RATING_HARD = "hard";
    static final String RATING_GOOD = "good";
    static final String STATE_LEARNING = "learning";
    static final String STATE_RETIRED = "retired";
    static final String SOURCE_ACTIVE = "active";
    static final String SOURCE_SUSPENDED = "suspended";
    static final String TASK_FONT_MEANING = "font_meaning";

    static final String TASK_TARGETED_WRITING = "targeted_writing";
    static final String TASK_TYPING_MEANING = "typing_meaning";
    static final String TASK_WORD_READING = "word_reading";
    static final String TASK_REPAIR_WRITING = "repair_writing";

    static final String EMPTY_ACTIVE_PRACTICE_TITLE = "No active practice yet";
    static final String EMPTY_ACTIVE_PRACTICE_BODY = "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.";

    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final HintProgression hintProgression = new HintProgression();
    final StudySessionTracker studySessionTracker = new StudySessionTracker();
    LocalStore store;
    AnkiDroidGateway gateway;
    LinearLayout content;
    ScrollView contentScroll;
    LinearLayout studyActionBar;
    int studyActionBarBottomInset;
    RecordsSchedulerModels.StudySession activeSession;
    RecordsImportModels.SimilarKanjiWritingRepair activeSimilarWritingRepair;
    RecordsSchedulerModels.AdaptiveLoadPlan activeStudyPlan;
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
    Button undoStrokeButton;
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
    float flashcardTouchStartX;
    float flashcardTouchStartY;
    boolean activityPaused;
    HintState currentHintState = HintState.initial();
    Map<String, StrokeGuide> strokeGuides;
    WritingRecognizer writingRecognizer;
    DictionaryLookup dictionaryLookup;
    LocalStore.ReminderSettings pendingReminderSettings;
    boolean settingsAnkiExpanded = true;
    boolean settingsStudyExpanded;
    boolean settingsSyncExpanded;
    boolean settingsAppExpanded;
    int updateUiRunCounter;
    int activeUpdateUiRunToken;
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
    abstract void initializeSessionProgressTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan);
    abstract DictionaryLookup currentDictionaryLookup();
    abstract RecordsImportModels.Example wordReadingExample(RecordsImportModels.DashboardRow row);
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
        boolean allowed = notificationsAllowedForReminders();
        Toast.makeText(this, ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed), allowed ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }

    void disableReminderAfterDeniedPermission(LocalStore.ReminderSettings pending) {
        LocalStore.ReminderSettings fallback = pending == null ? store.reminderSettings() : pending;
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(false, fallback.hour, fallback.minute);
        store.saveReminderSettings(new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute()));
        ReminderScheduler.cancel(this);
        Toast.makeText(this, ReminderSettingsSavePolicy.PERMISSION_DENIED_MESSAGE, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleFlashcardGesture(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    void base(String selected) {
        activeUpdateUiRunToken = 0;
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
        applyStudyActionBarPadding();
        studyActionBar.setBackgroundColor(STUDY_BG_SOFT);
        studyActionBar.setVisibility(View.GONE);
        root.addView(studyActionBar, new LinearLayout.LayoutParams(-1, -2));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                    .getInsets(WindowInsetsCompat.Type.systemBars());
            studyActionBarBottomInset = bars.bottom;
            content.setPadding(dp(18), dp(18) + bars.top, dp(18), dp(18) + bars.bottom);
            applyStudyActionBarPadding();
            return insets;
        });
        root.requestApplyInsets();
    }

    boolean isActiveToken(String token) {
        return activeSession != null && activeSession.token.equals(token);
    }

    String reviewToast(RecordsSchedulerModels.ReviewResult result, StudyStatsStore.StudyStreak streak) {
        int currentStreakDays = streak == null ? 0 : streak.currentDays;
        return HomeTextCopy.reviewToast(result.duplicate, result.appliedRating, currentStreakDays);
    }

    WritingRecognizer currentWritingRecognizer() {
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
        return WritingRecognizer.recognitionCandidates(result);
    }

    RecordsImportModels.DashboardRow findRow(List<RecordsImportModels.DashboardRow> rows, String kanji) {
        return StudyCollectionLookup.dashboardRowByKanji(rows, kanji);
    }

    RecordsStudyModels.StudyItem findStudyItem(List<RecordsStudyModels.StudyItem> items, String kanji) {
        return StudyCollectionLookup.studyItemByKanji(items, kanji);
    }

    String candidateText(List<RecognitionCandidate> candidates) {
        return WritingFeedbackCopy.candidateText(candidates);
    }

    StrokeGuide strokeGuide(String kanji) {
        if (strokeGuides == null) {
            strokeGuides = StrokeGuideAssets.load(this);
        }
        return strokeGuides.get(kanji);
    }

    RecordsSyncModels.Settings settings() {
        return SyncSettings.fromStore(store);
    }

    RecordsBase.StudyLadderSettings studyLadderSettings() {
        return store.studyLadderSettings();
    }

    RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return new AdaptiveLoadPlanner().plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                rows,
                                items,
                                store.reviewStatsSince(now - 7 * DAY_MILLIS),
                                store.studyStreak(now).currentDays,
                                store.studiedKanjiSince(startOfDay(now)),
                                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                                        store.adaptiveLoadWorkPercent(),
                                        store.adaptiveLoadMode(),
                                        store.adaptiveLoadMaxItems()
                                ),
                                now
                        )
                        .settings(settings())
                        .build()
        );
    }

    RecordsSchedulerModels.AdaptiveLoadPlan studyPlanForMode(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        if (!studyMoreNewCardKanji.isEmpty()) {
            return studyMoreNewCardsPlan(rows, items, now);
        }
        if (continueAllKanjiSession) {
            return allCurrentProblemKanjiPlan(rows, items, now);
        }
        return adaptivePlan(rows, items, now);
    }

    RecordsSchedulerModels.AdaptiveLoadPlan studyMoreNewCardsPlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return FocusedStudyPlanPolicy.studyMoreNewCardsPlan(studyMoreNewCardKanji, rows, items, now);
    }

    RecordsSchedulerModels.AdaptiveLoadPlan allCurrentProblemKanjiPlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
                rows,
                items,
                store.studiedKanjiSince(startOfDay(now)),
                now);
    }

    void prepareStudyContent(RecordsSchedulerModels.AdaptiveLoadPlan plan, boolean fillViewport) {
        activeStudyPlan = plan;
        content.removeAllViews();
        if (contentScroll != null) {
            contentScroll.setFillViewport(fillViewport);
        }
        content.addView(studyTopBar(plan));
    }

    View studyTopBar(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        initializeSessionProgressTarget(plan);
        StudySessionProgressTracker.TopBarProgress progress = studySessionTracker.topBarProgress(
                activeSession != null,
                continueAllKanjiSession
        );
        return new StudyTopBarView(this, progress.completed, progress.target, progress.fraction, this::renderHome, this::renderSettings);
    }

    void styleStudyActionBarShell() {
        if (studyActionBar != null) {
            applyStudyActionBarPadding();
            studyActionBar.setBackgroundColor(STUDY_BG);
        }
    }

    void applyStudyActionBarPadding() {
        if (studyActionBar != null) {
            studyActionBar.setPadding(dp(18), dp(10), dp(18), dp(8) + studyActionBarBottomInset);
        }
    }

    void emptyState(String title, String body) {
        LinearLayout empty = band(GOLD);
        empty.addView(text(title, 24, INK, true));
        empty.addView(text(body, 16, INK, false));
        content.addView(empty);
    }

    String countText(int count, String singular, String plural) {
        return StudyTextCopy.countText(count, singular, plural);
    }

    String rowMeaning(RecordsImportModels.DashboardRow row) {
        return StudyTextCopy.rowMeaning(row);
    }

    String sessionClue(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.sessionClue(currentDictionaryLookup(), session);
    }

    String canonicalKanjiMeaning(String kanji, String fallback, int maxChars) {
        return StudyTextCopy.canonicalKanjiMeaning(currentDictionaryLookup(), kanji, fallback, maxChars);
    }

    String wordPrompt(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.wordPrompt(session);
    }

    String cleanLearnerText(String raw, String fallback, int maxChars) {
        return StudyTextCopy.cleanLearnerText(raw, fallback, maxChars);
    }

    String compact(String value, int maxChars) {
        return StudyTextCopy.compact(value, maxChars);
    }

    void addSpace(int dp) {
        SpaceView space = new SpaceView(this);
        content.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    String labelForTask(String task) {
        return StudyTaskCopy.labelForTask(task);
    }

    String adaptiveFocusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return AdaptiveFocusCopy.adaptiveFocusText(plan);
    }

    String guideLabel(int level, StrokeGuide guide) {
        return WritingFeedbackCopy.guideLabel(level, guide);
    }

    String guideLabel(HintState state, StrokeGuide guide) {
        return WritingFeedbackCopy.guideLabel(state, guide);
    }

    String attemptProgressText(WritingAnalysis analysis) {
        if (analysis == null) {
            return "";
        }
        Integer activeWritingLevel = activeSession == null ? null : activeSession.item.writingLevel;
        return WritingFeedbackCopy.attemptProgressText(analysis, activeWritingLevel, shouldIncreaseSupportAfterAnalysis(analysis));
    }

    String stageLabel(HintLevel level) {
        return WritingFeedbackCopy.stageLabel(level);
    }

    String targetRevealText(WritingAnalysis analysis) {
        String targetKanji = activeSession == null ? null : activeSession.item.kanji;
        return WritingFeedbackCopy.targetRevealText(analysis, targetKanji);
    }

    long startOfDay(long now) {
        return LocalDayPolicy.localDayStart(now);
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

    static final class QueueEntry extends FocusQueuePolicy.QueueEntry {
        QueueEntry(RecordsImportModels.DashboardRow row, RecordsStudyModels.StudyItem item) {
            super(row, item);
        }
    }

}
