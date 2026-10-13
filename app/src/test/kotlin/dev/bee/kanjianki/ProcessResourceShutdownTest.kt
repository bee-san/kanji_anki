package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ProcessResourceShutdownTest {
    @Test
    fun closesEveryResourceInDeclaredOrderExactlyOnce() {
        val events = mutableListOf<String>()
        val shutdown = ProcessResourceShutdown(
            { events += "user-io" },
            { events += "maintenance" },
            { events += "store" },
        )

        shutdown.close()
        shutdown.close()

        assertEquals(listOf("user-io", "maintenance", "store"), events)
    }

    @Test
    fun firstFailureIsThrownAndLaterFailuresAreSuppressed() {
        val events = mutableListOf<String>()
        val first = IllegalStateException("user-io")
        val second = IllegalArgumentException("store")
        val shutdown = ProcessResourceShutdown(
            {
                events += "user-io"
                throw first
            },
            { events += "maintenance" },
            {
                events += "store"
                throw second
            },
        )

        val actual = assertThrows(IllegalStateException::class.java) { shutdown.close() }

        assertSame(first, actual)
        assertEquals(listOf(second), actual.suppressed.toList())
        assertEquals(listOf("user-io", "maintenance", "store"), events)
    }

    @Test
    fun sameFailureInstanceIsNotSelfSuppressed() {
        val failure = IllegalStateException("shared")
        val shutdown = ProcessResourceShutdown(
            { throw failure },
            { throw failure },
        )

        assertSame(failure, assertThrows(IllegalStateException::class.java) { shutdown.close() })
        assertEquals(emptyList<Throwable>(), failure.suppressed.toList())
    }
}
