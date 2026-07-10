package dev.bee.kanjianki

import java.util.LinkedHashSet
import java.util.concurrent.Executor

/**
 * Coalesces reminder alarm refreshes and keeps their dashboard reads away from an in-flight
 * user-facing route load.
 *
 * Route callbacks may arrive on the main thread while review requests arrive on [MainActivityBase.io],
 * so all state is guarded by [lock]. A request made while a route is loading remains pending until
 * that exact, still-current request settles. Superseded route results never call [routeSettled], so
 * they cannot release work while the replacement route is still loading.
 */
internal class MainActivityReminderRearmCoordinator(
    private val executor: Executor,
    private val rearm: (reasons: Set<String>) -> Unit,
    private val onDispatchError: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private val pendingReasons = LinkedHashSet<String>()

    // Guarded by lock.
    private var currentRouteRequestId: Int? = null

    // Guarded by lock.
    private var hasSettledRoute = false

    // Guarded by lock.
    private var workerScheduledOrRunning = false

    fun routeRequested(requestId: Int) {
        synchronized(lock) {
            currentRouteRequestId = requestId
        }
    }

    /** Clears a canceled route without treating it as the first rendered/settled route. */
    fun routeCanceled(requestId: Int): Boolean {
        var shouldDispatch = false
        synchronized(lock) {
            if (currentRouteRequestId != requestId) {
                return false
            }
            currentRouteRequestId = null
            shouldDispatch = prepareDispatchLocked()
        }
        if (shouldDispatch) {
            dispatchWorker()
        }
        return true
    }

    /** Returns true only when [requestId] was the accepted in-flight route. */
    fun routeSettled(requestId: Int): Boolean {
        var shouldDispatch = false
        synchronized(lock) {
            if (currentRouteRequestId != requestId) {
                return false
            }
            currentRouteRequestId = null
            hasSettledRoute = true
            shouldDispatch = prepareDispatchLocked()
        }
        if (shouldDispatch) {
            dispatchWorker()
        }
        return true
    }

    fun request(reason: String) {
        val safeReason = reason.trim().ifEmpty { "unspecified" }
        var shouldDispatch = false
        synchronized(lock) {
            pendingReasons.add(safeReason)
            shouldDispatch = prepareDispatchLocked()
        }
        if (shouldDispatch) {
            dispatchWorker()
        }
    }

    private fun prepareDispatchLocked(): Boolean {
        if (
            workerScheduledOrRunning ||
            !hasSettledRoute ||
            currentRouteRequestId != null ||
            pendingReasons.isEmpty()
        ) {
            return false
        }
        workerScheduledOrRunning = true
        return true
    }

    private fun dispatchWorker() {
        try {
            executor.execute(::runWorker)
        } catch (error: RuntimeException) {
            synchronized(lock) {
                workerScheduledOrRunning = false
            }
            onDispatchError(error)
        }
    }

    private fun runWorker() {
        while (true) {
            val reasons: Set<String>
            synchronized(lock) {
                // A route can start after this worker was queued. Park the reminder read until
                // that route settles instead of competing with its model load.
                if (!hasSettledRoute || currentRouteRequestId != null) {
                    workerScheduledOrRunning = false
                    return
                }
                reasons = LinkedHashSet(pendingReasons)
                pendingReasons.clear()
            }

            rearm(reasons)

            synchronized(lock) {
                // Requests received during the re-arm collapse into one final pass. If a route
                // started meanwhile, leave them pending for routeSettled().
                if (pendingReasons.isEmpty() || currentRouteRequestId != null) {
                    workerScheduledOrRunning = false
                    return
                }
            }
        }
    }
}
