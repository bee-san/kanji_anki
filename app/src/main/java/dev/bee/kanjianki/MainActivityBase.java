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
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.domain.scheduler.StudyProgressSnapshot;
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

    RoomLegacyStudySnapshot legacyStudySnapshot() {
        return new RoomLegacyStudySnapshot(store.activeDashboardRows(), store.studyItems());
    }

    boolean isActiveToken(String token) {
        return activeSession != null && activeSession.token.equals(token);
    }

    String reviewToast(RecordsSchedulerModels.ReviewResult result, StudyStatsStore.StudyStreak streak) {
        if (result.duplicate) {
            return "Already saved.";
        }
        String streakText = streak == null || streak.currentDays <= 0 ? "" : " " + streakHeadline(streak) + ".";
        if (RATING_AGAIN.equals(result.appliedRating)) {
            return "Saved. This kanji will come back soon." + streakText;
        }
        return "Saved." + streakText;
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
        List<RecognitionCandidate> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (WritingRecognizer.Candidate candidate : result.candidates) {
            out.add(new RecognitionCandidate(candidate.text, candidate.score));
        }
        return out;
    }

    RecordsImportModels.DashboardRow findRow(List<RecordsImportModels.DashboardRow> rows, String kanji) {
        for (RecordsImportModels.DashboardRow row : rows) {
            if (row.kanji.equals(kanji)) {
                return row;
            }
        }
        return null;
    }

    RecordsStudyModels.StudyItem findStudyItem(List<RecordsStudyModels.StudyItem> items, String kanji) {
        for (RecordsStudyModels.StudyItem item : items) {
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

    RecordsSyncModels.Settings settings() {
        return SyncSettings.fromStore(store);
    }

    RecordsBase.StudyLadderSettings studyLadderSettings() {
        return store.studyLadderSettings();
    }

    RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return new LegacyAdaptiveStudyPlannerBridge().plan(
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
        List<String> focus = new ArrayList<>();
        for (String kanji : studyMoreNewCardKanji) {
            if (findRow(rows, kanji) != null) {
                focus.add(kanji);
            }
        }
        int remaining = 0;
        for (String kanji : focus) {
            RecordsStudyModels.StudyItem item = findStudyItem(items, kanji);
            if (itemDueForFocus(item, now)) {
                remaining++;
            }
        }
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
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

    RecordsSchedulerModels.AdaptiveLoadPlan allCurrentProblemKanjiPlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        List<String> focus = new ArrayList<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            focus.add(row.kanji);
        }
        Set<String> studied = store.studiedKanjiSince(startOfDay(now));
        int remaining = 0;
        for (String kanji : focus) {
            RecordsStudyModels.StudyItem item = findStudyItem(items, kanji);
            if (!studied.contains(kanji) || itemDueForFocus(item, now)) {
                remaining++;
            }
        }
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
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

    boolean itemDueForFocus(RecordsStudyModels.StudyItem item, long now) {
        if (item == null || STATE_RETIRED.equals(item.state)) {
            return false;
        }
        if (STATE_LEARNING.equals(item.state)) {
            return item.dueAtMillis <= now;
        }
        return item.totalReviews > 0 && item.dueAtMillis <= now;
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
        StudyProgressSnapshot progress = studySessionTracker.progressSnapshot(activeSession != null, continueAllKanjiSession);
        return new StudyTopBarView(
                this,
                progress.getVisibleCompletedCount(),
                progress.getVisibleTargetCount(),
                progress.getFraction(),
                this::renderHome,
                this::renderSettings
        );
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
        return count + " " + (count == 1 ? singular : plural);
    }

    String rowMeaning(RecordsImportModels.DashboardRow row) {
        return cleanLearnerText(row.primaryMeaning, row.reasonCode, 72);
    }

    String sessionClue(RecordsSchedulerModels.StudySession session) {
        String raw = session.row == null || session.row.primaryMeaning.isEmpty()
                ? session.prompt
                : session.row.primaryMeaning;
        return canonicalKanjiMeaning(session == null ? "" : session.item.kanji, raw, 96);
    }

    String canonicalKanjiMeaning(String kanji, String fallback, int maxChars) {
        DictionaryLookup.KanjiEntry entry = currentDictionaryLookup().lookupKanji(kanji);
        if (entry != null) {
            String meaning = StudyCueTexts.displayGlosses(entry.meanings, 2);
            if (!meaning.isEmpty()) {
                return compact(meaning, maxChars);
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars);
    }

    String wordPrompt(RecordsSchedulerModels.StudySession session) {
        RecordsImportModels.Example example = session == null ? null : wordReadingExample(session.row);
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
            case BridgeScheduler.TASK_MEANING_KANJI -> "Meaning -> kanji";
            case TASK_TYPING_MEANING, BridgeScheduler.TASK_TYPE_MEANING -> "Type the meaning";
            case TASK_FONT_MEANING -> "Font -> meaning";
            case TASK_WORD_READING -> "Word -> reading";
            case BridgeScheduler.TASK_WRITE_KANJI -> "Write kanji";
            case BridgeScheduler.TASK_SIMILAR_KANJI -> LABEL_SIMILAR_KANJI;
            case "meaning_flashcard" -> "Quick recall";
            case "font_recognition" -> "Font check";
            case TASK_REPAIR_WRITING -> "Write to repair";
            case TASK_TARGETED_WRITING -> "Focused practice";
            case "context_writing" -> "New problem kanji";
            case "guided_writing" -> "Guided review";
            case "blind_writing", "sampled_handwriting" -> "Memory check";
            case "confusable_recognition" -> "Learn the shape";
            default -> LABEL_STUDY;
        };
    }

    String adaptiveFocusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
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

    static final class QueueEntry {
        final RecordsImportModels.DashboardRow row;
        final RecordsStudyModels.StudyItem item;

        QueueEntry(RecordsImportModels.DashboardRow row, RecordsStudyModels.StudyItem item) {
            this.row = row;
            this.item = item;
        }
    }

}
