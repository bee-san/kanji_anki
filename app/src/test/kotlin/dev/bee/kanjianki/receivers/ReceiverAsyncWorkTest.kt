package dev.bee.kanjianki.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiverAsyncWorkTest {
    private class NoopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }

    @Test
    fun runsWorkSynchronouslyWhenNotDispatchingBroadcast() {
        // goAsync() returns null outside an active broadcast, so work runs inline.
        val ran = AtomicInteger(0)
        val executorCalls = AtomicInteger(0)
        val executor = Executor { command ->
            executorCalls.incrementAndGet()
            command.run()
        }

        ReceiverAsyncWork.run(NoopReceiver(), executor) { ran.incrementAndGet() }

        assertEquals(1, ran.get())
        assertEquals(0, executorCalls.get())
    }
}
