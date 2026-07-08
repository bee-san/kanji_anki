package dev.bee.kanjianki.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Goal 46: the tracker is written on the io thread and read on the main thread.
 * Racing writes against reads must never throw (e.g.
 * ConcurrentModificationException from iterating the key sets) and reads must
 * always observe a consistent completed <= target invariant.
 */
class StudySessionProgressTrackerConcurrencyTest {
    @Test
    fun concurrentWritesAndReadsStayConsistentAndDoNotThrow() {
        repeat(50) { iteration ->
            val tracker = StudySessionProgressTracker()
            tracker.setTargetCount(0)
            val error = AtomicReference<Throwable?>(null)
            val start = CountDownLatch(1)
            val n = 400

            val writer = Thread {
                start.await()
                try {
                    for (i in 0 until n) {
                        val key = "session:kanji_meaning:字$i:tok$i"
                        tracker.includePendingTask(key)
                        tracker.markTaskCompleted(key)
                        tracker.recordReviewOutcome("字$i", "good", null, null)
                    }
                } catch (t: Throwable) {
                    error.compareAndSet(null, t)
                }
            }
            val reader = Thread {
                start.await()
                try {
                    for (i in 0 until n) {
                        // These reads iterate the key sets internally.
                        tracker.completedTaskBreakdown()
                        val completed = tracker.completedCount()
                        val target = tracker.targetCount()
                        assertTrue("completed=$completed target=$target", completed <= target)
                        tracker.movedForwardCount()
                        tracker.topBarProgress(true, false)
                    }
                } catch (t: Throwable) {
                    error.compareAndSet(null, t)
                }
            }

            writer.start()
            reader.start()
            start.countDown()
            writer.join(TimeUnit.SECONDS.toMillis(10))
            reader.join(TimeUnit.SECONDS.toMillis(10))

            val thrown = error.get()
            if (thrown != null) {
                throw AssertionError("iteration $iteration threw", thrown)
            }
        }
    }
}
