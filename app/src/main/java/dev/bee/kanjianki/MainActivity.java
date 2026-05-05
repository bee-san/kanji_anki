package dev.bee.kanjianki;

import android.app.Activity;
import android.app.AlertDialog;
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
    private Button hintButton;
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
        nav.addView(navButton("Queue", selected.equals("kanji"), this::renderKanjiList));
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
            Button syncButton = primaryButton("Sync AnkiDroid", TEAL);
            syncButton.setOnClickListener(v -> confirmSync());
            content.addView(syncButton);
        } else {
            Button studyButton = primaryButton("Start writing practice", CORAL);
            studyButton.setOnClickListener(v -> renderStudy());
            content.addView(studyButton);

            Button syncAgainButton = secondaryButton("Sync again");
            syncAgainButton.setOnClickListener(v -> confirmSync());
            content.addView(syncAgainButton);
        }

        addSpace(16);
        if (rows.isEmpty()) {
            emptyState("No kanji queued yet", "After the first sync, this screen shows the kanji that need writing practice.");
        } else {
            long now = System.currentTimeMillis();
            List<QueueEntry> entries = queuedEntries(rows, studyQueue(rows, now, false), now);
            content.addView(sectionTitle("Your active kanji queue"));
            if (entries.isEmpty()) {
                emptyState("Queue resting", "Sync has candidate kanji, but nothing is currently admitted for writing practice.");
            }
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                content.addView(queueRowView(entries.get(i), now));
            }
        }
    }

    private void confirmSync() {
        new AlertDialog.Builder(this)
                .setTitle("Sync and archive imported cards?")
                .setMessage("Kanji Anki reads your Kiku cards from AnkiDroid. Suspended cards copied into writing practice may be tagged kanji_anki_archived in AnkiDroid after they are safely stored here, so they do not keep returning as new problems.")
                .setPositiveButton("Sync and tag archive", (dialog, which) -> runSync())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runSync() {
        base("home");
        content.addView(text("Syncing AnkiDroid", 34, INK, true));
        content.addView(text("Reading your latest Kiku cards, copying problem kanji locally, and tagging imported suspended cards as archived when AnkiDroid allows it.", 17, MUTED, false));
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
            retry.setOnClickListener(v -> confirmSync());
            content.addView(retry);
            Button home = secondaryButton("Back home");
            home.setOnClickListener(v -> renderHome());
            content.addView(home);
        }
    }

    private void renderKanjiList() {
        base("kanji");
        content.addView(text("Practice queue", 34, INK, true));
        content.addView(text("Only kanji admitted from your AnkiDroid problem cards appear here. New candidates wait behind the daily and active queue caps.", 16, MUTED, false));
        addSpace(12);
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            emptyState("No queued kanji yet", "Sync from AnkiDroid first. The app will build a personal writing queue from your own Kiku cards.");
            return;
        }
        long now = System.currentTimeMillis();
        List<Records.StudyItem> items = studyQueue(rows, now, false);
        List<QueueEntry> entries = queuedEntries(rows, items, now);
        int due = new BridgeScheduler().dueCount(items, now);

        LinearLayout summary = band(BLUE);
        summary.addView(text(countText(entries.size(), "active kanji", "active kanji"), 24, Color.WHITE, true));
        summary.addView(text(countText(due, "due now", "due now") + ". " + countText(Math.max(0, rows.size() - entries.size()), "candidate waiting to join later", "candidates waiting to join later") + ".", 16, Color.WHITE, false));
        summary.addView(text("Study mixes due items and brings misses back soon.", 15, Color.WHITE, false));
        content.addView(summary);

        Button study = primaryButton(due > 0 ? "Review due now" : (entries.isEmpty() ? "Learn next problem kanji" : "Start writing practice"), CORAL);
        study.setOnClickListener(v -> renderStudy());
        content.addView(study);

        if (entries.isEmpty()) {
            emptyState("Queue resting", "Your synced candidates are either retired or waiting for the active queue to open up.");
            return;
        }
        for (QueueEntry entry : entries) {
            content.addView(queueRowView(entry, now));
        }
    }

    private List<Records.StudyItem> studyQueue(List<Records.DashboardRow> rows, long now, boolean persist) {
        if (!persist) {
            return store.studyItems();
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.StudyItem> seeded = scheduler.seedQueue(rows, store.studyItems(), settings(), now, startOfDay(now));
        store.replaceStudyItems(seeded);
        return seeded;
    }

    private List<QueueEntry> queuedEntries(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
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
                .comparingInt((QueueEntry entry) -> entry.item.dueAtMillis <= now ? 0 : 1)
                .thenComparingInt(entry -> stateRank(entry.item.state))
                .thenComparingLong(entry -> entry.item.dueAtMillis)
                .thenComparingInt(entry -> -entry.row.weaknessScore)
                .thenComparing(entry -> entry.row.kanji));
        return entries;
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

    private String helpLabel(int writingLevel) {
        switch (Math.max(0, Math.min(3, writingLevel))) {
            case 0:
                return "trace";
            case 1:
                return "guided";
            case 2:
                return "cue";
            default:
                return "memory";
        }
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
        chips.addView(chip("help " + helpLabel(item.writingLevel), BLUE));
        box.addView(chips);
        return box;
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
            content.addView(text("Writing practice", 34, INK, true));
            emptyState("Nothing to write yet", "Sync from AnkiDroid first. Study opens once the app finds kanji to repair.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        long now = System.currentTimeMillis();
        List<Records.StudyItem> seeded = studyQueue(rows, now, true);
        activeSession = scheduler.nextSession(seeded, rows, now);
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
        currentPracticeLevel = initialPracticeLevel(session);

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
        studyStatus = text(guideLabel(currentPracticeLevel, guide), 16, MUTED, false);
        content.addView(studyStatus);
        drawingPad = new DrawingPadView(this);
        drawingPad.setTarget(session.item.kanji);
        drawingPad.setGuide(guide, currentPracticeLevel, false);
        content.addView(drawingPad, new LinearLayout.LayoutParams(-1, studyPadHeight()));

        buildStudyActionBar();
        updateResultActions();
        refreshWritingModelStatus();
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
            setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)), MUTED);
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
        manualOverrideButton = secondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview("good", true));
        fallbackActions.addView(manualOverrideButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        practiceWithGuideButton = secondaryButton("Try again with full guide");
        practiceWithGuideButton.setOnClickListener(v -> {
            currentPracticeLevel = 0;
            hintsUsed++;
            activeAnalysis = null;
            drawingPad.clear();
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, false);
            setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nFresh guided try. Draw it again, then check.", MUTED);
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
        String adjustedRating = adjustedRatingForHelp(rating, override);
        boolean writingRequired = activeSession.writingRequired;
        boolean passed = !writingRequired || (activeAnalysis != null && activeAnalysis.writingPassed);
        Records.ReviewRequest request = new Records.ReviewRequest(
                activeSession.item.kanji,
                activeSession.token,
                adjustedRating,
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

    private int initialPracticeLevel(Records.StudySession session) {
        int stored = Math.max(0, Math.min(3, session.item.writingLevel));
        if (isRecallTask(session)) {
            return 3;
        }
        if ("targeted_writing".equals(session.taskType) || session.item.totalReviews == 0 || session.item.learningStep == 0) {
            return Math.min(stored, 1);
        }
        if ("guided_writing".equals(session.taskType)) {
            return Math.min(Math.max(1, stored), 2);
        }
        return stored;
    }

    private String adjustedRatingForHelp(String rating, boolean override) {
        if (override || activeAnalysis == null || !activeAnalysis.writingPassed) {
            return rating;
        }
        if ("easy".equals(rating) && (currentPracticeLevel < 3 || hintsUsed > 0)) {
            return "good";
        }
        return rating;
    }

    private void showWritingHint() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        if (currentPracticeLevel > 0) {
            currentPracticeLevel--;
        }
        hintsUsed++;
        activeAnalysis = null;
        drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, false);
        setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nHint used. Your ink stayed on the canvas; erase only if you want a fresh try.", MUTED);
        updateResultActions();
    }

    private void showAnalysis(WritingAnalysis analysis) {
        if (drawingPad != null && activeSession != null) {
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentPracticeLevel, true);
        }
        int color = analysis.writingPassed ? TEAL : CORAL;
        String candidates = candidateText(analysis.candidates);
        String message = analysis.message + targetRevealText(analysis) + (candidates.isEmpty() ? "" : "\nIt saw: " + candidates);
        setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)), MUTED);
        setResultStatus(message, color);
        updateResultActions();
    }

    private void updateResultActions() {
        boolean hasResult = activeAnalysis != null;
        boolean passed = hasResult && activeAnalysis.writingPassed;
        boolean submittable = activeAnalysis != null && canSubmitAnalysis(activeAnalysis);
        if (checkWritingButton != null) {
            checkWritingButton.setVisibility(!passed ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(!checkingWriting);
            checkWritingButton.setText(checkingWriting ? "Checking..." : "Check");
        }
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(writingModelStatusKnown && writingModelDownloaded ? View.GONE : View.VISIBLE);
        }
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(submittable ? View.VISIBLE : View.GONE);
            if (submittable) {
                nextAfterPassButton.setText(nextReviewButtonText(adjustedRatingForHelp(activeAnalysis.rating, false)));
            }
        }
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(hasResult && canManualOverride(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(hasResult && !passed && canPracticeAfterAnalysis(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (hintButton != null) {
            hintButton.setVisibility(!passed && currentPracticeLevel > 0 ? View.VISIBLE : View.GONE);
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
            return false;
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
                || ("targeted_writing".equals(session.taskType) && session.item.learningStep < 2);
    }

    private boolean isRecallTask(Records.StudySession session) {
        if (session == null) {
            return false;
        }
        return "blind_writing".equals(session.taskType) || "sampled_handwriting".equals(session.taskType);
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
            setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nAutomatic handwriting checks are unavailable on this device.", CORAL);
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
                setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nUnable to read handwriting checker status.", CORAL);
            } else if (!status.downloaded) {
                setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nDownload the handwriting checker before automatic checks.", CORAL);
            } else {
                setStudyStatus(guideLabel(currentPracticeLevel, strokeGuide(activeSession.item.kanji)) + "\nHandwriting checker ready.", MUTED);
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
        return "Writing practice";
    }

    private String guideLabel(int level, StrokeGuide guide) {
        boolean hasGuide = guide != null && !guide.isEmpty();
        if (!hasGuide) {
            if (level >= 3) {
                return "Write from memory, then check. No numbered stroke guide is bundled for this kanji yet.";
            }
            return "No numbered stroke guide is bundled for this kanji yet. Use the reference, draw it, then check. Stroke-order feedback will be limited.";
        }
        switch (level) {
            case 0:
                return "Trace the numbered strokes, then check. This is a learning attempt.";
            case 1:
                return "Copy the faint stroke guide, then check.";
            case 2:
                return "Write with only the current stroke hinted, then check.";
            default:
                return "Write from memory, then check. Use Hint if you are stuck.";
        }
    }

    private String nextReviewButtonText(String rating) {
        return "Next card";
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
