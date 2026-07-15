package dev.bee.kanjianki

import java.util.concurrent.ExecutorService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Coroutine dispatchers wrapping the activity's existing executor pools so
 * coroutine-based code shares the same threads (and the same ordering
 * guarantees) as the legacy `execute { }` call sites. The single-threaded io
 * dispatcher preserves LocalStore's serialized-access assumption; converting a
 * call site from `io.execute { }` to `withContext(dispatchers.io) { }` cannot
 * introduce concurrent database access that the executor version prevented.
 *
 * Scoped per activity because the underlying executors are activity-owned and
 * shut down in onDestroy. Coroutines launched in `lifecycleScope` are
 * cancelled by the lifecycle before the executors shut down.
 */
internal class KaniDispatchers(
    io: ExecutorService,
    maintenance: ExecutorService,
) {
    val io: CoroutineDispatcher = io.asCoroutineDispatcher()
    val maintenance: CoroutineDispatcher = maintenance.asCoroutineDispatcher()
}
