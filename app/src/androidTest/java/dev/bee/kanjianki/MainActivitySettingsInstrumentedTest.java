package dev.bee.kanjianki;

import android.content.Intent;
import android.provider.Settings;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static dev.bee.kanjianki.MainActivitySettingsUpdatePageCompose.settingsUpdatePanelModel;

@RunWith(AndroidJUnit4.class)
public final class MainActivitySettingsInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.settings_no_anki"));
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
    }

    @After
    public void tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null);
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
        context.deleteDatabase("kanji_anki_simple.db");
        deleteRecursively(new File(context.getCacheDir(), "updates"));
    }

    @Test
    public void settingsCategoriesTogglePanelsAndReferenceNavigation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderSettings();
                View settingsRoot = activity.findViewById(android.R.id.content);
                assertTrue(activity.settingsAnkiExpanded);
                assertFalse(activity.settingsStudyExpanded);
                assertTrue(containsText(settingsRoot, "Frequency range"));
                assertFalse(containsText(settingsRoot, "Daily workload"));
                activity.contentScrollY = 48;
                activity.renderSettings(true);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(48, activity.contentScrollY);
                settingsRoot = activity.findViewById(android.R.id.content);

                performClickableWithText(settingsRoot, "Study behavior");
                settingsRoot = activity.findViewById(android.R.id.content);
                assertTrue(activity.settingsStudyExpanded);
                assertTrue(containsText(settingsRoot, "Daily workload"));

                performClickableWithText(settingsRoot, "Anki source");
                settingsRoot = activity.findViewById(android.R.id.content);
                assertFalse(activity.settingsAnkiExpanded);
                assertFalse(containsText(settingsRoot, "Frequency range"));

                performClickableWithText(settingsRoot, "Automation");
                settingsRoot = activity.findViewById(android.R.id.content);
                assertTrue(activity.settingsSyncExpanded);
                assertTrue(containsText(settingsRoot, "Daily Anki sync"));

                performClickableWithText(settingsRoot, "Reference data");
                settingsRoot = activity.findViewById(android.R.id.content);
                assertTrue(activity.settingsAppExpanded);
                assertTrue(containsText(settingsRoot, "Offline data & licenses"));
                performClickableWithText(settingsRoot, "Open data licenses");
                assertHasText(activity, "Data licenses");
                performClickableWithText(activity.findViewById(android.R.id.content), "Back to settings");
                assertHasText(activity, "Automation");
            });
        }
    }

    @Test
    public void settingsPanelsPersistWorkloadAndLearningStepActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO);
                SettingsWorkloadPanelModel autoPanel = activity.workloadSettingsPanelModel();
                assertTrue(autoPanel.getAutoMode());
                assertEquals(activity.store.adaptiveLoadWorkPercent(), autoPanel.getSelectedWorkloadPercent()[0]);

                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
                SettingsWorkloadPanelModel manualPanel = activity.workloadSettingsPanelModel();
                assertFalse(manualPanel.getAutoMode());
                manualPanel.getOnEnableManual().run();
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode());

                SettingsLearningStepsPanelModel stepsPanel = activity.learningStepsSettingsPanelModel();
                assertEquals("1m", stepsPanel.getInitialNewStepsText());
                assertEquals("1m, 10m", stepsPanel.getInitialReviewStepsText());
                assertEquals("1m, 10m", activity.store.learningStepSettings().reviewStepsText());
            });
        }
    }

    @Test
    public void importFilterAndFrequencyPanelsBuildSettingsModels() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SettingsImportFiltersPanelModel importFilters = activity.importFilterSettingsPanelModel(activity.settings());
                assertEquals(SettingsTextCopy.importFiltersTitle(), importFilters.getTitle());
                assertFalse(importFilters.getState().getActiveCards());
                assertTrue(importFilters.getState().getSuspendedCards());

                SettingsFrequencyRangePanelModel frequencyRange = activity.frequencyRangeSettingsPanelModel(activity.settings());
                assertEquals(SettingsTextCopy.frequencyRangeTitle(), frequencyRange.getTitle());
                assertEquals(activity.settings().suspendedRankMin, frequencyRange.getSelectedRanks()[0]);
                assertEquals(activity.settings().suspendedRankMax, frequencyRange.getSelectedRanks()[1]);
            });
        }
    }

    @Test
    public void noteTypeInputsWriteRealAndroidFieldsAndFallbackGuesses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                EditText noteType = new EditText(activity);
                EditText expression = new EditText(activity);
                EditText reading = new EditText(activity);
                EditText meaning = new EditText(activity);
                EditText sentence = new EditText(activity);
                EditText frequency = new EditText(activity);
                EditText frequencySort = new EditText(activity);
                NoteTypeFieldMappings.Inputs inputs = new NoteTypeFieldMappings.Inputs(
                        noteType,
                        expression,
                        reading,
                        meaning,
                        sentence,
                        frequency,
                        frequencySort
                );

                NoteTypeFieldMappings.chooseNoteType(
                        new NoteTypeFieldMappings.Choice("Fallback Model", Arrays.asList("Front", "Back", "Kana")),
                        inputs
                );

                assertEquals("Fallback Model", noteType.getText().toString());
                assertEquals("Front", expression.getText().toString());
                assertEquals("Kana", reading.getText().toString());
                assertEquals("Back", meaning.getText().toString());
                assertEquals("", sentence.getText().toString());
                assertEquals("", frequency.getText().toString());
                assertEquals("", frequencySort.getText().toString());

                SettingsNoteTypePanelModel panel = activity.noteTypeSettingsPanelModel(activity.settings());
                assertEquals(activity.settings().modelName, panel.getFields().getNoteType());
                assertEquals(activity.settings().expressionField, panel.getFields().getExpression());
            });
        }
    }

    @Test
    public void settingsValidationPanelsPersistStudyAheadLadderRetentionAndReminder() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SettingsStudyAheadPanelModel studyAhead = new SettingsStudyAheadPanelModel(
                        SettingsTextCopy.studyAheadTitle(),
                        SettingsTextCopy.studyAheadBody(),
                        SettingsTextCopy.studyAheadMinutesLabel(),
                        Integer.toString(activity.store.studyAheadMinutes()),
                        SettingsTextCopy.saveStudyAheadLabel(),
                        minutesText -> { }
                );
                assertEquals(Integer.toString(activity.store.studyAheadMinutes()), studyAhead.getInitialMinutesText());

                SettingsLadderThresholdPanelModel ladder = activity.ladderThresholdSettingsPanelModel();
                assertEquals(Integer.toString(activity.settings().ladderPromotionIntervalDays), ladder.getInitialPromotionDaysText());
                assertEquals(Integer.toString(activity.settings().ladderDemotionFailStreak), ladder.getInitialFailStreakText());

                SettingsStudyLadderPanelModel ladderOrder = activity.studyLadderSettingsPanelModel();
                assertFalse(ladderOrder.getRungs().isEmpty());
                ladderOrder.getRungs().stream()
                        .filter(rung -> rung.getLabel().contains("Similar kanji"))
                        .findFirst()
                        .orElseThrow()
                        .getOnToggle()
                        .run();
                assertFalse(activity.studyLadderSettings().isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI));
                activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(RecordsBase.LadderRung.WORD_READING, -6));
                assertEquals(RecordsBase.LadderRung.WORD_READING, activity.studyLadderSettings().orderedRungs.get(0));

                SettingsNewCardSortPanelModel newCardSort = activity.newCardSortSettingsPanelModel(activity.settings());
                assertEquals(activity.settings().newCardSortMode, newCardSort.getInitialMode());

                SettingsRetentionPanelModel retention = activity.retentionSettingsPanelModel();
                assertEquals(
                        (int) Math.round(activity.store.schedulerParameters().targetRetention * 100.0),
                        retention.getSelectedRetentionPercent()[0]
                );

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 21, 0));
                SettingsReminderPanelModel reminder = activity.reminderSettingsPanelModel();
                assertEquals(21, reminder.getSelectedHour()[0]);
                assertEquals(0, reminder.getSelectedMinute()[0]);
                assertEquals(MainActivityBase.CORAL, reminderStatusColor(true, true));
                assertEquals(MainActivityBase.TEAL, reminderStatusColor(true, false));
                assertEquals(MainActivityBase.MUTED, reminderStatusColor(false, false));

                int[] selectedHour = {21};
                int[] selectedMinute = {0};
                Button timeButtonDirect = new Button(activity);
                selectedHour[0] = 6;
                selectedMinute[0] = 5;
                timeButtonDirect.setText(SettingsTextCopy.reminderTimeButtonLabel(6, 5));
                assertEquals(6, selectedHour[0]);
                assertEquals(5, selectedMinute[0]);
                assertEquals("Reminder time: 06:05", timeButtonDirect.getText().toString());
                Intent notificationIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationIntent.getAction());
                assertEquals(activity.getPackageName(), notificationIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE));
            });
        }
    }

    @Test
    public void automationPanelsToggleSyncUpdatesAndReminderActions() {
        MainActivityRuntimeOverrides.setInstallPermission(false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MainActivitySettingsAutomationReminder reminderHelper = new MainActivitySettingsAutomationReminder(activity);
                activity.store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 6, 45, 1000L, 1000L, 2000L));
                SettingsAutoSyncPanelModel syncOn = activity.autoSyncSettingsPanelModel();
                assertEquals(MainActivityBase.TEAL, syncOn.getStatusColor());

                activity.store.setAutoSyncEnabled(false);
                SettingsAutoSyncPanelModel syncOff = activity.autoSyncSettingsPanelModel();
                assertEquals(MainActivityBase.MUTED, syncOff.getStatusColor());

                activity.store.recordAutoUpdateResult(1234L, "Ready to install.", "v0.5.0", "kani.apk", "");
                SettingsUpdatePanelModel missingPermission = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                );
                assertFalse(missingPermission.getCanInstallUpdates());

                MainActivityRuntimeOverrides.setInstallPermission(true);
                SettingsUpdatePanelModel readyUpdate = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                );
                assertTrue(readyUpdate.getCanInstallUpdates());

                activity.store.saveAutoUpdateEnabled(false);
                SettingsUpdatePanelModel updateOff = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                );
                assertEquals(SettingsTextCopy.automaticUpdatesToggleLabel(false), updateOff.getAutomaticUpdatesToggleLabel());

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 22, 45));
                reminderHelper.saveReminderFromSelection(6, 15, false);
                LocalStore.ReminderSettings reminder = activity.store.reminderSettings();
                assertFalse(reminder.enabled);
                assertEquals(6, reminder.hour);
                assertEquals(15, reminder.minute);
                assertEquals(6, activity.reminderSettingsPanelModel().getSelectedHour()[0]);
            });
        } finally {
            MainActivityRuntimeOverrides.setInstallPermission(null);
        }
    }

    @Test
    public void updateUiContinuationStopsAfterNavigationAway() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                int firstRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = firstRun;
                assertTrue(firstRun != 0 && activity.activeUpdateUiRunToken == firstRun);

                activity.renderSettings();
                assertFalse(firstRun != 0 && activity.activeUpdateUiRunToken == firstRun);

                int staleRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = staleRun;
                int activeRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = activeRun;
                assertFalse(staleRun != 0 && activity.activeUpdateUiRunToken == staleRun);
                assertTrue(activeRun != 0 && activity.activeUpdateUiRunToken == activeRun);

                activity.renderHome();
                assertFalse(activeRun != 0 && activity.activeUpdateUiRunToken == activeRun);
            });
        }
    }

    @Test
    public void reminderSavingCoversPermissionRequestsAndBlockedNotifications() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MainActivitySettingsAutomationReminder reminder = new MainActivitySettingsAutomationReminder(activity);
                MainActivityRuntimeOverrides.setRuntimeNotificationPermission(false);
                reminder.saveReminderFromSelection(7, 45, true);
                assertTrue(activity.pendingReminderSettings.enabled);
                assertEquals(7, activity.pendingReminderSettings.hour);
                assertEquals(45, activity.pendingReminderSettings.minute);

                MainActivityRuntimeOverrides.setRuntimeNotificationPermission(true);
                MainActivityRuntimeOverrides.setNotificationsAllowed(false);
                reminder.saveReminderFromSelection(8, 15, true);
                LocalStore.ReminderSettings saved = activity.store.reminderSettings();
                assertTrue(saved.enabled);
                assertEquals(8, saved.hour);
                assertEquals(15, saved.minute);
                assertEquals(8, activity.reminderSettingsPanelModel().getSelectedHour()[0]);

                activity.pendingReminderSettings = new LocalStore.ReminderSettings(true, 9, 30);
                activity.saveGrantedReminderPermission(activity.pendingReminderSettings);
                assertEquals(9, activity.store.reminderSettings().hour);
            });
        } finally {
            MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
            MainActivityRuntimeOverrides.setNotificationsAllowed(null);
        }
    }

    private static void assertHasText(MainActivity activity, String text) {
        View root = activity.findViewById(android.R.id.content);
        if (!containsText(root, text) && findDeviceTextNow(text) == null) {
            throw new AssertionError("Missing text: " + text);
        }
    }

    private static int reminderStatusColor(boolean enabled, boolean blocked) {
        return blocked ? MainActivityBase.CORAL : (enabled ? MainActivityBase.TEAL : MainActivityBase.MUTED);
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView textView && expected.contentEquals(textView.getText())) {
            return true;
        }
        if (view instanceof androidx.compose.ui.platform.ComposeView composeView && containsAccessibilityText(composeView.createAccessibilityNodeInfo(), expected)) {
            return true;
        }
        if (!(view instanceof ViewGroup group)) {
            return false;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), expected)) {
                return true;
            }
        }
        return false;
    }

    private static void performClickableWithText(View root, String label) {
        View clickable = findClickableWithText(root, label);
        if (clickable == null) {
            UiObject2 object = findDeviceClickableTextNow(label);
            if (object == null) {
                throw new AssertionError("Missing clickable text: " + label);
            }
            object.click();
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
            return;
        }
        clickable.performClick();
    }

    private static View findClickableWithText(View view, String label) {
        if (view.isClickable() && containsText(view, label)) {
            return view;
        }
        if (!(view instanceof ViewGroup group)) {
            return null;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findClickableWithText(group.getChildAt(i), label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static UiObject2 findDeviceTextNow(String label) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = appPackage();
        UiObject2 object = firstMatch(device.findObjects(By.pkg(pkg).text(label)));
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).textContains(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).text(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).textContains(label.toUpperCase(Locale.ROOT))));
        }
        return object;
    }

    private static UiObject2 findDeviceClickableTextNow(String label) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = appPackage();
        UiObject2 object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label)));
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).desc(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).descContains(label)));
        }
        if (object != null && !object.isClickable()) {
            UiObject2 parent = object.getParent();
            while (parent != null && parent != object && !parent.isClickable()) {
                object = parent;
                parent = object.getParent();
            }
            if (parent != null && parent.isClickable()) {
                object = parent;
            }
        }
        return object != null && object.isClickable() ? object : null;
    }

    private static String appPackage() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    }

    private static UiObject2 firstMatch(List<UiObject2> objects) {
        return objects.isEmpty() ? null : objects.get(0);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static boolean containsAccessibilityText(AccessibilityNodeInfo node, String expected) {
        if (node == null) {
            return false;
        }
        try {
            CharSequence value = node.getText();
            if (value != null && expected.contentEquals(value)) {
                return true;
            }
            CharSequence description = node.getContentDescription();
            if (description != null && expected.contentEquals(description)) {
                return true;
            }
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                if (containsAccessibilityText(child, expected)) {
                    return true;
                }
            }
            return false;
        } finally {
            node.recycle();
        }
    }

}
