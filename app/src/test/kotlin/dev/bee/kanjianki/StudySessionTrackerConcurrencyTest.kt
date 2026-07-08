package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Goal 46: io-thread task completion / review outcomes racing main-thread
 * pause/resume and completedTaskBreakdown reads must never throw and must keep
 * completed <= target at every read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudySessionTrackerConcurrencyTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun concurrentCompleteAndReadsStayConsistentAndDoNotThrow() {
        repeat(20) { iteration ->
            val tracker = StudySessionTracker()
            tracker.setTargetCount(0)
            val error = AtomicReference<Throwable?>(null)
            val start = CountDownLatch(1)
            val n = 200

            val ioThread = Thread {
                start.await()
                try {
                    for (i in 0 until n) {
                        val key = "session:kanji_meaning:字$i:tok$i"
                        tracker.startActiveTask(key, "字$i", "kanji_meaning", i.toLong(), true)
                        tracker.recordReviewOutcome("字$i", "good", null, null)
                        tracker.completeActiveTask(store, key, "good", (i + 1).toLong(), true)
                    }
                } catch (t: Throwable) {
                    error.compareAndSet(null, t)
                }
            }
            val mainThread = Thread {
                start.await()
                try {
                    for (i in 0 until n) {
                        tracker.pauseActiveTask()
                        tracker.completedTaskBreakdown()
                        val completed = tracker.completedCount()
                        val target = tracker.targetCount()
                        assertTrue("completed=$completed target=$target", completed <= target)
                        tracker.resumeActiveTask()
                    }
                } catch (t: Throwable) {
                    error.compareAndSet(null, t)
                }
            }

            ioThread.start()
            mainThread.start()
            start.countDown()
            ioThread.join(TimeUnit.SECONDS.toMillis(15))
            mainThread.join(TimeUnit.SECONDS.toMillis(15))

            val thrown = error.get()
            if (thrown != null) {
                throw AssertionError("iteration $iteration threw", thrown)
            }
        }
    }
}
