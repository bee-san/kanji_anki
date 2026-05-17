package dev.bee.kanjianki.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTokenGuardTest {
    private val guard = ReviewTokenGuard()

    @Test
    fun matchingActiveTokenIsAcceptedAndConsumed() {
        val result = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = "token-1",
                activeToken = "token-1",
                consumedTokens = setOf("older"),
            ),
        )

        assertTrue(result.accepted)
        assertNull(result.reason)
        assertEquals("Review token accepted.", result.message)
        assertEquals(setOf("older", "token-1"), result.consumedTokens)
    }

    @Test
    fun consumedTokenIsRejectedBeforeActiveTokenMismatch() {
        val result = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = "token-1",
                activeToken = "different",
                consumedTokens = setOf("token-1"),
            ),
        )

        assertFalse(result.accepted)
        assertEquals(ReviewTokenRejectionReason.ALREADY_CONSUMED, result.reason)
        assertEquals("Review token already consumed.", result.message)
        assertEquals(setOf("token-1"), result.consumedTokens)
    }

    @Test
    fun activeTokenMismatchIsRejectedWithoutConsumingRequestToken() {
        val result = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = "actual",
                activeToken = "expected",
                consumedTokens = setOf("older"),
            ),
        )

        assertFalse(result.accepted)
        assertEquals(ReviewTokenRejectionReason.ACTIVE_SESSION_MISMATCH, result.reason)
        assertEquals("Review token does not match the active session.", result.message)
        assertEquals(setOf("older"), result.consumedTokens)
    }

    @Test
    fun emptyActiveTokenDoesNotRejectReview() {
        val result = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = "token-1",
                activeToken = "",
            ),
        )

        assertTrue(result.accepted)
        assertEquals(setOf("token-1"), result.consumedTokens)
    }

    @Test
    fun nullRequestTokenIsNormalizedToEmptyToken() {
        val first = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = null,
                activeToken = null,
            ),
        )
        val second = guard.evaluate(
            ReviewTokenGuardInput(
                requestToken = "",
                activeToken = null,
                consumedTokens = first.consumedTokens,
            ),
        )

        assertTrue(first.accepted)
        assertEquals(setOf(""), first.consumedTokens)
        assertFalse(second.accepted)
        assertEquals(ReviewTokenRejectionReason.ALREADY_CONSUMED, second.reason)
    }
}
