@file:JvmName("MainActivityHomeFocusQueueRenderer")

package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy

internal fun renderFocusQueueScreen(home: MainActivityHome) {
    val now = System.currentTimeMillis()
    val rows = home.store.activeDashboardRows()
    val items = home.studyQueue(rows, now, false, null)
    val plan = if (rows.isEmpty()) null else home.adaptivePlan(rows, items, now)
    val entries = if (rows.isEmpty()) {
        emptyList()
    } else {
        home.queuedEntries(rows, items, now, plan)
    }

    val model = HomeFocusQueueScreenModel(
        title = HomeTextCopy.focusQueueTitle(),
        homeLabel = HomeTextCopy.homeLabel(),
        onHome = home::renderHome,
        queue = homeFocusQueuePanelModel(home, rows, entries, now, plan),
        onSync = home::confirmSync
    )
    home.renderHomeRoute {
        HomeFocusQueueScreen(model)
    }
}

internal fun renderRecentMistakesScreen(home: MainActivityHome) {
    val mistakes = home.store.recentMistakes(RECENT_MISTAKE_LIMIT)
    val mistakesModel = if (mistakes.isEmpty()) {
        HomeRecentMistakesPanelModel(
            emptyTitle = HomeTextCopy.noRecentMistakesTitle(),
            emptyBody = HomeTextCopy.noRecentMistakesBody(),
            cards = emptyList(),
            emptyStyle = HomeEmptyStateStyle.LegacyBand
        )
    } else {
        homeRecentMistakesPanelModel(home, mistakes, home.store.activeDashboardRows())
    }
    val model = HomeRecentMistakesScreenModel(
        title = HomeTextCopy.recentMistakesTitle(),
        homeLabel = HomeTextCopy.homeLabel(),
        onHome = home::renderHome,
        mistakes = mistakesModel
    )
    home.renderHomeRoute {
        HomeRecentMistakesScreen(model)
    }
}

private const val RECENT_MISTAKE_LIMIT = 12
