package dev.bee.kanjianki

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.theme.resolveSystemBars
import java.util.Locale

/**
 * Cards remaining in the user's focus study session, shown on the Study nav badge.
 * While a study card has unfinished scheduled session work, the live tracker is the
 * source of truth, so the badge follows answered card appearances. An in-horizon
 * learning/relearning repeat grows the target by one; only the persisted next
 * occurrence counts, and an out-of-horizon repeat does not. "Practice-only" excludes
 * those answers from real-due ladder promotion/demotion evidence, not from visible
 * session workload.
 * Otherwise the exact selectable Study-now count cached by the Home/Study route load
 * is used. Checking the active card explicitly prevents a stale plan target from
 * surviving on an empty Study screen. Non-positive results mean "hide the badge".
 */
internal fun studySessionBadgeCount(
    studySessionActive: Boolean,
    trackerTargetCount: Int,
    trackerCompletedCount: Int,
    cachedStudyNowCount: Int,
): Int {
    if (studySessionActive && trackerTargetCount > 0 && trackerCompletedCount < trackerTargetCount) {
        return trackerTargetCount - trackerCompletedCount
    }
    return cachedStudyNowCount
}

internal fun shouldStartNewStudyRunFromNavigation(
    studySessionActive: Boolean,
    currentRunAtHardCap: Boolean,
): Boolean = !studySessionActive && currentRunAtHardCap

