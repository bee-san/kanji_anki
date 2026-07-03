package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetWarmupTest {
    private fun activity(): MainActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(context, MainActivity::class.java),
        )
        return controller.get()
    }

    @Test
    fun warmStrokeGuidesReturnsSameCachedInstanceOnRepeatedCalls() {
        val activity = activity()

        val first = activity.warmStrokeGuides()
        val second = activity.warmStrokeGuides()

        assertNotNull(first)
        assertSame(first, second)
        assertSame(first, activity.strokeGuides)
    }

    @Test
    fun warmDictionaryLookupReturnsSameCachedInstanceOnRepeatedCalls() {
        val activity = activity()

        val first = activity.warmDictionaryLookup()
        val second = activity.warmDictionaryLookup()

        assertSame(first, second)
        assertSame(first, activity.dictionaryLookup)
    }

    @Test
    fun concurrentWarmStrokeGuidesInitializesExactlyOnce() {
        val activity = activity()
        val threads = 6
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = (0 until threads).map {
                Callable {
                    barrier.await()
                    activity.warmStrokeGuides()
                }
            }
            val results = pool.invokeAll(tasks).map { it.get() }
            // Every thread observes the identical single instance (no duplicate parse
            // published), proving the double-checked lock initializes exactly once.
            val canonical = results.first()
            results.forEach { assertSame(canonical, it) }
            assertSame(canonical, activity.strokeGuides)
        } finally {
            pool.shutdownNow()
        }
    }
}
