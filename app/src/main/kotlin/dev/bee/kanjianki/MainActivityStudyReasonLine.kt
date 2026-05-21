package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyReasonLine(private val home: MainActivityStudy) {
    fun studyReasonLine(session: RecordsSchedulerModels.StudySession): String {
        return StudyTextCopy.studyReasonLine(
            home.activeSimilarWritingRepair != null,
            session,
            home.settings().matureSupportThreshold,
            System.currentTimeMillis()
        )
    }
}
