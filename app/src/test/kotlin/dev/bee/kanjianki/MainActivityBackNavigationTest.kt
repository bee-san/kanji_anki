package dev.bee.kanjianki

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityBackNavigationTest {
    @Test
    fun routePreparationSetsHomeAsBackDestinationForNonHomeRoutes() {
        val activity = buildActivity()
        val host = MainActivityShellHost(activity)

        invokePrepareRoute(host, MainActivityBase.NAV_STUDY)
        assertNotNull(activity.backAction)
        invokePrepareRoute(host, MainActivityBase.NAV_STATS_ROUTE)
        assertNotNull(activity.backAction)
        invokePrepareRoute(host, MainActivityBase.NAV_SETTINGS_ROUTE)
        assertNotNull(activity.backAction)

        invokePrepareRoute(host, MainActivityBase.NAV_HOME_ROUTE)
        assertNull(activity.backAction)
    }

    @Test
    fun backNavigationRunsTheRegisteredActionOnce() {
        val activity = buildActivity()
        var backRuns = 0
        activity.backAction = Runnable { backRuns++ }

        assertTrue(activity.handleBackNavigation())
        assertEquals(1, backRuns)
    }

    @Test
    fun backNavigationFallsThroughWhenNoDestinationIsRegistered() {
        val activity = buildActivity()
        activity.backAction = null

        assertFalse(activity.handleBackNavigation())
    }

    @Test
    fun nonHomeBackDestinationRendersHome() {
        val activity = buildActivity()
        val host = MainActivityShellHost(activity)

        invokePrepareRoute(host, MainActivityBase.NAV_STATS_ROUTE)
        assertTrue(activity.handleBackNavigation())
        assertEquals(1, activity.renderHomeCalls)
    }

    private fun buildActivity(): BackNavigationTestActivity {
        return Robolectric.buildActivity(BackNavigationTestActivity::class.java)
            .create()
            .get()
    }

    private fun invokePrepareRoute(host: MainActivityShellHost, selected: String) {
        val method = MainActivityShellHost::class.java.getDeclaredMethod("prepareRoute", String::class.java)
        method.isAccessible = true
        method.invoke(host, selected)
    }

    private class BackNavigationTestActivity : MainActivity() {
        var renderHomeCalls = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            // Skip the real activity startup; this test only exercises back routing.
        }

        override fun renderHome() {
            renderHomeCalls++
        }

        override fun abandonActiveStudyTask() {
            // No-op for back-routing tests.
        }
    }
}
