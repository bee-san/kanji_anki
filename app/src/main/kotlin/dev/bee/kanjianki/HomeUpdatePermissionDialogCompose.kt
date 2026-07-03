package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun HomeUpdatePermissionDialog(model: HomeUpdatePermissionDialogModel?) {
    if (model == null) {
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            withButtonTrace("home-update-permission-dismiss") {
                model.onNotNow.run()
            }
        },
        title = { Text(text = model.title) },
        text = { Text(text = model.message) },
        confirmButton = {
            TextButton(
                onClick = {
                    withButtonTrace("home-update-permission-allow") {
                        model.onAllow.run()
                    }
                },
            ) {
                Text(text = model.allowLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    withButtonTrace("home-update-permission-dismiss") {
                        model.onNotNow.run()
                    }
                },
            ) {
                Text(text = model.notNowLabel)
            }
        },
    )
}
