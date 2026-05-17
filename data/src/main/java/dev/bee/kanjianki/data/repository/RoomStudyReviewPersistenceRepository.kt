package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.history.KanjiTimelineEventDao
import dev.bee.kanjianki.data.history.KanjiTimelineEventEntity
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.data.study.ReviewLogEntity
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyTaskLogDao
import dev.bee.kanjianki.data.study.StudyTaskLogEntity
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceInput
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceRepository
import dev.bee.kanjianki.domain.scheduler.StudyReviewRequest
import java.util.Calendar

class RoomStudyReviewPersistenceRepository internal constructor(
    private val studyItems: StudyItemDao,
    private val reviewLogs: ReviewLogDao,
    private val studyTaskLogs: StudyTaskLogDao,
    private val timelineEvents: KanjiTimelineEventDao,
    private val dashboardRows: DashboardRowDao,
    private val kanjiExamples: KanjiExampleDao,
    private val runInTransaction: suspend (suspend () -> Boolean) -> Boolean,
) : StudyReviewPersistenceRepository {
    constructor(
        database: KaniRoomDatabase,
    ) : this(
        studyItems = database.studyItemDao(),
        reviewLogs = database.reviewLogDao(),
        studyTaskLogs = database.studyTaskLogDao(),
        timelineEvents = database.kanjiTimelineEventDao(),
        dashboardRows = database.dashboardRowDao(),
        kanjiExamples = database.kanjiExampleDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun saveAppliedReview(input: StudyReviewPersistenceInput): Boolean =
        runInTransaction {
            val current = studyItems.get(
                kanji = input.before.kanji,
                answerSignature = input.before.answerSignature,
            ) ?: return@runInTransaction false
            val insertedReview = reviewLogs.insert(input.toReviewLogEntity())
            if (insertedReview == CONFLICT_IGNORED) {
                return@runInTransaction false
            }
            input.toStudyTaskLogEntity()?.let { studyTaskLogs.insert(it) }
            studyItems.upsert(current.withReviewUpdate(input.after))
            timelineEvents.upsert(input.toTimelineEventEntity())
            true
        }

    private suspend fun StudyReviewPersistenceInput.toTimelineEventEntity(): KanjiTimelineEventEntity {
        val row = dashboardRows.get(request.kanji)
        val source = kanjiExamples.firstTimelineExample(request.kanji)
        return KanjiTimelineEventEntity(
            kanji = request.kanji,
            occurredAt = reviewedAtMillis,
            eventType = timelineEventType(request, appliedRating),
            title = timelineTitle(request, appliedRating),
            detail = reviewDetail(request, appliedRating),
            sourceExpression = source?.expression.orEmpty(),
            sourceReading = source?.reading.orEmpty(),
            rating = appliedRating.wireName,
            writingRequired = request.writingRequired.toInt(),
            writingPassed = request.writingPassed.toInt(),
            manualOverride = request.manualOverride.toInt(),
            weaknessScore = row?.weaknessScore,
            matureSupportCount = row?.matureSupportCount,
            syncId = null,
            dedupeKey = "review:${request.token}",
        )
    }

    private fun StudyReviewPersistenceInput.toReviewLogEntity(): ReviewLogEntity =
        ReviewLogEntity(
            kanji = request.kanji,
            token = request.token,
            rating = appliedRating.wireName,
            writingRequired = request.writingRequired.toInt(),
            writingPassed = request.writingPassed.toInt(),
            manualOverride = request.manualOverride.toInt(),
            reviewedAt = reviewedAtMillis,
            reviewDayStart = localDayStart(reviewedAtMillis),
            taskType = request.taskType,
            answerSignature = request.answerSignature,
            prompt = request.prompt,
            hintsUsed = request.hintsUsed,
            writingClean = request.writingClean.toInt(),
            memoryBefore = before.taskMemoryText(request.taskType),
            memoryAfter = after.taskMemoryText(request.taskType),
            schedulerStateAfterJson = after.schedulerStateJson(),
        )

    private fun StudyReviewPersistenceInput.toStudyTaskLogEntity(): StudyTaskLogEntity? {
        val task = taskCompletion ?: return null
        if (task.taskKey.isEmpty()) {
            return null
        }
        return StudyTaskLogEntity(
            taskKey = task.taskKey,
            kanji = task.kanji,
            taskType = task.taskType,
            startedAt = task.startedAtMillis.coerceAtLeast(0L),
            answeredAt = reviewedAtMillis.coerceAtLeast(0L),
            activeElapsedMs = task.activeElapsedMillis.coerceIn(0L, MAX_STUDY_TASK_ELAPSED_MS),
            outcome = appliedRating.wireName,
        )
    }

    private suspend fun KanjiExampleDao.firstTimelineExample(kanji: String): KanjiExampleEntity? =
        listForTimeline(kanji, limit = 1).firstOrNull()

    private fun StudyQueueItem.taskMemoryText(taskType: String): String =
        if (taskType.isEmpty()) {
            ""
        } else {
            memories.memoryForTaskType(taskType).encode()
        }

    private fun StudyQueueItem.schedulerStateJson(): String =
        "{".plus("\"state\":").plus(state.wireName.jsonQuote())
            .plus(",\"due_at\":").plus(dueAtMillis)
            .plus(",\"stability\":").plus(stability)
            .plus(",\"difficulty\":").plus(difficulty)
            .plus(",\"total_reviews\":").plus(totalReviews)
            .plus(",\"lapses\":").plus(lapses)
            .plus(",\"learning_step\":").plus(learningStep)
            .plus(",\"writing_level\":").plus(writingLevel)
            .plus(",\"recognition_stage\":").plus(rung.toLegacyRecognitionStage())
            .plus(",\"writing_remediation_pending\":").plus(rung == StudyRung.WRITE_KANJI)
            .plus(",\"mature_interval_days\":").plus(matureIntervalDays)
            .plus("}")

    private fun timelineEventType(
        request: StudyReviewRequest,
        rating: StudyRating,
    ): String = when {
        request.manualOverride -> "manual_override"
        rating == StudyRating.AGAIN || request.writingRequired && !request.writingPassed -> "review_failed"
        else -> "review_passed"
    }

    private fun timelineTitle(
        request: StudyReviewRequest,
        rating: StudyRating,
    ): String = when (timelineEventType(request, rating)) {
        "manual_override" -> "Manual override"
        "review_failed" -> "Review failed"
        else -> "Review passed"
    }

    private fun reviewDetail(
        request: StudyReviewRequest,
        rating: StudyRating,
    ): String {
        val wireRating = rating.wireName
        if (request.manualOverride) {
            return "Saved as $wireRating after manual confirmation."
        }
        if (rating == StudyRating.AGAIN) {
            return if (request.writingRequired) {
                "Writing missed; Kani scheduled another try."
            } else {
                "Recall missed; Kani scheduled another try."
            }
        }
        if (request.writingRequired) {
            return if (request.writingPassed) {
                "Writing passed and was rated $wireRating."
            } else {
                "Writing was not passed and was rated $wireRating."
            }
        }
        return "Recall review was rated $wireRating."
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private fun String.jsonQuote(): String =
        buildString {
            append('"')
            for (char in this@jsonQuote) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char < ' ') {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }

    private fun localDayStart(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun StudyRung.toLegacyRecognitionStage(): Int = when (this) {
        StudyRung.TYPE_MEANING -> -1
        StudyRung.FONT_MEANING -> 1
        StudyRung.WORD_READING -> 2
        StudyRung.WRITE_KANJI,
        StudyRung.SIMILAR_KANJI,
        StudyRung.MEANING_KANJI,
        StudyRung.KANJI_MEANING -> 0
    }

    private companion object {
        const val CONFLICT_IGNORED = -1L
        const val MAX_STUDY_TASK_ELAPSED_MS = 30L * 60L * 1000L
    }
}
