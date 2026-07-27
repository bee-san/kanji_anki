package dev.bee.kanjianki.sync

import android.content.Context
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.time.AppClock

internal fun createAutoSyncRunner(
    context: Context,
    syncUseCases: SyncUseCases,
    deviceSettingsStore: DeviceSettingsStore,
    gateway: CollectionGateway,
    clock: AppClock? = AppClock.systemClock(),
): AutoSyncRunner {
    val appContext = context.applicationContext
    return AutoSyncRunner(
        syncUseCases = syncUseCases,
        gateway = gateway,
        autoSyncState = DeviceAutoSyncState(deviceSettingsStore),
        manualSyncFactory = AutoSyncRunner.ManualSyncFactory { settings, syncClock ->
            createManualSyncEngine(
                appContext,
                syncUseCases,
                gateway,
                settings,
                SyncProgress.NONE,
                syncClock,
                repairedWriteBackAuthorized = false,
            )
        },
        clock = clock,
    )
}

internal fun createAutoSyncRunner(
    context: Context,
    store: LocalStore,
    gateway: CollectionGateway,
    clock: AppClock? = AppClock.systemClock(),
): AutoSyncRunner =
    createAutoSyncRunner(
        context,
        syncUseCases(store),
        store.deviceSettingsStore(),
        gateway,
        clock,
    )

private class DeviceAutoSyncState(
    private val store: DeviceSettingsStore,
) : AutoSyncRunner.AutoSyncState {
    override fun isEnabled(): Boolean =
        store.snapshot().read(DeviceSettingKeys.autoSyncEnabled) ?: false

    override fun recordAttempt(attemptedAt: Long, success: Boolean) {
        store.edit {
            put(DeviceSettingKeys.autoSyncLastAttemptAt, attemptedAt)
            if (success) {
                put(DeviceSettingKeys.autoSyncLastSuccessAt, attemptedAt)
            }
        }
    }
}
