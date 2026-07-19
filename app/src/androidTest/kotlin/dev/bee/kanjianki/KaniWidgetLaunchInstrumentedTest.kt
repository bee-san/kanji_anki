package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.widget.KaniWidgetSnapshot
import dev.bee.kanjianki.widget.KaniWidgetState

import dev.bee.kanjianki.widget.kaniFocusDetailIntent
import dev.bee.kanjianki.widget.kaniWidgetLaunchIntent
import dev.bee.kanjianki.widget.kaniWidgetStatsIntent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaniWidgetLaunchInstrumentedTest {
    @Test
    fun openStudyExtraLandsOnStudyRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var route = ""
            repeat(100) {
                scenario.onActivity { activity -> route = activity.currentRoute }
                if (route == MainActivityBase.NAV_STUDY) {
                    return@use
                }
                Thread.sleep(50L)
            }
            assertEquals(MainActivityBase.NAV_STUDY, route)
        }
    }

    @Test
    fun dueWidgetTapReusesWarmActivityAndOpensStudyRoute() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var originalActivityId = 0
            scenario.onActivity { activity ->
                originalActivityId = System.identityHashCode(activity)
                activity.startActivity(
                    kaniWidgetLaunchIntent(
                        activity,
                        KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1),
                    ),
                )
            }

            var activeActivityId = 0
            var route = ""
            repeat(100) {
                scenario.onActivity { activity ->
                    activeActivityId = System.identityHashCode(activity)
                    route = activity.currentRoute
                }
                if (route == MainActivityBase.NAV_STUDY) {
                    assertEquals(originalActivityId, activeActivityId)
                    return@use
                }
                Thread.sleep(50L)
            }
            assertEquals(MainActivityBase.NAV_STUDY, route)
        }
    }

    @Test
    fun coldAndWarmStatsWidgetTapsOpenStatsWithoutDuplicatingActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(kaniWidgetStatsIntent(context)).use { scenario ->
            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STATS_ROUTE }
            assertEquals(MainActivityBase.NAV_STATS_ROUTE, state.route)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val originalId = awaitState(scenario).activityId
            scenario.onActivity { it.startActivity(kaniWidgetStatsIntent(it)) }
            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STATS_ROUTE }
            assertEquals(MainActivityBase.NAV_STATS_ROUTE, state.route)
            assertEquals(originalId, state.activityId)
        }
    }

    @Test
    fun coldAndWarmFocusWidgetTapsOpenExactBrowseDetailWithoutDuplicatingActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(kaniFocusDetailIntent(context, "学")).use { scenario ->
            val state = awaitState(scenario) { it.browseQuery == "学" && it.allKanji }
            assertEquals("学", state.browseQuery)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val originalId = awaitState(scenario).activityId
            scenario.onActivity { it.startActivity(kaniFocusDetailIntent(it, "学")) }
            val state = awaitState(scenario) { it.browseQuery == "学" && it.allKanji }
            assertEquals("学", state.browseQuery)
            assertEquals(originalId, state.activityId)
        }
    }


    private fun awaitState(
        scenario: ActivityScenario<MainActivity>,
        ready: (ActivityState) -> Boolean = { true },
    ): ActivityState {
        var state = ActivityState()
        repeat(100) {
            scenario.onActivity { activity ->
                state = ActivityState(
                    route = activity.currentRoute,
                    browseQuery = activity.activeBrowseQuery,
                    allKanji = activity.activeBrowseAllKanji,
                    activityId = System.identityHashCode(activity),
                )
            }
            if (ready(state)) return state
            Thread.sleep(50L)
        }
        return state
    }

    private data class ActivityState(
        val route: String = "",
        val browseQuery: String = "",
        val allKanji: Boolean = false,
        val activityId: Int = 0,
    )
}
