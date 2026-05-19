@file:JvmName("MainActivitySettingsUpdatePageCompose")

package dev.bee.kanjianki

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.update.AutoUpdateScheduler
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val Coral = ComposeColor(0xFFFF4C76)
private val Teal = ComposeColor(0xFF00AEB5)
private val PinkDark = ComposeColor(0xFFDA3A7A)
private val HomeButtonBorder = ComposeColor(0xFFEBD6E4)
private val ButtonBorder = ComposeColor(0xFFEEBDDA)
private val PanelBorder = ComposeColor(0xFFFFC7DE)
private val PanelFill = ComposeColor(0xFFFFFDFE)
private val White = ComposeColor(0xFFFFFFFF)
private val PrimaryButtonShape = RoundedCornerShape(12.dp)
private val WideButtonShape = RoundedCornerShape(22.dp)
private val PanelShape = RoundedCornerShape(24.dp)

data class SettingsUpdatePageModel(
    val title: String,
    val body: String,
    val onHome: () -> Unit,
    val onBack: () -> Unit,
    val onCheckForUpdate: () -> Unit,
    val panel: SettingsUpdatePanelModel,
)

data class SettingsUpdatePanelModel(
    val title: String,
    val statusLine: String,
    val statusColor: ComposeColor,
    val lastCheckLine: String,
    val lastResultLine: String,
    val installPermissionLine: String,
    val installPermissionColor: ComposeColor,
    val hasPendingUpdate: Boolean,
    val pendingVersionLine: String?,
    val pendingMessageLine: String?,
    val canInstallUpdates: Boolean,
    val onInstallVerifiedUpdate: () -> Unit,
    val onOpenInstallSettings: () -> Unit,
    val onToggleAutomaticUpdates: () -> Unit,
    val automaticUpdatesToggleLabel: String,
)

fun settingsUpdatePageView(activity: Activity): View {
    val settingsActivity = activity as MainActivitySettings
    val model = settingsUpdatePageModel(settingsActivity)
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                SettingsUpdatePage(model)
            }
        }
    }
}

private fun settingsUpdatePageModel(activity: MainActivitySettings): SettingsUpdatePageModel {
    val status = activity.store.autoUpdateStatus()
    val canInstallUpdates = canInstallUpdates(activity)
    return SettingsUpdatePageModel(
        title = SettingsTextCopy.updatePageTitle(),
        body = SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME),
        onHome = activity::renderHome,
        onBack = { activity.renderSettings(false) },
        onCheckForUpdate = { activity.runUpdate(false) },
        panel = SettingsUpdatePanelModel(
            title = SettingsTextCopy.automaticUpdatesTitle(),
            statusLine = SettingsTextCopy.autoUpdatePanelStatus(status.enabled),
            statusColor = if (status.enabled) Teal else Muted,
            lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine(
                DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis)
            ),
            lastResultLine = SettingsTextCopy.autoUpdateLastResultLine(status.lastResult),
            installPermissionLine = SettingsTextCopy.installPermissionLine(canInstallUpdates),
            installPermissionColor = if (canInstallUpdates) Teal else Coral,
            hasPendingUpdate = status.hasPendingUpdate(),
            pendingVersionLine = if (status.hasPendingUpdate()) {
                SettingsTextCopy.verifiedApkReadyLine(status.lastVersion)
            } else {
                null
            },
            pendingMessageLine = if (status.hasPendingUpdate()) {
                if (status.pendingMessage.isEmpty()) {
                    SettingsTextCopy.pendingUpdateFallback()
                } else {
                    status.pendingMessage
                }
            } else {
                null
            },
            canInstallUpdates = canInstallUpdates,
            onInstallVerifiedUpdate = { activity.runUpdate(true) },
            onOpenInstallSettings = { activity.startActivity(GitHubUpdater.installPermissionIntent(activity)) },
            onToggleAutomaticUpdates = { toggleAutomaticUpdates(activity, status.enabled) },
            automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(status.enabled),
        )
    )
}

