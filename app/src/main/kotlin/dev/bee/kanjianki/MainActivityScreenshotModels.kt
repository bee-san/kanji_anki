package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.SettingsTextCopy

internal fun screenshotStatsScreenModel(): StatsScreenModel {
    return StatsScreenModel(
        title = "Stats",
        intro = "Static screenshot mode renders the stats layout without loading the store or launcher startup work.",
        verdict = StatsCardModel(
            title = "Working",
            body = "The screenshot route is deterministic and ready for CI capture.",
            fillColor = STATS_VERDICT_WORKING_FILL,
            strokeColor = STATS_TEAL_COLOR,
            emptyState = false,
            titleColor = STATS_TEAL_COLOR,
            bodyColor = STATS_INK_COLOR,
            titleSizeSp = 24,
            bodySizeSp = 15,
        ),
        sections = listOf(
            StatsCardModel(
                title = "Weak kanji trend",
                summary = "3 weak kanji improved",
                body = "These examples keep the first card readable on a phone screen.",
                lines = listOf(
                    StatsLineModel("川 12.4 → 8.2"),
                    StatsLineModel("海 11.8 → 7.9"),
                    StatsLineModel("森 10.5 → 7.1"),
                ),
                strokeColor = STATS_TEAL_COLOR,
            ),
            StatsCardModel(
                title = "Anki support",
                summary = "2 mature cards gained",
                body = "Enough mature support has accumulated to keep the loop moving.",
                lines = listOf(
                    StatsLineModel("復 gained 2 mature cards"),
                    StatsLineModel("語 gained 1 mature card"),
                ),
                strokeColor = STATS_BLUE_COLOR,
            ),
            StatsCardModel(
                title = "Ladder status",
                summary = "6 active kanji on the ladder",
                body = "Promotion and demotion counts stay visible without touching the database.",
                lines = listOf(
                    StatsLineModel("Promotion ready: 2"),
                    StatsLineModel("Demotion risk: 1"),
                    StatsLineModel("Inactive: 3"),
                ),
                strokeColor = STATS_GOLD_COLOR,
            ),
        ),
    )
}

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
        hero = SettingsAutomationHeroModel(
            cockpitLabel = SettingsTextCopy.settingsCockpitLabel(),
            title = MainActivityBase.NAV_SETTINGS,
            body = SettingsTextCopy.settingsHeroBody(),
            rows = listOf(
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.noteTypeStatusLabel(),
                        "Sample note type",
                        SettingsAutomationHeroColors.studyPlum,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.importFiltersStatusLabel(),
                        "All cards",
                        SettingsAutomationHeroColors.teal,
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.importRanksStatusLabel(),
                        "10-25",
                        SettingsAutomationHeroColors.teal,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.reminderStatusLabel(),
                        "Off",
                        SettingsAutomationHeroColors.muted,
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.dailySyncStatusLabel(),
                        "Manual only",
                        SettingsAutomationHeroColors.studyPinkDark,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.updatesStatusLabel(),
                        "Ready",
                        SettingsAutomationHeroColors.coral,
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.matchingCardsStatusLabel(),
                        "2,048 items",
                        SettingsAutomationHeroColors.studyPlum,
                    ),
                ),
            ),
        ),
        categories = listOf(
            settingsCategorySectionModel(
                sectionKey = "settings-anki-source",
                title = SettingsTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsTextCopy.settingsAnkiSourceBody(),
                iconRes = R.drawable.ic_book_24,
                expanded = false,
                onToggle = Runnable {},
                panels = emptyList(),
            ),
            settingsCategorySectionModel(
                sectionKey = "settings-study-behavior",
                title = SettingsTextCopy.settingsStudyBehaviorTitle(),
                summary = SettingsTextCopy.settingsStudyBehaviorBody(),
                iconRes = R.drawable.ic_study_24,
                expanded = false,
                onToggle = Runnable {},
                panels = emptyList(),
            ),
            settingsCategorySectionModel(
                sectionKey = "settings-automation",
                title = SettingsTextCopy.settingsAutomationTitle(),
                summary = SettingsTextCopy.settingsAutomationBody(),
                iconRes = R.drawable.ic_sync_24,
                expanded = false,
                onToggle = Runnable {},
                panels = emptyList(),
            ),
            settingsCategorySectionModel(
                sectionKey = "settings-reference-data",
                title = SettingsTextCopy.settingsReferenceDataTitle(),
                summary = SettingsTextCopy.settingsReferenceDataBody(),
                iconRes = R.drawable.ic_sparkle_24,
                expanded = true,
                onToggle = Runnable {},
                panels = listOf(MainActivitySettingsReferenceData(activity).dataLicenseSettingsPanelModel()),
            ),
        ),
        onHome = Runnable { activity.renderHome() },
    )
}

internal fun screenshotUpdatePageModel(activity: MainActivitySettings): SettingsUpdatePageModel {
    return SettingsUpdatePageModel(
        title = SettingsTextCopy.updatePageTitle(),
        body = SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME),
        onHome = activity::renderHome,
        onBack = { activity.renderSettings(true) },
        onCheckForUpdate = {},
        panel = SettingsUpdatePanelModel(
            title = SettingsTextCopy.automaticUpdatesTitle(),
            statusLine = "Static screenshot fixture",
            statusColor = Color(0xFF00AEB5),
            lastCheckLine = "Last check: never",
            lastResultLine = "Last result: no update run",
            installPermissionLine = "Install permission: not needed in screenshots",
            installPermissionColor = Color(0xFF6C5674),
            hasPendingUpdate = false,
            pendingVersionLine = null,
            pendingMessageLine = null,
            canInstallUpdates = true,
            onInstallVerifiedUpdate = {},
            onOpenInstallSettings = {},
            onToggleAutomaticUpdates = {},
            automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(false),
        ),
    )
}

private fun screenshotGameColor(mode: KanjiGameEngine.GameMode): Int {
    return when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> GamesCoral.toArgb()
        KanjiGameEngine.GameMode.READING_RUSH -> GamesTeal.toArgb()
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> GamesBlue.toArgb()
    }
}
