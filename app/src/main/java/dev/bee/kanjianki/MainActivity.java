package dev.bee.kanjianki;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.study.HintPolicy;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
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
    private static final int BG = Color.rgb(255, 247, 251);
    private static final int INK = Color.rgb(45, 22, 53);
    private static final int MUTED = Color.rgb(108, 86, 116);
    private static final int CORAL = Color.rgb(255, 76, 118);
    private static final int TEAL = Color.rgb(0, 174, 181);
    private static final int GOLD = Color.rgb(255, 214, 64);
    private static final int BLUE = Color.rgb(110, 92, 230);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LocalStore store;
    private AnkiDroidGateway gateway;
    private LinearLayout root;
    private LinearLayout content;
    private Records.StudySession activeSession;
    private DrawingPadView drawingPad;
    private TextView studyStatus;
    private Button checkWritingButton;
    private Button downloadModelButton;
    private Button manualOverrideButton;
    private Button nextAfterPassButton;
    private Button practiceWithGuideButton;
    private Button advanceGuideButton;
    private View studyAnswerPanel;
    private WritingAnalysis activeAnalysis;
    private boolean checkingWriting;
    private boolean writingModelDownloaded;
    private boolean writingModelStatusKnown;
    private int hintsUsed;
    private int currentPracticeLevel;
    private Map<String, StrokeGuide> strokeGuides;
    private WritingRecognizer writingRecognizer;
    private static AnkiDroidGateway ankiDroidGatewayForTests;
    private static WritingRecognizer writingRecognizerForTests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        gateway = ankiDroidGatewayForTests == null ? new AnkiDroidGateway(this) : ankiDroidGatewayForTests;
        requestAnkiPermissionIfNeeded();
        renderHome();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (writingRecognizer != null && writingRecognizer != writingRecognizerForTests) {
            writingRecognizer.close();
        }
        super.onDestroy();
    }

    public static void setWritingRecognizerForTests(WritingRecognizer recognizer) {
        writingRecognizerForTests = recognizer;
    }

    public static void setAnkiDroidGatewayForTests(AnkiDroidGateway gateway) {
        ankiDroidGatewayForTests = gateway;
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
        nav.addView(navButton("Kanji", selected.equals("kanji"), this::renderKanjiList));
        nav.addView(navButton("Settings", selected.equals("settings"), this::renderSettings));
        nav.addView(navButton("Update", selected.equals("update"), this::renderUpdate));
        return nav;
    }

    private Button navButton(String label, boolean active, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setTextSize(13);
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
        TextView title = text("Kanji Anki", 42, INK, true);
        title.setLetterSpacing(0);
        content.addView(title);
        content.addView(text("Write the kanji your Kiku reviews keep exposing.", 18, MUTED, false));
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
            hero.addView(text("Sync once to build your personal writing queue.", 16, Color.WHITE, false));
        }
        hero.addView(text("New writing items come from your own Anki cards.", 14, Color.WHITE, false));
        content.addView(hero);
        addSpace(18);

        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            Button syncButton = primaryButton("Sync from AnkiDroid", TEAL);
            syncButton.setOnClickListener(v -> runSync());
            content.addView(syncButton);
        } else {
            Button studyButton = primaryButton("Start writing practice", CORAL);
            studyButton.setOnClickListener(v -> renderStudy());
            content.addView(studyButton);

            Button syncAgainButton = secondaryButton("Sync again");
            syncAgainButton.setOnClickListener(v -> runSync());
            content.addView(syncAgainButton);
        }

        addSpace(16);
        if (rows.isEmpty()) {
            emptyState("No kanji queued yet", "After the first sync, this screen shows the kanji that need writing practice.");
        } else {
            content.addView(sectionTitle("Next kanji to repair"));
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                content.addView(rowView(rows.get(i)));
            }
        }
    }

    private void runSync() {
        base("home");
        content.addView(text("Syncing AnkiDroid", 34, INK, true));
        content.addView(text("Building your writing queue from the latest Kiku cards.", 17, MUTED, false));
        io.execute(() -> {
            ManualSyncEngine.SyncResult result = new ManualSyncEngine(this, store, gateway, settings()).run();
            main.post(() -> {
                renderSyncResult(result);
            });
        });
    }

    private void renderSyncResult(ManualSyncEngine.SyncResult result) {
        base("home");
        if (result.success) {
            content.addView(text("Sync complete", 34, INK, true));
            LinearLayout summary = band(TEAL);
            summary.addView(text(countText(result.dashboardRows, "kanji ready", "kanji ready"), 24, Color.WHITE, true));
            summary.addView(text(countText(result.importedSuspendedKanji, "suspended kanji added", "suspended kanji added"), 16, Color.WHITE, false));
            if (result.message != null && !result.message.isEmpty()) {
                summary.addView(text(result.message, 14, Color.WHITE, false));
            }
            content.addView(summary);
            if (result.dashboardRows > 0) {
                Button study = primaryButton("Start writing practice", CORAL);
                study.setOnClickListener(v -> renderStudy());
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
            retry.setOnClickListener(v -> runSync());
            content.addView(retry);
            Button home = secondaryButton("Back home");
            home.setOnClickListener(v -> renderHome());
            content.addView(home);
        }
    }

    private void renderKanjiList() {
        base("kanji");
        content.addView(text("Kanji to repair", 34, INK, true));
        content.addView(text("Tap a row to see why it was picked, then practice it or continue the queue.", 16, MUTED, false));
        addSpace(12);
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            emptyState("No kanji yet", "Sync from AnkiDroid first.");
            return;
        }
        for (Records.DashboardRow row : rows) {
            content.addView(rowView(row));
        }
    }

    private View rowView(Records.DashboardRow row) {
        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.setOnClickListener(v -> renderDetail(row.kanji));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView kanji = text(row.kanji, 44, INK, true);
        kanji.setGravity(Gravity.CENTER);
        top.addView(kanji, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(rowMeaning(row), 19, INK, true));
        copy.addView(text(row.reasonText, 14, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip("priority " + row.weaknessScore, CORAL));
        chips.addView(chip(countText(row.suspendedExampleCount, "missed example", "missed examples"), BLUE));
        chips.addView(chip(countText(row.matureSupportCount, "known example", "known examples"), TEAL));
        box.addView(chips);
        return box;
    }

    private void renderDetail(String kanji) {
        base("kanji");
        Records.DashboardRow row = store.rowForKanji(kanji);
        if (row == null) {
            emptyState("Kanji not found", "This row may have disappeared after a sync.");
            return;
        }
        TextView glyph = text(row.kanji, 92, INK, true);
        glyph.setGravity(Gravity.CENTER);
        content.addView(glyph);
        content.addView(text(rowMeaning(row), 25, INK, true));
        content.addView(text(row.reading, 20, TEAL, true));
        addSpace(10);
        LinearLayout why = band(BLUE);
        why.addView(text("Why it is here", 22, Color.WHITE, true));
        why.addView(text(row.reasonText, 17, Color.WHITE, false));
        why.addView(text("Anki browser: " + row.browserSearch, 14, Color.WHITE, false));
        content.addView(why);
        Button practice = primaryButton("Practice this kanji", CORAL);
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
        addSpace(12);
        content.addView(sectionTitle("Examples"));
        for (Records.Example example : row.examples) {
            content.addView(exampleView(example));
        }
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
        box.addView(text("Meet the kanji", 22, INK, true));
        TextView glyph = text(session.item.kanji, 84, INK, true);
        glyph.setGravity(Gravity.CENTER);
        box.addView(glyph, new LinearLayout.LayoutParams(-1, dp(104)));
        if (session.row != null) {
            box.addView(text("Meaning: " + rowMeaning(session.row), 17, INK, true));
            if (!session.row.reading.isEmpty()) {
                box.addView(text("Reading: " + session.row.reading, 16, TEAL, true));
            }
            Records.Example example = firstExample(session.row);
            if (example != null) {
                box.addView(text("Example: " + example.expression + (example.reading.isEmpty() ? "" : "  " + example.reading), 16, INK, true));
                if (!example.meaning.isEmpty()) {
                    box.addView(text(cleanLearnerText(example.meaning, "", 96), 14, MUTED, false));
                }
            }
        } else {
            box.addView(text(session.prompt, 16, MUTED, false));
        }
        box.addView(text("Copy it until the shape feels familiar, then start the memory check.", 14, MUTED, false));
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
            content.addView(text("Writing practice", 34, INK, true));
            emptyState("Nothing to write yet", "Sync from AnkiDroid first. Study opens once the app finds kanji to repair.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.StudyItem> seeded = scheduler.seedQueue(rows, store.studyItems(), settings(), System.currentTimeMillis(), startOfDay(System.currentTimeMillis()));
        store.replaceStudyItems(seeded);
        activeSession = scheduler.nextSession(seeded, rows, System.currentTimeMillis());
        if (activeSession == null) {
            content.addView(text("Queue resting", 34, INK, true));
            content.addView(text("No kanji is due right now.", 18, MUTED, false));
            Button back = primaryButton("Back home", TEAL);
            back.setOnClickListener(v -> renderHome());
            content.addView(back);
            return;
        }
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private void renderStudyForKanji(String kanji) {
        base("study");
        List<Records.DashboardRow> rows = store.dashboardRows();
        Records.DashboardRow row = findRow(rows, kanji);
        if (row == null) {
            content.addView(text("Writing practice", 34, INK, true));
            emptyState("Kanji not available", "This row may have changed after sync.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = System.currentTimeMillis();
        List<Records.StudyItem> seeded = scheduler.seedQueue(rows, store.studyItems(), settings(), now, startOfDay(now));
        store.replaceStudyItems(seeded);
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
        activeSession = new Records.StudySession(
                item.withToken(token),
                row,
                token,
                "targeted_writing",
                true,
                row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning
        );
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private void renderSession(Records.StudySession session) {
        content.removeAllViews();
        activeAnalysis = null;
        checkingWriting = false;
        hintsUsed = 0;
        currentPracticeLevel = Math.max(0, Math.min(3, session.item.writingLevel));

        content.addView(text("Writing practice", 30, INK, true));
        LinearLayout stage = band(CORAL);
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (session.row != null) {
            stage.addView(text("Meaning cue: " + sessionClue(session), 17, Color.WHITE, false));
            if (!session.row.reading.isEmpty()) {
                stage.addView(text("Reading cue: " + session.row.reading, 15, Color.WHITE, false));
            }
            stage.addView(text("Why this is due: " + session.row.reasonText, 15, Color.WHITE, false));
        } else {
            stage.addView(text(session.prompt, 17, Color.WHITE, false));
        }
        content.addView(stage);
        studyAnswerPanel = learningPanel(session);
        content.addView(studyAnswerPanel);

        content.addView(sectionTitle("Writing"));
        studyStatus = text(guideLabel(currentPracticeLevel), 16, MUTED, false);
        content.addView(studyStatus);
        StrokeGuide guide = strokeGuide(session.item.kanji);
        drawingPad = new DrawingPadView(this);
        drawingPad.setTarget(session.item.kanji);
        drawingPad.setGuide(guide, currentPracticeLevel, false);
        content.addView(drawingPad, new LinearLayout.LayoutParams(-1, dp(330)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = secondaryButton("Erase");
        clear.setOnClickListener(v -> {
            drawingPad.clear();
            activeAnalysis = null;
            setStudyStatus(guideLabel(currentPracticeLevel), MUTED);
            updateResultActions();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        advanceGuideButton = secondaryButton(stageAdvanceButtonText(currentPracticeLevel, guide));
        advanceGuideButton.setOnClickListener(v -> advanceWritingStage());
        actions.addView(advanceGuideButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        content.addView(actions);

        checkWritingButton = primaryButton("Check my writing", CORAL);
        checkWritingButton.setOnClickListener(v -> checkWriting());
        content.addView(checkWritingButton);

        downloadModelButton = secondaryButton("Download handwriting checker");
        downloadModelButton.setOnClickListener(v -> downloadWritingModel());
        content.addView(downloadModelButton);

        nextAfterPassButton = primaryButton("Next", TEAL);
        nextAfterPassButton.setOnClickListener(v -> submitReview(activeAnalysis == null ? "again" : activeAnalysis.rating, false));
        content.addView(nextAfterPassButton);

        manualOverrideButton = secondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview("good", true));
        content.addView(manualOverrideButton);

        practiceWithGuideButton = secondaryButton("Show guide and try again");
        practiceWithGuideButton.setOnClickListener(v -> {
            currentPracticeLevel = 0;
            hintsUsed++;
            activeAnalysis = null;
            drawingPad.clear();
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, false);
            setStudyStatus(guideLabel(currentPracticeLevel), MUTED);
            updateResultActions();
        });
        content.addView(practiceWithGuideButton);

        updateResultActions();
        refreshWritingModelStatus();
    }

    private void checkWriting() {
        if (activeSession == null) {
            return;
        }
        if (drawingPad == null || !drawingPad.hasInk()) {
            activeAnalysis = WritingAnalysisEngine.noInk();
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
            activeAnalysis = WritingAnalysisEngine.noInk();
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
            activeAnalysis = WritingAnalysisEngine.modelUnavailable("The handwriting checker is unavailable on this device.");
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
                    activeAnalysis = WritingAnalysisEngine.modelUnavailable("Download the handwriting checker before automatic checks.");
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
                    activeAnalysis = WritingAnalysisEngine.recognitionError(error.getMessage());
                } else {
                    activeAnalysis = WritingAnalysisEngine.analyze(target, sample, guide, candidates(result));
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
        Records.ReviewRequest request = new Records.ReviewRequest(
                activeSession.item.kanji,
                activeSession.token,
                rating,
                writingRequired,
                passed,
                override,
                hintsUsed
        );
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(store.consumedTokens());
        long now = System.currentTimeMillis();
        Records.SchedulerParameters parameters = store.schedulerParameters();
        Records.ReviewResult result = scheduler.applyReview(activeSession.item, request, consumed, now, parameters);
        if (!result.duplicate) {
            store.saveStudyItem(result.item);
            store.saveReview(request, result.appliedRating, now);
            Records.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
            if (tuned.lastAdjustedAtMillis != parameters.lastAdjustedAtMillis || tuned.lastAdjustmentReviewCount != parameters.lastAdjustmentReviewCount) {
                store.saveSchedulerParameters(tuned);
            }
        }
        Toast.makeText(this, reviewToast(result), Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private void advanceWritingStage() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        int next = nextPracticeLevel(currentPracticeLevel, strokeGuide(activeSession.item.kanji));
        if (next != currentPracticeLevel) {
            if (next == 3 || currentPracticeLevel == 3) {
                drawingPad.clear();
                activeAnalysis = null;
            }
            currentPracticeLevel = next;
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, false);
            setStudyStatus(guideLabel(currentPracticeLevel), MUTED);
            if (advanceGuideButton != null) {
                advanceGuideButton.setText(stageAdvanceButtonText(currentPracticeLevel, strokeGuide(activeSession.item.kanji)));
            }
            updateResultActions();
        }
    }

    private int nextPracticeLevel(int level, StrokeGuide guide) {
        if (level <= 0) {
            return 1;
        }
        if (level == 1 && guide != null && guide.strokeCount() >= 3) {
            return 2;
        }
        return 3;
    }

    private String stageAdvanceButtonText(int level, StrokeGuide guide) {
        int next = nextPracticeLevel(level, guide);
        if (next == 1) {
            return "I copied it";
        }
        if (next == 2) {
            return "Try with less help";
        }
        return "Start memory check";
    }

    private void showAnalysis(WritingAnalysis analysis) {
        if (drawingPad != null && activeSession != null) {
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, true);
        }
        int color = analysis.writingPassed ? TEAL : CORAL;
        String candidates = candidateText(analysis.candidates);
        setStudyStatus(analysis.message + targetRevealText(analysis) + (candidates.isEmpty() ? "" : "\nIt saw: " + candidates), color);
        updateResultActions();
    }

    private void updateResultActions() {
        boolean hasResult = activeAnalysis != null;
        boolean passed = hasResult && activeAnalysis.writingPassed;
        boolean submittable = activeAnalysis != null && canSubmitAnalysis(activeAnalysis);
        if (checkWritingButton != null) {
            boolean readyToCheck = currentPracticeLevel == 3 || hasResult;
            checkWritingButton.setVisibility(!passed && readyToCheck ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(!checkingWriting);
            checkWritingButton.setText(checkingWriting ? "Checking..." : "Check my writing");
        }
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(writingModelStatusKnown && writingModelDownloaded ? View.GONE : View.VISIBLE);
        }
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(submittable ? View.VISIBLE : View.GONE);
            if (submittable) {
                nextAfterPassButton.setText(nextReviewButtonText(activeAnalysis.rating));
            }
        }
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(hasResult && canManualOverride(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(hasResult && !passed && canPracticeAfterAnalysis(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (advanceGuideButton != null) {
            advanceGuideButton.setVisibility(currentPracticeLevel == 3 ? View.GONE : View.VISIBLE);
        }
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(shouldShowLearningPanel(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean shouldShowLearningPanel(WritingAnalysis analysis) {
        if (currentPracticeLevel < 3) {
            return true;
        }
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
            case NO_STROKE_DATA:
            case RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    private boolean canSubmitAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
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
    }

    private void refreshWritingModelStatus() {
        writingModelStatusKnown = false;
        writingModelDownloaded = false;
        updateResultActions();
        String token = activeSession == null ? null : activeSession.token;
        WritingRecognizer recognizer = writingRecognizer();
        if (recognizer == null) {
            writingModelStatusKnown = true;
            setStudyStatus(guideLabel(currentPracticeLevel) + "\nAutomatic handwriting checks are unavailable on this device.", CORAL);
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
                setStudyStatus(guideLabel(currentPracticeLevel) + "\nUnable to read handwriting checker status.", CORAL);
            } else if (!status.downloaded) {
                setStudyStatus(guideLabel(currentPracticeLevel) + "\nDownload the handwriting checker before the final check.", CORAL);
            } else {
                setStudyStatus(guideLabel(currentPracticeLevel) + "\nHandwriting checker ready.", MUTED);
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

    private String reviewToast(Records.ReviewResult result) {
        if (result.duplicate) {
            return "Already saved.";
        }
        if ("again".equals(result.appliedRating)) {
            return "Saved. This kanji will come back soon.";
        }
        return "Saved.";
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
            return "nothing clear";
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
        base("update");
        content.addView(text("GitHub updater", 34, INK, true));
        content.addView(text("Current version " + BuildConfig.VERSION_NAME + ". Checks GitHub Releases and opens Android's installer when an APK is ready.", 16, MUTED, false));
        Button button = primaryButton("Check for update", BLUE);
        button.setOnClickListener(v -> runUpdate());
        content.addView(button);
    }

    private void renderSettings() {
        base("settings");
        Records.Settings current = settings();
        content.addView(text("Settings", 34, INK, true));
        content.addView(text("Tune which suspended Kiku cards become writing practice.", 16, MUTED, false));
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

        LinearLayout mapping = band(BLUE);
        mapping.addView(text("Kiku fields used for clues", 22, Color.WHITE, true));
        mapping.addView(text("Expression -> kanji source\nExpressionReading -> reading\nMainDefinition -> meaning\nSentence -> context\nFrequency/FreqSort -> collection metadata", 15, Color.WHITE, false));
        content.addView(mapping);

        LinearLayout attribution = panelBox(Color.WHITE, Color.rgb(221, 214, 255));
        attribution.addView(text("Stroke data", 22, INK, true));
        attribution.addView(text(kanjiVgAttribution(), 14, MUTED, false));
        content.addView(attribution);
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

    private void runUpdate() {
        base("update");
        content.addView(text("Checking release", 32, INK, true));
        content.addView(text("Downloading metadata and verifying assets.", 16, MUTED, false));
        io.execute(() -> {
            GitHubUpdater.UpdateResult result = new GitHubUpdater(this).checkDownloadAndPrepareInstaller();
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
        Records.Settings defaults = Records.Settings.kikuDefaults();
        int cutoff = store == null ? defaults.suspendedRankCutoff : store.getIntSetting("suspended_rank_cutoff", defaults.suspendedRankCutoff);
        return new Records.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                cutoff,
                defaults.activeQueueCap,
                defaults.newPerDay
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
        if ("targeted_writing".equals(task)) {
            return "Learn this kanji";
        }
        if ("context_writing".equals(task)) {
            return "Learn this kanji";
        }
        if ("guided_writing".equals(task)) {
            return "Copy the shape";
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
        return "Writing practice";
    }

    private String guideLabel(int level) {
        switch (level) {
            case 0:
                return "Step 1 of 4: look at the kanji above, then trace the numbered strokes.";
            case 1:
                return "Step 2 of 4: copy with the faint guide while the kanji is still visible.";
            case 2:
                return "Step 3 of 4: only the current stroke is hinted. Use the panel if you need it.";
            default:
                return "Step 4 of 4: the answer is hidden. Write from memory, then check.";
        }
    }

    private String nextReviewButtonText(String rating) {
        if ("again".equals(rating)) {
            return "Save miss";
        }
        if ("hard".equals(rating)) {
            return "Save and review soon";
        }
        return "Save and continue";
    }

    private String targetRevealText(WritingAnalysis analysis) {
        if (activeSession == null || analysis == null) {
            return "";
        }
        switch (analysis.status) {
            case PASS:
            case CLOSE:
            case WRONG:
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

    public static final class DrawingPadView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Path> paths = new ArrayList<>();
        private final List<List<CapturedStroke.Point>> committedStrokes = new ArrayList<>();
        private final List<CapturedStroke.Point> currentPoints = new ArrayList<>();
        private Path current;
        private StrokeGuide guide;
        private int guideLevel = 3;
        private boolean revealGuide;
        private String target = "";
        private int activePointerId = -1;

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
        }

        public boolean hasInk() {
            return !committedStrokes.isEmpty();
        }

        public void clear() {
            paths.clear();
            committedStrokes.clear();
            currentPoints.clear();
            current = null;
            activePointerId = -1;
            invalidate();
        }

        public void setTarget(String target) {
            this.target = target == null ? "" : target;
        }

        public void setGuide(StrokeGuide guide, int level, boolean revealGuide) {
            this.guide = guide;
            this.guideLevel = Math.max(0, Math.min(3, level));
            this.revealGuide = revealGuide;
            invalidate();
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
            for (Path path : paths) {
                canvas.drawPath(path, paint);
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
                List<HintPolicy.StrokeHint> hints = HintPolicy.hintsFor(guide, guideLevel, committedStrokes.size(), revealGuide);
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
                    drawStartMarker(canvas, first.x * width, first.y * height, hint.strokeIndex + 1, hint.current);
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

        private void drawStartMarker(Canvas canvas, float x, float y, int number, boolean active) {
            markerPaint.setColor(Color.argb(230, 255, 255, 255));
            canvas.drawCircle(x, y, 17f, markerPaint);
            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(3f);
            markerPaint.setColor(active ? CORAL : Color.rgb(111, 74, 39));
            canvas.drawCircle(x, y, 17f, markerPaint);
            markerPaint.setStyle(Paint.Style.FILL);
            markerText.setTextSize(18f);
            markerText.setColor(active ? CORAL : Color.rgb(111, 74, 39));
            canvas.drawText(Integer.toString(number), x, y - (markerText.descent() + markerText.ascent()) / 2f, markerText);
        }
    }
}
