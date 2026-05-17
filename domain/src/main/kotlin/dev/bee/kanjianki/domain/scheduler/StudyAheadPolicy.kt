package dev.bee.kanjianki.domain.scheduler

class StudyAheadPolicy(
    private val maxStudyAheadMillis: Long = DAY_MILLIS,
) {
    init {
        require(maxStudyAheadMillis > 0L) { "maxStudyAheadMillis must be positive" }
    }

    fun clamp(studyAheadMillis: Long): Long = when {
        studyAheadMillis <= 0L -> 0L
        studyAheadMillis > maxStudyAheadMillis -> maxStudyAheadMillis
        else -> studyAheadMillis
    }

    fun horizon(
        nowMillis: Long,
        studyAheadMillis: Long,
    ): Long = nowMillis + clamp(studyAheadMillis)

    fun isDueWithinHorizon(
        dueAtMillis: Long,
        nowMillis: Long,
        studyAheadMillis: Long,
    ): Boolean = dueAtMillis <= horizon(nowMillis, studyAheadMillis)

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
