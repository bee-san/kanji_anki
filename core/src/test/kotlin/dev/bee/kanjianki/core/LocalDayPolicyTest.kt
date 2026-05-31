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
                LocalDayPolicy.localDayStart(utc(2026, Calendar.MAY, 15, 23, 59)),
            )
        }
    }

    @Test
    fun moveLocalDaysKeepsResultAtLocalMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 17, 0, 0),
                LocalDayPolicy.moveLocalDays(utc(2026, Calendar.MAY, 15, 18, 30), 2),
            )
        }
    }

    @Test
    fun nextLocalDayStartReturnsNextLocalMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 16, 0, 0),
                LocalDayPolicy.nextLocalDayStart(utc(2026, Calendar.MAY, 15, 18, 30)),
            )
        }
    }

    @Test
    fun sameLocalDayComparesCalendarDay() {
        withUtcZone {
            assertTrue(
                LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 0, 0),
                    utc(2026, Calendar.MAY, 15, 23, 59),
                ),
            )
            assertFalse(
                LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 23, 59),
                    utc(2026, Calendar.MAY, 16, 0, 0),
                ),
            )
        }
    }

    @Test
    fun sameLocalDayRejectsDifferentEraAndYear() {
        withUtcZone {
            val ad = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(Calendar.ERA, GregorianCalendar.AD)
                set(Calendar.YEAR, 2026)
                set(Calendar.DAY_OF_YEAR, 1)
            }
            val nextYear = ad.clone() as Calendar
            nextYear.add(Calendar.YEAR, 1)
            val bc = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(Calendar.ERA, GregorianCalendar.BC)
                set(Calendar.YEAR, 1)
                set(Calendar.DAY_OF_YEAR, 1)
            }

            assertFalse(LocalDayPolicy.sameLocalDay(ad.timeInMillis, nextYear.timeInMillis))
            assertFalse(LocalDayPolicy.sameLocalDay(ad.timeInMillis, bc.timeInMillis))
        }
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
