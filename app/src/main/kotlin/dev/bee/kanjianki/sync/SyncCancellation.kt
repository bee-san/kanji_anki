package dev.bee.kanjianki.sync

/**
 * Cooperative cancellation signal for a sync run. The provider/SQLite calls a sync
 * makes ignore thread interruption, so cancellation is checked at safe points (e.g.
 * between note batches) and turned into a retryable failure rather than relying on
 * [java.util.concurrent.ExecutorService.shutdownNow].
 */
fun interface SyncCancellation {
    fun isStopped(): Boolean

    companion object {
        @JvmField
        val NONE: SyncCancellation = SyncCancellation { false }
    }
}
