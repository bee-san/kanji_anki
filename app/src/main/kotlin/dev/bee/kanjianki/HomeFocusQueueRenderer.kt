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
    val model = if (mistakes.isEmpty()) {
        HomeRecentMistakesPanelModel(
            emptyTitle = HomeTextCopy.noRecentMistakesTitle(),
            emptyBody = HomeTextCopy.noRecentMistakesBody(),
            cards = emptyList(),
            emptyStyle = HomeEmptyStateStyle.LegacyBand
        )
    } else {
        homeRecentMistakesPanelModel(home, mistakes, home.store.activeDashboardRows())
    }
    home.content.addView(
        homeRecentMistakesScreenView(
            home,
            HomeRecentMistakesScreenModel(
                title = HomeTextCopy.recentMistakesTitle(),
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = home::renderHome,
                mistakes = model
            )
        )
    )
}

private const val RECENT_MISTAKE_LIMIT = 12
