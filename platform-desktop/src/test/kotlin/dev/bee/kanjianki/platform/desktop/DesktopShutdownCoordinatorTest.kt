package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLifecycleState
import dev.bee.kanjianki.platform.AppLogEvent
import dev.bee.kanjianki.platform.AppLogLevel
import dev.bee.kanjianki.platform.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShutdownCoordinatorTest {
    @Test
    fun stepsRunInReverseRegistrationOrder() {
        // Startup order is lock -> database; teardown must be database -> lock, or
        // the lock is released while a connection still holds the file.
        val order = ArrayList<String>()
        val coordinator = DesktopShutdownCoordinator()
        coordinator.register("profile-lock") { order.add("profile-lock") }
        coordinator.register("database") { order.add("database") }
        coordinator.register("secrets") { order.add("secrets") }

        val outcome = coordinator.shutDown()

        assertEquals(listOf("secrets", "database", "profile-lock"), order)
        assertEquals(listOf("secrets", "database", "profile-lock"), outcome.completed)
        assertTrue(outcome.isClean)
        assertTrue(outcome.ranNow)
    }

    @Test
    fun aFailingStepDoesNotPreventTheRemainingOnesFromRunning() {
        // The failure this exists to prevent: a database close that throws leaves the
        // profile lock held, and the user's next launch says "profile in use" for a
        // process that no longer exists.
        val order = ArrayList<String>()
        val logged = ArrayList<AppLogEvent>()
        val coordinator = DesktopShutdownCoordinator(logger = AppLogger(logged::add))
        coordinator.register("profile-lock") { order.add("profile-lock") }
        coordinator.register("database") { throw IllegalStateException("close failed") }
        coordinator.register("secrets") { order.add("secrets") }

        val outcome = coordinator.shutDown()

        assertEquals(listOf("secrets", "profile-lock"), order)
        assertEquals(listOf("secrets", "profile-lock"), outcome.completed)
        assertEquals(listOf("database"), outcome.failures.map { it.first })
        assertFalse(outcome.isClean)
        assertEquals(listOf(AppLogLevel.ERROR), logged.map(AppLogEvent::level))
    }

    @Test
    fun anErrorRatherThanAnExceptionStillLetsTheLockBeReleased() {
        // This is the last code to run, so catching Throwable is deliberate: an
        // OutOfMemoryError in one step must not strand the profile lock.
        val released = ArrayList<String>()
        val coordinator = DesktopShutdownCoordinator()
        coordinator.register("profile-lock") { released.add("profile-lock") }
        coordinator.register("database") { throw StackOverflowError("deep") }

        val outcome = coordinator.shutDown()

        assertEquals(listOf("profile-lock"), released)
        assertEquals(listOf("database"), outcome.failures.map { it.first })
    }

    @Test
    fun shutdownRunsOnceEvenWhenRequestedTwice() {
        // The window close button and a tray "quit" can both arrive.
        var closes = 0
        val coordinator = DesktopShutdownCoordinator()
        coordinator.register("database") { closes++ }

        val first = coordinator.shutDown()
        val second = coordinator.shutDown()

        assertEquals(1, closes)
        assertTrue(first.ranNow)
        assertFalse(second.ranNow)
        assertEquals(first.completed, second.completed)
        assertTrue(coordinator.hasShutDown)
    }

    @Test
    fun aStepThatItselfRequestsShutdownDoesNotRerunTheList() {
        // A window listener firing during teardown re-enters; running the list again
        // would close an already-closed database and report it as a new failure.
        var closes = 0
        val coordinator = DesktopShutdownCoordinator()
        var reentrant: DesktopShutdownCoordinator.Outcome? = null
        coordinator.register("database") { closes++ }
        coordinator.register("window") { reentrant = coordinator.shutDown() }

        val outcome = coordinator.shutDown()

        assertEquals(1, closes)
        assertFalse(requireNotNull(reentrant).ranNow)
        assertTrue(outcome.isClean)
    }

    @Test
    fun shutdownMovesTheLifecycleToStoppingBeforeAnyStepRuns() {
        // A step may publish an app event; an observer must already see STOPPING so
        // it does not re-arm work against a closing profile.
        val lifecycle = DesktopAppLifecycle(initialState = AppLifecycleState.FOREGROUND)
        val observed = ArrayList<AppLifecycleState>()
        val coordinator = DesktopShutdownCoordinator(lifecycle = lifecycle)
        coordinator.register("database") { observed.add(lifecycle.currentState()) }

        coordinator.shutDown()

        assertEquals(listOf(AppLifecycleState.STOPPING), observed)
        assertEquals(AppLifecycleState.STOPPING, lifecycle.currentState())
    }

    @Test
    fun registeringAfterShutdownIsAProgrammingErrorNotASilentLeak() {
        val coordinator = DesktopShutdownCoordinator()
        coordinator.shutDown()

        assertThrows(IllegalStateException::class.java) {
            coordinator.register("late") { }
        }
    }

    @Test
    fun aBlankStepNameIsRejectedSoAFailureIsAlwaysAttributable() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopShutdownCoordinator().register(" ") { }
        }
    }

    @Test
    fun shuttingDownWithNothingRegisteredIsCleanAndNotAnError() {
        val outcome = DesktopShutdownCoordinator().shutDown()

        assertTrue(outcome.isClean)
        assertTrue(outcome.completed.isEmpty())
    }

    @Test
    fun registerReturnsTheCoordinatorSoStartupCanChainItsSteps() {
        val order = ArrayList<String>()
        val coordinator = DesktopShutdownCoordinator()
            .register("first") { order.add("first") }
            .register("second") { order.add("second") }

        coordinator.shutDown()

        assertEquals(listOf("second", "first"), order)
    }

    @Test
    fun aFailureCarriesTheCauseSoDiagnosticsCanShowWhatBroke() {
        val cause = IllegalStateException("wal checkpoint failed")
        val coordinator = DesktopShutdownCoordinator()
        coordinator.register("database") { throw cause }

        val outcome = coordinator.shutDown()

        assertEquals(cause, outcome.failures.single().second)
    }
}
