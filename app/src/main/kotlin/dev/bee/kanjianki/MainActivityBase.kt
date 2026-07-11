package dev.bee.kanjianki

import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

    /**
     * Post [action] to the main thread, but drop it if the activity is already
     * destroyed by the time it runs. Guards background-completion callbacks that touch
     * `setContent`/`startActivity`/toasts against a torn-down activity.
     */
    fun postToMainIfActive(action: () -> Unit) {
        main.post {
            if (isDestroyed || isFinishing) {
                return@post
            }
            action()
        }
    }

    @JvmField
    val io: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Second single-threaded executor for background maintenance that must NOT block user-facing
     * route loads: cold-start scheduler setup (reminders/auto-sync/auto-update/backup plus
     * first-time WorkManager init), the daily stats precompute, and the resume-time update check.
     * Keeping these off [io] fixes cold-boot head-of-line blocking, where tapping between screens
     * queued behind seconds of startup maintenance on the shared executor. SQLite stays consistent
     * across both threads via WAL plus the per-helper write lock.
     */
    @JvmField
    val maintenance: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kani-maintenance").apply { isDaemon = true }
    }

    @JvmField
    val hintProgression = HintProgression()

    @JvmField
    val studySessionTracker = StudySessionTracker()

    lateinit var store: LocalStore

    lateinit var gateway: AnkiDroidGateway

    /** True once [store] has been assigned by startup (replaces the old NPE-catch). */
    fun isStoreInitialized(): Boolean = ::store.isInitialized

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
    var flashcardSwipeFeedback: StudySwipeFeedbackState? = null

    /**
     * Exact selectable tasks in the user's current/next focus session, used for the
     * Study badge in the bottom nav and the count on the home Study-now card. This is
     * intentionally narrower than the adaptive plan's daily-focus `remaining` value.
     * While a study card is active the shell prefers the live session tracker instead
     * of this cache. Negative means unknown (not yet computed for this process).
     * Written from background route loads and read on the main thread, so it is
     * volatile.
     */
    @JvmField
    @Volatile
    var studySessionBadgeCount: Int = -1

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
    @Volatile
    var strokeGuides: Map<String, StrokeGuide>? = null

    @JvmField
    var writingRecognizer: WritingRecognizer? = null

    @JvmField
    @Volatile
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

    private val permissionHandler by lazy { MainActivityPermissionHandler(this) }
    private val writingRecognizerProvider by lazy { MainActivityWritingRecognizerProvider(this) }
    private val studyPlanProvider by lazy { MainActivityStudyPlanProvider(this) }
    private val shellHost by lazy { MainActivityShellHost(this) }
    private val startup by lazy { MainActivityStartup(this as MainActivityHome) }
    private val activityLifecycle by lazy { MainActivityLifecycle(this) }

    private lateinit var backupExportDocumentLauncher: ActivityResultLauncher<String>
    private lateinit var backupRestoreDocumentLauncher: ActivityResultLauncher<Array<String>>

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
        // SAF launchers must be registered once, before the activity reaches STARTED.
        // Settings prepares/validates private files on the IO executor and these callbacks
        // bridge the system picker result back to that flow.
        backupExportDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/gzip"),
        ) { uri -> onBackupExportDocumentSelected(uri) }
        backupRestoreDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> onBackupRestoreDocumentSelected(uri) }
        onBackPressedDispatcher.addCallback(this, backCallback)
        startup.start()
    }

    protected open fun onBackupExportDocumentSelected(uri: Uri?) = Unit

    protected open fun onBackupRestoreDocumentSelected(uri: Uri?) = Unit

    fun launchBackupExportDocument(suggestedName: String): Boolean {
        return runCatching { backupExportDocumentLauncher.launch(suggestedName) }.isSuccess
    }

    fun launchBackupRestoreDocument(): Boolean {
        return runCatching {
            backupRestoreDocumentLauncher.launch(
                arrayOf("application/gzip", "application/octet-stream", "*/*"),
            )
        }.isSuccess
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

    /**
     * Coalesces a reminder alarm refresh behind the currently loading route. Callers that mutate
     * study state should render/queue the replacement route first, then request this refresh.
     */
    fun requestReminderRearm(reason: String) {
        activityLifecycle.requestReminderRearm(reason)
    }

    internal fun onAsyncRouteRequested(requestId: Int, route: String) {
        activityLifecycle.onAsyncRouteRequested(requestId, route)
    }

    internal fun onAsyncRouteCanceled(requestId: Int) {
        activityLifecycle.onAsyncRouteCanceled(requestId)
    }

    internal fun onAsyncRouteSettled(requestId: Int, route: String, succeeded: Boolean) {
        activityLifecycle.onAsyncRouteSettled(requestId, route, succeeded)
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
        onScrollY: (Int) -> Unit = NoOpRouteScrollY,
        studySessionActive: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        shellHost.composeRoute(selected, initialScrollY, scrollPositionLabel, onScrollY, studySessionActive, content)
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = NoOpRouteScrollY,
        studySessionActive: Boolean = false,
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        shellHost.composeRouteWithActionBar(
            selected,
            initialScrollY,
            scrollPositionLabel,
            onScrollY,
            studySessionActive,
            beforeContent,
            content,
            actionBar,
        )
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
        return warmStrokeGuides().get(kanji)
    }

    /**
     * Parse and cache the stroke-guide asset, safe to call from any thread. Startup
     * warms this on a dedicated thread; the cache is process-wide (see
     * [AssetWarmupCache]), so activity recreation reuses the already-parsed map and
     * only the very first call in the process pays the 9.5 MB parse.
     */
    fun warmStrokeGuides(): Map<String, StrokeGuide> {
        strokeGuides?.let { return it }
        val loaded = AssetWarmupCache.strokeGuides(this)
        strokeGuides = loaded
        return loaded
    }

    /**
     * Open and cache the dictionary lookup (copies + hashes the bundled asset DB on
     * first run), safe to call from any thread. Cached process-wide (see
     * [AssetWarmupCache]) so activity recreation reuses the installed lookup and its
     * open read connection.
     */
    fun warmDictionaryLookup(): DictionaryLookup {
        dictionaryLookup?.let { return it }
        val loaded = AssetWarmupCache.dictionaryLookup(this)
        dictionaryLookup = loaded
        return loaded
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
        const val EXTRA_OPEN_UPDATE = "dev.bee.kanjianki.extra.OPEN_UPDATE"
        const val EXTRA_OPEN_STUDY = "dev.bee.kanjianki.extra.OPEN_STUDY"
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
