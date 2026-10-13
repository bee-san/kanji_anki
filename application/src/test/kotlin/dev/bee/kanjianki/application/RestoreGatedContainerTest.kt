package dev.bee.kanjianki.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RestoreGatedContainerTest {
    @Test
    fun successfulStartupRestoresBeforeCreatingAndClosesOnce() {
        val events = mutableListOf<String>()
        val container = RecordingContainer(events)
        val owner = owner(events, result = "allowed", container = { container })

        assertEquals("allowed", owner.start())
        assertSame(container, owner.container)
        owner.close()
        owner.close()

        assertEquals(listOf("restore", "create", "close"), events)
        assertThrows(IllegalStateException::class.java) { owner.container }
    }

    @Test
    fun blockedRestoreNeverConstructsAContainerOrAllowsRetry() {
        val events = mutableListOf<String>()
        val owner = owner(events, result = "blocked")

        val failure = assertThrows(IllegalStateException::class.java) { owner.start() }

        assertEquals("blocked: blocked", failure.message)
        assertEquals(listOf("restore"), events)
        assertThrows(IllegalStateException::class.java) { owner.container }
        assertThrows(IllegalStateException::class.java) { owner.start() }
    }

    @Test
    fun containerConstructionFailureLeavesNoOwnedResource() {
        val events = mutableListOf<String>()
        val failure = IllegalArgumentException("create")
        val owner = owner(
            events,
            result = "allowed",
            container = { throw failure },
        )

        assertSame(
            failure,
            assertThrows(IllegalArgumentException::class.java) { owner.start() },
        )
        assertThrows(IllegalStateException::class.java) { owner.container }
        owner.close()
        assertEquals(listOf("restore", "create"), events)
    }

    @Test
    fun closeSuppressingPreservesTheStartupFailure() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val owner = owner(
            events,
            result = "allowed",
            container = { RecordingContainer(events, closeFailure) },
        )
        owner.start()
        val startupFailure = IllegalArgumentException("startup")

        owner.closeSuppressing(startupFailure)

        assertEquals(listOf(closeFailure), startupFailure.suppressed.toList())
        assertEquals(listOf("restore", "create", "close"), events)
    }

    @Test
    fun closeSuppressingDoesNotSelfSuppress() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("same")
        val owner = owner(
            events,
            result = "allowed",
            container = { RecordingContainer(events, failure) },
        )
        owner.start()

        owner.closeSuppressing(failure)

        assertEquals(emptyList<Throwable>(), failure.suppressed.toList())
    }

    @Test
    fun directClosePropagatesFailureAfterDroppingOwnership() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("close")
        val owner = owner(
            events,
            result = "allowed",
            container = { RecordingContainer(events, failure) },
        )
        owner.start()

        assertSame(failure, assertThrows(IllegalStateException::class.java) { owner.close() })
        owner.close()
        assertThrows(IllegalStateException::class.java) { owner.container }
    }

    private fun owner(
        events: MutableList<String>,
        result: String,
        container: () -> RecordingContainer = { RecordingContainer(events) },
    ): RestoreGatedContainer<String, RecordingContainer> = RestoreGatedContainer(
        restore = {
            events += "restore"
            result
        },
        allowsStartup = { it == "allowed" },
        blockedMessage = { "blocked: $it" },
        createContainer = {
            events += "create"
            container()
        },
    )

    private class RecordingContainer(
        private val events: MutableList<String>,
        private val closeFailure: RuntimeException? = null,
    ) : AutoCloseable {
        override fun close() {
            events += "close"
            closeFailure?.let { throw it }
        }
    }
}
