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
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.FrequencyRetentionRanges;
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy;
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RetentionSettingsPolicy;
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TypingAnswerMatcher;
import dev.bee.kanjianki.core.WorkloadSettingsPolicy;
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
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy;
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy;

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
        Button backButton = secondaryButton(SettingsTextCopy.backToSettingsLabel());
        backButton.setOnClickListener(v -> renderSettings(false));
        content.addView(backButton);
        content.addView(text(SettingsTextCopy.updatePageTitle(), 34, INK, true));
        content.addView(text(SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME), 16, MUTED, false));
        content.addView(autoUpdatePanel(SettingsTextCopy.automaticUpdatesTitle()));

        Button button = primaryButton(SettingsTextCopy.checkForUpdateLabel(), STUDY_PINK_DARK);
        button.setOnClickListener(v -> runUpdate(false));
        content.addView(button);
    }

    LinearLayout autoUpdatePanel(String title) {
        LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
        boolean canInstall = canInstallUpdates();
        LinearLayout box = settingsPanelBox();
        box.addView(text(title, 23, INK, true));
        box.addView(text(SettingsTextCopy.autoUpdatePanelStatus(status.enabled), 18, status.enabled ? TEAL : MUTED, true));
        box.addView(text(
                SettingsTextCopy.autoUpdateLastCheckLine(DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis)),
                15,
                MUTED,
                false
        ));
        box.addView(text(SettingsTextCopy.autoUpdateLastResultLine(status.lastResult), 15, MUTED, false));
        box.addView(text(SettingsTextCopy.installPermissionLine(canInstall), 15, canInstall ? TEAL : CORAL, true));

        if (status.hasPendingUpdate()) {
            box.addView(text(SettingsTextCopy.verifiedApkReadyLine(status.lastVersion), 18, CORAL, true));
            String pending = status.pendingMessage.isEmpty() ? SettingsTextCopy.pendingUpdateFallback() : status.pendingMessage;
            box.addView(text(pending, 15, MUTED, false));
            if (canInstall) {
                Button install = primaryButton(SettingsTextCopy.installVerifiedUpdateLabel(), CORAL);
                install.setOnClickListener(v -> runUpdate(true));
                box.addView(install);
            }
        }

        if (!canInstall) {
            Button permission = secondaryButton(SettingsTextCopy.setupAppInstallsLabel());
            permission.setOnClickListener(v -> startActivity(GitHubUpdater.installPermissionIntent(this)));
            box.addView(permission);
        }

        Button toggle = secondaryButton(SettingsTextCopy.automaticUpdatesToggleLabel(status.enabled));
        toggle.setOnClickListener(v -> {
            AutoUpdateSettingsTogglePolicy.ToggleResult result = AutoUpdateSettingsTogglePolicy.toggle(status.enabled);
            SettingsWriteActions.setAutoUpdateEnabled(result, store::saveAutoUpdateEnabled);
            if (result.enabled()) {
                AutoUpdateScheduler.schedule(this);
            } else {
                AutoUpdateScheduler.cancel(this);
            }
            Toast.makeText(this, result.message(), Toast.LENGTH_SHORT).show();
            renderUpdate();
        });
        box.addView(toggle);
        return box;
    }

    boolean canInstallUpdates() {
        if (installPermissionForTests != null) {
            return installPermissionForTests;
        }
        return getPackageManager().canRequestPackageInstalls();
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
                SettingsTextCopy.settingsAnkiSourceTitle(),
                SettingsTextCopy.settingsAnkiSourceBody(),
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
                SettingsTextCopy.settingsStudyBehaviorTitle(),
                SettingsTextCopy.settingsStudyBehaviorBody(),
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
                SettingsTextCopy.settingsAutomationTitle(),
                SettingsTextCopy.settingsAutomationBody(),
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
                SettingsTextCopy.settingsReferenceDataTitle(),
                SettingsTextCopy.settingsReferenceDataBody(),
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

        TextView pill = text(SettingsTextCopy.settingsCockpitLabel(), 13, STUDY_PINK_DARK, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        hero.addView(pill, new LinearLayout.LayoutParams(-2, -2));

        TextView title = text(NAV_SETTINGS, 34, STUDY_PLUM, true);
        title.setPadding(0, dp(12), 0, dp(4));
        hero.addView(title);
        hero.addView(text(SettingsTextCopy.settingsHeroBody(), 16, STUDY_MUTED, false));

        LinearLayout topRow = settingsStatusRow(
                settingsStatusPill(SettingsTextCopy.noteTypeStatusLabel(), current.modelName, STUDY_PLUM),
                settingsStatusPill(SettingsTextCopy.importFiltersStatusLabel(), SettingsTextCopy.settingsImportSummary(current), TEAL)
        );
        LinearLayout bottomRow = settingsStatusRow(
                settingsStatusPill(SettingsTextCopy.importRanksStatusLabel(), current.suspendedRankMin + "-" + current.suspendedRankMax, TEAL),
                settingsStatusPill(
                        SettingsTextCopy.reminderStatusLabel(),
                        SettingsTextCopy.settingsReminderSummary(
                                reminder.enabled,
                                reminder.enabled && !ReminderScheduler.notificationsAllowed(this),
                                reminder.displayTime()
                        ),
                        reminder.enabled ? TEAL : MUTED
                )
        );
        LinearLayout automationRow = settingsStatusRow(
                settingsStatusPill(
                        SettingsTextCopy.dailySyncStatusLabel(),
                        SettingsTextCopy.settingsAutoSyncSummary(autoSync.configured, autoSync.enabled, autoSync.displayTime()),
                        autoSync.enabled ? TEAL : MUTED
                ),
                settingsStatusPill(
                        SettingsTextCopy.updatesStatusLabel(),
                        SettingsTextCopy.settingsUpdateSummary(autoUpdate.hasPendingUpdate(), autoUpdate.enabled),
                        autoUpdate.hasPendingUpdate() ? CORAL : STUDY_PINK_DARK
                )
        );
        hero.addView(topRow);
        hero.addView(bottomRow);
        hero.addView(automationRow);
        hero.addView(settingsStatusPill(SettingsTextCopy.matchingCardsStatusLabel(), SettingsTextCopy.matchingCardsSummary(current), STUDY_PLUM));

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
        pill.setContentDescription(SettingsTextCopy.statusPillDescription(label, value));
        return pill;
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
        header.setContentDescription(SettingsTextCopy.categoryToggleDescription(expanded, title));
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

        TextView count = text(SettingsTextCopy.settingsCategoryPanelCount(panels.length), 12, STUDY_PINK_DARK, true);
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
        box.addView(text(SettingsTextCopy.importFiltersTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.settingsImportSummary(current), 17, TEAL, true));
        box.addView(text(SettingsTextCopy.importFiltersBody(), 15, MUTED, false));
        addImportPresetButtons(box);

        CheckBox activeCards = importFilterCheckBox(SettingsTextCopy.activeCardsLabel(), current.importActiveCards);
        CheckBox suspendedCards = importFilterCheckBox(SettingsTextCopy.suspendedCardsLabel(), current.importSuspendedCards);
        CheckBox taggedCards = importFilterCheckBox(SettingsTextCopy.taggedCardsLabel(), current.importTaggedCardsEnabled());
        CheckBox weakCards = importFilterCheckBox(SettingsTextCopy.weakCardsLabel(), current.importWeakCards);
        CheckBox browserQueryCards = importFilterCheckBox(SettingsTextCopy.browserQueryLabel(), current.importBrowserQueryCards);
        box.addView(activeCards);
        box.addView(suspendedCards);
        box.addView(taggedCards);
        box.addView(weakCards);
        box.addView(browserQueryCards);

        EditText browserQueryInput = fieldInput(current.importBrowserQuery);
        browserQueryInput.setHint(SettingsTextCopy.ankiBrowserQueryHint());
        addFieldMappingInput(box, SettingsTextCopy.ankiBrowserQueryLabel(), browserQueryInput);

        EditText tags = fieldInput(current.importTagsText());
        tags.setHint(SettingsTextCopy.ankiNoteTagsHint());
        addFieldMappingInput(box, SettingsTextCopy.ankiNoteTagsLabel(), tags);

        LinearLayout thresholds = new LinearLayout(this);
        thresholds.setOrientation(LinearLayout.HORIZONTAL);
        EditText difficultyInput = decimalInput(current.importWeakFsrsDifficultyThreshold);
        LinearLayout difficultyColumn = inputColumn(SettingsTextCopy.fsrsDifficultyLabel(), difficultyInput, 0);
        EditText lapses = thresholdInput(current.importWeakLapsesThreshold);
        LinearLayout lapsesColumn = inputColumn(SettingsTextCopy.lapsesLabel(), lapses, dp(10));
        thresholds.addView(difficultyColumn, new LinearLayout.LayoutParams(0, -2, 1));
        thresholds.addView(lapsesColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(thresholds);

        EditText minMatching = thresholdInput(current.importMinMatchingCardsPerKanji);
        addFieldMappingInput(box, SettingsTextCopy.minimumMatchingCardsLabel(), minMatching);

        Button save = primaryButton(SettingsTextCopy.saveImportFiltersLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            List<String> parsedTags = RecordsBase.parseImportTags(tags.getText().toString());
            String queryText = browserQueryInput.getText().toString().trim();
            if (browserQueryCards.isChecked() && queryText.isEmpty()) {
                Toast.makeText(this, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText)) {
                Toast.makeText(this, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            ImportThresholds parsedThresholds = readImportThresholds(difficultyInput, lapses, minMatching);
            if (parsedThresholds == null) {
                return;
            }
            SettingsWriteActions.saveImportFilters(
                    new SettingsWriteActions.ImportFilterWriteRequest(
                            activeCards.isChecked(),
                            suspendedCards.isChecked(),
                            taggedCards.isChecked(),
                            String.join(" ", parsedTags),
                            weakCards.isChecked(),
                            parsedThresholds.difficulty,
                            parsedThresholds.lapseThreshold,
                            parsedThresholds.minCards,
                            browserQueryCards.isChecked(),
                            queryText
                    ),
                    importFilterWriter()
            );
            Toast.makeText(this, SettingsTextCopy.importFiltersSavedToast(), Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    void addImportPresetButtons(LinearLayout box) {
        box.addView(text(SettingsTextCopy.presetsTitle(), 17, INK, true));
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
        SettingsWriteActions.applyImportPreset(preset, importFilterWriter());
        Toast.makeText(this, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show();
        renderSettings();
    }

    SettingsWriteActions.SettingWriter importFilterWriter() {
        return new SettingsWriteActions.SettingWriter() {
            @Override
            public void putIntSetting(String key, int value) {
                store.putIntSetting(key, value);
            }

            @Override
            public void putStringSetting(String key, String value) {
                store.putStringSetting(key, value);
            }

            @Override
            public void putDoubleSetting(String key, double value) {
                store.putDoubleSetting(key, value);
            }
        };
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
            Toast.makeText(this, SettingsTextCopy.numericImportThresholdsToast(), Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!SettingsInputRules.validImportThresholds(difficulty, lapseThreshold, minCards)) {
            Toast.makeText(this, SettingsTextCopy.importThresholdRangeToast(), Toast.LENGTH_SHORT).show();
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
        if (activeCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null);
        }
        if (suspendedCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null);
        }
        if (weakCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null);
        }
        if (taggedCards.isChecked() && SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, parsedTags, "")) {
            return true;
        }
        return SettingsInputRules.hasSelectedImportSource(
                false,
                false,
                false,
                false,
                browserQueryCards.isChecked(),
                Collections.emptyList(),
                queryText
        );
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
        box.addView(text(SettingsTextCopy.frequencyRangeTitle(), 23, INK, true));
        TextView status = text(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]), 17, TEAL, true);
        box.addView(status);
        box.addView(text(SettingsTextCopy.frequencyRangeBody(), 15, MUTED, false));

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout minColumn = new LinearLayout(this);
        minColumn.setOrientation(LinearLayout.VERTICAL);
        minColumn.addView(text(SettingsTextCopy.minRankLabel(), 15, INK, true));
        EditText minInput = rankInput(selected[0]);
        minColumn.addView(minInput, new LinearLayout.LayoutParams(-1, dp(58)));
        inputs.addView(minColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout maxColumn = new LinearLayout(this);
        maxColumn.setOrientation(LinearLayout.VERTICAL);
        maxColumn.setPadding(dp(10), 0, 0, 0);
        maxColumn.addView(text(SettingsTextCopy.maxRankLabel(), 15, INK, true));
        EditText maxInput = rankInput(selected[1]);
        maxColumn.addView(maxInput, new LinearLayout.LayoutParams(-1, dp(58)));
        inputs.addView(maxColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(inputs);

        box.addView(text(SettingsTextCopy.minimumRankLabel(), 14, MUTED, true));
        SeekBar minSlider = new SeekBar(this);
        box.addView(minSlider, new LinearLayout.LayoutParams(-1, dp(56)));
        box.addView(text(SettingsTextCopy.maximumRankLabel(), 14, MUTED, true));
        SeekBar maxSlider = new SeekBar(this);
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, dp(56)));
        bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);

        Button save = primaryButton(SettingsTextCopy.saveFrequencyRangeLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int minRank;
            int maxRank;
            try {
                minRank = parseRankInput(minInput);
                maxRank = parseRankInput(maxInput);
            } catch (NumberFormatException error) {
                Toast.makeText(this, SettingsTextCopy.numericRanksToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SettingsInputRules.validRank(minRank) || !SettingsInputRules.validRank(maxRank)) {
                Toast.makeText(this, SettingsTextCopy.rankRangeToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsInputRules.RankRange rankRange = SettingsInputRules.normalizedRankRange(minRank, maxRank);
            SettingsWriteActions.saveFrequencyRange(rankRange.minRank(), rankRange.maxRank(), store::putIntSetting);
            Toast.makeText(this, SettingsTextCopy.frequencyRangeSavedToast(), Toast.LENGTH_LONG).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    LinearLayout dataLicenseSettingsPanel() {
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.offlineDataLicensesTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.offlineDataLicensesBody(), 15, MUTED, false));
        Button open = secondaryButton(SettingsTextCopy.openDataLicensesLabel());
        open.setOnClickListener(v -> renderDataSources());
        box.addView(open);
        return box;
    }

    void renderDataSources() {
        base(NAV_SETTINGS_ROUTE);
        content.addView(fullWidthHomeButton());
        Button backButton = secondaryButton(SettingsTextCopy.backToSettingsLabel());
        backButton.setOnClickListener(v -> renderSettings(false));
        content.addView(backButton);
        content.addView(text(SettingsTextCopy.dataLicensesTitle(), 34, INK, true));
        content.addView(text(SettingsTextCopy.dataLicensesBody(), 16, MUTED, false));

        LinearLayout dictionary = panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        dictionary.addView(text(SettingsTextCopy.dictionaryDataTitle(), 23, INK, true));
        dictionary.addView(text(AttributionTexts.dictionarySources(this), 14, MUTED, false));
        content.addView(dictionary);

        LinearLayout stroke = panelBox(Color.WHITE, Color.rgb(246, 202, 225));
        stroke.addView(text(SettingsTextCopy.strokeDataTitle(), 23, INK, true));
        stroke.addView(text(AttributionTexts.kanjiVg(this), 14, MUTED, false));
        content.addView(stroke);

        LinearLayout fonts = panelBox(Color.WHITE, Color.rgb(255, 247, 220));
        fonts.addView(text(SettingsTextCopy.fontsTitle(), 23, INK, true));
        fonts.addView(text(AttributionTexts.rawResourceText(this, R.raw.font_attribution), 14, MUTED, false));
        content.addView(fonts);

    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.noteTypeFieldsTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.noteTypeUsingText(current.modelName), 17, TEAL, true));
        box.addView(text(SettingsTextCopy.noteTypeFieldsBody(), 15, MUTED, false));

        EditText noteType = noteTypeInput(current.modelName);
        box.addView(noteType, new LinearLayout.LayoutParams(-1, dp(58)));
        EditText expressionField = fieldInput(current.expressionField);
        EditText readingField = fieldInput(current.readingField);
        EditText meaningField = fieldInput(current.meaningField);
        EditText sentenceField = fieldInput(current.sentenceField);
        EditText frequencyField = fieldInput(current.frequencyField);
        EditText frequencySortField = fieldInput(current.frequencySortField);
        box.addView(text(SettingsTextCopy.requiredFieldsTitle(), 15, STUDY_PLUM, true));
        box.addView(text(SettingsTextCopy.requiredFieldsBody(), 14, MUTED, false));
        addFieldMappingInput(box, SettingsTextCopy.expressionFieldLabel(), expressionField);
        addFieldMappingInput(box, SettingsTextCopy.readingFieldLabel(), readingField);
        addFieldMappingInput(box, SettingsTextCopy.meaningFieldLabel(), meaningField);
        addFieldMappingInput(box, SettingsTextCopy.sentenceFieldLabel(), sentenceField);
        addFieldMappingInput(box, SettingsTextCopy.frequencyFieldLabel(), frequencyField);
        addFieldMappingInput(box, SettingsTextCopy.frequencySortFieldLabel(), frequencySortField);

        NoteTypeFieldMappings.Inputs fieldMappings = new NoteTypeFieldMappings.Inputs(
                noteType,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField
        );
        Button choose = secondaryButton(SettingsTextCopy.chooseFromAnkiDroidLabel());
        choose.setOnClickListener(v -> NoteTypeFieldMappings.choose(this, gateway, io, main, fieldMappings));
        box.addView(choose);
        Button kiku = secondaryButton(SettingsTextCopy.useKikuLabel());
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

        Button save = primaryButton(SettingsTextCopy.saveNoteTypeLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            String selected = noteType.getText().toString().trim();
            if (selected.isEmpty()) {
                Toast.makeText(this, SettingsTextCopy.noteTypeRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (expressionField.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, SettingsTextCopy.expressionFieldRequiredToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsWriteActions.saveNoteTypeFields(
                    new SettingsWriteActions.NoteTypeFieldWriteRequest(
                            selected,
                            expressionField.getText().toString().trim(),
                            readingField.getText().toString().trim(),
                            meaningField.getText().toString().trim(),
                            sentenceField.getText().toString().trim(),
                            frequencyField.getText().toString().trim(),
                            frequencySortField.getText().toString().trim()
                    ),
                    store::putStringSetting
            );
            Toast.makeText(this, SettingsTextCopy.noteTypeSavedToast(), Toast.LENGTH_LONG).show();
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
        minSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
        maxSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));

        minSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = Math.min(SettingsInputRules.rankFromSliderProgress(progress), selected[1]);
                minInput.setText(String.format(Locale.ROOT, "%d", selected[0]));
                status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
            }
        });
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[1] = Math.max(SettingsInputRules.rankFromSliderProgress(progress), selected[0]);
                maxInput.setText(String.format(Locale.ROOT, "%d", selected[1]));
                status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));
            }
        });
    }

    int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    double parseDecimalInput(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        final String[] selected = new String[]{current.newCardSortMode};
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.newCardSortTitle(), 23, INK, true));
        TextView status = text(SettingsTextCopy.newCardSortStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text(SettingsTextCopy.newCardSortBody(), 15, MUTED, false));

        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY), RecordsBase.NEW_CARD_SORT_FREQUENCY, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY), RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK), RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS), RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, selected, status);

        Button save = primaryButton(SettingsTextCopy.saveNewCardSortLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            NewCardSortSettingsPolicy.SaveRequest request = NewCardSortSettingsPolicy.saveRequest(selected[0]);
            SettingsWriteActions.saveNewCardSort(request, store::putStringSetting);
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, TextView status) {
        Button button = secondaryButton(label);
        button.setOnClickListener(v -> {
            selected[0] = mode;
            status.setText(SettingsTextCopy.newCardSortStatusText(mode));
        });
        box.addView(button);
    }

    LinearLayout workloadSettingsPanel() {
        int current = store.adaptiveLoadWorkPercent();
        int currentMax = store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.dailyWorkloadTitle(), 23, INK, true));

        if (autoMode) {
            long now = System.currentTimeMillis();
            List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
            RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty()
                    ? null
                    : adaptivePlan(rows, store.studyItems(), now);
            box.addView(text(SettingsTextCopy.autoWorkloadStatusText(plan), 17, TEAL, true));
            box.addView(text(SettingsTextCopy.automaticWorkloadBody(), 15, MUTED, false));
            addMaxItemsControl(box, selectedMax, null, null);
            Button saveMax = primaryButton(SettingsTextCopy.saveMaximumLabel(), STUDY_PINK_DARK);
            saveMax.setOnClickListener(v -> {
                WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveMaximum(selectedMax[0]);
                SettingsWriteActions.saveWorkload(request, workloadSettingsWriter());
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(saveMax);
            Button manual = secondaryButton(SettingsTextCopy.manualWorkloadLabel());
            manual.setOnClickListener(v -> {
                WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableManualMode();
                SettingsWriteActions.saveWorkload(request, workloadSettingsWriter());
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(manual);
            return box;
        }

        TextView status = text(SettingsTextCopy.workloadStatusText(selected[0], selectedMax[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text(SettingsTextCopy.manualWorkloadBody(), 15, MUTED, false));

        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = AdaptiveLoadPlanner.snapWorkloadPercent(progress);
                status.setText(SettingsTextCopy.workloadStatusText(selected[0], selectedMax[0]));
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
        for (String label : SettingsTextCopy.workloadScaleLabels()) {
            TextView item = text(label, 11, MUTED, false);
            item.setGravity(Gravity.CENTER);
            labels.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        box.addView(labels);

        addMaxItemsControl(box, selectedMax, status, selected);

        Button save = primaryButton(SettingsTextCopy.saveWorkloadLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveManualWorkload(selected[0], selectedMax[0]);
            SettingsWriteActions.saveWorkload(request, workloadSettingsWriter());
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        Button automatic = secondaryButton(SettingsTextCopy.automaticParetoLabel());
        automatic.setOnClickListener(v -> {
            WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableAutomaticMode();
            SettingsWriteActions.saveWorkload(request, workloadSettingsWriter());
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(automatic);
        return box;
    }

    SettingsWriteActions.WorkloadSettingsWriter workloadSettingsWriter() {
        return new SettingsWriteActions.WorkloadSettingsWriter() {
            @Override
            public void saveAdaptiveLoadMode(String mode) {
                store.saveAdaptiveLoadMode(mode);
            }

            @Override
            public void saveAdaptiveLoadWorkPercent(int workloadPercent) {
                store.saveAdaptiveLoadWorkPercent(workloadPercent);
            }

            @Override
            public void saveAdaptiveLoadMaxItems(int maxItems) {
                store.saveAdaptiveLoadMaxItems(maxItems);
            }
        };
    }

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        TextView maxStatus = text(SettingsTextCopy.maxItemsStatusText(selectedMax[0]), 17, TEAL, true);
        maxStatus.setPadding(0, dp(8), 0, 0);
        box.addView(maxStatus);

        SeekBar maxSlider = new SeekBar(this);
        maxSlider.setMax(AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedMax[0] = AdaptiveLoadPlanner.normalizeMaxItems(progress + AdaptiveLoadPlanner.MIN_MAX_ITEMS);
                maxStatus.setText(SettingsTextCopy.maxItemsStatusText(selectedMax[0]));
                if (workloadStatus != null && selectedWorkload != null) {
                    workloadStatus.setText(SettingsTextCopy.workloadStatusText(selectedWorkload[0], selectedMax[0]));
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
        box.addView(text(SettingsTextCopy.learningStepsTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.learningStepsBody(), 15, MUTED, false));

        EditText newSteps = stepInput(current.newStepsText());
        EditText reviewSteps = stepInput(current.reviewStepsText());
        box.addView(text(LABEL_NEW_CARDS, 15, INK, true));
        box.addView(newSteps, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text(SettingsTextCopy.reviewMissesLabel(), 15, INK, true));
        box.addView(reviewSteps, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        Button ankiDefault = secondaryButton(SettingsTextCopy.ankiDefaultLabel());
        ankiDefault.setOnClickListener(v -> {
            RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.reviewStepsText());
        });
        presets.addView(ankiDefault, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button sameSteps = secondaryButton(SettingsTextCopy.sameLearningStepsLabel());
        sameSteps.setOnClickListener(v -> {
            RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
            newSteps.setText(defaults.newStepsText());
            reviewSteps.setText(defaults.newStepsText());
        });
        presets.addView(sameSteps, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(presets);

        Button save = primaryButton(SettingsTextCopy.saveLearningStepsLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            LearningStepsSettingsPolicy.SaveResult request = LearningStepsSettingsPolicy.saveRequest(
                    newSteps.getText().toString(),
                    reviewSteps.getText().toString()
            );
            if (!request.valid) {
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsWriteActions.saveLearningSteps(request, store::saveLearningStepSettings);
            Toast.makeText(this, SettingsTextCopy.learningStepsSavedToast(), Toast.LENGTH_SHORT).show();
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
        box.addView(text(SettingsTextCopy.studyAheadTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.studyAheadBody(), 15, MUTED, false));

        EditText minutesInput = new EditText(this);
        minutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(String.format(Locale.ROOT, "%d", currentMinutes));
        minutesInput.setTextSize(20);
        minutesInput.setSingleLine(true);
        minutesInput.setSelectAllOnFocus(true);
        box.addView(text(SettingsTextCopy.studyAheadMinutesLabel(), 15, INK, true));
        box.addView(minutesInput, new LinearLayout.LayoutParams(-1, dp(58)));

        Button save = primaryButton(SettingsTextCopy.saveStudyAheadLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            StudyAheadSettingsPolicy.SaveResult request = StudyAheadSettingsPolicy.saveRequest(minutesInput.getText().toString());
            if (!request.valid) {
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsWriteActions.saveStudyAhead(request, store::saveStudyAheadMinutes);
            Toast.makeText(this, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        return box;
    }

    LinearLayout studyLadderSettingsPanel() {
        RecordsBase.StudyLadderSettings ladder = studyLadderSettings();
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.studyLadderTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.studyLadderBody(), 15, MUTED, false));

        List<RecordsBase.LadderRung> rungs = ladder.orderedRungs;
        for (int i = 0; i < rungs.size(); i++) {
            RecordsBase.LadderRung rung = rungs.get(i);
            LinearLayout row = softInsetPanel();
            row.addView(text(SettingsTextCopy.settingsLadderRungLabel(rung), 19, STUDY_PLUM, true));
            row.addView(text(SettingsTextCopy.ladderRungSubtitle(ladder, rung), 13, MUTED, false));

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            Button toggle = secondaryButton(SettingsTextCopy.ladderToggleLabel(ladder.isEnabled(rung)));
            toggle.setOnClickListener(v -> toggleLadderRung(rung));
            controls.addView(toggle, new LinearLayout.LayoutParams(0, dp(48), 1));

            Button up = secondaryButton(SettingsTextCopy.moveUpLabel());
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> {
                SettingsWriteActions.moveStudyLadderRung(studyLadderSettings(), rung, -1, store::saveStudyLadderSettings);
                renderSettings();
            });
            LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, dp(48), 1);
            upLp.setMargins(dp(8), 0, 0, 0);
            controls.addView(up, upLp);

            Button down = secondaryButton(SettingsTextCopy.moveDownLabel());
            down.setEnabled(i < rungs.size() - 1);
            down.setOnClickListener(v -> {
                SettingsWriteActions.moveStudyLadderRung(studyLadderSettings(), rung, 1, store::saveStudyLadderSettings);
                renderSettings();
            });
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(0, dp(48), 1);
            downLp.setMargins(dp(8), 0, 0, 0);
            controls.addView(down, downLp);
            row.addView(controls);
            box.addView(row);
        }

        Button reset = secondaryButton(SettingsTextCopy.restoreDefaultLadderLabel());
        reset.setOnClickListener(v -> {
            SettingsWriteActions.restoreDefaultStudyLadder(store::saveStudyLadderSettings);
            Toast.makeText(this, SettingsTextCopy.studyLadderRestoredToast(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, SettingsTextCopy.keepAlwaysAvailableRungToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsWriteActions.saveStudyLadder(next, store::saveStudyLadderSettings);
        Toast.makeText(this, SettingsTextCopy.ladderRungToggleToast(rung, wasEnabled), Toast.LENGTH_SHORT).show();
        renderSettings();
    }

    LinearLayout ladderThresholdSettingsPanel() {
        RecordsSyncModels.Settings current = settings();
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.ladderThresholdsTitle(), 23, INK, true));
        box.addView(text(SettingsTextCopy.ladderThresholdsBody(), 15, MUTED, false));

        EditText promotionDays = thresholdInput(current.ladderPromotionIntervalDays);
        EditText failStreak = thresholdInput(current.ladderDemotionFailStreak);
        box.addView(text(SettingsTextCopy.fsrsDaysToGoUpLabel(), 15, INK, true));
        box.addView(promotionDays, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(text(SettingsTextCopy.failsToGoDownLabel(), 15, INK, true));
        box.addView(failStreak, new LinearLayout.LayoutParams(-1, dp(58)));

        Button defaults = secondaryButton(SettingsTextCopy.useDefaultLadderThresholdsLabel());
        defaults.setOnClickListener(v -> {
            promotionDays.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS));
            failStreak.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK));
        });
        box.addView(defaults);

        Button save = primaryButton(SettingsTextCopy.saveLadderThresholdsLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            StudyLadderThresholdPolicy.SaveResult request = StudyLadderThresholdPolicy.saveRequest(
                    promotionDays.getText().toString(),
                    failStreak.getText().toString()
            );
            if (!request.valid) {
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsWriteActions.saveLadderThresholds(request, store::putIntSetting);
            Toast.makeText(this, SettingsTextCopy.ladderThresholdsSavedToast(), Toast.LENGTH_SHORT).show();
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
        final int[] selected = new int[]{SettingsInputRules.retentionPercent(current.targetRetention)};
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.fsrsRetentionTitle(), 23, INK, true));
        TextView status = text(SettingsTextCopy.retentionStatusText(selected[0]), 17, TEAL, true);
        box.addView(status);
        box.addView(text(SettingsTextCopy.fsrsRetentionBody(), 15, MUTED, false));

        SeekBar slider = new SeekBar(this);
        slider.setMax(17);
        slider.setProgress(selected[0] - 80);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 80 + progress;
                status.setText(SettingsTextCopy.retentionStatusText(selected[0]));
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
            Button preset = secondaryButton(SettingsTextCopy.retentionPresetLabel(value));
            preset.setOnClickListener(v -> {
                selected[0] = value;
                slider.setProgress(value - 80);
                status.setText(SettingsTextCopy.retentionStatusText(selected[0]));
            });
            quick.addView(preset, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        box.addView(quick);

        CheckBox rankRetentionEnabled = importFilterCheckBox(SettingsTextCopy.useJitenRankRetentionRangesLabel(), current.frequencyRetentionEnabled);
        box.addView(rankRetentionEnabled);
        box.addView(text(SettingsTextCopy.jitenRankRetentionRangesBody(), 15, MUTED, false));
        EditText rankRanges = rankRetentionRangesInput(current.frequencyRetentionRanges);
        box.addView(rankRanges, new LinearLayout.LayoutParams(-1, dp(132)));

        Button exampleRanges = secondaryButton(SettingsTextCopy.useExampleRangesLabel());
        exampleRanges.setOnClickListener(v -> rankRanges.setText(FrequencyRetentionRanges.exampleText()));
        box.addView(exampleRanges);

        Button save = primaryButton(SettingsTextCopy.saveRetentionLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            RetentionSettingsPolicy.SaveResult request = RetentionSettingsPolicy.saveRequest(
                    selected[0],
                    rankRetentionEnabled.isChecked(),
                    rankRanges.getText().toString(),
                    store.schedulerParameters()
            );
            if (!request.valid) {
                Toast.makeText(this, request.message, Toast.LENGTH_LONG).show();
                return;
            }
            SettingsWriteActions.saveRetention(request, store::saveSchedulerParameters);
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
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

    LinearLayout reminderSettingsPanel() {
        LocalStore.ReminderSettings reminder = store.reminderSettings();
        boolean notificationsAllowed = notificationsAllowedForReminders();
        boolean blocked = reminder.enabled && !notificationsAllowed;
        int[] selectedHour = new int[]{reminder.hour};
        int[] selectedMinute = new int[]{reminder.minute};

        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.dailyReminderTitle(), 23, INK, true));
        box.addView(text(
                SettingsTextCopy.reminderStatus(reminder.enabled, blocked, reminder.displayTime()),
                17,
                blocked ? CORAL : (reminder.enabled ? TEAL : MUTED),
                true
        ));
        box.addView(text(SettingsTextCopy.dailyReminderBody(), 15, MUTED, false));

        Button time = secondaryButton(SettingsTextCopy.reminderTimeButtonLabel(selectedHour[0], selectedMinute[0]));
        time.setOnClickListener(v -> new TimePickerDialog(
                this,
                (view, hour, minute) -> applyReminderTimeSelection(selectedHour, selectedMinute, time, hour, minute),
                selectedHour[0],
                selectedMinute[0],
                true
        ).show());
        box.addView(time);

        List<View> presets = new ArrayList<>();
        presets.add(reminderPresetButton(SettingsTextCopy.morningReminderPresetLabel(), 8, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.lunchReminderPresetLabel(), 12, 30, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.eveningReminderPresetLabel(), 19, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.nightReminderPresetLabel(), 21, 0, selectedHour, selectedMinute, time));
        box.addView(twoColumnGrid(presets));

        Button save = primaryButton(reminder.enabled ? SettingsTextCopy.saveReminderLabel() : SettingsTextCopy.enableReminderLabel(), STUDY_PINK_DARK);
        save.setOnClickListener(v -> saveReminderFromSelection(selectedHour[0], selectedMinute[0], true));
        box.addView(save);
        if (reminder.enabled) {
            Button off = secondaryButton(SettingsTextCopy.turnOffReminderLabel());
            off.setOnClickListener(v -> {
                ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(false, reminder.hour, reminder.minute);
                SettingsWriteActions.saveReminder(fields, store::saveReminderSettings);
                ReminderScheduler.cancel(this);
                Toast.makeText(this, ReminderSettingsSavePolicy.DISABLED_MESSAGE, Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(off);
        }
        if (blocked) {
            box.addView(text(SettingsTextCopy.notificationsBlockedBody(), 14, CORAL, false));
            Button notificationSettings = secondaryButton(SettingsTextCopy.openNotificationSettingsLabel());
            notificationSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
            box.addView(notificationSettings);
        } else if (!hasRuntimeNotificationPermissionForReminder()) {
            box.addView(text(SettingsTextCopy.notificationPermissionBody(), 14, CORAL, false));
        }
        return box;
    }

    LinearLayout autoSyncSettingsPanel() {
        LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
        LinearLayout box = settingsPanelBox();
        box.addView(text(SettingsTextCopy.dailyAnkiSyncTitle(), 23, INK, true));
        box.addView(text(
                SettingsTextCopy.autoSyncStatus(auto.configured, auto.enabled, auto.displayTime()),
                17,
                auto.enabled ? TEAL : MUTED,
                true
        ));
        String lastSuccess = auto.lastSuccessAt > 0L ? DateTextPolicy.shortDateTime(auto.lastSuccessAt) : "";
        String lastAttempt = auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt
                ? DateTextPolicy.shortDateTime(auto.lastAttemptAt)
                : "";
        String nextRun = auto.nextRunAt > 0L ? DateTextPolicy.shortDateTime(auto.nextRunAt) : "";
        box.addView(text(SettingsTextCopy.autoSyncDetail(auto.configured, auto.enabled, lastSuccess, lastAttempt, nextRun), 15, MUTED, false));
        if (auto.configured) {
            if (auto.enabled) {
                Button off = secondaryButton(SettingsTextCopy.turnOffDailySyncLabel());
                off.setOnClickListener(v -> {
                    AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.disable();
                    SettingsWriteActions.setAutoSyncEnabled(result, store::setAutoSyncEnabled);
                    AutoSyncScheduler.cancel(this);
                    Toast.makeText(this, result.message(), Toast.LENGTH_SHORT).show();
                    renderSettings();
                });
                box.addView(off);
            } else {
                Button on = primaryButton(SettingsTextCopy.turnOnDailySyncLabel(), STUDY_PINK_DARK);
                on.setOnClickListener(v -> {
                    AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.enable();
                    SettingsWriteActions.setAutoSyncEnabled(result, store::setAutoSyncEnabled);
                    AutoSyncScheduler.schedule(this);
                    Toast.makeText(this, result.message(), Toast.LENGTH_SHORT).show();
                    renderSettings();
                });
                box.addView(on);
            }
        }
        return box;
    }

    LinearLayout updateSettingsPanel() {
        LinearLayout box = autoUpdatePanel(SettingsTextCopy.appUpdatesTitle());
        Button update = primaryButton(SettingsTextCopy.openUpdaterLabel(), STUDY_PINK_DARK);
        update.setOnClickListener(v -> renderUpdate());
        box.addView(update);
        return box;
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        Button preset = secondaryButton(SettingsTextCopy.reminderPresetButtonLabel(label, hour, minute));
        preset.setTextSize(13);
        preset.setMinHeight(dp(54));
        preset.setOnClickListener(v -> {
            selectedHour[0] = hour;
            selectedMinute[0] = minute;
            timeButton.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
        });
        return preset;
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(enabled, hour, minute);
        LocalStore.ReminderSettings reminder = SettingsWriteActions.reminderSettings(fields);
        if (!enabled) {
            SettingsWriteActions.saveReminder(fields, store::saveReminderSettings);
            ReminderScheduler.cancel(this);
            Toast.makeText(this, ReminderSettingsSavePolicy.DISABLED_MESSAGE, Toast.LENGTH_SHORT).show();
            renderSettings();
            return;
        }
        ReminderScheduler.ensureNotificationChannel(this);
        if (!hasRuntimeNotificationPermissionForReminder()) {
            pendingReminderSettings = reminder;
            requestPermissions(new String[]{PERMISSION_POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            return;
        }
        SettingsWriteActions.saveReminder(fields, store::saveReminderSettings);
        ReminderScheduler.schedule(this, reminder);
        boolean allowed = notificationsAllowedForReminders();
        Toast.makeText(this, ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed), allowed ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        renderSettings();
    }

    void applyReminderTimeSelection(int[] selectedHour, int[] selectedMinute, Button time, int hour, int minute) {
        selectedHour[0] = hour;
        selectedMinute[0] = minute;
        time.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
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
        UpdateRunScreenCopy.Copy copy = UpdateRunScreenCopy.forRun(cachedPending);
        content.addView(fullWidthHomeButton());
        Button back = secondaryButton(SettingsTextCopy.backToSettingsLabel());
        back.setOnClickListener(v -> renderSettings(false));
        content.addView(back);
        content.addView(text(copy.title(), 32, INK, true));
        content.addView(text(copy.body(), 16, MUTED, false));
        content.addView(indeterminateProgressRow(copy.progressLabel()));
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