@Composable
fun SettingsUpdatePage(model: SettingsUpdatePageModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsHomeButton(onClick = model.onHome)
        SettingsBackButton(onClick = model.onBack)
        Text(
            text = model.title,
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = model.body,
            modifier = Modifier.fillMaxWidth(),
            color = Muted,
            fontSize = 16.sp
        )
        SettingsUpdatePanel(model = model.panel)
        SettingsFilledButton(
            label = SettingsTextCopy.checkForUpdateLabel(),
            containerColor = PinkDark,
            contentColor = White,
            minHeight = 62.dp,
            shape = PrimaryButtonShape,
            onClick = model.onCheckForUpdate
        )
    }
}

@Composable
private fun SettingsUpdatePanel(model: SettingsUpdatePanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = PanelFill,
        border = BorderStroke(1.dp, PanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = Ink,
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
                color = Muted,
                fontSize = 15.sp
            )
            Text(
                text = model.lastResultLine,
                color = Muted,
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
                    color = Coral,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = requireNotNull(model.pendingMessageLine),
                    color = Muted,
                    fontSize = 15.sp
                )
                if (model.canInstallUpdates) {
                    SettingsFilledButton(
                        label = SettingsTextCopy.installVerifiedUpdateLabel(),
                        containerColor = Coral,
                        contentColor = White,
                        minHeight = 62.dp,
                        shape = PrimaryButtonShape,
                        onClick = model.onInstallVerifiedUpdate
                    )
                }
            }

            if (!model.canInstallUpdates) {
                SettingsOutlinedButton(
                    label = SettingsTextCopy.setupAppInstallsLabel(),
                    minHeight = 54.dp,
                    shape = PrimaryButtonShape,
                    onClick = model.onOpenInstallSettings
                )
            }

            SettingsOutlinedButton(
                label = model.automaticUpdatesToggleLabel,
                minHeight = 54.dp,
                shape = PrimaryButtonShape,
                onClick = model.onToggleAutomaticUpdates
            )
        }
    }
}

@Composable
private fun SettingsHomeButton(onClick: () -> Unit) {
    SettingsOutlinedButton(
        label = HomeTextCopy.homeLabel(),
        iconRes = R.drawable.ic_home_24,
        minHeight = 56.dp,
        shape = WideButtonShape,
        fontSize = 15.sp,
        borderColor = HomeButtonBorder,
        onClick = onClick
    )
}

@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    SettingsOutlinedButton(
        label = SettingsTextCopy.backToSettingsLabel(),
        minHeight = 54.dp,
        shape = PrimaryButtonShape,
        fontSize = 16.sp,
        onClick = onClick
    )
}

@Composable
private fun SettingsOutlinedButton(
    label: String,
    onClick: () -> Unit,
    iconRes: Int? = null,
    minHeight: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    borderColor: ComposeColor = ButtonBorder,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = White,
            contentColor = Ink
        )
    ) {
        SettingsButtonContent(
            label = label,
            iconRes = iconRes,
            contentColor = Ink,
            fontSize = fontSize
        )
    }
}

@Composable
private fun SettingsFilledButton(
    label: String,
    onClick: () -> Unit,
    containerColor: ComposeColor,
    contentColor: ComposeColor,
    minHeight: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
    iconRes: Int? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        SettingsButtonContent(
            label = label,
            iconRes = iconRes,
            contentColor = contentColor,
            fontSize = fontSize
        )
    }
}

@Composable
private fun SettingsButtonContent(
    label: String,
    iconRes: Int?,
    contentColor: ComposeColor,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun canInstallUpdates(activity: MainActivitySettings): Boolean {
    MainActivityBase.installPermissionForTests?.let { return it }
    return activity.packageManager.canRequestPackageInstalls()
}

private fun toggleAutomaticUpdates(activity: MainActivitySettings, enabled: Boolean) {
    val result = AutoUpdateSettingsTogglePolicy.toggle(enabled)
    activity.store.saveAutoUpdateEnabled(result.enabled())
    if (result.enabled()) {
        AutoUpdateScheduler.schedule(activity)
    } else {
        AutoUpdateScheduler.cancel(activity)
    }
    Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
    activity.renderUpdate()
}
