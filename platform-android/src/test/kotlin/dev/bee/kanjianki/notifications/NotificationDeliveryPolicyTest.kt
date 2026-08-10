package dev.bee.kanjianki.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryPolicyTest {
    @Test
    fun attemptReportsAcceptedSubmission() {
        var invoked = false

        val accepted = NotificationDeliveryPolicy.attempt { invoked = true }

        assertTrue(accepted)
        assertTrue(invoked)
    }

    @Test
    fun attemptReportsPermissionRaceAsRejectedSubmission() {
        val accepted = NotificationDeliveryPolicy.attempt {
            throw SecurityException("permission changed")
        }

        assertFalse(accepted)
    }

    @Test(expected = IllegalStateException::class)
    fun attemptDoesNotHideProgrammingFailures() {
        NotificationDeliveryPolicy.attempt {
            throw IllegalStateException("broken notification")
        }
    }
}
