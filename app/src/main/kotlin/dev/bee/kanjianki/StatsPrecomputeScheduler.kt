package dev.bee.kanjianki

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class StatsPrecomputeScheduler(
    private val background: Executor,
    private val isFresh: () -> Boolean,
    private val refresh: (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private val running = AtomicBoolean(false)
    @Volatile
    private var lastScheduledAtMillis: Long = Long.MIN_VALUE

    fun scheduleIfStale(): Boolean {
        if (isFresh()) {
            return false
        }
        val now = clock()
        if (!claim(now)) {
            return false
        }
        background.execute {
            try {
                if (!isFresh()) {
                    refresh(clock())
                }
            } finally {
                running.set(false)
            }
        }
        return true
    }

    private fun claim(now: Long): Boolean {
        synchronized(this) {
            if (running.get()) {
                return false
            }
            if (lastScheduledAtMillis != Long.MIN_VALUE && now - lastScheduledAtMillis < minIntervalMillis) {
                return false
            }
            running.set(true)
            lastScheduledAtMillis = now
            return true
        }
    }

    private companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 60_000L
    }
}
