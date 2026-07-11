package dev.bee.kanjianki

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal class StatsPrecomputeScheduler(
    private val background: Executor,
    private val isFresh: () -> Boolean,
    private val refresh: (Long) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private val running = AtomicBoolean(false)
    @Volatile
    private var lastScheduledAtMillis: Long = Long.MIN_VALUE

    fun scheduleIfStale(): Boolean {
        if (freshOrUnavailable()) {
            return false
        }
        val now = clock()
        if (!claim(now)) {
            return false
        }
        return try {
            background.execute {
                try {
                    if (!freshOrUnavailable()) {
                        refresh(clock())
                    }
                } catch (error: RuntimeException) {
                    report(error)
                } finally {
                    running.set(false)
                }
            }
            true
        } catch (error: RejectedExecutionException) {
            running.set(false)
            report(error)
            false
        }
    }

    /** A cache-read failure makes this optional precompute unavailable for this attempt. */
    private fun freshOrUnavailable(): Boolean {
        return try {
            isFresh()
        } catch (error: RuntimeException) {
            report(error)
            true
        }
    }

    private fun report(error: Throwable) {
        try {
            onError(error)
        } catch (_: RuntimeException) {
            // Optional analytics maintenance must never crash the app, including its logger.
        }
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
