package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.data.ankidroid.AnkiDroidStatus
import dev.bee.kanjianki.data.update.ReleaseCheckResult
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.ui.components.BlossomCard
import dev.bee.kanjianki.ui.components.BlossomTag
import dev.bee.kanjianki.ui.components.BlossomTone
import dev.bee.kanjianki.ui.components.DetailLine
import dev.bee.kanjianki.ui.components.SectionEyebrow
import dev.bee.kanjianki.ui.components.StatusBanner
import dev.bee.kanjianki.ui.components.blossomSwitchColors
import dev.bee.kanjianki.ui.components.blossomTextFieldColors
import dev.bee.kanjianki.ui.components.ghostButtonColors
import dev.bee.kanjianki.ui.components.primaryButtonColors
import kotlin.math.max

@Composable
fun SettingsScreen(
    settings: SettingsSnapshot?,
    health: HealthSnapshot?,
    settingsBusy: Boolean,
    settingsStatusMessage: String?,
    ankiDroidStatus: AnkiDroidStatus?,
    ankiDroidBusy: Boolean,
    ankiDroidStatusMessage: String?,
    onRefreshAnkiDroidStatus: () -> Unit,
    onRequestAnkiDroidPermission: () -> Unit,
    onSaveSettings: (SettingsSnapshot) -> Unit,
    releaseCheck: ReleaseCheckResult?,
    releaseBusy: Boolean,
    releaseStatusMessage: String?,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canInstallUpdate = releaseCheck?.let { it.updateAvailable && it.hasApkAsset } == true
    val canOpenReleasePage = !releaseCheck?.releaseHtmlUrl.isNullOrBlank()
    val installLabel = releaseCheck?.latestTag ?: "latest release"
    val currentAppVersion = releaseCheck?.currentVersion ?: health?.version
    val canRequestAnkiDroidPermission =
        ankiDroidStatus?.installed == true &&
            ankiDroidStatus.permissionGranted != true &&
            !ankiDroidStatus.permissionName.isNullOrBlank()

    var noteModelsText by rememberSaveable { mutableStateOf("") }
    var expressionFieldText by rememberSaveable { mutableStateOf("") }
    var readingFieldText by rememberSaveable { mutableStateOf("") }
    var meaningFieldText by rememberSaveable { mutableStateOf("") }
    var matureDaysText by rememberSaveable { mutableStateOf("") }
    var supportThresholdText by rememberSaveable { mutableStateOf("") }
    var pollingEnabled by rememberSaveable { mutableStateOf(false) }
    var pollingIntervalMinutesText by rememberSaveable { mutableStateOf("") }
    var localValidationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        if (settings == null) {
            return@LaunchedEffect
        }
        noteModelsText = settings.noteModels.joinToString(", ")
        expressionFieldText = settings.expressionField
        readingFieldText = settings.readingField
        meaningFieldText = settings.meaningField
        matureDaysText = settings.matureDays.toString()
        supportThresholdText = settings.kanjiSupportThreshold.toString()
        pollingEnabled = settings.pollingEnabled
        pollingIntervalMinutesText = max(settings.pollingIntervalSeconds / 60, 1).toString()
        localValidationMessage = null
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BlossomCard(tone = BlossomTone.PINK) {
            SectionEyebrow("Settings studio")
            Text(
                text = "Keep this simple, save once, sync fast",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "The only fields you should need often are note models, field mapping, and how often the app should quietly refresh your deck.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BlossomCard(tone = BlossomTone.ROSE) {
            SectionEyebrow("Collection mapping")
            SettingsField(
                value = noteModelsText,
                onValueChange = { noteModelsText = it },
                label = "Note models",
                supporting = "Comma-separated model names",
                enabled = !settingsBusy,
            )
            SettingsField(
                value = expressionFieldText,
                onValueChange = { expressionFieldText = it },
                label = "Expression field",
                enabled = !settingsBusy,
            )
            SettingsField(
                value = readingFieldText,
                onValueChange = { readingFieldText = it },
                label = "Reading field",
                enabled = !settingsBusy,
            )
            SettingsField(
                value = meaningFieldText,
                onValueChange = { meaningFieldText = it },
                label = "Meaning field",
                enabled = !settingsBusy,
            )
        }

        BlossomCard(tone = BlossomTone.VIOLET) {
            SectionEyebrow("Cadence and thresholds")
            SettingsField(
                value = matureDaysText,
                onValueChange = { matureDaysText = it },
                label = "Mature days",
                enabled = !settingsBusy,
            )
            SettingsField(
                value = supportThresholdText,
                onValueChange = { supportThresholdText = it },
                label = "Kanji support threshold",
                enabled = !settingsBusy,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Background sync",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Android enforces a minimum periodic interval of 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = pollingEnabled,
                    onCheckedChange = { pollingEnabled = it },
                    enabled = !settingsBusy,
                    colors = blossomSwitchColors(),
                )
            }
            SettingsField(
                value = pollingIntervalMinutesText,
                onValueChange = { pollingIntervalMinutesText = it },
                label = "Polling interval (minutes)",
                enabled = !settingsBusy,
            )
            if (settingsBusy) {
                CircularProgressIndicator()
            }
            localValidationMessage?.takeIf { it.isNotBlank() }?.let { message ->
                StatusBanner(message = message, tone = BlossomTone.DANGER)
            }
            settingsStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                StatusBanner(message = message, tone = BlossomTone.MINT)
            }
            Button(
                onClick = {
                    val current = settings ?: return@Button
                    val matureDays = matureDaysText.trim().toIntOrNull()
                    val supportThreshold = supportThresholdText.trim().toIntOrNull()
                    val pollingIntervalMinutes = pollingIntervalMinutesText.trim().toIntOrNull()
                    val models = noteModelsText.split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)

                    localValidationMessage = when {
                        expressionFieldText.isBlank() || readingFieldText.isBlank() || meaningFieldText.isBlank() ->
                            "Field names cannot be blank."

                        matureDays == null || matureDays < 0 ->
                            "Mature days must be 0 or greater."

                        supportThreshold == null || supportThreshold < 0 ->
                            "Kanji support threshold must be 0 or greater."

                        pollingIntervalMinutes == null || pollingIntervalMinutes <= 0 ->
                            "Polling interval must be greater than 0 minutes."

                        else -> null
                    }

                    if (localValidationMessage == null) {
                        onSaveSettings(
                            SettingsSnapshot(
                                ankiConnectUrl = current.ankiConnectUrl,
                                noteModels = models,
                                expressionField = expressionFieldText.trim(),
                                readingField = readingFieldText.trim(),
                                meaningField = meaningFieldText.trim(),
                                matureDays = matureDays ?: 0,
                                kanjiSupportThreshold = supportThreshold ?: 0,
                                jitenCacheTtlHours = current.jitenCacheTtlHours,
                                jitenRequestTimeoutSeconds = current.jitenRequestTimeoutSeconds,
                                pollingEnabled = pollingEnabled,
                                pollingIntervalSeconds = (pollingIntervalMinutes ?: 15) * 60,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = settings != null && !settingsBusy,
                colors = primaryButtonColors(),
            ) {
                Text("Save and resync")
            }
        }

        BlossomCard(tone = BlossomTone.MINT) {
            SectionEyebrow("App status")
            if (health == null) {
                Text(
                    text = "Loading app status…",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                DetailLine(label = "Version", value = health.version)
                DetailLine(
                    label = "Saved deck size",
                    value = "${health.sourceCounts.noteCount} notes / ${health.sourceCounts.cardCount} cards",
                )
                val latestSync = health.latestSync
                if (latestSync == null) {
                    DetailLine(label = "Latest sync", value = "none recorded yet")
                } else {
                    DetailLine(
                        label = "Latest sync",
                        value = "${latestSync.status} from ${friendlySyncSource(latestSync.source)}",
                    )
                    DetailLine(label = "Started", value = latestSync.startedAt)
                    DetailLine(label = "Finished", value = latestSync.finishedAt ?: "in progress")
                    DetailLine(
                        label = "Counts",
                        value = "${latestSync.noteCount} notes / ${latestSync.cardCount} cards",
                    )
                    latestSync.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        StatusBanner(message = message, tone = BlossomTone.DANGER)
                    }
                }
            }
        }

        BlossomCard(tone = BlossomTone.APRICOT) {
            SectionEyebrow("AnkiDroid live sync")
            if (ankiDroidStatus == null) {
                Text(
                    text = "Loading AnkiDroid provider status…",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = ankiDroidStatus.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlossomTag(
                        text = if (ankiDroidStatus.installed) "Installed" else "Not installed",
                        tone = if (ankiDroidStatus.installed) BlossomTone.MINT else BlossomTone.DANGER,
                        selected = true,
                    )
                    BlossomTag(
                        text = if (ankiDroidStatus.permissionGranted) "Permission granted" else "Permission missing",
                        tone = if (ankiDroidStatus.permissionGranted) BlossomTone.MINT else BlossomTone.APRICOT,
                    )
                }
                DetailLine(
                    label = "Deck access",
                    value = if (ankiDroidStatus.canReadCollection) "ready" else "not ready yet",
                )
            }
            if (ankiDroidBusy) {
                CircularProgressIndicator()
            }
            ankiDroidStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                StatusBanner(message = message, tone = BlossomTone.APRICOT)
            }
            Button(
                onClick = onRefreshAnkiDroidStatus,
                modifier = Modifier.fillMaxWidth(),
                enabled = !ankiDroidBusy,
                colors = ghostButtonColors(),
            ) {
                Text("Refresh AnkiDroid status")
            }
            OutlinedButton(
                onClick = onRequestAnkiDroidPermission,
                modifier = Modifier.fillMaxWidth(),
                enabled = !ankiDroidBusy && canRequestAnkiDroidPermission,
            ) {
                Text(
                    if (canRequestAnkiDroidPermission) {
                        "Grant AnkiDroid permission"
                    } else if (ankiDroidStatus?.installed != true) {
                        "Install AnkiDroid first"
                    } else {
                        "Permission already satisfied"
                    },
                )
            }
        }

        BlossomCard(tone = BlossomTone.ROSE) {
            SectionEyebrow("App updates")
            currentAppVersion?.let {
                DetailLine(label = "Current version", value = it)
            }
            if (releaseBusy) {
                CircularProgressIndicator()
            }
            if (releaseCheck == null) {
                Text(
                    text = "No GitHub release check has completed yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DetailLine(label = "Feed", value = "${releaseCheck.releaseOwner}/${releaseCheck.releaseRepo}")
                DetailLine(label = "Latest tag", value = releaseCheck.latestTag ?: "none")
                StatusBanner(
                    message = releaseCheck.statusMessage,
                    tone = if (releaseCheck.updateAvailable) BlossomTone.MINT else BlossomTone.APRICOT,
                )
                releaseCheck.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    DetailLine(label = "Release notes preview", value = releaseCheck.releaseNotesPreview)
                }
            }
            releaseStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                StatusBanner(message = message, tone = BlossomTone.ROSE)
            }
            Button(
                onClick = onCheckForUpdates,
                modifier = Modifier.fillMaxWidth(),
                enabled = !releaseBusy,
                colors = ghostButtonColors(),
            ) {
                Text("Check for updates")
            }
            Button(
                onClick = onInstallUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !releaseBusy && canInstallUpdate,
                colors = primaryButtonColors(),
            ) {
                Text(if (canInstallUpdate) "Download and install $installLabel" else "No update available")
            }
            OutlinedButton(
                onClick = onOpenReleasePage,
                modifier = Modifier.fillMaxWidth(),
                enabled = !releaseBusy && canOpenReleasePage,
            ) {
                Text("Open release page")
            }
        }
    }
}

private fun friendlySyncSource(source: String): String =
    when (source) {
        "ankidroid-content-provider" -> "AnkiDroid"
        "parity-fixture-fallback", "parity-fixture" -> "an older fixture import"
        else -> source
    }

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        colors = blossomTextFieldColors(),
    )
}
