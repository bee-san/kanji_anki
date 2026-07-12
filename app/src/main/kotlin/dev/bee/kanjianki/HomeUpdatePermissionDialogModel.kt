package dev.bee.kanjianki

import dev.bee.kanjianki.updatecore.InstallPermissionPromptPolicy
import dev.bee.kanjianki.updatecore.UpdateTextPolicy

data class HomeUpdatePermissionPromptSnapshot(
    val lastVersion: String,
    val pendingVersion: String?,
)

object HomeUpdatePermissionPromptSnapshots {
    @JvmStatic
    fun create(
        autoUpdateEnabled: Boolean,
        canRequestPackageInstalls: Boolean,
        hasCompletedUpdateCheck: Boolean,
        firstPromptShown: Boolean,
        hasPendingUpdate: Boolean,
        latestVersion: String?,
        lastPromptedVersion: String?,
    ): HomeUpdatePermissionPromptSnapshot? {
        val shouldPrompt = InstallPermissionPromptPolicy.shouldPrompt(
            autoUpdateEnabled,
            canRequestPackageInstalls,
            hasCompletedUpdateCheck,
            firstPromptShown,
            hasPendingUpdate,
            latestVersion,
            lastPromptedVersion,
        )
        if (!shouldPrompt) {
            return null
        }
        val normalizedVersion = InstallPermissionPromptPolicy.normalizedVersion(latestVersion)
        return HomeUpdatePermissionPromptSnapshot(
            lastVersion = normalizedVersion,
            pendingVersion = if (hasPendingUpdate) normalizedVersion else null,
        )
    }
}

data class HomeUpdatePermissionDialogModel(
    val title: String,
    val message: String,
    val allowLabel: String,
    val notNowLabel: String,
    val onAllow: Runnable,
    val onNotNow: Runnable,
)

object HomeUpdatePermissionDialogModels {
    @JvmStatic
    fun create(
        pendingVersion: String?,
        onAllow: Runnable,
        onNotNow: Runnable,
    ): HomeUpdatePermissionDialogModel {
        return HomeUpdatePermissionDialogModel(
            title = UpdateTextPolicy.installPermissionDialogTitle(),
            message = UpdateTextPolicy.installPermissionDialogMessage(pendingVersion),
            allowLabel = UpdateTextPolicy.installPermissionDialogAllowLabel(),
            notNowLabel = UpdateTextPolicy.installPermissionDialogNotNowLabel(),
            onAllow = onAllow,
            onNotNow = onNotNow,
        )
    }
}
