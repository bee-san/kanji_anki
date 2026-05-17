package dev.bee.kanjianki.domain.sync

import java.util.concurrent.atomic.AtomicBoolean

class SyncAlreadyRunningException(
    message: String = "Sync already running.",
) : IllegalStateException(message)

class SyncExecutionGate {
    private val running = AtomicBoolean(false)

    suspend fun <T> run(block: suspend () -> T): T {
        if (!running.compareAndSet(false, true)) {
            throw SyncAlreadyRunningException()
        }
        try {
            return block()
        } finally {
            running.set(false)
        }
    }
}
