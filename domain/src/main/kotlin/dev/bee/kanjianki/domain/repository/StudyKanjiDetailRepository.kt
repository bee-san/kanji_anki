package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyKanjiRecoveryTimeline

interface StudyKanjiDetailRepository {
    suspend fun timelineForKanji(
        kanji: String,
        eventLimit: Int,
    ): StudyKanjiRecoveryTimeline
}
