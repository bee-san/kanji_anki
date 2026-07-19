package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules

internal object BrowseManualReviewPolicy {
    fun shouldOfferReview(
        hasDashboardRow: Boolean,
        suspended: Boolean,
        item: RecordsStudyModels.StudyItem?,
    ): Boolean = hasDashboardRow && !suspended && item?.state != StudyLadderRules.STATE_RETIRED

    fun selectSeededTarget(
        items: List<RecordsStudyModels.StudyItem>,
        kanji: String,
    ): RecordsStudyModels.StudyItem? = items.firstOrNull {
        it.kanji == kanji && it.state != StudyLadderRules.STATE_RETIRED
    }
}
