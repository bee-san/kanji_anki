package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.fakes.FakeHomeRepository
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeStatsRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DesktopContainerLifecycleTest {
    @Test
    fun successfulRunReleasesServicesDataAndLockInReverseOrder() {
        val events = mutableListOf<String>()
        val lifecycle = DesktopContainerLifecycle(RecordingStages(events))

        val result = lifecycle.run {
            events += "build-presentation"
            "closed"
        }

        assertEquals("closed", result)
        assertEquals(
            listOf(
                "acquire-lock",
                "apply-restore",
                "open-data",
                "start-services",
                "build-presentation",
                "stop-services",
                "close-data",
                "release-lock",
            ),
            events,
        )
    }

    @Test
    fun restoreFailureReleasesOnlyTheProfileLock() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("restore")
        val lifecycle = DesktopContainerLifecycle(
            RecordingStages(events, failureAt = "apply-restore", failure = failure),
        )

        assertSame(
            failure,
            assertThrows(IllegalStateException::class.java) {
                lifecycle.run { }
            },
        )
        assertEquals(
            listOf("acquire-lock", "apply-restore", "release-lock"),
            events,
        )
    }

    @Test
    fun serviceFailureClosesDataBeforeTheProfileLock() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("services")
        val lifecycle = DesktopContainerLifecycle(
            RecordingStages(events, failureAt = "start-services", failure = failure),
        )

        assertSame(
            failure,
            assertThrows(IllegalStateException::class.java) {
                lifecycle.run { }
            },
        )
        assertEquals(
            listOf(
                "acquire-lock",
                "apply-restore",
                "open-data",
                "start-services",
                "close-data",
                "release-lock",
            ),
            events,
        )
    }

    @Test
    fun presentationFailureRetainsPrimaryAndSuppressesCleanupFailures() {
        val events = mutableListOf<String>()
        val presentationFailure = IllegalStateException("presentation")
        val serviceCloseFailure = IllegalArgumentException("stop-services")
        val dataCloseFailure = IllegalArgumentException("close-data")
        val lifecycle = DesktopContainerLifecycle(
            RecordingStages(
                events = events,
                serviceCloseFailure = serviceCloseFailure,
                dataCloseFailure = dataCloseFailure,
            ),
        )

        val actual = assertThrows(IllegalStateException::class.java) {
            lifecycle.run<Unit> {
                events += "build-presentation"
                throw presentationFailure
            }
        }

        assertSame(presentationFailure, actual)
        assertEquals(
            listOf(serviceCloseFailure, dataCloseFailure),
            actual.suppressed.toList(),
        )
        assertEquals(
            listOf(
                "acquire-lock",
                "apply-restore",
                "open-data",
                "start-services",
                "build-presentation",
                "stop-services",
                "close-data",
                "release-lock",
            ),
            events,
        )
    }

    @Test
    fun firstCleanupFailureIsThrownAndLaterFailuresAreSuppressed() {
        val events = mutableListOf<String>()
        val serviceCloseFailure = IllegalStateException("stop-services")
        val dataCloseFailure = IllegalArgumentException("close-data")
        val lifecycle = DesktopContainerLifecycle(
            RecordingStages(
                events = events,
                serviceCloseFailure = serviceCloseFailure,
                dataCloseFailure = dataCloseFailure,
            ),
        )

        val actual = assertThrows(IllegalStateException::class.java) {
            lifecycle.run { events += "build-presentation" }
        }

        assertSame(serviceCloseFailure, actual)
        assertEquals(listOf(dataCloseFailure), actual.suppressed.toList())
        assertEquals("release-lock", events.last())
    }

    private class RecordingStages(
        private val events: MutableList<String>,
        private val failureAt: String? = null,
        private val failure: RuntimeException = IllegalStateException(failureAt),
        private val serviceCloseFailure: RuntimeException? = null,
        private val dataCloseFailure: RuntimeException? = null,
    ) : DesktopContainerLifecycle.Stages<TestContainer> {
        override fun acquireProfileLock(): AutoCloseable {
            record("acquire-lock")
            return AutoCloseable { events += "release-lock" }
        }

        override fun applyStagedRestore() {
            record("apply-restore")
        }

        override fun openData(): TestContainer {
            record("open-data")
            return TestContainer(events, dataCloseFailure)
        }

        override fun startServices(container: TestContainer): AutoCloseable {
            record("start-services")
            return AutoCloseable {
                events += "stop-services"
                serviceCloseFailure?.let { throw it }
            }
        }

        private fun record(event: String) {
            events += event
            if (failureAt == event) throw failure
        }
    }

    private class TestContainer(
        private val events: MutableList<String>,
        private val closeFailure: RuntimeException? = null,
    ) : KaniContainer {
        override val homeRepository = FakeHomeRepository()
        override val studyRepository = FakeStudyRepository()
        override val statsRepository = FakeStatsRepository()
        override val settingsRepository = FakeSettingsRepository()
        override val syncRepository = FakeSyncRepository()
        override val deviceSettingsStore = TestDeviceSettingsStore
        override val userIoExecutor = DirectExecutor
        override val maintenanceExecutor = DirectExecutor

        override fun close() {
            events += "close-data"
            closeFailure?.let { throw it }
        }
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    private object TestDeviceSettingsStore : DeviceSettingsStore {
        override fun contains(key: DeviceSettingKey<*>): Boolean = false

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = null

        override fun snapshot(): DeviceSettingsReader = this

        override fun edit(block: DeviceSettingsEditor.() -> Unit) = Unit
    }
}
