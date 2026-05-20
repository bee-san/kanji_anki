@file:JvmName("MainActivityHomeFocusQueueRenderer")

package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy

internal fun renderFocusQueueScreen(home: MainActivityHome) {
    home.base("home")

    val now = System.currentTimeMillis()
    val rows = home.store.activeDashboardRows()
    val items = home.studyQueue(rows, now, false, null)
    val plan = if (rows.isEmpty()) null else home.adaptivePlan(rows, items, now)
    val entries = if (rows.isEmpty()) {
        emptyList()
    } else {
        home.queuedEntries(rows, items, now, plan)
    }

    home.content.addView(
        homeFocusQueueScreenView(
            home,
            HomeFocusQueueScreenModel(
                title = HomeTextCopy.focusQueueTitle(),
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = home::renderHome,
                queue = homeFocusQueuePanelModel(home, rows, entries, now, plan),
                onSync = home::confirmSync
            )
        )
    )
}

internal fun renderRecentMistakesScreen(home: MainActivityHome) {
    home.base("home")

    val mistakes = home.store.recentMistakes(RECENT_MISTAKE_LIMIT)
    val rows = home.store.activeDashboardRows()
    home.content.addView(
        homeRecentMistakesScreenView(
            home,
            HomeRecentMistakesScreenModel(
                title = HomeTextCopy.recentMistakesTitle(),
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = home::renderHome,
                mistakes = homeRecentMistakesPanelModel(home, mistakes, rows).copy(
                    emptyStyle = HomeEmptyStateStyle.LegacyBand
                )
            )
        )
    )
}

private const val RECENT_MISTAKE_LIMIT = 12
