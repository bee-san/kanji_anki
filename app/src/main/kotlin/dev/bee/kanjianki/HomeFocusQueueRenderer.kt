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

    home.content.addView(home.homeSectionHeader(HomeTextCopy.focusQueueTitle(), HomeTextCopy.homeLabel(), home::renderHome))
    home.content.addView(homeFocusQueueContentView(home, rows, entries, now, plan))
}

internal fun renderRecentMistakesScreen(home: MainActivityHome) {
    home.base("home")
    home.content.addView(home.homeSectionHeader(HomeTextCopy.recentMistakesTitle(), HomeTextCopy.homeLabel(), home::renderHome))

    val mistakes = home.store.recentMistakes(RECENT_MISTAKE_LIMIT)
    if (mistakes.isEmpty()) {
        home.emptyState(HomeTextCopy.noRecentMistakesTitle(), HomeTextCopy.noRecentMistakesBody())
        return
    }

    val rows = home.store.activeDashboardRows()
    home.content.addView(homeRecentMistakesContentView(home, mistakes, rows))
}

private const val RECENT_MISTAKE_LIMIT = 12
