package dev.bee.kanjianki.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface StudyQueueMutationGate {
    suspend fun <T> mutate(block: suspend () -> T): T
}

class RoomStudyQueueMutationGate : StudyQueueMutationGate {
    private val mutex = Mutex()

    override suspend fun <T> mutate(block: suspend () -> T): T =
        mutex.withLock {
            block()
        }
}
