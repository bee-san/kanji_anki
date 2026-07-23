package dev.bee.kanjianki

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityMissingKanjiRouteTest {
    @Test
    fun homeActionsPlaceGamesAndMissingKanjiTogetherInTheFinalRow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, ActionTrackingActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
        }
        val controller = Robolectric.buildActivity(ActionTrackingActivity::class.java, intent)
            .create()
            .start()
            .resume()
        try {
            val activity = controller.get()
            val actions = homeActionModels(activity)
            val labels = actions.map(HomeActionModel::label)
            val gamesIndex = labels.indexOf(HomeTextCopy.gamesActionLabel())
            val missingIndex = labels.indexOf(MissingKanjiTextCopy.actionLabel())

            assertEquals(4, gamesIndex)
            assertEquals(5, missingIndex)
            assertEquals(gamesIndex / 2, missingIndex / 2)

            actions[gamesIndex].onClick()
            actions[missingIndex].onClick()
            assertEquals(1, activity.gamesCalls)
            assertEquals(1, activity.missingKanjiCalls)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun missingKanjiRouteRoundTripsThroughSavedHomeState() {
        val route = HomeRouteRestoration.missingKanji()

        val restored = HomeRouteRestoration.fromBundle(route.toBundle())

        assertEquals(route, restored)
        assertEquals(
            HomeRouteRestoration.Destination.MISSING_KANJI,
            restored?.destination,
        )
        assertNotEquals(
            MainActivityRouteStateKey(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                homeRoute = HomeRouteRestoration.games(),
            ).saveableStateKey(),
            MainActivityRouteStateKey(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                homeRoute = route,
            ).saveableStateKey(),
        )
    }

    @Test
    fun permissionResultRestoresMissingKanjiInsteadOfReplacingItWithHome() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, ActionTrackingActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
        }
        val controller = Robolectric.buildActivity(ActionTrackingActivity::class.java, intent)
            .create()
            .start()
            .resume()
        try {
            val activity = controller.get()
            val route = HomeRouteRestoration.missingKanji()
            activity.currentRoute = MainActivityBase.NAV_HOME_ROUTE
            activity.currentHomeRouteRestoration = route

            activity.handleAnkiPermissionResult()

            assertEquals(route, activity.restoredRoute)
            assertEquals(0, activity.productionHomeCalls)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun screenshotHarnessDispatchesMissingKanjiRoute() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, ActionTrackingActivity::class.java).apply {
            putExtra(
                MainActivityBase.EXTRA_SCREENSHOT_ROUTE,
                MainActivityBase.SCREENSHOT_MISSING_KANJI_ROUTE,
            )
        }
        val controller = Robolectric.buildActivity(ActionTrackingActivity::class.java, intent)
            .create()
            .start()
            .resume()
        try {
            assertTrue(controller.get().missingKanjiCalls > 0)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private class ActionTrackingActivity : MainActivity() {
        var gamesCalls = 0
        var missingKanjiCalls = 0
        var productionHomeCalls = 0
        var restoredRoute: HomeRouteRestoration? = null

        override fun renderGames() {
            gamesCalls += 1
        }

        override fun renderMissingKanji() {
            missingKanjiCalls += 1
        }

        override fun renderHome() {
            if (isScreenshotLaunchRequested()) {
                super.renderHome()
            } else {
                productionHomeCalls += 1
            }
        }

        override fun renderRestoredHomeRoute(route: HomeRouteRestoration) {
            restoredRoute = route
        }
    }
}
