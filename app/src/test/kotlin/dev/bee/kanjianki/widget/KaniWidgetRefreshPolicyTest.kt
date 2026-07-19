package dev.bee.kanjianki.widget

import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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
    fun oneShotFiresForAnyFutureUsefulBoundary() {
        val now = 1_800_000_000_000L
        val inWindow = now + 30 * 60 * 1000L
        val laterToday = now + 3 * 60 * 60 * 1000L

        assertEquals(inWindow, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, inWindow))
        assertEquals(laterToday, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, laterToday))
    }

    @Test
    fun oneShotSkipsPastAndPresentTimes() {
        val now = 1_800_000_000_000L

        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, 0L))
        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, now - 1L))
        assertEquals(0L, KaniWidgetRefreshPolicy.oneShotRefreshAtMillis(now, now))
    }

    @Test
    fun nextLocalMidnightUsesTheRequestedZoneAcrossDst() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, KaniWidgetRefreshPolicy.nextLocalMidnightMillis(now, zone))
    }

    @Test
    fun earliestBoundaryPrefersUsefulDueThenFallsBackToMidnight() {
        val now = Instant.parse("2026-07-18T10:00:00Z").toEpochMilli()
        val dueSoon = Instant.parse("2026-07-18T10:30:00Z").toEpochMilli()
        val dueLater = Instant.parse("2026-07-18T13:00:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-07-19T00:00:00Z").toEpochMilli()

        assertEquals(dueSoon, KaniWidgetRefreshPolicy.earliestBoundaryAtMillis(now, dueSoon, midnight))
        assertEquals(dueLater, KaniWidgetRefreshPolicy.earliestBoundaryAtMillis(now, dueLater, midnight))
        assertEquals(
            midnight,
            KaniWidgetRefreshPolicy.earliestBoundaryAtMillis(now, midnight + 60_000L, midnight),
        )
        assertEquals(0L, KaniWidgetRefreshPolicy.earliestBoundaryAtMillis(now, 0L, now))
    }
}
