package dev.bee.kanjianki.core

/** Deterministically collapses legacy same-kanji records without dropping review evidence. */
internal object StudyItemReconciliationPolicy {
    fun merge(
        left: RecordsStudyModels.StudyItem,
        right: RecordsStudyModels.StudyItem,
    ): RecordsStudyModels.StudyItem = mergeAll(listOf(left, right))

    fun mergeAll(items: List<RecordsStudyModels.StudyItem>): RecordsStudyModels.StudyItem {
        require(items.isNotEmpty()) { "Cannot reconcile an empty study-item family" }
        val kanji = items.first().kanji
        require(items.all { it.kanji == kanji }) { "Cannot reconcile different kanji" }
        val primary = items.maxWithOrNull(Comparator(::compareDurableEvidence))!!
        return primary.copyBuilder()
            .totalReviews(items.maxOf { it.totalReviews })
            .lapses(items.maxOf { it.lapses })
            .createdAtMillis(items.map { it.createdAtMillis }.filter { it > 0L }.minOrNull() ?: 0L)
            .typingMeaningMemory(mergeMemories(items.map { it.typingMeaningMemory }))
            .meaningKanjiMemory(mergeMemories(items.map { it.meaningKanjiMemory }))
            .kanjiMeaningMemory(mergeMemories(items.map { it.kanjiMeaningMemory }))
            .fontMeaningMemory(mergeMemories(items.map { it.fontMeaningMemory }))
            .wordReadingMemory(mergeMemories(items.map { it.wordReadingMemory }))
            .writingRemediationMemory(mergeMemories(items.map { it.writingRemediationMemory }))
            .similarKanjiMemory(mergeMemories(items.map { it.similarKanjiMemory }))
            .kanjiReadingMemory(mergeMemories(items.map { it.kanjiReadingMemory }))
            .readingKanjiMemory(mergeMemories(items.map { it.readingKanjiMemory }))
            .sentenceReadingMemory(mergeMemories(items.map { it.sentenceReadingMemory }))
            .lastRealReviewDueAtMillis(items.maxOf { it.lastRealReviewDueAtMillis })
            .routingVersion(items.maxOf { it.routingVersion })
            .adaptiveRouteStateJson(mergeRouteStates(items))
            .schedulerRevision(items.maxOf { it.schedulerRevision })
            .build()
    }

    private fun compareDurableEvidence(
        left: RecordsStudyModels.StudyItem,
        right: RecordsStudyModels.StudyItem,
    ): Int {
        compareValues(left.totalReviews, right.totalReviews).takeIf { it != 0 }?.let { return it }
        compareValues(taskReviewCount(left), taskReviewCount(right)).takeIf { it != 0 }?.let { return it }
        compareValues(latestTaskReview(left), latestTaskReview(right)).takeIf { it != 0 }?.let { return it }
        compareValues(left.lapses, right.lapses).takeIf { it != 0 }?.let { return it }
        compareValues(left.lastRealReviewDueAtMillis, right.lastRealReviewDueAtMillis)
            .takeIf { it != 0 }
            ?.let { return it }
        compareValues(left.schedulerRevision, right.schedulerRevision).takeIf { it != 0 }?.let { return it }
        return itemFingerprint(left).compareTo(itemFingerprint(right))
    }

    private fun taskReviewCount(item: RecordsStudyModels.StudyItem): Int {
        return memories(item).sumOf { it.totalReviews }
    }

    private fun latestTaskReview(item: RecordsStudyModels.StudyItem): Long {
        return memories(item).maxOfOrNull { maxOf(it.lastReviewedAtMillis, it.lastPassedDueAtMillis) } ?: 0L
    }

    private fun memories(item: RecordsStudyModels.StudyItem): List<RecordsStudyModels.TaskMemory> {
        return listOf(
            item.typingMeaningMemory,
            item.meaningKanjiMemory,
            item.kanjiMeaningMemory,
            item.fontMeaningMemory,
            item.wordReadingMemory,
            item.writingRemediationMemory,
            item.similarKanjiMemory,
            item.kanjiReadingMemory,
            item.readingKanjiMemory,
            item.sentenceReadingMemory,
        )
    }

    private fun mergeMemories(memories: List<RecordsStudyModels.TaskMemory>): RecordsStudyModels.TaskMemory {
        return memories.maxWithOrNull(Comparator(::compareMemory))!!
    }

