package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetRefreshReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun delayedRefreshDoesNotFinishPendingWorkUntilRefreshCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        var finished = false
        val receiver = KaniWidgetRefreshReceiver().apply {
            coroutineScope = this@runTest
            refreshInstalled = { gate.await() }
        }

        receiver.launchRefresh(context) { finished = true }
        runCurrent()
        assertFalse(finished)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(finished)
    }

    @Test
    fun refreshFailureStillFinishesPendingWork() = runTest {
        var finished = false
        val receiver = KaniWidgetRefreshReceiver().apply {
            coroutineScope = this@runTest
            refreshInstalled = { error("refresh failed") }
        }

        receiver.launchRefresh(context) { finished = true }
        advanceUntilIdle()

        assertTrue(finished)
    }

    @Test
    fun updaterUsesTheSingleExplicitRefreshAction() {
        val intent = KaniWidgetUpdater.refreshIntent(context)

        assertTrue(intent.component?.className == KaniWidgetRefreshReceiver::class.java.name)
        assertTrue(intent.action == KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH)
        assertTrue(intent.`package` == context.packageName)
    }

    @Test
    fun timeAndZoneChangesResetBoundaryBeforeRefreshing() = runTest {
        val receiver = KaniWidgetRefreshReceiver()
        val events = mutableListOf<String>()
        receiver.coroutineScope = this
        receiver.boundaryReset = { events += "reset" }
        receiver.refreshInstalled = { events += "refresh" }

        receiver.prepareBoundaryState(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        receiver.launchRefresh(context) {}
        advanceUntilIdle()

        assertTrue(events == listOf("reset", "refresh"))
    }

    @Test
    fun alarmRefreshMarksPersistedBoundaryFiredBeforeRefreshing() = runTest {
        val receiver = KaniWidgetRefreshReceiver()
        val events = mutableListOf<String>()
        receiver.coroutineScope = this
        receiver.boundaryFired = { events += "fired" }
        receiver.refreshInstalled = { events += "refresh" }
        val intent = KaniWidgetUpdater.refreshIntent(context)
            .putExtra(KaniWidgetBoundaryAlarm.EXTRA_BOUNDARY_TRIGGER, true)

        receiver.prepareBoundaryState(context, intent)
        receiver.launchRefresh(context) {}
        advanceUntilIdle()

        assertTrue(events == listOf("fired", "refresh"))
    }
}
