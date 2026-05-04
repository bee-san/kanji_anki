package dev.bee.kanjianki.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.bee.kanjianki.AppContainer
import dev.bee.kanjianki.data.ankidroid.AnkiDroidStatus
import dev.bee.kanjianki.data.update.ReleaseCheckResult
import dev.bee.kanjianki.data.update.UpdateInstallLaunchResult
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.HandwritingResult
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SessionMode
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewRequest
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.ui.screens.DashboardScreen
import dev.bee.kanjianki.ui.screens.DetailScreen
import dev.bee.kanjianki.ui.screens.SettingsScreen
import dev.bee.kanjianki.ui.screens.StudyScreen
import dev.bee.kanjianki.ui.theme.KanjiAnkiTheme
import java.io.File
import kotlinx.coroutines.launch

private enum class AppDestination(val label: String) {
    DASHBOARD("Dashboard"),
    STUDY("Study"),
    DETAIL("Detail"),
    SETTINGS("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanjiAnkiApp(container: AppContainer) {
    KanjiAnkiTheme {
        val scope = rememberCoroutineScope()
        var destination by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD) }
        var health by remember { mutableStateOf<HealthSnapshot?>(null) }
        var settings by remember { mutableStateOf<SettingsSnapshot?>(null) }
        var dashboard by remember { mutableStateOf<DashboardSnapshot?>(null) }
        var detail by remember { mutableStateOf<KanjiDetailSnapshot?>(null) }
        var overview by remember { mutableStateOf<StudyOverviewSnapshot?>(null) }
        var refreshResult by remember { mutableStateOf<SeedRefreshSnapshot?>(null) }
        var session by remember { mutableStateOf<StudySessionSnapshot?>(null) }
        var review by remember { mutableStateOf<StudyReviewSnapshot?>(null) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        var syncBusy by remember { mutableStateOf(false) }
        var syncStatusMessage by remember { mutableStateOf<String?>(null) }
        var ankiDroidStatus by remember { mutableStateOf<AnkiDroidStatus?>(null) }
        var ankiDroidBusy by remember { mutableStateOf(false) }
        var ankiDroidStatusMessage by remember { mutableStateOf<String?>(null) }
        var settingsBusy by remember { mutableStateOf(false) }
        var settingsStatusMessage by remember { mutableStateOf<String?>(null) }
        var releaseCheck by remember { mutableStateOf<ReleaseCheckResult?>(null) }
        var releaseBusy by remember { mutableStateOf(false) }
        var releaseStatusMessage by remember { mutableStateOf<String?>(null) }
        var downloadedReleaseApk by remember { mutableStateOf<File?>(null) }

        suspend fun loadPreferredDetail(
            preferredKanji: String?,
            latestDashboard: DashboardSnapshot?,
        ): KanjiDetailSnapshot? {
            val selectedKanji = latestDashboard?.rows
                ?.firstOrNull { it.kanji == preferredKanji }
                ?.kanji
                ?: latestDashboard?.rows?.firstOrNull { it.kanji == "学" }?.kanji
                ?: latestDashboard?.rows?.firstOrNull()?.kanji
            return if (selectedKanji != null) {
                container.useCases.getKanjiDetail(selectedKanji)
            } else {
                null
            }
        }

        suspend fun refreshAnkiDroidStatus() {
            runCatching {
                container.ankiDroidGateway.getStatus()
            }.onSuccess { status ->
                ankiDroidStatus = status
                ankiDroidStatusMessage = status.message
            }.onFailure { error ->
                ankiDroidStatus = null
                ankiDroidStatusMessage = error.message ?: "Failed to inspect AnkiDroid status."
            }
        }

        suspend fun refreshAfterSync(
            payloadDashboard: DashboardSnapshot,
            preferredKanji: String?,
        ): String {
            dashboard = payloadDashboard
            detail = loadPreferredDetail(preferredKanji, payloadDashboard)
            overview = container.useCases.getStudyOverview()
            health = container.useCases.getHealth()
            session = null
            review = null
            refreshResult = null
            refreshAnkiDroidStatus()
            return describeSyncSource(health?.latestSync?.source)
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            scope.launch {
                ankiDroidBusy = true
                ankiDroidStatusMessage = if (granted) {
                    "AnkiDroid permission granted. Refreshing the local cache from the live provider…"
                } else {
                    "AnkiDroid permission was not granted. The app will stay on the fixture fallback."
                }
                refreshAnkiDroidStatus()
                if (granted) {
                    val detailKanji = detail?.kanji
                    syncBusy = true
                    runCatching {
                        val payload = container.useCases.sync()
                        val sourceLabel = refreshAfterSync(payload.dashboard, detailKanji)
                        payload to sourceLabel
                    }.onSuccess { (payload, sourceLabel) ->
                        syncStatusMessage =
                            "Synced ${payload.sourceCounts.noteCount} notes / ${payload.sourceCounts.cardCount} cards via $sourceLabel."
                    }.onFailure { error ->
                        health = runCatching { container.useCases.getHealth() }.getOrNull() ?: health
                        syncStatusMessage = error.message ?: "Sync failed."
                    }
                    syncBusy = false
                }
                ankiDroidBusy = false
            }
        }

        LaunchedEffect(Unit) {
            refreshAnkiDroidStatus()
            runCatching {
                val loadedSettings = container.useCases.getSettings()
                loadedSettings?.let(container.syncScheduler::configurePolling)
                val loadedDashboard = container.useCases.getDashboard()
                val loadedDetail = loadPreferredDetail(null, loadedDashboard)
                val loadedOverview = container.useCases.getStudyOverview()
                val loadedHealth = container.useCases.getHealth()
                Quintuple(
                    loadedSettings,
                    loadedDashboard,
                    loadedDetail,
                    loadedOverview,
                    loadedHealth,
                )
            }.onSuccess { loaded ->
                settings = loaded.first
                dashboard = loaded.second
                detail = loaded.third
                overview = loaded.fourth
                health = loaded.fifth
            }.onFailure { error ->
                health = runCatching { container.useCases.getHealth() }.getOrNull()
                syncStatusMessage = error.message ?: "Failed to load the Android cache."
            }
            releaseBusy = true
            releaseStatusMessage = "Checking GitHub releases on launch…"
            runCatching {
                container.releaseUpdater.checkForUpdate()
            }.onSuccess { result ->
                releaseCheck = result
                downloadedReleaseApk = null
                releaseStatusMessage = result.statusMessage
            }.onFailure { error ->
                releaseStatusMessage = error.message
            }
            releaseBusy = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kanji Anki Android Spike") },
                )
            },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { target ->
                        NavigationBarItem(
                            selected = destination == target,
                            onClick = { destination = target },
                            label = { Text(target.label) },
                            icon = {},
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (destination) {
                    AppDestination.DASHBOARD -> DashboardScreen(
                        dashboard = dashboard,
                        selectedKanji = detail?.kanji,
                        syncBusy = syncBusy,
                        syncStatusMessage = syncStatusMessage,
                        onSyncNow = {
                            val detailKanji = detail?.kanji
                            scope.launch {
                                syncBusy = true
                                syncStatusMessage = "Syncing the Android collection snapshot into Room…"
                                runCatching {
                                    val payload = container.useCases.sync()
                                    val sourceLabel = refreshAfterSync(payload.dashboard, detailKanji)
                                    payload to sourceLabel
                                }.onSuccess { (payload, sourceLabel) ->
                                    syncStatusMessage =
                                        "Synced ${payload.sourceCounts.noteCount} notes / ${payload.sourceCounts.cardCount} cards via $sourceLabel."
                                }.onFailure { error ->
                                    health = runCatching { container.useCases.getHealth() }.getOrNull() ?: health
                                    refreshAnkiDroidStatus()
                                    syncStatusMessage = error.message ?: "Sync failed."
                                }
                                syncBusy = false
                            }
                        },
                        onOpenDetail = { kanji ->
                            scope.launch {
                                detail = container.useCases.getKanjiDetail(kanji)
                                destination = AppDestination.DETAIL
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppDestination.DETAIL -> DetailScreen(detail = detail, modifier = Modifier.fillMaxSize())
                    AppDestination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        health = health,
                        settingsBusy = settingsBusy,
                        settingsStatusMessage = settingsStatusMessage,
                        ankiDroidStatus = ankiDroidStatus,
                        ankiDroidBusy = ankiDroidBusy,
                        ankiDroidStatusMessage = ankiDroidStatusMessage,
                        onRefreshAnkiDroidStatus = {
                            scope.launch {
                                ankiDroidBusy = true
                                ankiDroidStatusMessage = "Refreshing AnkiDroid status…"
                                refreshAnkiDroidStatus()
                                ankiDroidBusy = false
                            }
                        },
                        onRequestAnkiDroidPermission = {
                            val permissionName = ankiDroidStatus?.permissionName
                            if (permissionName.isNullOrBlank()) {
                                ankiDroidStatusMessage =
                                    "No AnkiDroid runtime permission is available to request."
                            } else {
                                permissionLauncher.launch(permissionName)
                            }
                        },
                        onSaveSettings = { updatedSettings ->
                            val detailKanji = detail?.kanji
                            scope.launch {
                                settingsBusy = true
                                settingsStatusMessage = "Saving Android settings and refreshing local sync…"
                                val savedSettings = runCatching {
                                    container.useCases.updateSettings(updatedSettings)
                                }.onFailure { error ->
                                    settingsStatusMessage =
                                        "Failed to save settings: ${error.message ?: "unknown error"}"
                                }.getOrNull()
                                if (savedSettings != null) {
                                    settings = savedSettings
                                    container.syncScheduler.configurePolling(savedSettings)
                                    runCatching {
                                        val payload = container.useCases.sync()
                                        val sourceLabel = refreshAfterSync(payload.dashboard, detailKanji)
                                        payload to sourceLabel
                                    }.onSuccess { (payload, sourceLabel) ->
                                        syncStatusMessage =
                                            "Synced ${payload.sourceCounts.noteCount} notes / ${payload.sourceCounts.cardCount} cards after the settings change via $sourceLabel."
                                        settingsStatusMessage =
                                            "Saved settings and resynced the local Room cache."
                                    }.onFailure { error ->
                                        health = runCatching { container.useCases.getHealth() }.getOrNull() ?: health
                                        refreshAnkiDroidStatus()
                                        syncStatusMessage = error.message ?: "Sync failed."
                                        settingsStatusMessage =
                                            "Saved settings, but the follow-up sync failed: ${error.message ?: "unknown error"}"
                                    }
                                }
                                settingsBusy = false
                            }
                        },
                        releaseCheck = releaseCheck,
                        releaseBusy = releaseBusy,
                        releaseStatusMessage = releaseStatusMessage,
                        onCheckForUpdates = {
                            scope.launch {
                                releaseBusy = true
                                releaseStatusMessage = "Checking GitHub releases…"
                                runCatching {
                                    container.releaseUpdater.checkForUpdate()
                                }.onSuccess { result ->
                                    releaseCheck = result
                                    downloadedReleaseApk = null
                                    releaseStatusMessage = result.statusMessage
                                }.onFailure { error ->
                                    releaseStatusMessage = error.message
                                }
                                releaseBusy = false
                            }
                        },
                        onInstallUpdate = {
                            val latestRelease = releaseCheck
                            if (latestRelease == null || !latestRelease.updateAvailable || !latestRelease.hasApkAsset) {
                                releaseStatusMessage = "No installable GitHub update is available."
                            } else {
                                scope.launch {
                                    releaseBusy = true
                                    releaseStatusMessage = "Downloading ${latestRelease.apkAssetName}…"
                                    val apkFile = runCatching {
                                        downloadedReleaseApk?.takeIf { it.exists() }
                                            ?: container.releaseUpdater.downloadLatestApk(latestRelease)
                                    }.onFailure { error ->
                                        releaseStatusMessage = error.message
                                    }.getOrNull()
                                    if (apkFile != null) {
                                        downloadedReleaseApk = apkFile
                                        when (val result = container.releaseUpdater.launchInstall(apkFile)) {
                                            is UpdateInstallLaunchResult.StartedInstaller -> {
                                                releaseStatusMessage =
                                                    "Installer opened for ${result.file.name}."
                                            }

                                            is UpdateInstallLaunchResult.OpenedPermissionSettings -> {
                                                releaseStatusMessage = result.message
                                            }
                                        }
                                    }
                                    releaseBusy = false
                                }
                            }
                        },
                        onOpenReleasePage = {
                            val latestRelease = releaseCheck
                            if (latestRelease == null) {
                                releaseStatusMessage = "No GitHub release has been loaded yet."
                            } else {
                                container.releaseUpdater.openReleasePage(latestRelease)
                                releaseStatusMessage =
                                    "Opened ${latestRelease.releaseOwner}/${latestRelease.releaseRepo} releases."
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppDestination.STUDY -> StudyScreen(
                        overview = overview,
                        refreshResult = refreshResult,
                        session = session,
                        review = review,
                        statusMessage = statusMessage,
                        onRefreshSeeds = {
                            scope.launch {
                                refreshResult = container.useCases.refreshSeeds()
                                overview = container.useCases.getStudyOverview()
                                session = null
                                review = null
                                statusMessage = "Rebuilt the local study queue from cached dashboard seeds."
                            }
                        },
                        onLoadNewSession = {
                            scope.launch {
                                session = container.useCases.createSession(SessionMode.NEW)
                                review = null
                                statusMessage = if (session != null) {
                                    "Loaded the next new-item session from the local Room queue."
                                } else {
                                    "No new introductions are currently available."
                                }
                            }
                        },
                        onLoadMixedSession = {
                            scope.launch {
                                session = container.useCases.createSession(SessionMode.MIXED)
                                review = null
                                statusMessage = if (session != null) {
                                    "Loaded the next mixed session from the local Room queue."
                                } else {
                                    "No due reviews or new introductions are waiting."
                                }
                            }
                        },
                        onLoadReviewSession = {
                            scope.launch {
                                session = container.useCases.createSession(SessionMode.REVIEW)
                                review = null
                                statusMessage = if (session != null) {
                                    "Loaded the next due review from the local Room queue."
                                } else {
                                    "No due review sessions are waiting."
                                }
                            }
                        },
                        onSubmitPass = {
                            val activeSession = session
                            if (activeSession != null) {
                                scope.launch {
                                    review = container.useCases.submitReview(
                                        StudyReviewRequest(
                                            kanji = activeSession.kanji,
                                            reviewToken = activeSession.reviewToken,
                                            promptType = activeSession.promptType,
                                            rating = "good",
                                            hintsUsed = 0,
                                            handwritingResult = HandwritingResult(
                                                attempted = true,
                                                passed = true,
                                                score = 0.95,
                                                evaluationMode = "guided",
                                            ),
                                        ),
                                    )
                                    overview = container.useCases.getStudyOverview()
                                    session = null
                                    statusMessage =
                                        "Recorded a local pass review for ${activeSession.kanji}."
                                }
                            }
                        },
                        onSubmitRetry = {
                            val activeSession = session
                            if (activeSession != null) {
                                scope.launch {
                                    review = runCatching {
                                        container.useCases.submitReview(
                                            StudyReviewRequest(
                                                kanji = activeSession.kanji,
                                                reviewToken = activeSession.reviewToken,
                                                promptType = activeSession.promptType,
                                                rating = "again",
                                                hintsUsed = 0,
                                                handwritingResult = HandwritingResult(
                                                    attempted = true,
                                                    passed = false,
                                                    score = 0.11,
                                                    evaluationMode = "manual-override",
                                                    selfAssessment = "override-retry",
                                                ),
                                            ),
                                        )
                                    }.onFailure { error ->
                                        statusMessage = error.message
                                    }.getOrNull()
                                    if (review != null) {
                                        overview = container.useCases.getStudyOverview()
                                        session = null
                                        statusMessage =
                                            "Recorded a local retry review for ${activeSession.kanji}."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)

private fun describeSyncSource(source: String?): String =
    when (source) {
        "ankidroid-content-provider" -> "the live AnkiDroid content provider"
        "parity-fixture-fallback", "parity-fixture" -> "the parity fixture fallback"
        null -> "the Android collection source"
        else -> source
    }
