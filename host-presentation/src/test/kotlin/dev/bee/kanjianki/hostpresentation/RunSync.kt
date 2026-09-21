package dev.bee.kanjianki.hostpresentation

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs a suspending block that must complete without suspending, and returns its result.
 *
 * This module's tests deliberately have no `kotlinx-coroutines-test` dependency: every
 * suspend function here is suspend only because its callee is, and none of them actually
 * suspend when handed fakes. So the requirement is the assertion — a block that *did*
 * suspend means a fake started doing real asynchronous work, and failing on that is more
 * useful than a dispatcher quietly draining it.
 */
internal fun <T> runSync(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return requireNotNull(outcome) { "the block did not complete synchronously" }.getOrThrow()
}
