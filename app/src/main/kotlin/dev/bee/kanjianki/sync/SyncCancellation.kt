package dev.bee.kanjianki.sync

import dev.bee.kanjianki.syncapi.CollectionCancellation

/**
 * Cooperative cancellation signal for a sync run. The provider/SQLite calls a sync
 * makes ignore thread interruption, so cancellation is checked at safe points (e.g.
 * between note batches) and turned into a retryable failure rather than relying on
 * [java.util.concurrent.ExecutorService.shutdownNow].
 */
fun interface SyncCancellation : CollectionCancellation {
    fun isStopped(): Boolean

    override fun isCancelled(): Boolean = isStopped()

    companion object {
        @JvmField
        val NONE: SyncCancellation = SyncCancellation { false }
    }
}
