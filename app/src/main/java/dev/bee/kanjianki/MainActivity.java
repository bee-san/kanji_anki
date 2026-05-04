package dev.bee.kanjianki;

import android.Manifest;
import android.app.Activity;
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
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private CheckBox writingPassed;
    private CheckBox manualOverride;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        gateway = new AnkiDroidGateway(this);
        requestAnkiPermissionIfNeeded();
        renderHome();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void requestAnkiPermissionIfNeeded() {
        AnkiDroidGateway.ProviderStatus status = gateway.status();
        if (status.permission != null && !status.permissionGranted) {
            requestPermissions(new String[]{status.permission}, 7);
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
        nav.addView(navButton("Prefs", selected.equals("settings"), this::renderSettings));
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
        content.addView(text("Live Kiku sync. Local suspended archive. One bridge SRS button.", 18, MUTED, false));
        addSpace(18);

        LocalStore.SyncStatus sync = store.latestSync();
        AnkiDroidGateway.ProviderStatus provider = gateway.status();
        LinearLayout hero = band(provider.canSync ? TEAL : CORAL);
        hero.addView(text(provider.canSync ? "AnkiDroid ready" : "Sync needs attention", 26, Color.WHITE, true));
        hero.addView(text(provider.message, 16, Color.WHITE, false));
        if (sync != null) {
            hero.addView(text(sync.headline(), 16, Color.WHITE, false));
            if (!sync.removalMessage.isEmpty()) {
                hero.addView(text(sync.removalMessage, 14, Color.WHITE, false));
            }
        } else {
            hero.addView(text("No local mirror yet. Run a manual sync to build the queue.", 16, Color.WHITE, false));
        }
        hero.addView(text("Suspended import cutoff: rank > " + settings().suspendedRankCutoff, 14, Color.WHITE, false));
        content.addView(hero);
        addSpace(18);

        Button syncButton = primaryButton("Sync AnkiDroid now", TEAL);
        syncButton.setOnClickListener(v -> runSync());
        content.addView(syncButton);

        Button studyButton = primaryButton("Study now", CORAL);
        studyButton.setOnClickListener(v -> renderStudy());
        content.addView(studyButton);

        addSpace(16);
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            emptyState("No weak-kanji rows yet", "The app does not ship demo cards. It waits for your live Kiku collection, then builds detail pages from real examples.");
        } else {
            content.addView(sectionTitle("Top weak kanji"));
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                content.addView(rowView(rows.get(i)));
            }
        }
    }

    private void runSync() {
        base("home");
        content.addView(text("Syncing Kiku", 34, INK, true));
        content.addView(text("Reading AnkiDroid, archiving suspended cards, rebuilding kanji rows, and refreshing the bridge queue.", 17, MUTED, false));
        io.execute(() -> {
            ManualSyncEngine.SyncResult result = new ManualSyncEngine(this, store, gateway, settings()).run();
            main.post(() -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                renderHome();
            });
        });
    }

    private void renderKanjiList() {
        base("kanji");
        content.addView(text("Weak kanji", 34, INK, true));
        content.addView(text("Every row is rebuilt from your active Kiku mirror and local suspended archive.", 16, MUTED, false));
        addSpace(12);
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            emptyState("No rows", "Run a manual sync from the Home screen.");
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
        copy.addView(text(row.primaryMeaning.isEmpty() ? row.reasonCode : row.primaryMeaning, 19, INK, true));
        copy.addView(text(row.reasonText, 14, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip("score " + row.weaknessScore, CORAL));
        chips.addView(chip(row.suspendedExampleCount + " suspended", BLUE));
        chips.addView(chip(row.matureSupportCount + " mature", TEAL));
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
        content.addView(text(row.primaryMeaning.isEmpty() ? "Collection-derived detail" : row.primaryMeaning, 25, INK, true));
        content.addView(text(row.reading, 20, TEAL, true));
        addSpace(10);
        LinearLayout why = band(BLUE);
        why.addView(text("Why it is here", 22, Color.WHITE, true));
        why.addView(text(row.reasonText, 17, Color.WHITE, false));
        why.addView(text("Anki browser: " + row.browserSearch, 14, Color.WHITE, false));
        content.addView(why);
        Button copy = secondaryButton("Copy Anki search");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Anki search", row.browserSearch));
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
            box.addView(text(example.meaning, 15, MUTED, false));
        }
        return box;
    }

    private void renderStudy() {
        base("study");
        List<Records.DashboardRow> rows = store.dashboardRows();
        if (rows.isEmpty()) {
            content.addView(text("Study now", 34, INK, true));
            emptyState("Nothing to study yet", "Run a manual sync first. The study queue is seeded only from your collection-derived problem kanji.");
            return;
        }
        BridgeScheduler scheduler = new BridgeScheduler();
        List<Records.StudyItem> seeded = scheduler.seedQueue(rows, store.studyItems(), settings(), System.currentTimeMillis(), startOfDay(System.currentTimeMillis()));
        store.replaceStudyItems(seeded);
        activeSession = scheduler.nextSession(seeded, rows, System.currentTimeMillis());
        if (activeSession == null) {
            content.addView(text("Queue resting", 34, INK, true));
            content.addView(text("No bridge item is due right now.", 18, MUTED, false));
            Button back = primaryButton("Back home", TEAL);
            back.setOnClickListener(v -> renderHome());
            content.addView(back);
            return;
        }
        store.saveStudyItem(activeSession.item);
        renderSession(activeSession);
    }

    private void renderSession(Records.StudySession session) {
        content.removeAllViews();
        content.addView(text("Study now", 30, INK, true));
        LinearLayout stage = band(session.writingRequired ? CORAL : TEAL);
        stage.addView(text(session.item.kanji, 88, Color.WHITE, true));
        stage.addView(text(labelForTask(session.taskType), 22, Color.WHITE, true));
        if (session.row != null) {
            stage.addView(text(session.row.primaryMeaning, 18, Color.WHITE, false));
            stage.addView(text(session.row.reasonText, 15, Color.WHITE, false));
        }
        content.addView(stage);

        if (session.row != null) {
            for (Records.Example example : session.row.examples) {
                content.addView(exampleView(example));
            }
        }

        if (session.writingRequired) {
            content.addView(sectionTitle("Writing"));
            content.addView(text(guideLabel(session.item.writingLevel), 16, MUTED, false));
            drawingPad = new DrawingPadView(this);
            content.addView(drawingPad, new LinearLayout.LayoutParams(-1, dp(220)));
            LinearLayout checks = new LinearLayout(this);
            checks.setOrientation(LinearLayout.VERTICAL);
            writingPassed = new CheckBox(this);
            writingPassed.setText(R.string.writing_passed);
            manualOverride = new CheckBox(this);
            manualOverride.setText(R.string.manual_override);
            checks.addView(writingPassed);
            checks.addView(manualOverride);
            content.addView(checks);
            Button clear = secondaryButton("Clear drawing");
            clear.setOnClickListener(v -> drawingPad.clear());
            content.addView(clear);
        }

        LinearLayout ratings = new LinearLayout(this);
        ratings.setOrientation(LinearLayout.HORIZONTAL);
        for (String rating : new String[]{"again", "hard", "good", "easy"}) {
            Button button = secondaryButton(rating);
            button.setOnClickListener(v -> submitReview(rating));
            ratings.addView(button, new LinearLayout.LayoutParams(0, dp(58), 1));
        }
        content.addView(ratings);
    }

    private void submitReview(String rating) {
        if (activeSession == null) {
            return;
        }
        boolean writingRequired = activeSession.writingRequired;
        boolean drawn = drawingPad != null && drawingPad.hasInk();
        boolean passed = !writingRequired || (writingPassed != null && writingPassed.isChecked() && drawn);
        boolean override = manualOverride != null && manualOverride.isChecked();
        Records.ReviewRequest request = new Records.ReviewRequest(
                activeSession.item.kanji,
                activeSession.token,
                rating,
                writingRequired,
                passed,
                override,
                0
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
        Toast.makeText(this, result.message + " Rating: " + result.appliedRating, Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    private void renderUpdate() {
        base("update");
        content.addView(text("GitHub updater", 34, INK, true));
        content.addView(text("Current version " + BuildConfig.VERSION_NAME + ". Checks public GitHub Releases, verifies SHA-256, then opens Android's installer.", 16, MUTED, false));
        Button button = primaryButton("Check for update", BLUE);
        button.setOnClickListener(v -> runUpdate());
        content.addView(button);
    }

    private void renderSettings() {
        base("settings");
        Records.Settings current = settings();
        content.addView(text("Settings", 34, INK, true));
        content.addView(text("Kiku mapping is fixed and validated up front. The suspended import cutoff is intentionally the one tunable ranking rule.", 16, MUTED, false));
        addSpace(12);

        LinearLayout box = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        box.addView(text("Suspended kanji rank cutoff", 23, INK, true));
        box.addView(text("Import suspended kanji only when its Jiten rank is worse than this number. Smaller ranks are more common. Default: 3000.", 15, MUTED, false));
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
            Toast.makeText(this, "Cutoff saved. Run sync to rebuild imports.", Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        content.addView(box);

        LinearLayout mapping = band(BLUE);
        mapping.addView(text("Kiku fields", 22, Color.WHITE, true));
        mapping.addView(text("Expression -> kanji source\nExpressionReading -> reading\nMainDefinition -> meaning\nSentence -> context\nFrequency/FreqSort -> collection metadata", 15, Color.WHITE, false));
        content.addView(mapping);
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

    private void addSpace(int dp) {
        SpaceView space = new SpaceView(this);
        content.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private String labelForTask(String task) {
        if ("context_writing".equals(task)) {
            return "Context production";
        }
        if ("confusable_recognition".equals(task)) {
            return "Confusable recognition";
        }
        if ("sampled_handwriting".equals(task)) {
            return "Sampled handwriting";
        }
        return "Recognition";
    }

    private String guideLabel(int level) {
        switch (level) {
            case 0:
                return "Trace: copy the shape deliberately before rating.";
            case 1:
                return "Outline: use the examples, then write from memory.";
            case 2:
                return "Minimal hints: glance once, then write blind.";
            default:
                return "Blind recall: write first, check after.";
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
        private final List<Path> paths = new ArrayList<>();
        private Path current;

        public DrawingPadView(Context context) {
            super(context);
            setBackgroundColor(Color.WHITE);
            paint.setColor(INK);
            paint.setStrokeWidth(10f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            grid.setColor(Color.rgb(244, 199, 225));
            grid.setStrokeWidth(2f);
        }

        public boolean hasInk() {
            return !paths.isEmpty();
        }

        public void clear() {
            paths.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            canvas.drawLine(w / 2f, 0, w / 2f, h, grid);
            canvas.drawLine(0, h / 2f, w, h / 2f, grid);
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
                    current = new Path();
                    current.moveTo(event.getX(), event.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        current.lineTo(event.getX(), event.getY());
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) {
                        current.lineTo(event.getX(), event.getY());
                        paths.add(current);
                        current = null;
                        invalidate();
                    }
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
    }
}
