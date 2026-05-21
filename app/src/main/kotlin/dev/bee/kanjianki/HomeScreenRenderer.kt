@file:JvmName("MainActivityHomeScreenRenderer")

package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy

internal fun renderHomeScreen(home: MainActivityHome) {
    home.clearStudyModeOverrides()

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

    val model = HomeScreenModel(
        title = HomeTextCopy.appTitle(),
        subtitle = HomeTextCopy.appSubtitle(),
        metrics = homeMetricModels(home, sync, provider, streak, homePlan),
        showSyncCta = rows.isEmpty(),
        syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
        studyLabel = MainActivityBase.LABEL_STUDY_NOW,
        studySubtitle = HomeTextCopy.studySupportText(),
        onSync = home::confirmSync,
        onStudy = home::startFocusedStudy,
        actions = homeActionModels(home),
        focusTitle = HomeTextCopy.focusQueueTitle(),
        focusActionLabel = if (rows.isEmpty()) null else HomeTextCopy.viewAllLabel(),
        onFocusAction = if (rows.isEmpty()) null else home::renderFocusQueue,
        emptyTitle = when {
            rows.isEmpty() -> HomeTextCopy.noKanjiQueuedTitle()
            entries.isEmpty() -> MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE
            else -> null
        },
        emptyBody = when {
            rows.isEmpty() -> HomeTextCopy.homeNoKanjiQueuedBody()
            entries.isEmpty() -> MainActivityBase.EMPTY_ACTIVE_PRACTICE_BODY
            else -> null
        },
        previewCards = entries.take(HOME_PREVIEW_ROW_LIMIT).map { entry ->
            homeFocusQueueCardModel(home, entry, now)
        }
    )
    home.composeRoute("home") {
        HomeScreen(model)
    }
}

private const val HOME_PREVIEW_ROW_LIMIT = 3
