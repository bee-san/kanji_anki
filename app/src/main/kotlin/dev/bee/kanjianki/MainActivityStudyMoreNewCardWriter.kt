package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsStudyModels

internal class MainActivityStudyMoreNewCardWriter(
    private val activity: MainActivityStudy,
) : StudyMoreNewCardActions.StudyItemWriter {
    override fun annotateSimilarKanjiAvailability(
        items: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        return activity.store.annotateSimilarKanjiAvailability(items)
    }

    override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
        activity.store.replaceStudyItems(items)
    }
}
