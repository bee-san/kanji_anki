package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
