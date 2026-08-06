package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.host.KaniLaunchIntents
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessContainerLifecycleInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
    }

    @Test
    fun recreationAndActivityCloseKeepProcessResourcesAlive() {
        val application = context.applicationContext as KaniApplication
        val container = application.container
        lateinit var userIo: ExecutorService
        lateinit var maintenance: ExecutorService
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(KaniLaunchIntents.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_SETTINGS_ROUTE)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertSame(container.localStore, activity.store)
                assertSame(container.ankiDroidGateway, activity.gateway)
                assertSame(container.userIoExecutor, activity.io)
                assertSame(container.maintenanceExecutor, activity.maintenance)
                assertSame(container.dispatchers, activity.dispatchers)
                userIo = activity.io
                maintenance = activity.maintenance
            }

            scenario.recreate()

            scenario.onActivity { recreated ->
                assertSame(container.localStore, recreated.store)
                assertSame(container.ankiDroidGateway, recreated.gateway)
                assertSame(userIo, recreated.io)
                assertSame(maintenance, recreated.maintenance)
                assertFalse(recreated.io.isShutdown)
                assertFalse(recreated.maintenance.isShutdown)
            }
        }

        assertFalse(userIo.isShutdown)
        assertFalse(maintenance.isShutdown)
    }
}
