package dev.bee.kanjianki

import android.os.Bundle
import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityShellHostStableContentTest {
    @Test
    fun repeatedImperativeRoutesInstallActivityContentOnlyOnce() {
        val activity = Robolectric.buildActivity(StableContentTestActivity::class.java)
            .create()
            .get()
        activity.screenshotThemeChoiceOverride = KaniThemeChoice.GIRLYPOP
        var installCount = 0
        var beforeContentCount = 0
        val host = MainActivityShellHost(
            activity = activity,
            installContent = { installCount++ },
        )

        host.composeRoute(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            content = {},
        )
        host.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            beforeContent = { beforeContentCount++ },
            content = {},
            actionBar = {},
        )

        assertEquals(1, installCount)
        assertEquals(1, beforeContentCount)
    }

    private class StableContentTestActivity : MainActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            // Skip startup: this test exercises the shell host publication contract only.
        }
    }
}
