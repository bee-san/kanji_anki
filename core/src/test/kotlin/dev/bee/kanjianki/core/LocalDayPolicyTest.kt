package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class LocalDayPolicyTest {
    @Test
    fun localDayStartUsesCurrentTimeZoneMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 15, 0, 0),
                LocalDayPolicy.localDayStart(utc(2026, Calendar.MAY, 15, 23, 59))
            )
        }
    }

    @Test
    fun moveLocalDaysKeepsResultAtLocalMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 17, 0, 0),
                LocalDayPolicy.moveLocalDays(utc(2026, Calendar.MAY, 15, 18, 30), 2)
            )
        }
    }

    @Test
    fun nextLocalDayStartReturnsNextLocalMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 16, 0, 0),
                LocalDayPolicy.nextLocalDayStart(utc(2026, Calendar.MAY, 15, 18, 30))
            )
        }
    }

    @Test
    fun sameLocalDayComparesCalendarDay() {
        withUtcZone {
            assertTrue(
                LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 0, 0),
                    utc(2026, Calendar.MAY, 15, 23, 59)
                )
            )
            assertFalse(
                LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 23, 59),
                    utc(2026, Calendar.MAY, 16, 0, 0)
                )
            )
        }
    }

    @Test
    fun sameLocalDayRejectsDifferentEraAndYear() {
        withUtcZone {
            val ad = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            ad.clear()
            ad.set(Calendar.ERA, GregorianCalendar.AD)
            ad.set(Calendar.YEAR, 2026)
            ad.set(Calendar.DAY_OF_YEAR, 1)
            val nextYear = ad.clone() as Calendar
            nextYear.add(Calendar.YEAR, 1)
            val bc = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            bc.clear()
            bc.set(Calendar.ERA, GregorianCalendar.BC)
            bc.set(Calendar.YEAR, 1)
            bc.set(Calendar.DAY_OF_YEAR, 1)

            assertFalse(LocalDayPolicy.sameLocalDay(ad.timeInMillis, nextYear.timeInMillis))
            assertFalse(LocalDayPolicy.sameLocalDay(ad.timeInMillis, bc.timeInMillis))
        }
    }

    @Test
    fun injectedTimeZoneOverridesDefault() {
        // Default TZ is UTC, but the injected Tokyo zone (UTC+9) must win: 2026-05-15
        // 20:00 UTC is 2026-05-16 05:00 in Tokyo, so its local day start differs.
        withUtcZone {
            val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
            val instant = utc(2026, Calendar.MAY, 15, 20, 0)
            assertEquals(
                zoned(tokyo, 2026, Calendar.MAY, 16, 0, 0),
                LocalDayPolicy.localDayStart(instant, tokyo),
            )
            assertFalse(
                LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 20, 0),
                    utc(2026, Calendar.MAY, 15, 12, 0),
                    tokyo,
                ),
            )
        }
    }

    @Test
    fun localDaysBetweenDefaultZoneOverloadUsesSystemZone() {
        withUtcZone {
            // Default-zone overload (no TimeZone arg) resolves to the system zone (UTC).
            assertEquals(
                2,
                LocalDayPolicy.localDaysBetween(
                    utc(2026, Calendar.MAY, 15, 12, 0),
                    utc(2026, Calendar.MAY, 17, 12, 0),
                ),
            )
        }
    }

    @Test
    fun localDaysBetweenCountsCalendarDaysNotTwentyFourHourBlocks() {
        val utcZone = TimeZone.getTimeZone("UTC")
        // 23h59m apart but crossing local midnight => one calendar day elapsed.
        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                utc(2026, Calendar.MAY, 15, 0, 1),
                utc(2026, Calendar.MAY, 16, 0, 0),
                utcZone,
            ),
        )
        // Same day => zero.
        assertEquals(
            0,
            LocalDayPolicy.localDaysBetween(
                utc(2026, Calendar.MAY, 15, 1, 0),
                utc(2026, Calendar.MAY, 15, 23, 0),
                utcZone,
            ),
        )
        // Backwards clock clamps to zero.
        assertEquals(
            0,
            LocalDayPolicy.localDaysBetween(
                utc(2026, Calendar.MAY, 16, 0, 0),
                utc(2026, Calendar.MAY, 15, 0, 0),
                utcZone,
            ),
        )
    }

    @Test
    fun localDaysBetweenHandlesDstSpringForwardAndFallBack() {
        val eastern = TimeZone.getTimeZone("America/New_York")
        // Spring forward: 2026-03-08 is a 23-hour day in US Eastern. Two calendar days
        // from Mar 7 noon to Mar 9 noon must count as 2, not be under-counted by the
        // missing hour.
        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                zoned(eastern, 2026, Calendar.MARCH, 7, 12, 0),
                zoned(eastern, 2026, Calendar.MARCH, 9, 12, 0),
                eastern,
            ),
        )
        // Fall back: 2026-11-01 is a 25-hour day. Oct 31 noon to Nov 2 noon still counts
        // as 2 calendar days despite the extra hour.
        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                zoned(eastern, 2026, Calendar.OCTOBER, 31, 12, 0),
                zoned(eastern, 2026, Calendar.NOVEMBER, 2, 12, 0),
                eastern,
            ),
        )
        // A crossing of the spring-forward midnight still counts as one day.
        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                zoned(eastern, 2026, Calendar.MARCH, 8, 1, 0),
                zoned(eastern, 2026, Calendar.MARCH, 9, 1, 0),
                eastern,
            ),
        )
    }

    private fun zoned(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, month, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    private fun withUtcZone(body: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            body()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
