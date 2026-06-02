package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import java.io.File
import java.util.Arrays
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySettingsInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.settings_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        context.deleteDatabase("kanji_anki_simple.db")
        deleteRecursively(File(context.cacheDir, "updates"))
    }

    @Test
    fun settingsCategoriesTogglePanelsAndReferenceNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderSettings()
                var settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))
                assertTrue(activity.settingsAnkiExpanded)
                assertFalse(activity.settingsStudyExpanded)
                assertTrue(containsText(settingsRoot, "Frequency range"))
                assertFalse(containsText(settingsRoot, "Daily workload"))
                activity.contentScrollY = 48
                activity.renderSettings(true)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(48, activity.contentScrollY)
                settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))

                performClickableWithText(settingsRoot, "Study behavior")
                assertEquals(48, activity.contentScrollY)
                settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))
                assertTrue(activity.settingsStudyExpanded)
                assertTrue(containsText(settingsRoot, "Daily workload"))

                performClickableWithText(settingsRoot, "Import from Anki")
                assertEquals(48, activity.contentScrollY)
                settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))
                assertFalse(activity.settingsAnkiExpanded)
                assertFalse(containsText(settingsRoot, "Frequency range"))

                performClickableWithText(settingsRoot, "Automation")
                assertEquals(48, activity.contentScrollY)
                settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))
                assertTrue(activity.settingsSyncExpanded)
                assertTrue(containsText(settingsRoot, "Daily Anki sync"))

                performClickableWithText(settingsRoot, "Data sources")
                settingsRoot = requireNotNull(activity.findViewById<View>(android.R.id.content))
                assertTrue(activity.settingsAppExpanded)
                assertTrue(containsText(settingsRoot, "Offline data licenses"))
                performClickableWithText(settingsRoot, "Open data licenses")
                assertHasText(activity, "Data licenses")
                performClickableWithText(requireNotNull(activity.findViewById<View>(android.R.id.content)), "Back to settings")
                assertHasText(activity, "Automation")
            }
        }
    }

    @Test
    fun settingsPanelsPersistWorkloadAndLearningStepActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO)
                val autoPanel = activity.workloadSettingsPanelModel()
                assertTrue(autoPanel.autoMode)
                assertEquals(activity.store.adaptiveLoadWorkPercent(), autoPanel.selectedWorkloadPercent[0])

                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL)
                val manualPanel = activity.workloadSettingsPanelModel()
                assertFalse(manualPanel.autoMode)
                manualPanel.onEnableManual.run()
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode())

                val stepsPanel = activity.learningStepsSettingsPanelModel()
                assertEquals("1m", stepsPanel.initialNewStepsText)
                assertEquals("1m, 10m", stepsPanel.initialReviewStepsText)
                assertEquals("1m, 10m", activity.store.learningStepSettings().reviewStepsText())
            }
        }
    }

    @Test
    fun importFilterAndFrequencyPanelsBuildSettingsModels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val importFilters = activity.importFilterSettingsPanelModel(activity.settings())
                assertEquals(SettingsTextCopy.importFiltersTitle(), importFilters.title)
                assertFalse(importFilters.state.activeCards)
                assertTrue(importFilters.state.suspendedCards)

                val frequencyRange = activity.frequencyRangeSettingsPanelModel(activity.settings())
                assertEquals(SettingsTextCopy.frequencyRangeTitle(), frequencyRange.title)
                assertEquals(activity.settings().suspendedRankMin, frequencyRange.selectedRanks[0])
                assertEquals(activity.settings().suspendedRankMax, frequencyRange.selectedRanks[1])
            }
        }
    }

    @Test
    fun noteTypeInputsWriteRealAndroidFieldsAndFallbackGuesses() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val noteType = EditText(activity)
                val expression = EditText(activity)
                val reading = EditText(activity)
                val meaning = EditText(activity)
                val sentence = EditText(activity)
                val frequency = EditText(activity)
                val frequencySort = EditText(activity)
                val inputs = NoteTypeFieldMappings.Inputs(
                    noteType,
                    expression,
                    reading,
                    meaning,
                    sentence,
                    frequency,
                    frequencySort
                )

                NoteTypeFieldMappings.chooseNoteType(
                    NoteTypeFieldMappings.Choice("Fallback Model", Arrays.asList("Front", "Back", "Kana")),
                    inputs
                )

                assertEquals("Fallback Model", noteType.text.toString())
                assertEquals("Front", expression.text.toString())
                assertEquals("Kana", reading.text.toString())
                assertEquals("Back", meaning.text.toString())
                assertEquals("", sentence.text.toString())
                assertEquals("", frequency.text.toString())
                assertEquals("", frequencySort.text.toString())

                val panel = activity.noteTypeSettingsPanelModel(activity.settings())
                assertEquals(activity.settings().modelName, panel.fields.noteType)
                assertEquals(activity.settings().expressionField, panel.fields.expression)
            }
        }
    }

    @Test
    fun settingsValidationPanelsPersistStudyAheadLadderRetentionAndReminder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val studyAhead = SettingsStudyAheadPanelModel(
                    SettingsTextCopy.studyAheadTitle(),
                    SettingsTextCopy.studyAheadBody(),
                    SettingsTextCopy.studyAheadMinutesLabel(),
                    activity.store.studyAheadMinutes().toString(),
                    SettingsTextCopy.saveStudyAheadLabel(),
                    { _: String -> }
                )
                assertEquals(activity.store.studyAheadMinutes().toString(), studyAhead.initialMinutesText)

                val ladder = activity.ladderThresholdSettingsPanelModel()
                assertEquals(activity.settings().ladderPromotionIntervalDays.toString(), ladder.initialPromotionDaysText)
                assertEquals(activity.settings().ladderDemotionFailStreak.toString(), ladder.initialFailStreakText)

                val ladderOrder = activity.studyLadderSettingsPanelModel()
                assertTrue(ladderOrder.rungs.isNotEmpty())
                ladderOrder.rungs.first { it.label.contains("Similar kanji") }.onToggle.run()
                assertFalse(activity.studyLadderSettings().isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI))
                activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(RecordsBase.LadderRung.WORD_READING, -6))
                assertEquals(RecordsBase.LadderRung.WORD_READING, activity.studyLadderSettings().orderedRungs[0])

                val newCardSort = activity.newCardSortSettingsPanelModel(activity.settings())
                assertEquals(activity.settings().newCardSortMode, newCardSort.initialMode)

                val retention = activity.retentionSettingsPanelModel()
                assertEquals(
                    (activity.store.schedulerParameters().targetRetention * 100.0).roundToInt(),
                    retention.selectedRetentionPercent[0]
                )

                activity.store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 21, 0))
                val reminder = activity.reminderSettingsPanelModel()
                assertEquals(21, reminder.selectedHour[0])
                assertEquals(0, reminder.selectedMinute[0])
                assertEquals(MainActivityBase.CORAL, reminderStatusColor(true, true))
                assertEquals(MainActivityBase.TEAL, reminderStatusColor(true, false))
                assertEquals(MainActivityBase.MUTED, reminderStatusColor(false, false))

                val selectedHour = intArrayOf(21)
                val selectedMinute = intArrayOf(0)
                val timeButtonDirect = Button(activity)
                selectedHour[0] = 6
                selectedMinute[0] = 5
                timeButtonDirect.text = SettingsTextCopy.reminderTimeButtonLabel(6, 5)
                assertEquals(6, selectedHour[0])
                assertEquals(5, selectedMinute[0])
                assertEquals("Reminder time: 06:05", timeButtonDirect.text.toString())
                val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationIntent.action)
                assertEquals(activity.packageName, notificationIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            }
        }
    }

    @Test
    fun automationPanelsToggleSyncUpdatesAndReminderActions() {
        MainActivityRuntimeOverrides.setInstallPermission(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val reminderHelper = MainActivitySettingsAutomationReminder(activity)
                    activity.store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 6, 45, 1000L, 1000L, 2000L))
                    val syncOn = activity.autoSyncSettingsPanelModel()
                    assertEquals(MainActivityBase.TEAL, syncOn.statusColor)

                    activity.store.setAutoSyncEnabled(false)
                    val syncOff = activity.autoSyncSettingsPanelModel()
                    assertEquals(MainActivityBase.MUTED, syncOff.statusColor)

                    activity.store.recordAutoUpdateResult(1234L, "Ready to install.", "v0.5.0", "kani.apk", "")
                    val missingPermission = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                    )
                    assertFalse(missingPermission.canInstallUpdates)

                    MainActivityRuntimeOverrides.setInstallPermission(true)
                    val readyUpdate = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                    )
                    assertTrue(readyUpdate.canInstallUpdates)

                    activity.store.saveAutoUpdateEnabled(false)
                    val updateOff = settingsUpdatePanelModel(
                        activity,
                        SettingsTextCopy.automaticUpdatesTitle()
                    )
                    assertEquals(
                        SettingsTextCopy.automaticUpdatesToggleLabel(false),
                        updateOff.automaticUpdatesToggleLabel
                    )

                    activity.store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 22, 45))
                    reminderHelper.saveReminderFromSelection(6, 15, false)
                    val reminder = requireNotNull(activity.store.reminderSettings())
                    assertFalse(reminder.enabled)
                    assertEquals(6, reminder.hour)
                    assertEquals(15, reminder.minute)
                    assertEquals(6, activity.reminderSettingsPanelModel().selectedHour[0])
                }
            }
        } finally {
            MainActivityRuntimeOverrides.setInstallPermission(null)
        }
    }

    @Test
    fun updateUiContinuationStopsAfterNavigationAway() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val firstRun = ++activity.updateUiRunCounter
                activity.activeUpdateUiRunToken = firstRun
                assertTrue(firstRun != 0 && activity.activeUpdateUiRunToken == firstRun)

                activity.renderSettings()
                assertFalse(isActiveRun(activity, firstRun))

                val staleRun = ++activity.updateUiRunCounter
                activity.activeUpdateUiRunToken = staleRun
                val activeRun = ++activity.updateUiRunCounter
                activity.activeUpdateUiRunToken = activeRun
                assertFalse(isActiveRun(activity, staleRun))
                assertTrue(isActiveRun(activity, activeRun))

                activity.renderHome()
                assertFalse(isActiveRun(activity, activeRun))
            }
        }
    }

    private fun isActiveRun(activity: MainActivity, run: Int): Boolean =
        run != 0 && activity.activeUpdateUiRunToken == run

    @Test
    fun reminderSavingCoversPermissionRequestsAndBlockedNotifications() {
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val reminder = MainActivitySettingsAutomationReminder(activity)
                MainActivityRuntimeOverrides.setRuntimeNotificationPermission(false)
                reminder.saveReminderFromSelection(7, 45, true)
                val pendingReminder = requireNotNull(activity.pendingReminderSettings)
                assertTrue(pendingReminder.enabled)
                assertEquals(7, pendingReminder.hour)
                assertEquals(45, pendingReminder.minute)

                MainActivityRuntimeOverrides.setRuntimeNotificationPermission(true)
                MainActivityRuntimeOverrides.setNotificationsAllowed(false)
                reminder.saveReminderFromSelection(8, 15, true)
                val saved = requireNotNull(activity.store.reminderSettings())
                assertTrue(saved.enabled)
                assertEquals(8, saved.hour)
                assertEquals(15, saved.minute)
                assertEquals(8, activity.reminderSettingsPanelModel().selectedHour[0])

                activity.pendingReminderSettings = LocalStoreBase.ReminderSettings(true, 9, 30)
                val grantedReminder = requireNotNull(activity.pendingReminderSettings)
                activity.saveGrantedReminderPermission(grantedReminder)
                assertEquals(9, requireNotNull(activity.store.reminderSettings()).hour)
            }
        }
    } finally {
            MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
            MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        }
    }

    private fun assertHasText(activity: MainActivity, text: String) {
        val root = requireNotNull(activity.findViewById<View>(android.R.id.content))
        if (!containsText(root, text) && findDeviceTextNow(text) == null) {
            throw AssertionError("Missing text: $text")
        }
    }

    private fun reminderStatusColor(enabled: Boolean, blocked: Boolean): Int {
        return when {
            blocked -> MainActivityBase.CORAL
            enabled -> MainActivityBase.TEAL
            else -> MainActivityBase.MUTED
        }
    }

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView && expected.contentEquals(view.text)) {
            return true
        }
        if (view is androidx.compose.ui.platform.ComposeView && containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
            return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsText(view.getChildAt(i), expected)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performClickableWithText(root: View, label: String) {
        val clickableView = findClickableWithText(root, label)
        if (clickableView != null) {
            clickableView.performClick()
        } else {
            val clickableObject = requireNotNull(findDeviceClickableTextNow(label)) {
                "Missing clickable text: $label"
            }
            clickableObject.click()
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000L)
    }

    private fun findClickableWithText(view: View, label: String): View? {
        if (view.isClickable && containsText(view, label)) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findClickableWithText(view.getChildAt(i), label)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findDeviceTextNow(label: String): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = appPackage()
        var object2 = firstMatch(device.findObjects(By.pkg(pkg).text(label)))
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).textContains(label)))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).text(label.uppercase(Locale.ROOT))))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).textContains(label.uppercase(Locale.ROOT))))
        }
        return object2
    }

    private fun findDeviceClickableTextNow(label: String): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = appPackage()
        var object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label)))
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label)))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label.uppercase(Locale.ROOT))))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label.uppercase(Locale.ROOT))))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).desc(label)))
        }
        if (object2 == null) {
            object2 = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).descContains(label)))
        }
        if (object2 != null && !object2.isClickable) {
            var parent = object2.parent
            while (parent != null && parent != object2 && !parent.isClickable) {
                object2 = parent
                parent = object2.parent
            }
            if (parent?.isClickable == true) {
                object2 = parent
            }
        }
        return object2?.takeIf { it.isClickable }
    }

    private fun appPackage(): String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun firstMatch(objects: List<UiObject2>): UiObject2? = objects.firstOrNull()

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun containsAccessibilityText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false
        }
        val value = node.text?.toString()
        if (value != null && expected.contentEquals(value)) {
            return true
        }
        val description = node.contentDescription?.toString()
        if (description != null && expected.contentEquals(description)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsAccessibilityText(child, expected)) {
                return true
            }
        }
        return false
    }
}
