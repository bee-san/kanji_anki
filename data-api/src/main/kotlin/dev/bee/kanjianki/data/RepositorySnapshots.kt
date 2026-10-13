package dev.bee.kanjianki.data

/** Stable repository-facing projection of the latest persisted sync run. */
data class SyncStatusSnapshot(
    val status: String,
    val activeNotes: Int,
    val activeCards: Int,
    val suspendedCards: Int,
    val importedKanji: Int,
    val finishedAtMillis: Long,
    val errorMessage: String,
    val removalMessage: String,
)

/** Stable repository-facing study streak projection. */
data class StudyStreakSnapshot(
    val currentDays: Int,
    val bestDays: Int,
    val studiedToday: Boolean,
    val reviewsToday: Int,
    val lastStudyAtMillis: Long,
)

data class AdaptiveWorkloadSnapshot(
    val workPercent: Int,
    val maxItems: Int,
    val mode: String,
)
