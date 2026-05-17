package dev.bee.kanjianki.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyQueueMutationGateTest {
    @Test
    fun roomGateSerializesMutations() = runBlocking {
        val gate = RoomStudyQueueMutationGate()
        val events = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async {
            gate.mutate {
                events += "first-entered"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-released"
                "first"
            }
        }
        firstEntered.await()

        val second = async {
            gate.mutate {
                events += "second-entered"
                secondEntered.complete(Unit)
                "second"
            }
        }

        assertNull(withTimeoutOrNull(50) { secondEntered.await() })
        releaseFirst.complete(Unit)

        assertEquals("first", first.await())
        assertEquals("second", second.await())
        assertEquals(listOf("first-entered", "first-released", "second-entered"), events)
    }
}
