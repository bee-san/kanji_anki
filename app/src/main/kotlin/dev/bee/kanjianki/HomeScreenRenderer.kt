@file:JvmName("MainActivityHomeScreenRenderer")

package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.HomeTextCopy

internal fun renderHomeScreen(home: MainActivityHome) {
    home.clearStudyModeOverrides()
    home.base("home")

    val now = System.currentTimeMillis()
    val sync = home.store.latestSync()
    val streak = home.store.studyStreak(now)
    val rows = home.store.activeDashboardRows()
    val homeItems = home.studyQueue(rows, now, false, null)
    val homePlan = if (rows.isEmpty()) null else home.adaptivePlan(rows, homeItems, now)
    val entries = if (rows.isEmpty()) {
        emptyList()
    } else {
        home.queuedEntries(rows, homeItems, now, homePlan)
    }
    val provider = home.gateway.status()

    home.content.addView(home.homeHeader())
    home.addSpace(12)
    home.content.addView(home.homeMetricRow(sync, provider, streak, homePlan))
    home.addSpace(14)

    if (rows.isEmpty()) {
        home.content.addView(home.homeSyncCta())
    } else {
        val studyButton: View = home.homeStudyCta()
        studyButton.setOnClickListener(RunnableClickListener(home::startFocusedStudy))
        home.content.addView(studyButton)
    }

    home.content.addView(home.homeActionRow())
    home.addSpace(16)
    home.content.addView(
        home.homeSectionHeader(
            HomeTextCopy.focusQueueTitle(),
            if (rows.isEmpty()) null else HomeTextCopy.viewAllLabel(),
            if (rows.isEmpty()) null else home::renderFocusQueue
        )
    )

    if (rows.isEmpty()) {
        home.emptyState(HomeTextCopy.noKanjiQueuedTitle(), HomeTextCopy.homeNoKanjiQueuedBody())
        return
    }

    if (entries.isEmpty()) {
        home.emptyState(MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE, MainActivityBase.EMPTY_ACTIVE_PRACTICE_BODY)
    }
    entries.take(HOME_PREVIEW_ROW_LIMIT).forEach { entry ->
        home.content.addView(home.queueRowView(entry, now))
    }
}

private const val HOME_PREVIEW_ROW_LIMIT = 3
