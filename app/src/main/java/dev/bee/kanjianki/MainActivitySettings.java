package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
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
import android.widget.ProgressBar;
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
import dev.bee.kanjianki.core.FrequencyRetentionRanges;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;
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

abstract class MainActivitySettings extends MainActivityStudy {
    void renderUpdate() {
        base(NAV_SETTINGS_ROUTE);
        content.addView(fullWidthHomeButton());
        content.addView(backToSettingsButton());
        content.addView(text("GitHub updater", 34, INK, true));
        content.addView(text("Current version " + BuildConfig.VERSION_NAME + ". Checks GitHub Releases, verifies the APK, and asks Android to install it.", 16, MUTED, false));
        content.addView(autoUpdatePanel("Automatic updates"));

        Button button = primaryButton("Check for update", STUDY_PINK_DARK);
        button.setOnClickListener(v -> runUpdate(false));
        content.addView(button);
    }

    LinearLayout autoUpdatePanel(String title) {
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

    String autoUpdateLastCheckText(LocalStore.AutoUpdateStatus status) {
        return UiDateText.autoUpdateLastCheckText(status.lastCheckAtMillis);
    }

    String versionText(String version) {
        return SettingsTextCopy.versionText(version);
    }

    boolean canInstallUpdates() {
        if (installPermissionForTests != null) {
            return installPermissionForTests;
        }
        return getPackageManager().canRequestPackageInstalls();
    }

    Button backToSettingsButton() {
        Button back = secondaryButton("Back to settings");
        back.setOnClickListener(v -> renderSettings(false));
        return back;
    }

    void renderSettings() {
        renderSettings(false);
    }

    void renderSettings(boolean preserveScroll) {
        int scrollY = preserveScroll && contentScroll != null ? contentScroll.getScrollY() : 0;
        base(NAV_SETTINGS_ROUTE);
        RecordsSyncModels.Settings current = settings();
        content.addView(fullWidthHomeButton());
        content.addView(settingsHero(current, store.reminderSettings(), store.autoSyncSettings(), store.autoUpdateStatus()));
        addSpace(10);

        content.addView(settingsCategory(
                "Anki source",
                "What Kani reads from AnkiDroid, and which cards become practice.",
                R.drawable.ic_book_24,
                settingsAnkiExpanded,
                () -> {
                    settingsAnkiExpanded = !settingsAnkiExpanded;
                    renderSettings(true);
                },
                noteTypeSettingsPanel(current),
                importFilterSettingsPanel(current),
                frequencyRangeSettingsPanel(current)
        ));
        content.addView(settingsCategory(
                "Study behavior",
                "How much appears today, how quickly repeats return, and when cards move rungs.",
                R.drawable.ic_study_24,
                settingsStudyExpanded,
                () -> {
                    settingsStudyExpanded = !settingsStudyExpanded;
                    renderSettings(true);
                },
                newCardSortSettingsPanel(current),
                workloadSettingsPanel(),
                retentionSettingsPanel(),
                learningStepsSettingsPanel(),
                studyAheadSettingsPanel(),
                studyLadderSettingsPanel(),
                ladderThresholdSettingsPanel()
        ));
        content.addView(settingsCategory(
                "Automation",
                "Background nudges, daily AnkiDroid refreshes, and app update checks.",
                R.drawable.ic_sync_24,
                settingsSyncExpanded,
                () -> {
                    settingsSyncExpanded = !settingsSyncExpanded;
                    renderSettings(true);
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
                    renderSettings(true);
                },
                dataLicenseSettingsPanel()
        ));
        if (preserveScroll) {
            contentScroll.post(() -> contentScroll.scrollTo(0, scrollY));
        }
    }

    View settingsHero(
            RecordsSyncModels.Settings current,
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
                settingsStatusPill("Import filters", settingsImportSummary(current), TEAL)
        );
        LinearLayout bottomRow = settingsStatusRow(
                settingsStatusPill("Import ranks", current.suspendedRankMin + "-" + current.suspendedRankMax, TEAL),
                settingsStatusPill("Reminder", settingsReminderSummary(reminder), reminder.enabled ? TEAL : MUTED)
        );
        LinearLayout automationRow = settingsStatusRow(
                settingsStatusPill("Daily sync", settingsAutoSyncSummary(autoSync), autoSync.enabled ? TEAL : MUTED),
                settingsStatusPill("Updates", settingsUpdateSummary(autoUpdate), autoUpdate.hasPendingUpdate() ? CORAL : STUDY_PINK_DARK)
        );
        hero.addView(topRow);
        hero.addView(bottomRow);
        hero.addView(automationRow);
        hero.addView(settingsStatusPill("Matching cards", matchingCardsSummary(current), STUDY_PLUM));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(10));
        hero.setLayoutParams(lp);
        return hero;
    }

    LinearLayout settingsStatusRow(View first, View second) {
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

    LinearLayout settingsStatusPill(String label, String value, int valueColor) {
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

        TextView valueView = text(compact(value, 56), 17, valueColor, true);
        valueView.setSingleLine(false);
        valueView.setMaxLines(2);
        valueView.setPadding(0, dp(3), 0, 0);
        pill.addView(valueView);
        pill.setContentDescription(label + ": " + value);
        return pill;
    }

    String settingsReminderSummary(LocalStore.ReminderSettings reminder) {
        boolean blocked = reminder.enabled && !ReminderScheduler.notificationsAllowed(this);
        return SettingsTextCopy.settingsReminderSummary(reminder.enabled, blocked, reminder.displayTime());
    }

    String settingsAutoSyncSummary(LocalStore.AutoSyncSettings autoSync) {
        return SettingsTextCopy.settingsAutoSyncSummary(autoSync.configured, autoSync.enabled, autoSync.displayTime());
    }

    String settingsUpdateSummary(LocalStore.AutoUpdateStatus autoUpdate) {
        return SettingsTextCopy.settingsUpdateSummary(autoUpdate.hasPendingUpdate(), autoUpdate.enabled);
    }

    String settingsImportSummary(RecordsSyncModels.Settings settings) {
        return SettingsTextCopy.settingsImportSummary(settings);
    }

    String matchingCardsSummary(RecordsSyncModels.Settings settings) {
        return SettingsTextCopy.matchingCardsSummary(settings);
    }

    LinearLayout settingsCategory(
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

    LinearLayout settingsPanelBox() {
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

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        LinearLayout box = settingsPanelBox();
        box.addView(text("Import filters", 23, INK, true));
        box.addView(text(settingsImportSummary(current), 17, TEAL, true));
        box.addView(text("Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.", 15, MUTED, false));
        addImportPresetButtons(box);

        CheckBox activeCards = importFilterCheckBox("Active cards", current.importActiveCards);
        CheckBox suspendedCards = importFilterCheckBox("Suspended cards", current.importSuspendedCards);
        CheckBox taggedCards = importFilterCheckBox("Tagged cards", current.importTaggedCardsEnabled());
        CheckBox weakCards = importFilterCheckBox("Weak cards", current.importWeakCards);
        CheckBox browserQueryCards = importFilterCheckBox("Browser query", current.importBrowserQueryCards);
        box.addView(activeCards);
        box.addView(suspendedCards);
        box.addView(taggedCards);
        box.addView(weakCards);
        box.addView(browserQueryCards);

        EditText browserQueryInput = fieldInput(current.importBrowserQuery);
        browserQueryInput.setHint("deck:Japanese tag:kani");
        addFieldMappingInput(box, "Anki browser query", browserQueryInput);

        EditText tags = fieldInput(current.importTagsText());
        tags.setHint("tag1, tag2");
        addFieldMappingInput(box, "Anki note tags", tags);

        LinearLayout thresholds = new LinearLayout(this);
        thresholds.setOrientation(LinearLayout.HORIZONTAL);
        EditText difficultyInput = decimalInput(current.importWeakFsrsDifficultyThreshold);
        LinearLayout difficultyColumn = inputColumn("FSRS difficulty", difficultyInput, 0);
        EditText lapses = thresholdInput(current.importWeakLapsesThreshold);
        LinearLayout lapsesColumn = inputColumn("Lapses", lapses, dp(10));
        thresholds.addView(difficultyColumn, new LinearLayout.LayoutParams(0, -2, 1));
        thresholds.addView(lapsesColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(thresholds);

        EditText minMatching = thresholdInput(current.importMinMatchingCardsPerKanji);
        addFieldMappingInput(box, "Minimum matching cards per kanji", minMatching);

        Button save = primaryButton("Save import filters", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<String> parsedTags = RecordsBase.parseImportTags(tags.getText().toString());
            String queryText = browserQueryInput.getText().toString().trim();
            if (browserQueryCards.isChecked() && queryText.isEmpty()) {
                Toast.makeText(this, "Enter an Anki browser query or turn off Browser query.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText)) {
                Toast.makeText(this, "Turn on at least one import source.", Toast.LENGTH_SHORT).show();
                return;
            }
            ImportThresholds parsedThresholds = readImportThresholds(difficultyInput, lapses, minMatching);
            if (parsedThresholds == null) {
                return;
            }
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, boolFlag(activeCards.isChecked()));
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, boolFlag(suspendedCards.isChecked()));
            store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, boolFlag(taggedCards.isChecked()));
            store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, String.join(" ", parsedTags));
            store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, boolFlag(weakCards.isChecked()));
            store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, parsedThresholds.difficulty);
            store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, parsedThresholds.lapseThreshold);
            store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, parsedThresholds.minCards);
            store.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, boolFlag(browserQueryCards.isChecked()));
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, queryText);
            Toast.makeText(this, "Import filters saved. Sync again to rebuild practice.", Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    void addImportPresetButtons(LinearLayout box) {
        box.addView(text("Presets", 17, INK, true));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (SettingsImportPreset preset : SettingsImportPreset.defaults()) {
            Button button = secondaryButton(preset.label());
            button.setOnClickListener(v -> applyImportPreset(preset));
            grid.addView(button);
        }
        box.addView(grid);
    }

    void applyImportPreset(SettingsImportPreset preset) {
        store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, boolFlag(preset.activeCards()));
        store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, boolFlag(preset.suspendedCards()));
        store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, boolFlag(preset.taggedCards()));
        store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, preset.tags());
        store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, boolFlag(preset.weakCards()));
        store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, preset.weakDifficulty());
        store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, preset.weakLapses());
        store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, preset.minMatchingCards());
        store.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, boolFlag(preset.browserQueryCards()));
        store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, preset.browserQuery());
        Toast.makeText(this, "Import preset saved. Sync again to rebuild practice.", Toast.LENGTH_LONG).show();
        renderSettings();
    }

    static int boolFlag(boolean value) {
        return SettingsImportPreset.boolFlag(value);
    }

    ImportThresholds readImportThresholds(EditText difficultyInput, EditText lapses, EditText minMatching) {
        double difficulty;
        int lapseThreshold;
        int minCards;
        try {
            difficulty = parseDecimalInput(difficultyInput);
            lapseThreshold = parseThresholdInput(lapses);
            minCards = parseThresholdInput(minMatching);
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Use numeric import thresholds.", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!validImportThresholds(difficulty, lapseThreshold, minCards)) {
            Toast.makeText(this, "Use difficulty 1-10, lapses 1-100, and cards 1-1000.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return new ImportThresholds(difficulty, lapseThreshold, minCards);
    }

    boolean hasSelectedImportSource(
            CheckBox activeCards,
            CheckBox suspendedCards,
            CheckBox taggedCards,
            CheckBox weakCards,
            CheckBox browserQueryCards,
            List<String> parsedTags,
            String queryText
    ) {
        return SettingsInputRules.hasSelectedImportSource(
                activeCards.isChecked(),
                suspendedCards.isChecked(),
                taggedCards.isChecked(),
                weakCards.isChecked(),
                browserQueryCards.isChecked(),
                parsedTags,
                queryText
        );
    }

    boolean validImportThresholds(double difficulty, int lapseThreshold, int minCards) {
        return SettingsInputRules.validImportThresholds(difficulty, lapseThreshold, minCards);
    }

    CheckBox importFilterCheckBox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(INK);
        box.setTextSize(17);
        box.setTypeface(Typeface.DEFAULT_BOLD);
        box.setChecked(checked);
        box.setButtonTintList(ColorStateList.valueOf(STUDY_PINK_DARK));
        box.setPadding(0, dp(4), 0, dp(4));
        return box;
    }

    LinearLayout inputColumn(String label, EditText input, int leftPadding) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(leftPadding, 0, 0, 0);
        column.addView(text(label, 15, INK, true));
        column.addView(input, new LinearLayout.LayoutParams(-1, dp(58)));
        return column;
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
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

    LinearLayout dataLicenseSettingsPanel() {
        LinearLayout box = settingsPanelBox();
        box.addView(text("Offline data & licenses", 23, INK, true));
        box.addView(text("One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.", 15, MUTED, false));
        Button open = secondaryButton("Open data licenses");
        open.setOnClickListener(v -> renderDataSources());
        box.addView(open);
        return box;
    }

    void renderDataSources() {
        base(NAV_SETTINGS_ROUTE);
        content.addView(fullWidthHomeButton());
        content.addView(backToSettingsButton());
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
        back.setOnClickListener(v -> renderSettings(false));
        content.addView(back);
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
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

    EditText noteTypeInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null || value.trim().isEmpty() ? RecordsSyncModels.Settings.kikuDefaults().modelName : value.trim());
        input.setHint(RecordsSyncModels.Settings.kikuDefaults().modelName);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText fieldInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null ? "" : value.trim());
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        box.addView(text(label, 14, INK, true));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    EditText rankInput(int value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", value));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText decimalInput(double value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.ROOT, "%.1f", value));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    void bindRankSliders(
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

    int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    double parseDecimalInput(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }

    int rankSliderProgress(int rank) {
        return SettingsInputRules.rankSliderProgress(rank);
    }

    int rankFromSliderProgress(int progress) {
        return SettingsInputRules.rankFromSliderProgress(progress);
    }

    String frequencyRangeStatusText(int minRank, int maxRank) {
        return SettingsTextCopy.frequencyRangeStatusText(minRank, maxRank);
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        final String[] selected = new String[]{current.newCardSortMode};
        LinearLayout box = settingsPanelBox();
        box.addView(text("New card sort", 23, INK, true));
        TextView status = text(newCardSortStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text("Choose how Kani admits and shows unseen new cards. Due reviews and learning repeats still keep their normal priority.", 15, MUTED, false));

        addSortModeButton(box, "Frequency", RecordsBase.NEW_CARD_SORT_FREQUENCY, selected, status);
        addSortModeButton(box, "Anki difficulty", RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, selected, status);
        addSortModeButton(box, "Retrievability risk", RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, selected, status);
        addSortModeButton(box, "Kani weakness", RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, selected, status);

        Button save = primaryButton("Save new card sort", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, selected[0]);
            Toast.makeText(this, "New card sort saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, TextView status) {
        Button button = secondaryButton(label);
        button.setOnClickListener(v -> {
            selected[0] = mode;
            status.setText(newCardSortStatusText(mode));
        });
        box.addView(button);
    }

    String newCardSortStatusText(String mode) {
        return SettingsTextCopy.newCardSortStatusText(mode);
    }

    String newCardSortLabel(String mode) {
        return SettingsTextCopy.newCardSortLabel(mode);
    }

    LinearLayout workloadSettingsPanel() {
        int current = store.adaptiveLoadWorkPercent();
        int currentMax = store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        LinearLayout box = settingsPanelBox();
        box.addView(text("Daily workload", 23, INK, true));

        if (autoMode) {
            long now = System.currentTimeMillis();
            List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
            RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty()
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

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
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

    LinearLayout learningStepsSettingsPanel() {
        RecordsSchedulerModels.LearningStepSettings current = store.learningStepSettings();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Learning steps", 23, INK, true));
        box.addView(text("New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer.", 15, MUTED, false));

        EditText newSteps = stepInput(current.newStepsText());
        EditText reviewSteps = stepInput(current.reviewStepsText());
        box.addView(text(LABEL_NEW_CARDS, 15, INK, true));
        box.addView(newSteps, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text("Review misses", 15, INK, true));
        box.addView(reviewSteps, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        Button ankiDefault = secondaryButton("Anki default");
        ankiDefault.setOnClickListener(v -> {
            RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.reviewStepsText());
        });
        presets.addView(ankiDefault, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button sameSteps = secondaryButton("Both 1m 10m");
        sameSteps.setOnClickListener(v -> {
            RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.newStepsText());
        });
        presets.addView(sameSteps, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(presets);

        Button save = primaryButton("Save learning steps", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<Integer> parsedNew = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(newSteps.getText().toString());
            List<Integer> parsedReview = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(reviewSteps.getText().toString());
            if (parsedNew.isEmpty() || parsedReview.isEmpty()) {
                Toast.makeText(this, "Use steps like 1m, 10m, or 1h.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.saveLearningStepSettings(new RecordsSchedulerModels.LearningStepSettings(parsedNew, parsedReview));
            Toast.makeText(this, "Learning steps saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    EditText stepInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    LinearLayout studyAheadSettingsPanel() {
        int currentMinutes = store.studyAheadMinutes();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Study ahead", 23, INK, true));
        box.addView(text("Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).", 15, MUTED, false));

        EditText minutesInput = new EditText(this);
        minutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(String.format(Locale.ROOT, "%d", currentMinutes));
        minutesInput.setTextSize(20);
        minutesInput.setSingleLine(true);
        minutesInput.setSelectAllOnFocus(true);
        box.addView(text("Minutes (0-1440)", 15, INK, true));
        box.addView(minutesInput, new LinearLayout.LayoutParams(-1, dp(58)));

        Button save = primaryButton("Save study ahead", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int parsed;
            try {
                parsed = Integer.parseInt(minutesInput.getText().toString().trim());
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Use a whole number of minutes (0-1440).", Toast.LENGTH_SHORT).show();
                return;
            }
            if (parsed < 0 || parsed > 1440) {
                Toast.makeText(this, "Use 0 to disable, or up to 1440 minutes (24h).", Toast.LENGTH_SHORT).show();
                return;
            }
            store.saveStudyAheadMinutes(parsed);
            Toast.makeText(this, "Study ahead saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    LinearLayout studyLadderSettingsPanel() {
        RecordsBase.StudyLadderSettings ladder = studyLadderSettings();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Study ladder", 23, INK, true));
        box.addView(text("Turn rungs off or move them up and down. At least one always-available rung stays on.", 15, MUTED, false));

        List<RecordsBase.LadderRung> rungs = ladder.orderedRungs;
        for (int i = 0; i < rungs.size(); i++) {
            RecordsBase.LadderRung rung = rungs.get(i);
            LinearLayout row = softInsetPanel();
            row.addView(text(ladderRungLabel(rung), 19, STUDY_PLUM, true));
            row.addView(text(ladderRungSubtitle(ladder, rung), 13, MUTED, false));

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            Button toggle = secondaryButton(ladder.isEnabled(rung) ? "On" : "Off");
            toggle.setOnClickListener(v -> toggleLadderRung(rung));
            controls.addView(toggle, new LinearLayout.LayoutParams(0, dp(48), 1));

            Button up = secondaryButton("Up");
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> {
                store.saveStudyLadderSettings(studyLadderSettings().moveRung(rung, -1));
                renderSettings();
            });
            LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, dp(48), 1);
            upLp.setMargins(dp(8), 0, 0, 0);
            controls.addView(up, upLp);

            Button down = secondaryButton("Down");
            down.setEnabled(i < rungs.size() - 1);
            down.setOnClickListener(v -> {
                store.saveStudyLadderSettings(studyLadderSettings().moveRung(rung, 1));
                renderSettings();
            });
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(0, dp(48), 1);
            downLp.setMargins(dp(8), 0, 0, 0);
            controls.addView(down, downLp);
            row.addView(controls);
            box.addView(row);
        }

        Button reset = secondaryButton("Restore default ladder");
        reset.setOnClickListener(v -> {
            store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults());
            Toast.makeText(this, "Study ladder restored.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(reset);
        return box;
    }

    void toggleLadderRung(RecordsBase.LadderRung rung) {
        RecordsBase.StudyLadderSettings current = studyLadderSettings();
        boolean wasEnabled = current.isEnabled(rung);
        RecordsBase.StudyLadderSettings next = current.withRungEnabled(rung, !wasEnabled);
        if (wasEnabled && next.enabledText().equals(current.enabledText())) {
            Toast.makeText(this, "Keep at least one always-available rung on.", Toast.LENGTH_SHORT).show();
            return;
        }
        store.saveStudyLadderSettings(next);
        Toast.makeText(this, ladderRungLabel(rung) + (wasEnabled ? " off." : " on."), Toast.LENGTH_SHORT).show();
        renderSettings();
    }

    String ladderRungSubtitle(RecordsBase.StudyLadderSettings ladder, RecordsBase.LadderRung rung) {
        return SettingsTextCopy.ladderRungSubtitle(ladder, rung);
    }

    String ladderRungLabel(RecordsBase.LadderRung rung) {
        return SettingsTextCopy.settingsLadderRungLabel(rung);
    }

    LinearLayout ladderThresholdSettingsPanel() {
        RecordsSyncModels.Settings current = settings();
        LinearLayout box = settingsPanelBox();
        box.addView(text("Ladder thresholds", 23, INK, true));
        box.addView(text("Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.", 15, MUTED, false));

        EditText promotionDays = thresholdInput(current.ladderPromotionIntervalDays);
        EditText failStreak = thresholdInput(current.ladderDemotionFailStreak);
        box.addView(text("FSRS days to go up", 15, INK, true));
        box.addView(promotionDays, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text("Fails to go down", 15, INK, true));
        box.addView(failStreak, new LinearLayout.LayoutParams(-1, dp(58)));

        Button defaults = secondaryButton("Use 21 and 3");
        defaults.setOnClickListener(v -> {
            promotionDays.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS));
            failStreak.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK));
        });
        box.addView(defaults);

        Button save = primaryButton("Save ladder thresholds", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int promotionDayCount;
            int failCount;
            try {
                promotionDayCount = parseThresholdInput(promotionDays);
                failCount = parseThresholdInput(failStreak);
            } catch (NumberFormatException error) {
                Toast.makeText(this, "Use positive whole numbers.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (promotionDayCount < 1 || failCount < 1) {
                Toast.makeText(this, "Use positive whole numbers.", Toast.LENGTH_SHORT).show();
                return;
            }
            store.putIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, promotionDayCount);
            store.putIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, failCount);
            store.putIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, failCount);
            store.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, failCount);
            Toast.makeText(this, "Ladder thresholds saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    EditText thresholdInput(int value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", Math.max(1, value)));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    int parseThresholdInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    LinearLayout retentionSettingsPanel() {
        RecordsSchedulerModels.SchedulerParameters current = store.schedulerParameters();
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

        CheckBox rankRetentionEnabled = importFilterCheckBox("Use Jiten-rank retention ranges", current.frequencyRetentionEnabled);
        box.addView(rankRetentionEnabled);
        box.addView(text("Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above.", 15, MUTED, false));
        EditText rankRanges = rankRetentionRangesInput(current.frequencyRetentionRanges);
        box.addView(rankRanges, new LinearLayout.LayoutParams(-1, dp(132)));

        Button exampleRanges = secondaryButton("Use example ranges");
        exampleRanges.setOnClickListener(v -> rankRanges.setText(FrequencyRetentionRanges.exampleText()));
        box.addView(exampleRanges);

        Button save = primaryButton("Save retention", STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            String rangesText = rankRanges.getText().toString().trim();
            if (rankRetentionEnabled.isChecked()) {
                try {
                    FrequencyRetentionRanges.parse(rangesText);
                } catch (IllegalArgumentException error) {
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            RecordsSchedulerModels.SchedulerParameters latest = store.schedulerParameters();
            store.saveSchedulerParameters(new RecordsSchedulerModels.SchedulerParameters(
                    selected[0] / 100.0,
                    latest.againMultiplier,
                    latest.hardMultiplier,
                    latest.goodMultiplier,
                    latest.easyMultiplier,
                    latest.lastAdjustedAtMillis,
                    latest.lastAdjustmentReviewCount
            ).withFrequencyRetention(
                    rankRetentionEnabled.isChecked(),
                    rangesText));
            Toast.makeText(this, "FSRS retention saved.", Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    EditText rankRetentionRangesInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(value == null || value.trim().isEmpty() ? FrequencyRetentionRanges.exampleText() : value.trim());
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSelectAllOnFocus(false);
        return input;
    }

    int retentionPercent(double retention) {
        return SettingsInputRules.retentionPercent(retention);
    }

    String retentionStatusText(int retentionPercent) {
        return SettingsTextCopy.retentionStatusText(retentionPercent);
    }

    LinearLayout reminderSettingsPanel() {
        LocalStore.ReminderSettings reminder = store.reminderSettings();
        boolean notificationsAllowed = notificationsAllowedForReminders();
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
                (view, hour, minute) -> applyReminderTimeSelection(selectedHour, selectedMinute, time, hour, minute),
                selectedHour[0],
                selectedMinute[0],
                true
        ).show());
        box.addView(time);

        List<View> presets = new ArrayList<>();
        presets.add(reminderPresetButton("Morning", 8, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton("Lunch", 12, 30, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton("Evening", 19, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton("Night", 21, 0, selectedHour, selectedMinute, time));
        box.addView(twoColumnGrid(presets));

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
        } else if (!hasRuntimeNotificationPermissionForReminder()) {
            box.addView(text("Android will ask for notification permission before turning this on.", 14, CORAL, false));
        }
        return box;
    }

    int reminderStatusColor(LocalStore.ReminderSettings reminder, boolean blocked) {
        if (blocked) {
            return CORAL;
        }
        return reminder.enabled ? TEAL : MUTED;
    }

    LinearLayout autoSyncSettingsPanel() {
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

    String autoSyncStatus(LocalStore.AutoSyncSettings auto) {
        return SettingsTextCopy.autoSyncStatus(auto.configured, auto.enabled, auto.displayTime());
    }

    String autoSyncDetail(LocalStore.AutoSyncSettings auto) {
        String lastSuccess = auto.lastSuccessAt > 0L ? shortDateTime(auto.lastSuccessAt) : "";
        String lastAttempt = auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt ? shortDateTime(auto.lastAttemptAt) : "";
        String nextRun = auto.nextRunAt > 0L ? shortDateTime(auto.nextRunAt) : "";
        return SettingsTextCopy.autoSyncDetail(auto.configured, auto.enabled, lastSuccess, lastAttempt, nextRun);
    }

    String shortDateTime(long millis) {
        return UiDateText.shortDateTime(millis);
    }

    String workloadStatusText(int percent, int maxItems) {
        return SettingsTextCopy.workloadStatusText(percent, maxItems);
    }

    String maxItemsStatusText(int maxItems) {
        return SettingsTextCopy.maxItemsStatusText(maxItems);
    }

    String autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return SettingsTextCopy.autoWorkloadStatusText(plan);
    }

    LinearLayout updateSettingsPanel() {
        LinearLayout box = autoUpdatePanel("App updates");
        Button update = primaryButton("Open updater", STUDY_PINK_DARK);
        update.setOnClickListener(v -> renderUpdate());
        box.addView(update);
        return box;
    }

    String reminderStatus(LocalStore.ReminderSettings reminder, boolean blocked) {
        return SettingsTextCopy.reminderStatus(reminder.enabled, blocked, reminder.displayTime());
    }

    String reminderTime(int hour, int minute) {
        return SettingsTextCopy.reminderTime(hour, minute);
    }

    String reminderTimeButtonLabel(int hour, int minute) {
        return SettingsTextCopy.reminderTimeButtonLabel(hour, minute);
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        Button preset = secondaryButton(label + " " + reminderTime(hour, minute));
        preset.setTextSize(13);
        preset.setMinHeight(dp(54));
        preset.setOnClickListener(v -> {
            selectedHour[0] = hour;
            selectedMinute[0] = minute;
            timeButton.setText(reminderTimeButtonLabel(hour, minute));
        });
        return preset;
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        LocalStore.ReminderSettings reminder = new LocalStore.ReminderSettings(enabled, hour, minute);
        if (!enabled) {
            store.saveReminderSettings(reminder);
            ReminderScheduler.cancel(this);
            Toast.makeText(this, "Reminder turned off.", Toast.LENGTH_SHORT).show();
            renderSettings();
            return;
        }
        ReminderScheduler.ensureNotificationChannel(this);
        if (!hasRuntimeNotificationPermissionForReminder()) {
            pendingReminderSettings = reminder;
            requestPermissions(new String[]{PERMISSION_POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            return;
        }
        store.saveReminderSettings(reminder);
        ReminderScheduler.schedule(this, reminder);
        if (notificationsAllowedForReminders()) {
            Toast.makeText(this, "Reminder saved for around " + reminder.displayTime() + ".", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Reminder saved, but Android notifications are off.", Toast.LENGTH_LONG).show();
        }
        renderSettings();
    }

    void applyReminderTimeSelection(int[] selectedHour, int[] selectedMinute, Button time, int hour, int minute) {
        selectedHour[0] = hour;
        selectedMinute[0] = minute;
        time.setText(reminderTimeButtonLabel(hour, minute));
    }

    void openNotificationSettings() {
        startActivity(notificationSettingsIntent());
    }

    Intent notificationSettingsIntent() {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
    }

    int beginUpdateUiRun() {
        activeUpdateUiRunToken = ++updateUiRunCounter;
        return activeUpdateUiRunToken;
    }

    boolean updateUiRunStillActive(int token) {
        return token != 0 && activeUpdateUiRunToken == token;
    }

    void runUpdate(boolean cachedPending) {
        base(NAV_SETTINGS_ROUTE);
        int updateUiRun = beginUpdateUiRun();
        content.addView(fullWidthHomeButton());
        content.addView(backToSettingsButton());
        content.addView(text(cachedPending ? "Starting installer" : "Checking release", 32, INK, true));
        content.addView(text(cachedPending ? "Using the verified APK already cached by Kani." : "Downloading metadata and verifying assets.", 16, MUTED, false));
        content.addView(indeterminateProgressRow(cachedPending ? "Preparing verified APK" : "Checking GitHub Releases"));
        io.execute(() -> {
            GitHubUpdater updater = new GitHubUpdater(this);
            GitHubUpdater.UpdateResult result = cachedPending
                    ? updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
                    : updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);
            main.post(() -> {
                if (!updateUiRunStillActive(updateUiRun)) {
                    return;
                }
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                if (result.intent != null) {
                    startActivity(result.intent);
                }
                renderUpdate();
            });
        });
    }

    LinearLayout indeterminateProgressRow(String label) {
        LinearLayout row = panelBox(Color.WHITE, STUDY_BORDER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setContentDescription(label);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressLp.setMargins(0, 0, dp(12), 0);
        row.addView(progress, progressLp);
        row.addView(text(label, 16, STUDY_PLUM, true), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

}
