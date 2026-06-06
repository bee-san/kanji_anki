package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun HomeSyncConfirmDialog(model: HomeSyncConfirmDialogModel?) {
    if (model == null) {
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            withButtonTrace("home-sync-confirm-dismiss") {
                model.onDismiss.run()
            }
        },
        title = { Text(text = model.title) },
        text = { Text(text = model.message) },
        confirmButton = {
            TextButton(
                onClick = {
                    withButtonTrace("home-sync-confirm-ok") {
                        model.onConfirm.run()
                    }
                },
            ) {
                Text(text = model.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    withButtonTrace("home-sync-confirm-dismiss") {
                        model.onDismiss.run()
                    }
                },
            ) {
                Text(text = model.dismissLabel)
            }
        },
    )
}
