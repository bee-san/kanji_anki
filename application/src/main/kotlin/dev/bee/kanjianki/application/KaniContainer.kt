package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.StatsRepository
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncRepository
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.platform.AppLifecycle
import java.util.concurrent.Executor

interface RepositoryOwner {
    val homeRepository: HomeRepository
    val studyRepository: StudyRepository
    val statsRepository: StatsRepository
    val settingsRepository: SettingsRepository
    val syncRepository: SyncRepository
}

interface DeviceSettingsOwner {
    val deviceSettingsStore: DeviceSettingsStore
}

interface TaskExecutorOwner {
    val userIoExecutor: Executor
    val maintenanceExecutor: Executor
}

interface PlatformLifecycleOwner {
    val appLifecycle: AppLifecycle
}

/** Process-owned host dependencies shared by UI and background components. */
interface KaniContainer :
    RepositoryOwner,
    DeviceSettingsOwner,
    TaskExecutorOwner,
    PlatformLifecycleOwner,
    AutoCloseable
