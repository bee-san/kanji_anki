package dev.bee.kanjianki

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.theme.resolveSystemBars

/**
 * Cards remaining in the user's focus study session, shown on the Study nav badge.
 * While a run is in flight (a target exists and is not yet met) the live session
 * tracker is the source of truth, so the badge decreases with every answered card.
 * Otherwise the latest adaptive-plan `remaining` cached by the background route
 * loads is used. Non-positive results mean "hide the badge".
 */
internal fun studySessionBadgeCount(
    trackerTargetCount: Int,
    trackerCompletedCount: Int,
    cachedPlanRemaining: Int,
): Int {
    if (trackerTargetCount > 0 && trackerCompletedCount < trackerTargetCount) {
        return trackerTargetCount - trackerCompletedCount
    }
    return cachedPlanRemaining
}

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun composeRoute(selected: String, initialScrollY: Int = 0, scrollPositionLabel: String? = null, onScrollY: (Int) -> Unit = NoOpRouteScrollY, content: @Composable () -> Unit) {
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
            activity.setContent {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(
                        selectedRoute = selected,
                        scrollPositionLabel = scrollPositionLabel,
                        studyBadgeCount = studyBadgeCount(),
                        studyCardKeyboardResident = studyCardKeyboardResident(selected),
                    ),
                    initialScrollY = initialScrollY,
                    onScrollY = onScrollY,
                    navActions = navActions(),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    content = content,
                )
            }
            activity.styleSystemBars(systemBars)
        }
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        onScrollY: (Int) -> Unit = NoOpRouteScrollY,
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
            activity.setContent {
                MainActivityComposeRouteWithActionBar(
                    model = MainActivityShellModel(
                        selectedRoute = selected,
                        scrollPositionLabel = scrollPositionLabel,
                        studyBadgeCount = studyBadgeCount(),
                        studyCardKeyboardResident = studyCardKeyboardResident(selected),
                    ),
                    initialScrollY = initialScrollY,
                    onScrollY = onScrollY,
                    navActions = navActions(),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    content = content,
                    actionBar = actionBar,
                )
            }
            activity.styleSystemBars(systemBars)
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
        return dev.bee.kanjianki.core.StudyTaskCopy.isTypingMeaningTask(activity.activeSession) &&
            !activity.flashcardAnswerRevealed
    }

    private fun studyBadgeCount(): Int? {
        val tracker = activity.studySessionTracker
        return studySessionBadgeCount(
            trackerTargetCount = tracker.targetCount(),
            trackerCompletedCount = tracker.completedCount(),
            cachedPlanRemaining = activity.studySessionBadgeCount,
        ).takeIf { it > 0 }
    }

    private fun navActions(): KaniNavActions {
        return KaniNavActions(
            onHome = { activity.renderHome() },
            onStudy = { activity.renderStudy() },
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
        }
        MainActivityStudyInteractionReset.resetRoute(activity)
        activity.backAction = if (MainActivityBase.NAV_HOME_ROUTE == selected) {
            null
        } else {
            Runnable { activity.renderHome() }
        }
    }

}