internal class MainActivityShellHost(
    private val activity: MainActivityBase,
    private val installContent: (@Composable () -> Unit) -> Unit = { content ->
        activity.setContent(content = content)
    },
) {
    private val hostedRoute = mutableStateOf<HostedRoute?>(null)
    private var contentInstalled = false
    private var nextRouteRevision = 0L

    fun composeRoute(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = NoOpRouteScrollY,
        studySessionActive: Boolean = false,
        scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
        content: @Composable () -> Unit,
    ) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            // Non-blocking theme read: route composition happens on the main thread and
            // must never wait behind a cold-boot DB open/migration. Background route
            // loads warm the cache before their render thunk runs (see
            // MainActivityHome.warmThemeThen), so this only falls back to the default
            // theme for the brief deferred-loading frame on a truly cold process.
            val themeChoice = activity.screenshotThemeChoiceOverride ?: activity.store.appThemeChoiceNonBlocking()
            val isSystemDarkTheme = MainActivityUiSupport.isNightMode(activity.resources.configuration)
            val systemBars = themeChoice.resolveSystemBars(isSystemDarkTheme)
            publishRoute(
                HostedRoute(
                    revision = nextRevision(),
                    stateKey = routeStateKey(selected),
                    publishedAtNanos = shellHostMonotonicNanos(),
                    model = shellModel(selected, scrollPositionLabel, studySessionActive),
                    initialScrollY = initialScrollY,
                    onScrollY = onScrollY,
                    navActions = navActions(studySessionActive),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    scrollMode = scrollMode,
                    content = content,
                    actionBar = null,
                )
            )
            activity.styleSystemBars(systemBars)
        }
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = NoOpRouteScrollY,
        studySessionActive: Boolean = false,
        scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            beforeContent()
            val themeChoice = activity.screenshotThemeChoiceOverride ?: activity.store.appThemeChoiceNonBlocking()
            val isSystemDarkTheme = MainActivityUiSupport.isNightMode(activity.resources.configuration)
            val systemBars = themeChoice.resolveSystemBars(isSystemDarkTheme)
            publishRoute(
                HostedRoute(
                    revision = nextRevision(),
                    stateKey = routeStateKey(selected),
                    publishedAtNanos = shellHostMonotonicNanos(),
                    model = shellModel(selected, scrollPositionLabel, studySessionActive),
                    initialScrollY = initialScrollY,
                    onScrollY = onScrollY,
                    navActions = navActions(studySessionActive),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    scrollMode = scrollMode,
                    content = content,
                    actionBar = actionBar,
                )
            )
            activity.styleSystemBars(systemBars)
        }
    }

    private fun shellModel(
        selected: String,
        scrollPositionLabel: String?,
        studySessionActive: Boolean,
    ): MainActivityShellModel {
        return MainActivityShellModel(
            selectedRoute = selected,
            scrollPositionLabel = scrollPositionLabel,
            studyBadgeCount = studyBadgeCount(studySessionActive),
            studyCardKeyboardResident = studyCardKeyboardResident(selected),
            studySessionActive = studySessionActive,
        )
    }

    private fun nextRevision(): Long {
        nextRouteRevision += 1L
        return nextRouteRevision
    }

    private fun routeStateKey(selected: String): MainActivityRouteStateKey {
        if (selected == MainActivityBase.NAV_HOME_ROUTE) {
            return MainActivityRouteStateKey(
                selectedRoute = selected,
                homeRoute = activity.currentHomeRouteRestoration,
            )
        }
        if (selected == MainActivityBase.NAV_STUDY) {
            val studyRoute = activity.studySessionViewModel.acceptedRouteSnapshot()
            return MainActivityRouteStateKey(
                selectedRoute = selected,
                studySessionGeneration = studyRoute.sessionGeneration.value,
                studySessionToken = studyRoute.sessionToken,
                studyComplete = studyRoute.isComplete,
            )
        }
        return MainActivityRouteStateKey(selectedRoute = selected)
    }

    /**
     * Installs the Activity's Compose owner once, then publishes immutable route
     * snapshots into that owner. Re-running setContent for every flashcard tears
     * down the composition, focus owner and layout tree at the hottest point in
     * the study loop; a state update lets Compose retain the shell and only
     * replace the explicitly keyed route body.
     */
    private fun publishRoute(route: HostedRoute) {
        hostedRoute.value = route
        if (contentInstalled) {
            return
        }
        contentInstalled = true
        try {
            installContent {
                val currentRoute = hostedRoute.value
                if (currentRoute != null) {
                    HostedRouteContent(currentRoute)
                }
            }
        } catch (error: RuntimeException) {
            contentInstalled = false
            throw error
        }
    }

    @Composable
    private fun HostedRouteContent(route: HostedRoute) {
        val studyState by activity.studySessionUiState.collectAsState()
        val observedModel = route.model.copy(
            studyBadgeCount = studySessionBadgeCount(
                studySessionActive = route.model.studySessionActive,
                trackerTargetCount = studyState.progress.targetCount,
                trackerCompletedCount = studyState.progress.completedCount,
                cachedStudyNowCount = activity.studySessionBadgeCount,
            ).takeIf { it > 0 },
        )
        LaunchedEffect(route.revision) {
            withFrameNanos { }
            if (AppDebugLog.isCapturing()) {
                val elapsedMillis = (shellHostMonotonicNanos() - route.publishedAtNanos)
                    .coerceAtLeast(0L) / 1_000_000.0
                AppDebugLog.log(
                    String.format(
                        Locale.US,
                        "route event=frame-scheduled route=%s revision=%d " +
                            "publish_to_frame_schedule_ms=%.2f",
                        traceToken(route.model.selectedRoute),
                        route.revision,
                        elapsedMillis,
                    ),
                )
            }
        }
        val actionBar = route.actionBar
        if (actionBar == null) {
            MainActivityComposeRoute(
                model = observedModel,
                initialScrollY = route.initialScrollY,
                onScrollY = route.onScrollY,
                navActions = route.navActions,
                themeChoice = route.themeChoice,
                isSystemDarkTheme = route.isSystemDarkTheme,
                contentKey = route.stateKey,
                scrollMode = route.scrollMode,
                content = route.content,
            )
        } else {
            MainActivityComposeRouteWithActionBar(
                model = observedModel,
                initialScrollY = route.initialScrollY,
                onScrollY = route.onScrollY,
                navActions = route.navActions,
                themeChoice = route.themeChoice,
                isSystemDarkTheme = route.isSystemDarkTheme,
                contentKey = route.stateKey,
                scrollMode = route.scrollMode,
                content = route.content,
                actionBar = actionBar,
            )
        }
    }

    /**
     * True when the study route is showing an unrevealed typing card — i.e. the
     * card is about to auto-focus its field and open the keyboard. Used to hide
     * the bottom nav for the whole keyboard-resident state, not just while the
     * IME inset is non-zero (KB1).
     */
    private fun studyCardKeyboardResident(selected: String): Boolean {
        if (MainActivityBase.NAV_STUDY != selected) {
            return false
        }
        return (dev.bee.kanjianki.core.StudyTaskCopy.isTypingMeaningTask(activity.activeSession) ||
            dev.bee.kanjianki.core.StudyTaskCopy.isTypingReadingTask(activity.activeSession)) &&
            !activity.flashcardAnswerRevealed
    }

    private fun studyBadgeCount(studySessionActive: Boolean): Int? {
        val tracker = activity.studySessionTracker
        return studySessionBadgeCount(
            studySessionActive = studySessionActive,
            trackerTargetCount = tracker.targetCount(),
            trackerCompletedCount = tracker.completedCount(),
            cachedStudyNowCount = activity.studySessionBadgeCount,
        ).takeIf { it > 0 }
    }

    private fun navActions(studySessionActive: Boolean): KaniNavActions {
        return KaniNavActions(
            onHome = { activity.renderHome() },
            onStudy = {
                if (
                    shouldStartNewStudyRunFromNavigation(
                        studySessionActive = studySessionActive,
                        currentRunAtHardCap = activity.studySessionTracker.atHardCap(
                            activity.continueAllKanjiSession,
                        ),
                    )
                ) {
                    activity.startFocusedStudy()
                } else {
                    activity.renderStudy()
                }
            },
            onStats = { activity.renderStats() },
            onSettings = { activity.renderSettings() },
        )
    }

    private fun prepareRoute(selected: String) {
        activity.currentRoute = selected
        activity.activeUpdateUiRunToken = 0
        if (MainActivityBase.NAV_STUDY != selected) {
            activity.abandonActiveStudyTask()
            activity.studyUndoState.clear()
            StudyCardFrameDiagnostics.clear("left-study")
        }
        MainActivityStudyInteractionReset.resetRoute(activity)
        activity.backAction = if (MainActivityBase.NAV_HOME_ROUTE == selected) {
            null
        } else {
            Runnable { activity.renderHome() }
        }
    }

    private data class HostedRoute(
        val revision: Long,
        val stateKey: MainActivityRouteStateKey,
        val publishedAtNanos: Long,
        val model: MainActivityShellModel,
        val initialScrollY: Int,
        val onScrollY: (Int) -> Unit,
        val navActions: KaniNavActions,
        val themeChoice: KaniThemeChoice,
        val isSystemDarkTheme: Boolean,
        val scrollMode: MainActivityRouteScrollMode,
        val content: @Composable () -> Unit,
        val actionBar: (@Composable () -> Unit)?,
    )

}

internal data class MainActivityRouteStateKey(
    val selectedRoute: String,
    val homeRoute: HomeRouteRestoration? = null,
    val studySessionGeneration: Long? = null,
    val studySessionToken: String? = null,
    val studyComplete: Boolean = false,
) {
    fun saveableStateKey(): String = buildString {
        appendStateKeyPart(selectedRoute)
        appendStateKeyPart(homeRoute?.destination?.wireName)
        appendStateKeyPart(homeRoute?.query)
        appendStateKeyPart(homeRoute?.kanji)
        appendStateKeyPart(homeRoute?.onlySimilarKanji?.toString())
        appendStateKeyPart(homeRoute?.allKanjiScope?.toString())
        appendStateKeyPart(homeRoute?.fromBrowse?.toString())
        appendStateKeyPart(studySessionGeneration?.toString())
        appendStateKeyPart(studySessionToken)
        appendStateKeyPart(studyComplete.toString())
    }
}

private fun StringBuilder.appendStateKeyPart(value: String?) {
    if (value == null) {
        append("-;")
        return
    }
    append(value.length).append(':').append(value).append(';')
}

private fun shellHostMonotonicNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}
