package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy;

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
    private MainActivitySettingsAnkiSource ankiSource() {
        return new MainActivitySettingsAnkiSource(this);
    }

    private MainActivitySettingsRetentionPanel retentionPanel() {
        return new MainActivitySettingsRetentionPanel(this);
    }

    private MainActivitySettingsStudyAheadPanel studyAheadPanel() {
        return new MainActivitySettingsStudyAheadPanel(this);
    }

    private MainActivitySettingsLadderThresholdPanel ladderThresholdPanel() {
        return new MainActivitySettingsLadderThresholdPanel(this);
    }

    private MainActivitySettingsStudySortPanel studySortPanel() {
        return new MainActivitySettingsStudySortPanel(this);
    }

    private MainActivitySettingsWorkloadPanel workloadPanel() {
        return new MainActivitySettingsWorkloadPanel(this);
    }

    private MainActivitySettingsLearningPanel learningPanel() {
        return new MainActivitySettingsLearningPanel(this);
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

    private MainActivitySettingsUpdateFlow updateFlow() {
        return new MainActivitySettingsUpdateFlow(this);
    }

    private MainActivitySettingsPanelFactory panelFactory() {
        return new MainActivitySettingsPanelFactory(this);
    }

    void renderUpdate() {
        updatePage().renderUpdate();
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
        return MainActivitySettingsAutomationHeroCompose.settingsAutomationHeroView(this, current, reminder, autoSync, autoUpdate);
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
        return panelFactory().settingsPanelBox();
    }

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        return ankiSource().importFilterSettingsPanel(current);
    }

    CheckBox importFilterCheckBox(String label, boolean checked) {
        return panelFactory().importFilterCheckBox(label, checked);
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return ankiSource().frequencyRangeSettingsPanel(current);
    }

    View dataLicenseSettingsPanel() {
        return referenceData().dataLicenseSettingsPanel();
    }

    void renderDataSources() {
        referenceData().renderDataSources();
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        return ankiSource().noteTypeSettingsPanel(current);
    }

    View newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        return studySortPanel().newCardSortSettingsPanel(current);
    }

    LinearLayout workloadSettingsPanel() {
        return workloadPanel().workloadSettingsPanel();
    }

    LinearLayout learningStepsSettingsPanel() {
        return learningPanel().learningStepsSettingsPanel();
    }

    View studyAheadSettingsPanel() {
        return studyAheadPanel().studyAheadSettingsPanel();
    }

    LinearLayout studyLadderSettingsPanel() {
        return studyLadderUi().studyLadderSettingsPanel();
    }

    void toggleLadderRung(RecordsBase.LadderRung rung) {
        studyLadderUi().toggleLadderRung(rung);
    }

    View ladderThresholdSettingsPanel() {
        return ladderThresholdPanel().ladderThresholdSettingsPanel();
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
        return retentionPanel().retentionSettingsPanel();
    }

    LinearLayout reminderSettingsPanel() {
        return new MainActivitySettingsAutomationReminder(this).reminderSettingsPanel();
    }

    LinearLayout autoSyncSettingsPanel() {
        return new MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanel();
    }

    View updateSettingsPanel() {
        return MainActivitySettingsUpdatePanelCompose.settingsUpdateOverviewPanelView(
                this,
                new SettingsUpdateOverviewPanelModel(
                        MainActivitySettingsUpdatePageCompose.settingsUpdatePanelModel(
                                this,
                                SettingsTextCopy.appUpdatesTitle()
                        ),
                        SettingsTextCopy.openUpdaterLabel(),
                        () -> {
                            renderUpdate();
                            return kotlin.Unit.INSTANCE;
                        }
                )
        );
    }

    void runUpdate(boolean cachedPending) {
        updateFlow().runUpdate(cachedPending);
    }
}
