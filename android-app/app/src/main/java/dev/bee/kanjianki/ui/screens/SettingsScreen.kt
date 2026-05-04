package dev.bee.kanjianki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.data.ankidroid.AnkiDroidStatus
import dev.bee.kanjianki.data.update.ReleaseCheckResult
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
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
    var jitenCacheTtlText by rememberSaveable { mutableStateOf("") }
    var requestTimeoutText by rememberSaveable { mutableStateOf("") }
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
        jitenCacheTtlText = settings.jitenCacheTtlHours.toString()
        requestTimeoutText = settings.jitenRequestTimeoutSeconds.toString()
        pollingEnabled = settings.pollingEnabled
        pollingIntervalMinutesText = max(settings.pollingIntervalSeconds / 60, 1).toString()
        localValidationMessage = null
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                if (settings == null) {
                    Text("Loading settings…")
                } else {
                    OutlinedTextField(
                        value = noteModelsText,
                        onValueChange = { noteModelsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note models") },
                        supportingText = { Text("Comma-separated model names") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = expressionFieldText,
                        onValueChange = { expressionFieldText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Expression field") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = readingFieldText,
                        onValueChange = { readingFieldText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reading field") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = meaningFieldText,
                        onValueChange = { meaningFieldText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Meaning field") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = matureDaysText,
                        onValueChange = { matureDaysText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mature days") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = supportThresholdText,
                        onValueChange = { supportThresholdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Kanji support threshold") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = jitenCacheTtlText,
                        onValueChange = { jitenCacheTtlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Jiten cache TTL (hours)") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    OutlinedTextField(
                        value = requestTimeoutText,
                        onValueChange = { requestTimeoutText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Jiten request timeout (seconds)") },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Background sync", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = pollingEnabled,
                            onCheckedChange = { pollingEnabled = it },
                            enabled = !settingsBusy,
                        )
                    }
                    OutlinedTextField(
                        value = pollingIntervalMinutesText,
                        onValueChange = { pollingIntervalMinutesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Polling interval (minutes)") },
                        supportingText = {
                            Text("Android enforces a minimum periodic sync interval of 15 minutes.")
                        },
                        singleLine = true,
                        enabled = !settingsBusy,
                    )
                    if (settingsBusy) {
                        CircularProgressIndicator()
                    }
                    localValidationMessage?.let { message ->
                        if (message.isNotBlank()) {
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    settingsStatusMessage?.let { message ->
                        if (message.isNotBlank()) {
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = {
                            val matureDays = matureDaysText.trim().toIntOrNull()
                            val supportThreshold = supportThresholdText.trim().toIntOrNull()
                            val jitenCacheTtlHours = jitenCacheTtlText.trim().toIntOrNull()
                            val requestTimeoutSeconds = requestTimeoutText.trim().toIntOrNull()
                            val pollingIntervalMinutes = pollingIntervalMinutesText.trim().toIntOrNull()
                            val models = noteModelsText.split(",")
                                .map(String::trim)
                                .filter(String::isNotBlank)
                            when {
                                expressionFieldText.isBlank() ||
                                    readingFieldText.isBlank() ||
                                    meaningFieldText.isBlank() -> {
                                    localValidationMessage = "Field names cannot be blank."
                                }

                                matureDays == null || matureDays < 0 -> {
                                    localValidationMessage = "Mature days must be 0 or greater."
                                }

                                supportThreshold == null || supportThreshold < 0 -> {
                                    localValidationMessage =
                                        "Kanji support threshold must be 0 or greater."
                                }

                                jitenCacheTtlHours == null || jitenCacheTtlHours < 0 -> {
                                    localValidationMessage =
                                        "Jiten cache TTL must be 0 or greater."
                                }

                                requestTimeoutSeconds == null || requestTimeoutSeconds <= 0 -> {
                                    localValidationMessage =
                                        "Jiten request timeout must be greater than 0."
                                }

                                pollingIntervalMinutes == null || pollingIntervalMinutes <= 0 -> {
                                    localValidationMessage =
                                        "Polling interval must be greater than 0 minutes."
                                }

                                else -> {
                                    localValidationMessage = null
                                    onSaveSettings(
                                        SettingsSnapshot(
                                            ankiConnectUrl = settings.ankiConnectUrl,
                                            noteModels = models,
                                            expressionField = expressionFieldText.trim(),
                                            readingField = readingFieldText.trim(),
                                            meaningField = meaningFieldText.trim(),
                                            matureDays = matureDays,
                                            kanjiSupportThreshold = supportThreshold,
                                            jitenCacheTtlHours = jitenCacheTtlHours,
                                            jitenRequestTimeoutSeconds = requestTimeoutSeconds,
                                            pollingEnabled = pollingEnabled,
                                            pollingIntervalSeconds = pollingIntervalMinutes * 60,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !settingsBusy,
                    ) {
                        Text("Save settings")
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Runtime boundary", style = MaterialTheme.typography.titleMedium)
                if (health == null) {
                    Text("Loading runtime info…")
                } else {
                    Text("Version: ${health.version}")
                    Text("DB path: ${health.databasePath}")
                    Text("Web app path: ${health.webAppPath}")
                    Text("Source counts: ${health.sourceCounts.noteCount} notes / ${health.sourceCounts.cardCount} cards")
                    val latestSync = health.latestSync
                    if (latestSync == null) {
                        Text("Latest sync: none recorded yet")
                    } else {
                        Text("Latest sync: ${latestSync.status} via ${latestSync.source}")
                        Text("Started: ${latestSync.startedAt}")
                        Text("Finished: ${latestSync.finishedAt ?: "in progress"}")
                        Text("Counts: ${latestSync.noteCount} notes / ${latestSync.cardCount} cards")
                        if (!latestSync.errorMessage.isNullOrBlank()) {
                            Text("Error: ${latestSync.errorMessage}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AnkiDroid live sync", style = MaterialTheme.typography.titleMedium)
                if (ankiDroidStatus == null) {
                    Text("Loading AnkiDroid provider status…")
                } else {
                    Text(ankiDroidStatus.message)
                    Text("Installed: ${if (ankiDroidStatus.installed) "yes" else "no"}")
                    Text("Permission granted: ${if (ankiDroidStatus.permissionGranted) "yes" else "no"}")
                    Text("Live collection readable: ${if (ankiDroidStatus.canReadCollection) "yes" else "no"}")
                    if (!ankiDroidStatus.packageName.isNullOrBlank()) {
                        Text("Package: ${ankiDroidStatus.packageName}")
                    }
                    if (!ankiDroidStatus.authority.isNullOrBlank()) {
                        Text("Provider authority: ${ankiDroidStatus.authority}")
                    }
                    if (!ankiDroidStatus.permissionName.isNullOrBlank()) {
                        Text("Runtime permission: ${ankiDroidStatus.permissionName}")
                    }
                }
                if (ankiDroidBusy) {
                    CircularProgressIndicator()
                }
                ankiDroidStatusMessage?.let { message ->
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = onRefreshAnkiDroidStatus,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ankiDroidBusy,
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
                            "Install AnkiDroid to enable live sync"
                        } else {
                            "AnkiDroid permission already satisfied"
                        },
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                if (currentAppVersion != null) {
                    Text("Current version: $currentAppVersion")
                }
                if (releaseBusy) {
                    CircularProgressIndicator()
                }
                if (releaseCheck == null) {
                    Text("No GitHub release check has completed yet.")
                } else {
                    Text("Feed: ${releaseCheck.releaseOwner}/${releaseCheck.releaseRepo}")
                    Text("Latest tag: ${releaseCheck.latestTag ?: "none"}")
                    Text(releaseCheck.statusMessage)
                    if (!releaseCheck.releaseNotes.isNullOrBlank()) {
                        Text(releaseCheck.releaseNotesPreview)
                    }
                }
                releaseStatusMessage?.let { message ->
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !releaseBusy,
                ) {
                    Text("Check GitHub release")
                }
                Button(
                    onClick = onInstallUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !releaseBusy && canInstallUpdate,
                ) {
                    Text(
                        if (canInstallUpdate) {
                            "Download and install $installLabel"
                        } else {
                            "No update available"
                        },
                    )
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
}
