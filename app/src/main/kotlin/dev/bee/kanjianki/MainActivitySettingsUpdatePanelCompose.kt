@file:JvmName("MainActivitySettingsUpdatePanelCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

internal fun settingsUpdateOverviewPanelView(
    activity: MainActivitySettings,
    model: SettingsUpdateOverviewPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                SettingsUpdateOverviewPanel(model)
            }
        }
    }
}

@Composable
fun SettingsUpdateOverviewPanel(model: SettingsUpdateOverviewPanelModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsUpdatePanel(model = model.panel)
        SettingsUpdateFilledButton(
            label = model.openUpdaterLabel,
            containerColor = SettingsUpdatePinkDark,
            contentColor = SettingsUpdateWhite,
            minHeight = 56.dp,
            shape = SettingsUpdatePrimaryButtonShape,
            onClick = model.onOpenUpdater
        )
    }
}

@Composable
internal fun SettingsUpdatePanel(model: SettingsUpdatePanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsUpdatePanelShape,
        color = SettingsUpdatePanelFill,
        border = BorderStroke(1.dp, SettingsUpdatePanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = SettingsUpdateInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.statusLine,
                color = model.statusColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.lastCheckLine,
                color = SettingsUpdateMuted,
                fontSize = 15.sp
            )
            Text(
                text = model.lastResultLine,
                color = SettingsUpdateMuted,
                fontSize = 15.sp
            )
            Text(
                text = model.installPermissionLine,
                color = model.installPermissionColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            if (model.hasPendingUpdate) {
                Text(
                    text = requireNotNull(model.pendingVersionLine),
                    color = SettingsUpdateCoral,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = requireNotNull(model.pendingMessageLine),
                    color = SettingsUpdateMuted,
                    fontSize = 15.sp
                )
                if (model.canInstallUpdates) {
                    SettingsUpdateFilledButton(
                        label = SettingsTextCopy.installVerifiedUpdateLabel(),
                        containerColor = SettingsUpdateCoral,
                        contentColor = SettingsUpdateWhite,
                        minHeight = 62.dp,
                        shape = SettingsUpdatePrimaryButtonShape,
                        onClick = model.onInstallVerifiedUpdate
                    )
                }
            }

            if (!model.canInstallUpdates) {
                SettingsUpdateOutlinedButton(
                    label = SettingsTextCopy.setupAppInstallsLabel(),
                    minHeight = 54.dp,
                    shape = SettingsUpdatePrimaryButtonShape,
                    onClick = model.onOpenInstallSettings
                )
            }

            SettingsUpdateOutlinedButton(
                label = model.automaticUpdatesToggleLabel,
                minHeight = 54.dp,
                shape = SettingsUpdatePrimaryButtonShape,
                onClick = model.onToggleAutomaticUpdates
            )
        }
    }
}
