package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreResultTest {

    @Test
    fun okHoldsValue() {
        val result: StoreResult<Int> = StoreResult.ok(42)
        assertTrue(result.isOk())
        assertEquals(42, result.valueOrNull())
    }

    @Test
    fun transientErrorIsNotOk() {
        val result: StoreResult<Int> = StoreResult.transient(RuntimeException("busy"))
        assertFalse(result.isOk())
        assertNull(result.valueOrNull())
        assertTrue(result is StoreResult.TransientError)
    }

    @Test
    fun permanentErrorIsNotOk() {
        val result: StoreResult<Int> = StoreResult.permanent(IllegalStateException("closed"))
        assertFalse(result.isOk())
        assertNull(result.valueOrNull())
        assertTrue(result is StoreResult.PermanentError)
    }

    @Test
    fun transientErrorPreservesCause() {
        val cause = RuntimeException("db locked")
        val result = StoreResult.TransientError(cause)
        assertEquals(cause, result.cause)
    }

    @Test
    fun permanentErrorPreservesCause() {
        val cause = IllegalStateException("closed")
        val result = StoreResult.PermanentError(cause)
        assertEquals(cause, result.cause)
    }
}
