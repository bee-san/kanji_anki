package dev.bee.kanjianki.core

/**
 * Field-by-field study item/queue equivalence, shared by the queue seeding path
 * (skip the persistence write when nothing changed) and the LocalStore diff writer
 * (upsert only the rows that actually changed instead of delete-all + reinsert).
 */
object StudyItemComparators {
    private data class StudyItemKey(val kanji: String, val answerSignature: String)

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

    /**
     * Persistence equivalence for callers whose list order is only an artifact of
     * a capped query or reconciliation. The database's due-time read order must not
     * turn an unchanged durable set into a write on every Home refresh.
     */
    @JvmStatic
    fun sameStudyItemsIgnoringOrder(
        current: List<RecordsStudyModels.StudyItem>,
        candidate: List<RecordsStudyModels.StudyItem>,
    ): Boolean {
        if (current.size != candidate.size) {
            return false
        }
        val remaining = HashMap<StudyItemKey, RecordsStudyModels.StudyItem>(current.size)
        for (item in current) {
            if (remaining.put(StudyItemKey(item.kanji, item.answerSignature), item) != null) {
                return false
            }
        }
        for (item in candidate) {
            val match = remaining.remove(StudyItemKey(item.kanji, item.answerSignature)) ?: return false
            if (!sameStudyItem(match, item)) {
                return false
            }
        }
        return remaining.isEmpty()
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
            sameTaskMemory(current.similarKanjiMemory, annotated.similarKanjiMemory) &&
            current.hasKanjiReading == annotated.hasKanjiReading &&
            sameTaskMemory(current.kanjiReadingMemory, annotated.kanjiReadingMemory) &&
            current.hasReadingKanji == annotated.hasReadingKanji &&
            sameTaskMemory(current.readingKanjiMemory, annotated.readingKanjiMemory) &&
            current.hasSentenceReading == annotated.hasSentenceReading &&
            sameTaskMemory(current.sentenceReadingMemory, annotated.sentenceReadingMemory) &&
            current.schedulerRevision == annotated.schedulerRevision &&
            current.routingVersion == annotated.routingVersion &&
            current.adaptiveRouteStateJson == annotated.adaptiveRouteStateJson
    }

    /**
     * Equality of the columns actually stored in study_items, excluding the
     * CAS revision itself and read-time conditional-rung annotations.
     */
    @JvmStatic
    fun samePersistedState(
        current: RecordsStudyModels.StudyItem,
        candidate: RecordsStudyModels.StudyItem,
    ): Boolean {
        return sameStudyItem(normalizePersistenceComparison(current), normalizePersistenceComparison(candidate))
    }

    private fun normalizePersistenceComparison(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        return item.copyBuilder()
            .schedulerRevision(0L)
            .hasSimilarKanji(false)
            .hasKanjiReading(false)
            .hasReadingKanji(false)
            .hasSentenceReading(false)
            .build()
    }

    private fun sameTaskMemory(
        current: RecordsStudyModels.TaskMemory,
        annotated: RecordsStudyModels.TaskMemory,
    ): Boolean {
        return current.encode() == annotated.encode()
    }
}
