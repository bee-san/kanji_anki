package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.core.StudyTaskTypes

/** Exact validation boundary before private UI state is mounted onto a current study item. */
internal object StudySessionRestorationPolicy {
    fun restoreActive(
        snapshot: StudyActiveSessionSnapshot,
        items: List<RecordsStudyModels.StudyItem>,
        row: RecordsImportModels.DashboardRow?,
        ladder: RecordsBase.StudyLadderSettings,
        latestSuccessfulSyncAtMillis: Long,
        tokenConsumed: Boolean,
    ): RecordsSchedulerModels.StudySession? {
        if (snapshot.taskType !in RESTORABLE_ACTIVE_TASK_TYPES) return null
        if (tokenConsumed || row == null || row.kanji != snapshot.kanji) return null
        if (latestSuccessfulSyncAtMillis != snapshot.sourceSyncFinishedAtMillis) return null
        val item = items.singleOrNull {
            it.kanji == snapshot.kanji &&
                studyAnswerSignatureDigest(it.answerSignature) == snapshot.answerSignatureDigest
        } ?: return null
        if (item.state == MainActivityBase.STATE_RETIRED || item.suppressedByTaskType.isNotBlank()) return null
        if (item.activeToken != snapshot.sessionToken ||
            item.schedulerRevision != snapshot.schedulerRevision ||
            item.routingVersion != snapshot.routingVersion
        ) {
            return null
        }
        if (studyAnswerSignatureDigest(StudyQueueSeeder.answerSignature(row)) != snapshot.answerSignatureDigest) return null
        if (AdaptiveStudyItemPolicy.taskTypeFor(item, ladder) != snapshot.taskType) return null
        val prompt = when (snapshot.promptSource) {
            StudyPromptSource.REASON_TEXT -> row.reasonText
            StudyPromptSource.PRIMARY_MEANING -> row.primaryMeaning.ifBlank { row.reasonText }
        }
        return RecordsSchedulerModels.StudySession(
            item,
            row,
            snapshot.sessionToken,
            snapshot.taskType,
            writingRequired = false,
            prompt = prompt,
        )
    }

    private val RESTORABLE_ACTIVE_TASK_TYPES = setOf(
        StudyTaskTypes.TYPE_MEANING,
        StudyTaskTypes.TYPING_MEANING,
        StudyTaskTypes.TYPE_READING,
        StudyTaskTypes.KANJI_MEANING,
        StudyTaskTypes.FONT_MEANING,
        StudyTaskTypes.WORD_READING,
        StudyTaskTypes.SENTENCE_READING,
        StudyTaskTypes.SIMILAR_KANJI,
    )

    /**
     * Resolve an answered canonical card without ever falling back to the first same-kanji family.
     * A legacy v1 snapshot has no family/revision binding and is accepted only for one unique family.
     */
    fun restorePendingItem(
        snapshot: StudyPendingAnswerSnapshot,
        items: List<RecordsStudyModels.StudyItem>,
        row: RecordsImportModels.DashboardRow?,
        tokenConsumed: Boolean,
    ): RecordsStudyModels.StudyItem? {
        if (!tokenConsumed) return null
        val sameKanji = items.filter { it.kanji == snapshot.kanji }
        val signature = snapshot.answerSignature
        val revision = snapshot.schedulerRevision
        val item = if (signature == null || revision == null) {
            sameKanji.singleOrNull()
        } else {
            if (revision == Long.MAX_VALUE) return null
            sameKanji.singleOrNull { it.answerSignature == signature }
                ?.takeIf { it.schedulerRevision == revision + 1L }
        } ?: return null
        if (item.state == MainActivityBase.STATE_RETIRED) return null
        if (signature != null && (row == null || StudyQueueSeeder.answerSignature(row) != signature)) return null
        return item
    }
}
