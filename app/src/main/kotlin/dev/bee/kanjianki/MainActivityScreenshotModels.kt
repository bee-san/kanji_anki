package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.SettingsTextCopy

internal fun screenshotGamesScreenModel(): GamesScreenModel {
    return GamesScreenModel(
        title = KanjiGameCopy.LABEL_GAMES,
        subtitle = KanjiGameCopy.GAMES_SUBTITLE,
        emptyTitle = null,
        emptyBody = null,
        showSyncButton = false,
        onSync = Runnable {},
        modeCards = KanjiGameEngine.GameMode.values().map { mode ->
            GamesModeCardModel(
                title = mode.title,
                label = mode.label,
                body = KanjiGameCopy.modeBody(mode, true),
                accentColor = screenshotGameColor(mode),
                available = true,
                chipLabel = KanjiGameCopy.LABEL_PLAY,
                onClick = Runnable {},
            )
        },
    )
}

internal fun screenshotMissingKanjiScreenModel(): MissingKanjiScreenModel {
    val rows = missingKanjiRows(
        listOf(
            MissingKanjiCandidate(
                literal = "朧",
                meanings = listOf("haze", "dimness"),
                onReadings = listOf("ロウ"),
                kunReadings = listOf("おぼろ"),
                jitenRank = 2_184,
            ),
            MissingKanjiCandidate(
                literal = "凪",
                meanings = listOf("calm", "lull"),
                onReadings = listOf("チ"),
                kunReadings = listOf("なぎ"),
                jitenRank = 2_641,
            ),
            MissingKanjiCandidate(
                literal = "憧",
                meanings = listOf("yearn", "long for"),
                onReadings = listOf("ショウ", "ドウ"),
                kunReadings = listOf("あこが.れる"),
                jitenRank = 2_903,
            ),
            MissingKanjiCandidate(
                literal = "燦",
                meanings = listOf("brilliant"),
                onReadings = listOf("サン"),
                jitenRank = 3_412,
            ),
        ),
    )
    return MissingKanjiScreenModel(
        content = MissingKanjiContentModel.Report(
            MissingKanjiReportUiModel(
                reportKey = "screenshot:1:5000:false",
                scan = MissingKanjiScanSummaryModel(
                    scanId = 1L,
                    completedAtMillis = 1_784_795_436_000L,
                    notesScanned = 12_480,
                    uniqueAnkiKanjiCount = 1_842,
                    skippedNotes = 0,
                ),
                eligibleDictionaryKanjiCount = 5_000,
                missingKanjiCount = rows.size,
                rows = rows,
                staleReason = null,
            ),
        ),
        providerAvailability = MissingKanjiProviderAvailability.READY,
        frequency = MissingKanjiFrequencyModel(
            preset = MissingKanjiPreset.TOP_5000,
            range = MissingKanjiFrequencyRange.TOP_5000,
            searchQuery = "",
        ),
        primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
        onHome = {},
        onPrimaryAction = {},
        onCancelScan = {},
        onRangeApplied = { _, _ -> },
        onRangePreview = { _, callback -> callback(5_000) },
        onSearchQueryChanged = {},
        initialSelectedLiterals = setOf("朧"),
        destinations = MissingKanjiDestinationModel(
            addToKaniEnabled = true,
            createAnkiDeckEnabled = true,
            csvExportEnabled = true,
            newPerDay = 5,
        ),
    )
}

internal fun screenshotSettingsScreenModel(activity: MainActivitySettings): SettingsScreenModel {
    return settingsScreenModel(
        cards = listOf(
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE,
                title = SettingsTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsTextCopy.settingsAnkiSourceBody(),
                iconRes = R.drawable.ic_book_24,
                panelCount = SettingsTextCopy.settingsCategoryPanelCount(4),
                contentDescription = SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAnkiSourceTitle()),
                onOpen = Runnable {},
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
                title = SettingsTextCopy.settingsStudyBehaviorTitle(),
                summary = SettingsTextCopy.settingsStudyBehaviorBody(),
                iconRes = R.drawable.ic_study_24,
                panelCount = SettingsTextCopy.settingsCategoryPanelCount(9),
                contentDescription = SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsStudyBehaviorTitle()),
                onOpen = Runnable {},
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE,
                title = SettingsTextCopy.settingsAutomationTitle(),
                summary = SettingsTextCopy.settingsAutomationBody(),
                iconRes = R.drawable.ic_sync_24,
                panelCount = SettingsTextCopy.settingsCategoryPanelCount(4),
                contentDescription = SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAutomationTitle()),
                onOpen = Runnable {},
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE,
                title = SettingsTextCopy.settingsAppearanceTitle(),
                summary = SettingsTextCopy.settingsAppearanceBody(),
                iconRes = R.drawable.ic_eye_24,
                panelCount = SettingsTextCopy.settingsCategoryPanelCount(1),
                contentDescription = SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAppearanceTitle()),
                onOpen = Runnable {},
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE,
                title = SettingsTextCopy.settingsReferenceDataTitle(),
                summary = SettingsTextCopy.settingsReferenceDataBody(),
                iconRes = R.drawable.ic_sparkle_24,
                panelCount = SettingsTextCopy.settingsCategoryPanelCount(1),
                contentDescription = SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsReferenceDataTitle()),
                onOpen = Runnable {},
            ),
        ),
        onHome = Runnable { activity.renderHome() },
    )
}

internal fun screenshotUpdatePageModel(activity: MainActivitySettings): SettingsUpdatePageModel {
    return SettingsUpdatePageModel(
        title = SettingsTextCopy.updatePageTitle(),
        onHome = activity::renderHome,
        onBack = { activity.renderSettingsAutomation(true) },
        onCheckForUpdate = {},
        panel = SettingsUpdatePanelModel(
            title = SettingsTextCopy.automaticUpdatesTitle(),
            statusLine = "Static screenshot fixture",
            statusColor = Color(0xFF00AEB5).toArgb(),
            lastCheckLine = "Last check: never",
            lastResultLine = "Last result: no update run",
            installPermissionLine = "Install permission: not needed in screenshots",
            installPermissionColor = Color(0xFF6C5674).toArgb(),
            hasPendingUpdate = false,
            pendingVersionLine = null,
            pendingMessageLine = null,
            canInstallUpdates = true,
            onInstallVerifiedUpdate = {},
            onOpenInstallSettings = {},
            onToggleAutomaticUpdates = {},
            automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(false),
            showAutoUpdateInBackground = false,
            autoUpdateInBackgroundLabel = SettingsTextCopy.autoUpdateInBackgroundLabel(),
            onAutoUpdateInBackground = {},
        ),
    )
}

private fun screenshotGameColor(mode: KanjiGameEngine.GameMode): Int {
    return when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> Color(0xFFFF4C76).toArgb()
        KanjiGameEngine.GameMode.READING_RUSH -> Color(0xFF00AEB5).toArgb()
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> Color(0xFF6E5CE6).toArgb()
        KanjiGameEngine.GameMode.MISS_SWEEP -> Color(0xFFFF4C76).toArgb()
    }
}
