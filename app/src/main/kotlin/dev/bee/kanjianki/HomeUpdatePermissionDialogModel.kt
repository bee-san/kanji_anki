package dev.bee.kanjianki

import dev.bee.kanjianki.updatecore.UpdateTextPolicy

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
