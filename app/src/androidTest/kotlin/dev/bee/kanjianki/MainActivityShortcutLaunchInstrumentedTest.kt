package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.host.KaniLaunchIntents
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityShortcutLaunchInstrumentedTest {
    @Test
    fun studyShortcutOpensStudyRoute() {
        launchShortcut(KaniLaunchIntents.ACTION_OPEN_STUDY) { activity ->
            activity.currentRoute == MainActivityBase.NAV_STUDY
        }
    }

    @Test
    fun browseShortcutOpensBrowseSubroute() {
        launchShortcut(KaniLaunchIntents.ACTION_OPEN_BROWSE) { activity ->
            activity.currentRoute == MainActivityBase.NAV_HOME_ROUTE && activity.backAction != null
        }
    }

    @Test
    fun gamesShortcutOpensGamesSubroute() {
        launchShortcut(KaniLaunchIntents.ACTION_OPEN_GAMES) { activity ->
            activity.currentRoute == MainActivityBase.NAV_HOME_ROUTE && activity.backAction != null
        }
    }

    @Test
    fun warmShortcutReusesTopActivityAndRoutesThroughOnNewIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var originalActivity: MainActivity? = null
            scenario.onActivity { activity ->
                originalActivity = activity
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(KaniLaunchIntents.ACTION_OPEN_STUDY),
                )
            }

            awaitRoute(scenario) { activity -> activity.currentRoute == MainActivityBase.NAV_STUDY }
            scenario.onActivity { activity -> assertSame(originalActivity, activity) }
        }
    }

    private fun launchShortcut(action: String, routeReady: (MainActivity) -> Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).setAction(action)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitRoute(scenario, routeReady)
        }
    }

    private fun awaitRoute(
        scenario: ActivityScenario<MainActivity>,
        routeReady: (MainActivity) -> Boolean,
    ): MainActivity {
        var currentActivity: MainActivity? = null
        repeat(100) {
            var readyActivity: MainActivity? = null
            scenario.onActivity { activity ->
                currentActivity = activity
                if (routeReady(activity)) {
                    readyActivity = activity
                }
            }
            readyActivity?.let { return it }
            Thread.sleep(50L)
        }
        throw AssertionError(
            "Shortcut route did not settle within 5 seconds; " +
                "last route was ${currentActivity?.currentRoute}",
        )
    }
}
