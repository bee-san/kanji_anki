package dev.bee.kanjianki.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetRefreshReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ShadowAppWidgetManager.reset()
    }

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
    fun updaterDoesNotBroadcastWhenNoFamilyIsInstalled() {
        val shadowApplication = shadowOf(context.applicationContext as Application)
        val before = shadowApplication.broadcastIntents.size

        KaniWidgetUpdater.requestUpdate(context)

        assertEquals(before, shadowApplication.broadcastIntents.size)
    }

    @Test
    fun updaterBroadcastsExplicitlyWhenAFamilyIsInstalled() {
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(
            701,
            ComponentName(context, KaniWidgetReceiver::class.java),
        )
        val shadowApplication = shadowOf(context.applicationContext as Application)
        val before = shadowApplication.broadcastIntents.size

        KaniWidgetUpdater.requestUpdate(context)

        assertEquals(before + 1, shadowApplication.broadcastIntents.size)
        val intent = shadowApplication.broadcastIntents.last()
        assertEquals(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH, intent.action)
        assertEquals(KaniWidgetRefreshReceiver::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.`package`)
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

        assertEquals(listOf("reset", "refresh"), events)
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

        assertEquals(listOf("fired", "refresh"), events)
    }
}
