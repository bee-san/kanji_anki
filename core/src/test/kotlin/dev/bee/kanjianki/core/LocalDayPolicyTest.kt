package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.SimpleTimeZone
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

    @Test
    fun localDaysBetweenSupportsCustomSimpleTimeZoneIds() {
        val custom = SimpleTimeZone(9 * 60 * 60 * 1000, "Kani/CustomTokyo")

        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                zoned(custom, 2026, Calendar.MAY, 15, 23, 0),
                zoned(custom, 2026, Calendar.MAY, 17, 1, 0),
                custom,
            ),
        )
    }

    @Test
    fun localDaysBetweenUsesSimpleTimeZoneRulesEvenWhenItsIdExists() {
        val customUtc = SimpleTimeZone(0, "Pacific/Apia")

        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                zoned(customUtc, 2011, Calendar.DECEMBER, 29, 12, 0),
                zoned(customUtc, 2011, Calendar.DECEMBER, 31, 12, 0),
                customUtc,
            ),
        )
    }

    @Test
    fun localDaysBetweenUsesArbitraryCustomTimeZoneRulesEvenWhenItsIdExists() {
        val customUtc = FixedOffsetTimeZone(0, "Asia/Tokyo")

        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                utc(2026, Calendar.MAY, 15, 23, 0),
                utc(2026, Calendar.MAY, 16, 1, 0),
                customUtc,
            ),
        )
    }

    @Test
    fun localDaysBetweenUsesUnknownCustomTimeZoneTransitions() {
        val custom = DelegatingTimeZone(longJumpSimpleTimeZone(24), "Kani/UnknownTransition")

        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                zoned(custom, 2026, Calendar.FEBRUARY, 28, 12, 0),
                zoned(custom, 2026, Calendar.MARCH, 2, 12, 0),
                custom,
            ),
        )
    }

    @Test
    fun localDaysBetweenUsesCustomTransitionsWhenCustomTimeZoneIdExists() {
        val custom = DelegatingTimeZone(longJumpSimpleTimeZone(24), "Asia/Tokyo")

        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                zoned(custom, 2026, Calendar.FEBRUARY, 28, 12, 0),
                zoned(custom, 2026, Calendar.MARCH, 2, 12, 0),
                custom,
            ),
        )
    }

    @Test
    fun localDaysBetweenUsesTimeZoneHistoricalRulesInsteadOfZoneIdRules() {
        val rarotonga = TimeZone.getTimeZone("Pacific/Rarotonga")

        assertEquals(
            4,
            LocalDayPolicy.localDaysBetween(
                zoned(rarotonga, 1899, Calendar.DECEMBER, 24, 12, 0),
                zoned(rarotonga, 1899, Calendar.DECEMBER, 28, 12, 0),
                rarotonga,
            ),
        )
    }

    @Test
    fun localDaysBetweenCountsOnlyExistingMidnightsAcrossApiaSkippedDate() {
        val apia = TimeZone.getTimeZone("Pacific/Apia")
        val beforeSkip = zoned(apia, 2011, Calendar.DECEMBER, 29, 12, 0)

        assertEquals(
            1,
            LocalDayPolicy.localDaysBetween(
                beforeSkip,
                zoned(apia, 2011, Calendar.DECEMBER, 31, 12, 0),
                apia,
            ),
        )
        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                beforeSkip,
                zoned(apia, 2012, Calendar.JANUARY, 1, 12, 0),
                apia,
            ),
        )
    }

    @Test(timeout = 1_000L)
    fun localDaysBetweenDoesNotWalkLongRangeForFullDaySimpleTimeZoneDst() {
        val fullDayDst = SimpleTimeZone(
            0,
            "Kani/FullDayDst",
            Calendar.MARCH,
            1,
            0,
            0,
            Calendar.OCTOBER,
            1,
            0,
            0,
            24 * 60 * 60 * 1000,
        )

        assertEquals(
            Int.MAX_VALUE,
            LocalDayPolicy.localDaysBetween(Long.MIN_VALUE, Long.MAX_VALUE, fullDayDst),
        )
        assertEquals(
            2,
            LocalDayPolicy.localDaysBetween(
                zoned(fullDayDst, 2026, Calendar.FEBRUARY, 28, 12, 0),
                zoned(fullDayDst, 2026, Calendar.MARCH, 3, 12, 0),
                fullDayDst,
            ),
        )

        fullDayDst.setStartYear(2020)
        assertEquals(
            4_373,
            LocalDayPolicy.localDaysBetween(
                zoned(fullDayDst, 2018, Calendar.JANUARY, 1, 12, 0),
                zoned(fullDayDst, 2030, Calendar.JANUARY, 1, 12, 0),
                fullDayDst,
            ),
        )
    }

    @Test
    fun localDaysBetweenCountsSkippedDatesForLongSimpleTimeZoneDstJumps() {
        val twentyFiveHourDst = longJumpSimpleTimeZone(25)
        assertEquals(
            12,
            LocalDayPolicy.localDaysBetween(
                zoned(twentyFiveHourDst, 2026, Calendar.FEBRUARY, 25, 12, 0),
                zoned(twentyFiveHourDst, 2026, Calendar.MARCH, 10, 12, 0),
                twentyFiveHourDst,
            ),
        )

        val fortyNineHourDst = longJumpSimpleTimeZone(49)
        assertEquals(
            11,
            LocalDayPolicy.localDaysBetween(
                zoned(fortyNineHourDst, 2026, Calendar.FEBRUARY, 25, 12, 0),
                zoned(fortyNineHourDst, 2026, Calendar.MARCH, 10, 12, 0),
                fortyNineHourDst,
            ),
        )
    }

    @Test
    fun localDaysBetweenCountsSimpleTimeZoneSkippedDatesInProlepticYearZero() {
        val fullDayDst = longJumpSimpleTimeZone(24)
        assertEquals(
            319,
            LocalDayPolicy.localDaysBetween(
                zonedEra(fullDayDst, GregorianCalendar.BC, 1, Calendar.FEBRUARY, 25, 12, 0),
                zonedEra(fullDayDst, GregorianCalendar.AD, 1, Calendar.JANUARY, 10, 12, 0),
                fullDayDst,
            ),
        )

        val fortyNineHourDst = longJumpSimpleTimeZone(49)
        assertEquals(
            318,
            LocalDayPolicy.localDaysBetween(
                zonedEra(fortyNineHourDst, GregorianCalendar.BC, 1, Calendar.FEBRUARY, 25, 12, 0),
                zonedEra(fortyNineHourDst, GregorianCalendar.AD, 1, Calendar.JANUARY, 10, 12, 0),
                fortyNineHourDst,
            ),
        )
    }

    @Test
    fun localDaysBetweenHandlesExtremeTimestampsWithoutWalkingEveryDay() {
        assertEquals(
            Int.MAX_VALUE,
            LocalDayPolicy.localDaysBetween(Long.MIN_VALUE, Long.MAX_VALUE, TimeZone.getTimeZone("UTC")),
        )
    }

    private fun zoned(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, month, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    private fun zonedEra(
        zone: TimeZone,
        era: Int,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(Calendar.ERA, era)
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
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

    private fun longJumpSimpleTimeZone(dstHours: Int): SimpleTimeZone {
        return SimpleTimeZone(
            0,
            "Kani/${dstHours}HourDst",
            Calendar.MARCH,
            1,
            0,
            0,
            Calendar.OCTOBER,
            1,
            0,
            0,
            dstHours * 60 * 60 * 1000,
        )
    }

    private class DelegatingTimeZone(
        private val delegate: TimeZone,
        id: String,
    ) : TimeZone() {
        init {
            setID(id)
        }

        override fun getOffset(date: Long): Int = delegate.getOffset(date)

        override fun getOffset(
            era: Int,
            year: Int,
            month: Int,
            day: Int,
            dayOfWeek: Int,
            milliseconds: Int,
        ): Int = delegate.getOffset(era, year, month, day, dayOfWeek, milliseconds)

        override fun setRawOffset(offsetMillis: Int) {
            delegate.rawOffset = offsetMillis
        }

        override fun getRawOffset(): Int = delegate.rawOffset

        override fun useDaylightTime(): Boolean = delegate.useDaylightTime()

        override fun inDaylightTime(date: Date): Boolean = delegate.inDaylightTime(date)

        override fun getDSTSavings(): Int = delegate.dstSavings
    }

    private class FixedOffsetTimeZone(offsetMillis: Int, id: String) : TimeZone() {
        private var offset = offsetMillis

        init {
            setID(id)
        }

        override fun getOffset(
            era: Int,
            year: Int,
            month: Int,
            day: Int,
            dayOfWeek: Int,
            milliseconds: Int,
        ): Int = offset

        override fun setRawOffset(offsetMillis: Int) {
            offset = offsetMillis
        }

        override fun getRawOffset(): Int = offset

        override fun useDaylightTime(): Boolean = false

        override fun inDaylightTime(date: Date): Boolean = false
    }
}
