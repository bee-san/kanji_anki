package dev.bee.kanjianki

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
    val status = store.autoUpdateStatus()
    return HomeUpdatePermissionPromptSnapshots.create(
        autoUpdateEnabled = status.enabled,
        canRequestPackageInstalls = canRequestPackageInstalls(this),
        hasCompletedUpdateCheck = status.lastCheckAtMillis > 0L,
        firstPromptShown = store.installPermissionPromptShown(),
        hasPendingUpdate = status.hasPendingUpdate(),
        latestVersion = status.lastVersion,
        lastPromptedVersion = store.installPermissionPromptLastVersion(),
    )
}
