package dev.bee.kanjianki.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAheadPolicyTest {
    private val policy = StudyAheadPolicy()

    @Test
    fun clampMatchesLegacyBounds() {
        assertEquals(0L, policy.clamp(-5L * MINUTE_MILLIS))
        assertEquals(0L, policy.clamp(0L))
        assertEquals(15L * MINUTE_MILLIS, policy.clamp(15L * MINUTE_MILLIS))
        assertEquals(StudyAheadPolicy.DAY_MILLIS, policy.clamp(48L * HOUR_MILLIS))
    }

    @Test
    fun horizonAddsOnlyClampedAheadWindow() {
        val now = 1_000_000L

        assertEquals(now, policy.horizon(now, -1L))
        assertEquals(now + 15L * MINUTE_MILLIS, policy.horizon(now, 15L * MINUTE_MILLIS))
        assertEquals(now + StudyAheadPolicy.DAY_MILLIS, policy.horizon(now, 48L * HOUR_MILLIS))
    }

    @Test
    fun dueWithinHorizonIncludesBoundaryAndExcludesBeyond() {
        val now = 1_000_000L
        val fifteenMinutes = 15L * MINUTE_MILLIS

        assertTrue(policy.isDueWithinHorizon(now, now, studyAheadMillis = 0L))
        assertTrue(policy.isDueWithinHorizon(now + fifteenMinutes, now, fifteenMinutes))
        assertFalse(policy.isDueWithinHorizon(now + fifteenMinutes + 1L, now, fifteenMinutes))
    }

    @Test
    fun customMaximumCanBeUsedByTestsOrFutureSettings() {
        val fiveMinutePolicy = StudyAheadPolicy(maxStudyAheadMillis = 5L * MINUTE_MILLIS)

        assertEquals(5L * MINUTE_MILLIS, fiveMinutePolicy.clamp(15L * MINUTE_MILLIS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun maximumMustBePositive() {
        StudyAheadPolicy(maxStudyAheadMillis = 0L)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60L * MINUTE_MILLIS
    }
}
