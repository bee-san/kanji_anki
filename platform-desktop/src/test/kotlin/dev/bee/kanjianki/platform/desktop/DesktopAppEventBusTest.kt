package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppEvent
import dev.bee.kanjianki.platform.AppEventType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAppEventBusTest {
    private fun event(
        type: AppEventType = AppEventType.SYNC_COMMITTED,
        at: Long = 1_700_000_000_000L,
    ) = AppEvent(type = type, occurredAtMillis = at)

    @Test
    fun deliversToEveryObserver() {
        val bus = DesktopAppEventBus()
        val seen = mutableListOf<String>()
        bus.observe { seen += "first:${it.type}" }
        bus.observe { seen += "second:${it.type}" }

        bus.publish(event(AppEventType.STUDY_COMMITTED))

        assertEquals(
            listOf("first:STUDY_COMMITTED", "second:STUDY_COMMITTED"),
            seen,
        )
    }

    @Test
    fun aThrowingObserverNeitherStopsTheOthersNorFailsThePublisher() {
        val failures = mutableListOf<Pair<AppEventType, String>>()
        val bus = DesktopAppEventBus(
            onObserverFailure = { failed, error ->
                failures += failed.type to (error.message ?: "")
            },
        )
        var laterObserverRan = false
        bus.observe { throw IllegalStateException("stats refresh failed") }
        bus.observe { laterObserverRan = true }

        // Publish happens at the end of a committed transaction; if this threw, a
        // committed sync would be reported to the user as a failed one.
        bus.publish(event())

        assertTrue("a failure must not stop later observers", laterObserverRan)
        assertEquals(
            listOf(AppEventType.SYNC_COMMITTED to "stats refresh failed"),
            failures,
        )
    }

    @Test
    fun aThrowingFailureReporterIsAlsoContained() {
        // The reporter is host-supplied. If it throws, there is nowhere left to send the
        // failure, and propagating would defeat the isolation the bus exists to give.
        val bus = DesktopAppEventBus(
            onObserverFailure = { _, _ -> throw UnsupportedOperationException("logger down") },
        )
        var laterObserverRan = false
        bus.observe { throw IllegalStateException("first") }
        bus.observe { laterObserverRan = true }

        bus.publish(event())

        assertTrue(laterObserverRan)
    }

    @Test
    fun closingASubscriptionDetachesItAndIsIdempotent() {
        val bus = DesktopAppEventBus()
        val counted = AtomicInteger()
        val subscription = bus.observe { counted.incrementAndGet() }

        bus.publish(event())
        assertEquals(1, counted.get())

        subscription.close()
        bus.publish(event())
        assertEquals("a closed subscription must stop receiving", 1, counted.get())
        assertEquals(0, bus.observerCount)

        // Closed again from a teardown path that runs twice.
        subscription.close()
        assertEquals(0, bus.observerCount)
    }

    @Test
    fun aSecondCloseDoesNotDetachAnIdenticalObserverBelongingToSomeoneElse() {
        val bus = DesktopAppEventBus()
        val counted = AtomicInteger()
        // Two routes registering equal lambdas is legitimate, and `remove` deletes the
        // first match — so an unguarded double close would silently unsubscribe the
        // other route and its panel would go stale with no error anywhere.
        val observer: (AppEvent) -> Unit = { counted.incrementAndGet() }
        val first = bus.observe(observer)
        bus.observe(observer)

        first.close()
        first.close()

        assertEquals(1, bus.observerCount)
        bus.publish(event())
        assertEquals(1, counted.get())
    }

    @Test
    fun anObserverMayPublishWhileHandling() {
        val bus = DesktopAppEventBus()
        val seen = mutableListOf<AppEventType>()
        var republished = false

        bus.observe { received ->
            seen += received.type
            // A settings change committed as part of handling a sync is real: the sync
            // can update a stored timestamp. This must not deadlock or skip anyone.
            if (!republished && received.type == AppEventType.SYNC_COMMITTED) {
                republished = true
                bus.publish(event(AppEventType.SETTINGS_CHANGED))
            }
        }

        bus.publish(event(AppEventType.SYNC_COMMITTED))

        assertEquals(
            listOf(AppEventType.SYNC_COMMITTED, AppEventType.SETTINGS_CHANGED),
            seen,
        )
    }

    @Test
    fun anObserverRegisteredDuringDispatchDoesNotReceiveTheEventInFlight() {
        val bus = DesktopAppEventBus()
        var lateObserverSaw = 0

        bus.observe {
            bus.observe { lateObserverSaw += 1 }
        }
        bus.publish(event())

        // The in-flight event describes work that committed before the late observer
        // existed; handing it over would report state it has no basis to interpret.
        assertEquals(0, lateObserverSaw)
        // It is attached for the next one.
        bus.publish(event())
        assertTrue(lateObserverSaw >= 1)
    }

    @Test
    fun unsubscribingDuringDispatchDoesNotDisturbTheEventInFlight() {
        val bus = DesktopAppEventBus()
        val order = mutableListOf<String>()
        lateinit var second: dev.bee.kanjianki.platform.PlatformSubscription

        bus.observe {
            order += "first"
            second.close()
        }
        second = bus.observe { order += "second" }

        bus.publish(event())

        // Snapshot iteration: the already-started dispatch still reaches `second`.
        assertEquals(listOf("first", "second"), order)
        assertEquals(1, bus.observerCount)
    }

    @Test
    fun concurrentPublishesAndSubscriptionsDoNotCorruptTheObserverList() {
        val bus = DesktopAppEventBus()
        val delivered = AtomicInteger()
        bus.observe { delivered.incrementAndGet() }

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        try {
            repeat(threads) { index ->
                pool.execute {
                    start.await()
                    repeat(50) {
                        if (index % 2 == 0) {
                            bus.publish(event())
                        } else {
                            bus.observe { }.close()
                        }
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue("workers must finish", done.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        // The bus is published to from a sync coroutine and observed from the UI, so a
        // plain ArrayList here would throw ConcurrentModificationException in production
        // under exactly this interleaving.
        assertEquals("every transient subscription must have detached", 1, bus.observerCount)
        assertTrue(delivered.get() > 0)
    }

    @Test
    fun publishingWithNoObserversIsAllowed() {
        val bus = DesktopAppEventBus()

        // A widget-less, panel-less start still commits syncs.
        bus.publish(event())

        assertEquals(0, bus.observerCount)
    }

    @Test
    fun theEventItselfIsPassedThroughUnchanged() {
        val bus = DesktopAppEventBus()
        val original = event(AppEventType.UPDATE_READY, at = 42L)
        var received: AppEvent? = null
        bus.observe { received = it }

        bus.publish(original)

        // Same instance: the bus is a fan-out, not a transformer, and a copy would
        // invite an observer to compare by identity and fail.
        assertSame(original, received)
        assertEquals(42L, received?.occurredAtMillis)
    }
}
