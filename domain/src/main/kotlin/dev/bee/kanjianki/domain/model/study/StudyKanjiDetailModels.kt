package dev.bee.kanjianki.domain.model.study

data class StudyKanjiTimelineEvent(
    val id: Long,
    val kanji: String,
    val occurredAtMillis: Long,
    val eventType: String,
    val title: String,
    val detail: String,
    val sourceExpression: String,
    val sourceReading: String,
    val rating: String,
    val writingRequired: Boolean,
    val writingPassed: Boolean,
    val manualOverride: Boolean,
    val weaknessScore: Int?,
    val matureSupportCount: Int?,
    val syncId: Long?,
    val dedupeKey: String,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(occurredAtMillis >= 0L) { "occurredAtMillis must not be negative" }
    }
}

data class StudyKanjiRecoveryTimeline(
    val inventoryItem: StudyKanjiInventoryItem? = null,
    val currentRow: StudyDashboardRow? = null,
    val currentStudyItem: StudyQueueItem? = null,
    val events: List<StudyKanjiTimelineEvent> = emptyList(),
)
