package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy

data class HomeSyncConfirmDialogModel(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String,
    val onConfirm: Runnable,
    val onDismiss: Runnable,
)

object HomeSyncConfirmDialogModels {
    @JvmStatic
    fun create(
        message: String,
        onConfirm: Runnable,
        onDismiss: Runnable,
    ): HomeSyncConfirmDialogModel {
        return HomeSyncConfirmDialogModel(
            title = HomeTextCopy.syncDialogTitle(),
            message = message,
            confirmLabel = HomeTextCopy.syncDialogPositiveLabel(),
            dismissLabel = HomeTextCopy.cancelLabel(),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}
