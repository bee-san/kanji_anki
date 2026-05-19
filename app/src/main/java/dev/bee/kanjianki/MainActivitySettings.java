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
import dev.bee.kanjianki.core.StudyTextCopy;
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
    private MainActivitySettingsAutomation automation() {
        return new MainActivitySettingsAutomation(this);
    }

    private MainActivitySettingsAnkiSource ankiSource() {
        return new MainActivitySettingsAnkiSource(this);
    }

    private MainActivitySettingsRetention retention() {
        return new MainActivitySettingsRetention(this);
    }

    private MainActivitySettingsStudyTuning studyTuning() {
        return new MainActivitySettingsStudyTuning(this);
    }

    private MainActivitySettingsStudySort studySort() {
        return new MainActivitySettingsStudySort(this);
    }

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
                store.saveAutoUpdateEnabled(result.enabled());
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
        return automation().settingsHero(current, reminder, autoSync, autoUpdate);
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

        TextView valueView = text(StudyTextCopy.compact(value, 56), 17, valueColor, true);
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
        return ankiSource().importFilterSettingsPanel(current);
    }

    void addImportPresetButtons(LinearLayout box) {
        ankiSource().addImportPresetButtons(box);
    }

    ImportThresholds readImportThresholds(EditText difficultyInput, EditText lapses, EditText minMatching) {
        return ankiSource().readImportThresholds(difficultyInput, lapses, minMatching);
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
        return ankiSource().hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText);
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
        return ankiSource().inputColumn(label, input, leftPadding);
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return ankiSource().frequencyRangeSettingsPanel(current);
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
        return ankiSource().noteTypeSettingsPanel(current);
    }

    EditText noteTypeInput(String value) {
        return ankiSource().noteTypeInput(value);
    }

    EditText fieldInput(String value) {
        return ankiSource().fieldInput(value);
    }

    void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        ankiSource().addFieldMappingInput(box, label, input);
    }

    EditText rankInput(int value) {
        return ankiSource().rankInput(value);
    }

    EditText decimalInput(double value) {
        return ankiSource().decimalInput(value);
    }

    void bindRankSliders(
            int[] selected,
            TextView status,
            EditText minInput,
            EditText maxInput,
            SeekBar minSlider,
            SeekBar maxSlider
    ) {
        ankiSource().bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);
    }

    int parseRankInput(EditText input) {
        return ankiSource().parseRankInput(input);
    }

    double parseDecimalInput(EditText input) {
        return ankiSource().parseDecimalInput(input);
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        return studySort().newCardSortSettingsPanel(current);
    }

    void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, TextView status) {
        studySort().addSortModeButton(box, label, mode, selected, status);
    }

    LinearLayout workloadSettingsPanel() {
        int current = store.adaptiveLoadWorkPercent();
        int currentMax = store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        SettingsWriteActions.WorkloadSettingsWriter writer = new SettingsWriteActions.WorkloadSettingsWriter() {
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
                SettingsWriteActions.saveWorkload(request, writer);
                Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
                renderSettings();
            });
            box.addView(saveMax);
            Button manual = secondaryButton(SettingsTextCopy.manualWorkloadLabel());
            manual.setOnClickListener(v -> {
                WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableManualMode();
                SettingsWriteActions.saveWorkload(request, writer);
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
            SettingsWriteActions.saveWorkload(request, writer);
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(save);
        Button automatic = secondaryButton(SettingsTextCopy.automaticParetoLabel());
        automatic.setOnClickListener(v -> {
            WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableAutomaticMode();
            SettingsWriteActions.saveWorkload(request, writer);
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show();
            renderSettings();
        });
        box.addView(automatic);
        return box;
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
        return studyTuning().studyAheadSettingsPanel();
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
                store.saveStudyLadderSettings(studyLadderSettings().moveRung(rung, -1));
                renderSettings();
            });
            LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, dp(48), 1);
            upLp.setMargins(dp(8), 0, 0, 0);
            controls.addView(up, upLp);

            Button down = secondaryButton(SettingsTextCopy.moveDownLabel());
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

        Button reset = secondaryButton(SettingsTextCopy.restoreDefaultLadderLabel());
        reset.setOnClickListener(v -> {
            store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults());
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
        store.saveStudyLadderSettings(next);
        Toast.makeText(this, SettingsTextCopy.ladderRungToggleToast(rung, wasEnabled), Toast.LENGTH_SHORT).show();
        renderSettings();
    }

    LinearLayout ladderThresholdSettingsPanel() {
        return studyTuning().ladderThresholdSettingsPanel();
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
        return retention().retentionSettingsPanel();
    }

    EditText rankRetentionRangesInput(String value) {
        return retention().rankRetentionRangesInput(value);
    }

    LinearLayout reminderSettingsPanel() {
        return automation().reminderSettingsPanel();
    }

    LinearLayout autoSyncSettingsPanel() {
        return automation().autoSyncSettingsPanel();
    }

    LinearLayout updateSettingsPanel() {
        return automation().updateSettingsPanel();
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        return automation().reminderPresetButton(label, hour, minute, selectedHour, selectedMinute, timeButton);
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        automation().saveReminderFromSelection(hour, minute, enabled);
    }

    void runUpdate(boolean cachedPending) {
        base(NAV_SETTINGS_ROUTE);
        int updateUiRun = ++updateUiRunCounter;
        activeUpdateUiRunToken = updateUiRun;
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
                if (activeUpdateUiRunToken != updateUiRun) {
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
