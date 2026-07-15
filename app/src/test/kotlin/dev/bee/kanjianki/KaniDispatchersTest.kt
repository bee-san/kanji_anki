package dev.bee.kanjianki

import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniDispatchersTest {
    @Test
    fun dispatchersExecuteWorkOnTheWrappedExecutorThreads() {
        val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "test-kani-io") }
        val maintenanceExecutor =
            Executors.newSingleThreadExecutor { r -> Thread(r, "test-kani-maintenance") }
        try {
            val dispatchers = KaniDispatchers(ioExecutor, maintenanceExecutor)

            val ioThreadName = runBlocking {
                withContext(dispatchers.io) { Thread.currentThread().name }
            }
            val maintenanceThreadName = runBlocking {
                withContext(dispatchers.maintenance) { Thread.currentThread().name }
            }

            // Each dispatcher must run work on its own wrapped executor thread so the
            // single-threaded io pool keeps LocalStore's serialized-access guarantee.
            // Coroutine debug mode may append " @coroutine#N", so match the prefix.
            assertTrue(
                "io ran on $ioThreadName",
                ioThreadName.startsWith("test-kani-io"),
            )
            assertTrue(
                "maintenance ran on $maintenanceThreadName",
                maintenanceThreadName.startsWith("test-kani-maintenance"),
            )
        } finally {
            ioExecutor.shutdownNow()
            maintenanceExecutor.shutdownNow()
        }
    }
}
