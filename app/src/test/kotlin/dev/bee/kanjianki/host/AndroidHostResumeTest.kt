package dev.bee.kanjianki.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a resume does its background work, and when it stays out of the way.
 *
 * A pure decision, tested without an alarm manager or a notification manager, which is the
 * point of splitting it out: the throttle and the harness gate are the parts that can be
 * wrong, and both are invisible in an instrumented run — a re-arm that fires every resume
 * looks identical to one that fires correctly except in battery stats.
 */
class AndroidHostResumeTest {
    @Test
    fun theFirstResumeDoesBothPiecesOfWork() {
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { true }, nowMillis = { now })

        val actions = resume.onResume()

        assertTrue(actions.cancelPostedReminder)
        assertTrue(actions.rearmReminder)
    }

    @Test
    fun aQuickReturnStillClearsTheNotificationButDoesNotReArm() {
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { true }, nowMillis = { now })
        resume.onResume()

        now += AndroidHostResume.RESUME_REARM_THROTTLE_MILLIS - 1
        val actions = resume.onResume()

        // App-switching must not recompute the alarm on every return, but the posted
        // reminder still has to go: the user is here, so leaving it in the shade is wrong
        // regardless of how recently they were last here.
        assertTrue("a posted reminder is always cleared", actions.cancelPostedReminder)
        assertFalse("a re-arm inside the throttle window is skipped", actions.rearmReminder)
    }

    @Test
    fun aReturnAfterTheThrottleWindowReArmsAgain() {
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { true }, nowMillis = { now })
        resume.onResume()

        now += AndroidHostResume.RESUME_REARM_THROTTLE_MILLIS
        val actions = resume.onResume()

        // A user who changes a reminder setting and comes back sees it take effect in the
        // same sitting; that is what bounds the throttle above.
        assertTrue(actions.rearmReminder)
    }

    @Test
    fun theThrottleWindowRunsFromTheLastReArmNotTheLastResume() {
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { true }, nowMillis = { now })
        resume.onResume()

        // Several skipped resumes inside the window must not each push the deadline out,
        // or a user switching apps steadily would never get a re-arm at all.
        repeat(3) {
            now += AndroidHostResume.RESUME_REARM_THROTTLE_MILLIS / 4
            resume.onResume()
        }
        now += AndroidHostResume.RESUME_REARM_THROTTLE_MILLIS / 4
        val actions = resume.onResume()

        assertTrue(actions.rearmReminder)
    }

    @Test
    fun aHarnessLaunchDoesNoBackgroundWorkAtAll() {
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { false }, nowMillis = { now })

        val actions = resume.onResume()

        // A screenshot or benchmark launch must not arm an alarm or touch the shade: the
        // run is supposed to observe one screen, and either side effect outlives it.
        assertFalse(actions.cancelPostedReminder)
        assertFalse(actions.rearmReminder)
    }

    @Test
    fun aRefusedResumeDoesNotConsumeTheThrottle() {
        var allowed = false
        var now = 1_000L
        val resume = AndroidHostResume(backgroundWorkAllowed = { allowed }, nowMillis = { now })

        resume.onResume()
        allowed = true
        val actions = resume.onResume()

        // The gate is checked before the clock, so a harness resume cannot silently spend
        // the first real resume's re-arm — which would leave the alarm unarmed for three
        // minutes after a genuine launch.
        assertTrue(actions.rearmReminder)
    }
}
