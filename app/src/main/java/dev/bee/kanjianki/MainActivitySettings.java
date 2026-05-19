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

    private MainActivitySettingsWorkload workload() {
        return new MainActivitySettingsWorkload(this);
    }

    private MainActivitySettingsLearning learning() {
        return new MainActivitySettingsLearning(this);
    }

    private MainActivitySettingsStudyLadder studyLadderUi() {
        return new MainActivitySettingsStudyLadder(this);
    }

    private MainActivitySettingsReferenceData referenceData() {
        return new MainActivitySettingsReferenceData(this);
    }

    private MainActivitySettingsUpdatePage updatePage() {
        return new MainActivitySettingsUpdatePage(this);
    }

    private MainActivitySettingsCategory settingsCategoryUi() {
        return new MainActivitySettingsCategory(this);
    }

    private MainActivitySettingsScreen settingsScreen() {
        return new MainActivitySettingsScreen(this);
    }

    void renderUpdate() {
        updatePage().renderUpdate();
    }

    LinearLayout autoUpdatePanel(String title) {
        return updatePage().autoUpdatePanel(title);
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
        settingsScreen().renderSettings(preserveScroll);
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
        return settingsCategoryUi().settingsCategory(title, summary, iconRes, expanded, toggle, panels);
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
        return referenceData().dataLicenseSettingsPanel();
    }

    void renderDataSources() {
        referenceData().renderDataSources();
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
        return workload().workloadSettingsPanel();
    }

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        workload().addMaxItemsControl(box, selectedMax, workloadStatus, selectedWorkload);
    }

    LinearLayout learningStepsSettingsPanel() {
        return learning().learningStepsSettingsPanel();
    }

    EditText stepInput(String value) {
        return learning().stepInput(value);
    }

    LinearLayout studyAheadSettingsPanel() {
        return studyTuning().studyAheadSettingsPanel();
    }

    LinearLayout studyLadderSettingsPanel() {
        return studyLadderUi().studyLadderSettingsPanel();
    }

    void toggleLadderRung(RecordsBase.LadderRung rung) {
        studyLadderUi().toggleLadderRung(rung);
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
