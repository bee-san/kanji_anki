package dev.bee.kanjianki

import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyCollectionLookup
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.study.HintProgression
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.RecognitionCandidate
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.study.WritingRecognizer
import dev.bee.kanjianki.sync.SyncSettings
import dev.bee.kanjianki.theme.KaniThemeChoice
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal abstract class MainActivityBase : MainActivityUiSupport() {
    @JvmField
    val main = Handler(Looper.getMainLooper())

    @JvmField
    val io: ExecutorService = Executors.newSingleThreadExecutor()

    @JvmField
    val hintProgression = HintProgression()

    @JvmField
    val studySessionTracker = StudySessionTracker()

    @JvmField
    var store: LocalStore = uninitialized()

    @JvmField
    var gateway: AnkiDroidGateway = uninitialized()

    @JvmField
    var contentScrollY = 0

    @JvmField
    var currentRoute: String = NAV_HOME_ROUTE

    @JvmField
    var screenshotThemeChoiceOverride: KaniThemeChoice? = null

    @JvmField
    var activeSession: RecordsSchedulerModels.StudySession? = null

    @JvmField
    var activeSimilarWritingRepair: RecordsImportModels.SimilarKanjiWritingRepair? = null

    @JvmField
    var activeStudyPlan: RecordsSchedulerModels.AdaptiveLoadPlan? = null

    @JvmField
    var drawingPad: DrawingPadView? = null

    @JvmField
    var studyStatus: WritingStatusState? = null

    @JvmField
    var writingResultStatus: WritingResultStatusHandle? = null

    @JvmField
    var writingPrimaryActionsView: WritingPrimaryActionsView? = null

    @JvmField
    var writingFallbackActionsView: WritingFallbackActionsView? = null

    @JvmField
    var writingToolActionsView: WritingToolActionsView? = null

    @JvmField
    var writingAnswerPanelState: WritingAnswerPanelState? = null

    @JvmField
    var studyAnswerPanel: View? = null

    @JvmField
    var flashcardGestureBounds: Rect? = null

    @JvmField
    var flashcardHeroPanel: View? = null

    @JvmField
    var flashcardRevealState: FlashcardRevealState? = null

    @JvmField
    var flashcardActionBarState: FlashcardActionBarState? = null

    @JvmField
    val studyUndoState = StudyUndoState()

    @JvmField
    var typingAnswerState: TypingAnswerState? = null

    @JvmField
    var activeAnalysis: WritingAnalysis? = null

    @JvmField
    var checkingWriting = false

    @JvmField
    var flashcardAnswerRevealed = false

    @JvmField
    var flashcardTouchTracking = false

    @JvmField
    var writingModelDownloaded = false

    @JvmField
    var writingModelStatusKnown = false

    @JvmField
    var continueAllKanjiSession = false

    @JvmField
    val studyMoreNewCardKanji: MutableList<String> = ArrayList()

    @JvmField
    var hintsUsed = 0

    @JvmField
    var currentPracticeLevel = 0

    @JvmField
    var flashcardTouchStartX = 0f

    @JvmField
    var flashcardTouchStartY = 0f

    @JvmField
    var activityPaused = false

    @JvmField
    var currentHintState: HintState = HintState.initial()

    @JvmField
    var strokeGuides: Map<String, StrokeGuide>? = null

    @JvmField
    var writingRecognizer: WritingRecognizer? = null

    @JvmField
    var dictionaryLookup: DictionaryLookup? = null

    @JvmField
    var pendingReminderSettings: LocalStoreBase.ReminderSettings? = null

    @JvmField
    var settingsAnkiExpanded = true

    @JvmField
    var settingsStudyExpanded = false

    @JvmField
    var settingsSyncExpanded = false

    @JvmField
    var settingsAppearanceExpanded = false

    @JvmField
    var settingsAppExpanded = false

    @JvmField
    var updateUiRunCounter = 0

    @JvmField
    var activeUpdateUiRunToken = 0

    /**
     * In-app destination for the system back gesture. Null means "no in-app
     * destination": the callback defers to the system default (exit).
     * The shell host sets a per-route default; sub-screens may override it
     * after rendering.
     */
    @JvmField
    var backAction: Runnable? = null

    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!handleBackNavigation()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    /** Returns true when back was consumed by an in-app destination. */
    fun handleBackNavigation(): Boolean {
        val action = backAction ?: return false
        withUiTrace("kani.button.system-back") {
            action.run()
        }
        return true
    }

    private val permissionHandler = MainActivityPermissionHandler(this)
    private val writingRecognizerProvider = MainActivityWritingRecognizerProvider(this)
    private val studyPlanProvider = MainActivityStudyPlanProvider(this)
    private val shellHost = MainActivityShellHost(this)
    private val startup = MainActivityStartup(this as MainActivityHome)
    private val activityLifecycle = MainActivityLifecycle(this)

    abstract fun renderHome()
    abstract fun renderUpdate()
    abstract fun renderStats()
    abstract fun renderSettings()
    open fun renderSettings(preserveScroll: Boolean) {
        renderSettings()
    }
    open fun renderDeferredStudyBehaviorPreviewIfNeeded() = Unit
    abstract fun renderStudy()
    abstract fun startFocusedStudy()
    abstract fun renderStudyForKanji(kanji: String?)
    abstract fun pauseActiveStudyTask()
    abstract fun resumeActiveStudyTask()
    abstract fun abandonActiveStudyTask()
    abstract fun handleFlashcardGesture(event: MotionEvent): Boolean
    abstract fun initializeSessionProgressTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?)
    abstract fun currentDictionaryLookup(): DictionaryLookup
    abstract fun wordReadingExample(row: RecordsImportModels.DashboardRow): RecordsImportModels.Example?
    abstract fun clearStudyModeOverrides()
    abstract fun fontResource(fontRes: Int, fallback: Typeface): Typeface
    abstract fun thresholdInput(value: Int): EditText
    abstract fun parseThresholdInput(input: EditText): Int

    fun isScreenshotLaunchRequested(): Boolean {
        return intent?.getStringExtra(EXTRA_SCREENSHOT_ROUTE).isNullOrBlank().not()
    }

    fun screenshotLocaleTag(): String? {
        return intent?.getStringExtra(EXTRA_SCREENSHOT_LOCALE)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun screenshotScrollPositionLabel(): String? {
        return intent?.getStringExtra(EXTRA_SCREENSHOT_SCROLL_POSITION)?.takeIf { it.isNotBlank() }
    }

    fun screenshotScrollY(): Int {
        return intent?.getIntExtra(EXTRA_SCREENSHOT_SCROLL_Y, 0) ?: 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
        startup.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onPause() {
        activityLifecycle.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityLifecycle.onResume()
    }

    override fun onDestroy() {
        activityLifecycle.onDestroy()
        super.onDestroy()
    }

    fun handleLaunchIntent(intent: Intent?) {
        startup.handleLaunchIntent(intent)
    }

    fun requestAnkiPermissionIfNeeded() {
        permissionHandler.requestAnkiPermissionIfNeeded()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handlePermissionResult(requestCode, grantResults)
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        permissionHandler.handlePermissionResult(requestCode, grantResults)
    }

    fun setFlashcardGestureBounds(left: Float, top: Float, right: Float, bottom: Float) {
        flashcardGestureBounds = Rect(
            kotlin.math.floor(left).toInt(),
            kotlin.math.floor(top).toInt(),
            kotlin.math.ceil(right).toInt(),
            kotlin.math.ceil(bottom).toInt(),
        )
    }

    fun handlePostNotificationPermission(grantResults: IntArray) {
        permissionHandler.handlePostNotificationPermission(grantResults)
    }

    fun saveGrantedReminderPermission(pending: LocalStoreBase.ReminderSettings?) {
        permissionHandler.saveGrantedReminderPermission(pending)
    }

    fun disableReminderAfterDeniedPermission(pending: LocalStoreBase.ReminderSettings?) {
        permissionHandler.disableReminderAfterDeniedPermission(pending)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (handleFlashcardGesture(event)) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    fun composeRoute(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        shellHost.composeRoute(selected, initialScrollY, scrollPositionLabel, onScrollY, content)
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = {},
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        shellHost.composeRouteWithActionBar(selected, initialScrollY, scrollPositionLabel, onScrollY, beforeContent, content, actionBar)
    }

    fun isActiveToken(token: String): Boolean {
        return activeSession != null && activeSession?.token == token
    }

    fun currentWritingRecognizer(): WritingRecognizer? {
        return writingRecognizerProvider.currentWritingRecognizer()
    }

    fun hasRuntimeNotificationPermissionForReminder(): Boolean {
        return MainActivityRuntimeOverrides.runtimeNotificationPermission
            ?: ReminderScheduler.hasRuntimeNotificationPermission(this)
    }

    fun notificationsAllowedForReminders(): Boolean {
        return MainActivityRuntimeOverrides.notificationsAllowed ?: ReminderScheduler.notificationsAllowed(this)
    }

    fun candidates(result: WritingRecognizer.RecognitionResult?): List<RecognitionCandidate> {
        return WritingRecognizer.recognitionCandidates(result)
    }

    fun findRow(rows: List<RecordsImportModels.DashboardRow>, kanji: String): RecordsImportModels.DashboardRow? {
        return StudyCollectionLookup.dashboardRowByKanji(rows, kanji)
    }

    fun findStudyItem(items: List<RecordsStudyModels.StudyItem>, kanji: String): RecordsStudyModels.StudyItem? {
        return StudyCollectionLookup.studyItemByKanji(items, kanji)
    }

    fun strokeGuide(kanji: String): StrokeGuide? {
        if (strokeGuides == null) {
            strokeGuides = StrokeGuideAssets.load(this)
            AppTimingDiagnostics.markStrokeGuideLoaded()
        }
        return strokeGuides?.get(kanji)
    }

    fun settings(): RecordsSyncModels.Settings {
        return SyncSettings.fromStore(store)
    }

    fun studyLadderSettings(): RecordsBase.StudyLadderSettings {
        return store.studyLadderSettings()
    }

    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return studyPlanProvider.adaptivePlan(rows, items, now)
    }

    fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): DailyStudyPlan {
        return studyPlanProvider.dailyStudyPlan(rows, items, now)
    }

    fun studyPlanForMode(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return studyPlanProvider.studyPlanForMode(rows, items, now)
    }

    fun studyMoreNewCardsPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return studyPlanProvider.studyMoreNewCardsPlan(rows, items, now)
    }

    fun allCurrentProblemKanjiPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return studyPlanProvider.allCurrentProblemKanjiPlan(rows, items, now)
    }

    fun startOfDay(now: Long): Long {
        return LocalDayPolicy.localDayStart(now)
    }

    class ImportThresholds(
        @JvmField val difficulty: Double,
        @JvmField val lapseThreshold: Int,
        @JvmField val minCards: Int,
    )

    class QueueEntry(
        row: RecordsImportModels.DashboardRow,
        item: RecordsStudyModels.StudyItem,
    ) : FocusQueuePolicy.QueueEntry(row, item)

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> uninitialized(): T = null as T

        const val EXTRA_OPEN_UPDATE = "dev.bee.kanjianki.extra.OPEN_UPDATE"
        const val EXTRA_SCREENSHOT_ROUTE = "dev.bee.kanjianki.extra.SCREENSHOT_ROUTE"
        const val EXTRA_SCREENSHOT_THEME = "dev.bee.kanjianki.extra.SCREENSHOT_THEME"
        const val EXTRA_SCREENSHOT_LOCALE = "dev.bee.kanjianki.extra.SCREENSHOT_LOCALE"
        const val EXTRA_SCREENSHOT_SCROLL_POSITION = "dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_POSITION"
        const val EXTRA_SCREENSHOT_SCROLL_Y = "dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_Y"
        const val EXTRA_BENCHMARK_ROUTE = "dev.bee.kanjianki.extra.BENCHMARK_ROUTE"
        const val REQUEST_POST_NOTIFICATIONS = 704
        const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        const val DAY_MILLIS = 86_400_000L
        const val NAV_HOME_ROUTE = "home"
        const val NAV_STUDY = "study"
        const val NAV_STATS_ROUTE = "stats"
        const val NAV_SETTINGS = "Settings"
        const val NAV_SETTINGS_ROUTE = "settings"
        const val NAV_SETTINGS_IMPORT_SYNC_ROUTE = "settings/import-sync"
        const val NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE = "settings/study-behavior"
        const val NAV_SETTINGS_AUTOMATION_ROUTE = "settings/automation"
        const val NAV_SETTINGS_APPEARANCE_ROUTE = "settings/appearance"
        const val NAV_SETTINGS_DISPLAY_DATA_ROUTE = "settings/display-data"
        const val NAV_SETTINGS_UPDATE_ROUTE = "settings/automation/update"
        const val NAV_SETTINGS_LICENSES_ROUTE = "settings/display-data/licenses"
        const val NAV_SETTINGS_TIMING_DIAGNOSTICS_ROUTE = "settings/timing-diagnostics"

        @JvmStatic
        fun isSettingsRoute(route: String): Boolean {
            return route == NAV_SETTINGS_ROUTE || route.startsWith("$NAV_SETTINGS_ROUTE/")
        }

        @JvmStatic
        fun settingsParentRoute(route: String): String {
            return when {
                route == NAV_SETTINGS_ROUTE -> NAV_HOME_ROUTE
                route.startsWith("$NAV_SETTINGS_ROUTE/") -> route.substringBeforeLast('/')
                else -> NAV_HOME_ROUTE
            }
        }
        const val LABEL_BACK_HOME = "Back home"
        const val LABEL_MEANING = "Meaning"
        const val LABEL_FAIL = "Fail"
        const val LABEL_PASS = "Pass"
        const val LABEL_PRACTICE = "Practice"
        const val LABEL_SIMILAR_KANJI = "Similar kanji"
        const val LABEL_STUDY_NOW = "Study now"
        const val LABEL_STUDY = "Study"
        const val LABEL_NEW_CARDS = "New cards"
        const val LABEL_CONTINUE_ALL_KANJI = "Continue all kanji"
        const val RATING_AGAIN = "again"
        const val RATING_HARD = "hard"
        const val RATING_GOOD = "good"
        const val STATE_LEARNING = "learning"
        const val STATE_RETIRED = "retired"
        const val SOURCE_ACTIVE = "active"
        const val SOURCE_SUSPENDED = "suspended"
        const val TASK_FONT_MEANING = "font_meaning"
        const val TASK_TARGETED_WRITING = "targeted_writing"
        const val TASK_TYPING_MEANING = "typing_meaning"
        const val TASK_WORD_READING = "word_reading"
        const val TASK_REPAIR_WRITING = "repair_writing"
        const val EMPTY_ACTIVE_PRACTICE_TITLE = "No active practice yet"
        const val EMPTY_ACTIVE_PRACTICE_BODY =
            "Study now adds the next kanji."

        @JvmField
        val MUTED: Int = MainActivityUiSupport.MUTED

        @JvmField
        val CORAL: Int = MainActivityUiSupport.CORAL

        @JvmField
        val TEAL: Int = MainActivityUiSupport.TEAL

        @JvmField
        val GOLD: Int = MainActivityUiSupport.GOLD

        @JvmField
        val BLUE: Int = MainActivityUiSupport.BLUE

        @JvmField
        val LILAC: Int = MainActivityUiSupport.LILAC

    }

}
