package dev.bee.kanjianki.receivers

import android.content.BroadcastReceiver
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Helper for running a BroadcastReceiver's work off the main thread.
 *
 * `onReceive` runs on the main thread and receivers here do DB/file work, so we hold
 * the broadcast alive with [BroadcastReceiver.goAsync] and hand the work to a shared
 * background executor, calling `finish()` when it completes.
 */
object ReceiverAsyncWork {
    private const val TAG = "ReceiverAsyncWork"

    private val executor: Executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kani-receiver").apply { isDaemon = true }
    }

    @JvmStatic
    @JvmOverloads
    fun run(receiver: BroadcastReceiver, executor: Executor = this.executor, work: () -> Unit) {
        // goAsync() only returns a PendingResult while a real broadcast is being
        // dispatched. When it is null (e.g. onReceive invoked directly in a test), run
        // synchronously so behavior and assertions stay deterministic.
        val pending = try {
            receiver.goAsync()
        } catch (_: RuntimeException) {
            null
        }
        if (pending == null) {
            work()
            return
        }
        executor.execute {
            try {
                work()
            } catch (error: Exception) {
                Log.e(TAG, "Async receiver work failed.", error)
            } finally {
                pending.finish()
            }
        }
    }
}
