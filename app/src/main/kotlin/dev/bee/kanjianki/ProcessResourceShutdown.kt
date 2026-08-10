package dev.bee.kanjianki

import java.util.concurrent.atomic.AtomicBoolean

/** Runs process-resource shutdown steps once, preserving the first failure. */
internal class ProcessResourceShutdown(
    private vararg val steps: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var firstFailure: Throwable? = null
        steps.forEach { step ->
            try {
                step()
            } catch (failure: Throwable) {
                val primary = firstFailure
                if (primary == null) {
                    firstFailure = failure
                } else if (primary !== failure) {
                    primary.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }
}
