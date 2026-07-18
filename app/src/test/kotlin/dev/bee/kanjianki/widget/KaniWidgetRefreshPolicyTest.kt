package dev.bee.kanjianki.widget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniWidgetRefreshPolicyTest {

    @Test
    fun supportedSystemAndExplicitBroadcastsTriggerRefresh() {
        assertTrue(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_TIME_CHANGED))
        assertTrue(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_LOCALE_CHANGED))
        assertTrue(
            KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH),
        )
    }

    @Test
    fun dateAppWidgetAndUnknownActionsAreNotPlainRefreshReceiverEvents() {
        assertFalse(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(null))
        assertFalse(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(""))
        assertFalse(
            KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast("android.appwidget.action.APPWIDGET_UPDATE"),
        )
        assertFalse(KaniWidgetRefreshPolicy.shouldRefreshOnBroadcast(Intent.ACTION_DATE_CHANGED))
    }

    @Test
    fun oneShotFiresOnlyForFutureTimesInsideTheHourlyFallbackWindow() {
        val now = 1_800_000_000_000L
        val inWindow = now + 30 * 60 * 1000L

        assertEquals(inWindow, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, inWindow))
        assertEquals(
            now + KaniWidgetRefreshPolicy.ONE_SHOT_WINDOW_MILLIS,
            KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(
                now,
                now + KaniWidgetRefreshPolicy.ONE_SHOT_WINDOW_MILLIS,
            ),
        )
    }

    @Test
    fun oneShotSkipsPastPresentAndBeyondWindowTimes() {
        val now = 1_800_000_000_000L

        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, 0L))
        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, now - 1L))
        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, now))
        assertEquals(
            0L,
            KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(
                now,
                now + KaniWidgetRefreshPolicy.ONE_SHOT_WINDOW_MILLIS + 1L,
            ),
        )
    }
}
