package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsStudyModels

/**
 * Field-by-field study item/queue equivalence, shared by the queue seeding path
 * (skip the persistence write when nothing changed) and the LocalStore diff writer
 * (upsert only the rows that actually changed instead of delete-all + reinsert).
 */
internal object StudyItemComparators {
    @JvmStatic
    fun sameStudyQueue(
        current: List<RecordsStudyModels.StudyItem>,
        annotated: List<RecordsStudyModels.StudyItem>,
    ): Boolean {
        if (current.size != annotated.size) {
            return false
        }
        for (i in current.indices) {
            if (!sameStudyItem(current[i], annotated[i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun sameStudyItem(
        current: RecordsStudyModels.StudyItem,
        annotated: RecordsStudyModels.StudyItem,
    ): Boolean {
        return current.kanji == annotated.kanji &&
            current.state == annotated.state &&
            current.dueAtMillis == annotated.dueAtMillis &&
            current.stability == annotated.stability &&
            current.difficulty == annotated.difficulty &&
            current.totalReviews == annotated.totalReviews &&
            current.lapses == annotated.lapses &&
            current.learningStep == annotated.learningStep &&
            current.writingLevel == annotated.writingLevel &&
            current.recognitionStage == annotated.recognitionStage &&
            current.consecutiveFailedRecognitionDays == annotated.consecutiveFailedRecognitionDays &&
            current.lastFailedRecognitionDayMillis == annotated.lastFailedRecognitionDayMillis &&
            current.writingRemediationPending == annotated.writingRemediationPending &&
            current.suppressedByTaskType == annotated.suppressedByTaskType &&
            current.suppressedAtMillis == annotated.suppressedAtMillis &&
            current.matureIntervalDays == annotated.matureIntervalDays &&
            current.answerSignature == annotated.answerSignature &&
            current.activeToken == annotated.activeToken &&
            current.createdAtMillis == annotated.createdAtMillis &&
            sameTaskMemory(current.typingMeaningMemory, annotated.typingMeaningMemory) &&
            sameTaskMemory(current.meaningKanjiMemory, annotated.meaningKanjiMemory) &&
            sameTaskMemory(current.kanjiMeaningMemory, annotated.kanjiMeaningMemory) &&
            sameTaskMemory(current.fontMeaningMemory, annotated.fontMeaningMemory) &&
            sameTaskMemory(current.wordReadingMemory, annotated.wordReadingMemory) &&
            sameTaskMemory(current.writingRemediationMemory, annotated.writingRemediationMemory) &&
            current.rung == annotated.rung &&
            current.phase == annotated.phase &&
            current.realPassStreak == annotated.realPassStreak &&
            current.realAgainStreak == annotated.realAgainStreak &&
            current.lastRealReviewDueAtMillis == annotated.lastRealReviewDueAtMillis &&
            current.hasSimilarKanji == annotated.hasSimilarKanji &&
            sameTaskMemory(current.similarKanjiMemory, annotated.similarKanjiMemory)
    }

    private fun sameTaskMemory(
        current: RecordsStudyModels.TaskMemory,
        annotated: RecordsStudyModels.TaskMemory,
    ): Boolean {
        return current.encode() == annotated.encode()
    }
}
