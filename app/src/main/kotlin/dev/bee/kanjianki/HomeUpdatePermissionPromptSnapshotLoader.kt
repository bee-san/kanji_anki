package dev.bee.kanjianki

import dev.bee.kanjianki.platform.DeviceSettingKeys

/** Reports install permission while honoring the test override seam. */
internal fun canRequestPackageInstalls(activity: MainActivityBase): Boolean {
    MainActivityRuntimeOverrides.installPermission?.let { return it }
    return activity.packageManager.canRequestPackageInstalls()
}

/**
 * Loads every input needed by the update-permission prompt while the Home route
 * is on its background loader. The rendered route consumes only the immutable
 * snapshot and never performs settings-table reads or PackageManager IPC.
 */
internal fun MainActivityHome.loadUpdatePermissionPromptSnapshot(): HomeUpdatePermissionPromptSnapshot? {
    val settings = deviceSettingsStore.snapshot()
    val lastCheckAt = settings.read(DeviceSettingKeys.autoUpdateLastCheckAt) ?: 0L
    val pendingPackage = settings.read(DeviceSettingKeys.autoUpdatePendingPackage).orEmpty()
    val lastVersion = settings.read(DeviceSettingKeys.autoUpdateLastVersion).orEmpty()
    return HomeUpdatePermissionPromptSnapshots.create(
        autoUpdateEnabled = settings.read(DeviceSettingKeys.autoUpdateEnabled) ?: true,
        canRequestPackageInstalls = canRequestPackageInstalls(this),
        hasCompletedUpdateCheck = lastCheckAt > 0L,
        firstPromptShown = settings.read(DeviceSettingKeys.updatePermissionPromptShown) ?: false,
        hasPendingUpdate = pendingPackage.isNotEmpty(),
        latestVersion = lastVersion,
        lastPromptedVersion = settings.read(
            DeviceSettingKeys.updatePermissionPromptLastVersion,
        ).orEmpty(),
    )
}
