package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
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
    }
}
