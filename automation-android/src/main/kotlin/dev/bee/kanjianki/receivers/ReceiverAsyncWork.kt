package dev.bee.kanjianki.receivers

import android.content.BroadcastReceiver
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
        run(receiver, { executor }, work)
    }

    fun run(
        receiver: BroadcastReceiver,
        executorProvider: () -> Executor,
        work: () -> Unit,
    ) {
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
        val executor = try {
            executorProvider()
        } catch (error: RuntimeException) {
            logError("Could not resolve async receiver work.", error)
            pending.finish()
            return
        }
        dispatch(executor, work) { pending.finish() }
    }

    internal fun dispatch(executor: Executor, work: () -> Unit, onFinished: () -> Unit) {
        val finished = AtomicBoolean(false)
        val finishOnce = {
            if (finished.compareAndSet(false, true)) {
                try {
                    onFinished()
                } catch (error: RuntimeException) {
                    logError("Could not finish async receiver work.", error)
                }
            }
        }
        val task = Runnable {
            try {
                work()
            } catch (error: Exception) {
                logError("Async receiver work failed.", error)
            } finally {
                finishOnce()
            }
        }
        try {
            executor.execute(task)
        } catch (error: RuntimeException) {
            logError("Could not dispatch async receiver work.", error)
            finishOnce()
        }
    }

    private fun logError(message: String, error: Throwable) {
        try {
            Log.e(TAG, message, error)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }
}