    private fun compareMemory(
        left: RecordsStudyModels.TaskMemory,
        right: RecordsStudyModels.TaskMemory,
    ): Int {
        compareValues(left.totalReviews, right.totalReviews).takeIf { it != 0 }?.let { return it }
        compareValues(left.lastReviewedAtMillis, right.lastReviewedAtMillis).takeIf { it != 0 }?.let { return it }
        compareValues(left.lapses, right.lapses).takeIf { it != 0 }?.let { return it }
        compareValues(left.lastPassedDueAtMillis, right.lastPassedDueAtMillis)
            .takeIf { it != 0 }
            ?.let { return it }
        compareValues(left.dueAtMillis, right.dueAtMillis).takeIf { it != 0 }?.let { return it }
        return left.encode().compareTo(right.encode())
    }

    private fun mergeRouteStates(items: List<RecordsStudyModels.StudyItem>): String {
        val routes = items.mapNotNull { AdaptiveRouteStateCodec.decode(it.adaptiveRouteStateJson) }
        if (routes.isEmpty()) return ""
        val donor = routes.maxWithOrNull(Comparator(::compareRoute))!!
        val recurringFailureDonor = routes.filter { it.recurringFailure != null }
            .maxWithOrNull(Comparator(::compareRoute))
        val answerEvidenceDonor = routes.filter { it.answerEvidence != null }
            .maxWithOrNull(Comparator(::compareRoute))
        return AdaptiveRouteStateCodec.encode(
            donor.copy(
                recognitionReviewCount = routes.maxOf { it.recognitionReviewCount },
                contextualReadingReviewCount = routes.maxOf { it.contextualReadingReviewCount },
                recurringFailure = recurringFailureDonor?.recurringFailure,
                recurringFailureCount = routes.maxOf { it.recurringFailureCount },
                repairAttemptCount = routes.maxOf { it.repairAttemptCount },
                answerEvidence = answerEvidenceDonor?.answerEvidence,
            ),
        )
    }

    private fun compareRoute(left: AdaptiveRouteState, right: AdaptiveRouteState): Int {
        compareValues(routeActivity(left), routeActivity(right)).takeIf { it != 0 }?.let { return it }
        compareValues(left.repairAttemptCount, right.repairAttemptCount).takeIf { it != 0 }?.let { return it }
        compareValues(routeReviews(left), routeReviews(right)).takeIf { it != 0 }?.let { return it }
        compareValues(routeLatestTime(left), routeLatestTime(right)).takeIf { it != 0 }?.let { return it }
        return AdaptiveRouteStateCodec.encode(left).compareTo(AdaptiveRouteStateCodec.encode(right))
    }

    private fun routeActivity(route: AdaptiveRouteState): Int {
        return when {
            route.isRepairActive() -> 2
            route.revalidationPending -> 1
            else -> 0
        }
    }

    private fun routeReviews(route: AdaptiveRouteState): Int {
        return route.recognitionReviewCount + route.contextualReadingReviewCount
    }

    private fun routeLatestTime(route: AdaptiveRouteState): Long {
        return maxOf(route.coreDueAtMillis, route.repairDueAtMillis, route.repairStartedAtMillis)
    }

    private fun itemFingerprint(item: RecordsStudyModels.StudyItem): String {
        return buildString {
            append(item.state).append('|').append(item.dueAtMillis).append('|')
            append(item.stability).append('|').append(item.difficulty).append('|')
            append(item.learningStep).append('|').append(item.totalReviews).append('|').append(item.lapses).append('|')
            append(item.writingLevel).append('|').append(item.recognitionStage).append('|')
            append(item.consecutiveFailedRecognitionDays).append('|').append(item.lastFailedRecognitionDayMillis).append('|')
            append(item.writingRemediationPending).append('|').append(item.suppressedByTaskType).append('|')
            append(item.suppressedAtMillis).append('|').append(item.matureIntervalDays).append('|')
            append(item.realPassStreak).append('|').append(item.realAgainStreak).append('|')
            append(item.lastRealReviewDueAtMillis).append('|')
            append(item.rung?.wireName().orEmpty()).append('|').append(item.phase?.wireName().orEmpty()).append('|')
            append(item.hasSimilarKanji).append('|').append(item.hasKanjiReading).append('|')
            append(item.hasReadingKanji).append('|').append(item.hasSentenceReading).append('|')
            append(item.answerSignature).append('|').append(item.activeToken).append('|')
            append(item.routingVersion).append('|').append(item.adaptiveRouteStateJson).append('|')
            memories(item).forEach { append(it.encode()).append('|') }
        }
    }
}
