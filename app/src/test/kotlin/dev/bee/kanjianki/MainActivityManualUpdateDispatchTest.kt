package dev.bee.kanjianki

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityManualUpdateDispatchTest {
    @Test
    fun homeRetryDoesNotQueueNetworkUpdateAheadOfRouteLoads() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val routeTasks = QueueingExecutorService()
        val maintenanceTasks = QueueingExecutorService()
        replaceBaseField(activity, "io", routeTasks)
        replaceBaseField(activity, "maintenance", maintenanceTasks)

        val trigger = MainActivityHome::class.java.getDeclaredMethod("triggerManualUpdateCheck")
        trigger.isAccessible = true
        trigger.invoke(activity)

        assertEquals("route executor must stay free for navigation", 0, routeTasks.pendingCount())
        assertEquals(1, maintenanceTasks.pendingCount())
    }

    @Test
    fun settingsUpdateDoesNotQueueNetworkWorkAheadOfRouteLoads() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()
        val routeTasks = QueueingExecutorService()
        val maintenanceTasks = QueueingExecutorService()
        replaceBaseField(activity, "io", routeTasks)
        replaceBaseField(activity, "maintenance", maintenanceTasks)

        activity.runUpdate(cachedPending = false)

        assertEquals("route executor must stay free for navigation", 0, routeTasks.pendingCount())
        assertEquals(1, maintenanceTasks.pendingCount())
    }

    private fun replaceBaseField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        (field.get(activity) as? java.util.concurrent.ExecutorService)?.shutdownNow()
        field.set(activity, value)
    }

    private class QueueingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        fun pendingCount(): Int = tasks.size

        override fun execute(command: Runnable) {
            check(!shutdown)
            tasks.addLast(command)
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val pending = tasks.toMutableList()
            tasks.clear()
            return pending
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }
}
