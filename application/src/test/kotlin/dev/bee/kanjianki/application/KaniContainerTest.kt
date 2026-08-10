package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.fakes.FakeHomeRepository
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeStatsRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.AppLifecycle
import dev.bee.kanjianki.platform.AppLifecycleState
import dev.bee.kanjianki.platform.PlatformSubscription
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import java.util.concurrent.Executor
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniContainerTest {
    @Test
    fun narrowOwnersExposeOneProcessDependencyGraph() {
        val container = TestContainer(mutableListOf())

        assertSame(container.home, container.homeRepository)
        assertSame(container.study, container.studyRepository)
        assertSame(container.stats, container.statsRepository)
        assertSame(container.settings, container.settingsRepository)
        assertSame(container.sync, container.syncRepository)
        assertSame(TestDeviceSettingsStore, container.deviceSettingsStore)
        assertSame(DirectExecutor, container.userIoExecutor)
        assertSame(DirectExecutor, container.maintenanceExecutor)
        assertSame(TestAppLifecycle, container.appLifecycle)

        container.close()
        assertTrue(container.events.single() == "close-data")
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

    private object TestAppLifecycle : AppLifecycle {
        override fun currentState(): AppLifecycleState = AppLifecycleState.BACKGROUND

        override fun observe(observer: (AppLifecycleState) -> Unit): PlatformSubscription =
            PlatformSubscription { }
    }

    private class TestContainer(
        val events: MutableList<String>,
    ) : KaniContainer {
        val home = FakeHomeRepository()
        val study = FakeStudyRepository()
        val stats = FakeStatsRepository()
        val settings = FakeSettingsRepository()
        val sync = FakeSyncRepository()

        override val homeRepository = home
        override val studyRepository = study
        override val statsRepository = stats
        override val settingsRepository = settings
        override val syncRepository = sync
        override val deviceSettingsStore = TestDeviceSettingsStore
        override val userIoExecutor = DirectExecutor
        override val maintenanceExecutor = DirectExecutor
        override val appLifecycle = TestAppLifecycle

        override fun close() {
            events += "close-data"
        }
    }
}
