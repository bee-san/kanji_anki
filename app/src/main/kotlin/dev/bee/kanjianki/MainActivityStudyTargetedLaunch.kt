package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler

internal class MainActivityStudyTargetedLaunch(private val home: MainActivityStudy) {
    fun renderStudyForKanji(kanji: String?) {
        home.clearStudyModeOverrides()
        home.resetStudyRunProgress()
        home.base(MainActivityBase.NAV_STUDY)
        home.activeSimilarWritingRepair = null
        val rows = home.store.activeDashboardRows()
        val now = System.currentTimeMillis()
        home.activeStudyPlan = if (rows.isEmpty()) null else home.adaptivePlan(rows, home.store.studyItems(), now)
        val row = home.findRow(rows, kanji ?: "")
        if (row == null) {
            home.renderStudyForKanjiNotAvailable()
            return
        }
        val seeded = home.studyQueue(rows, now, true, home.activeStudyPlan)
        home.activeStudyPlan = home.adaptivePlan(rows, seeded, now)
        val session = BridgeScheduler().targetedSession(
            seeded,
            row,
            now,
            home.studyLadderSettings()
        )
        home.activeSession = session
        StudySessionActions.activateStudySession(
            session,
            now,
            home.store::saveStudyItem,
            home::registerStudyTaskShown,
            home::startActiveStudyTask
        )
        home.renderSession(session)
    }
}
