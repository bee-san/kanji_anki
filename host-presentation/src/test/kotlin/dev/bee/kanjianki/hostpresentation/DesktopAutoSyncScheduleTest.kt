package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAutoSyncScheduleTest {
    private val config = DesktopAutoSyncSchedule.Config(enabled = true, hour = 3, minute = 0)

    @Test
    fun disabledOrInvalidConfigNeverSyncsOrWakes() {
        for (bad in listOf(config.copy(enabled = false), config.copy(hour = -1), config.copy(minute = 99))) {
            val tick = DesktopAutoSyncSchedule.tick(bad, NOW, lastSuccessAtMillis = null)
            assertFalse(bad.toString(), tick.syncNow)
            assertNull(bad.toString(), tick.nextWakeAtMillis)
        }
    }

    @Test
    fun afterTheTriggerWithNoSuccessSinceItSyncsAndSchedulesTheNextWake() {
        val justAfter = mostRecentTrigger(NOW) + 60_000L
        val tick = DesktopAutoSyncSchedule.tick(config, justAfter, lastSuccessAtMillis = null)

        assertTrue(tick.syncNow)
        assertTrue((tick.nextWakeAtMillis ?: 0L) > justAfter)
    }

    @Test
    fun aSuccessSinceThisTriggerStopsItFromSyncingAgain() {
        val justAfter = mostRecentTrigger(NOW) + 60_000L
        val tick = DesktopAutoSyncSchedule.tick(config, justAfter, lastSuccessAtMillis = justAfter)
        assertFalse(tick.syncNow)
    }

    @Test
    fun aWakeThatMissedTheTriggerWhileAsleepSyncsOnce() {
        val justAfter = mostRecentTrigger(NOW) + 60_000L
        val twoDaysAgo = justAfter - 2L * 24 * 60 * 60 * 1000
        val tick = DesktopAutoSyncSchedule.tick(config, justAfter, lastSuccessAtMillis = twoDaysAgo)
        assertTrue(tick.syncNow)

        // Having just synced, a follow-up tick does not re-sync.
        val settled = DesktopAutoSyncSchedule.tick(config, justAfter, lastSuccessAtMillis = justAfter)
        assertFalse(settled.syncNow)
    }

    @Test
    fun havingSyncedTodayTheNextWakeIsTomorrowsTrigger() {
        val justAfter = mostRecentTrigger(NOW) + 60_000L
        val tick = DesktopAutoSyncSchedule.tick(config, justAfter, lastSuccessAtMillis = justAfter)
        // Next wake is strictly after now and after today's already-passed trigger.
        assertTrue((tick.nextWakeAtMillis ?: 0L) > justAfter)
    }

    private fun mostRecentTrigger(nowMillis: Long): Long =
        AutoSyncSchedulePolicy.nextTriggerMillis(config.hour, config.minute, nowMillis - 24L * 60 * 60 * 1000, false)

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
