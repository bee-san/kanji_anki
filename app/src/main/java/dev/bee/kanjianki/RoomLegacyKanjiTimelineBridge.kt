package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyKanjiDetailRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomLegacyKanjiTimelineBridge @Inject constructor(
    private val studyKanjiDetailRepository: StudyKanjiDetailRepository,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
) {
    fun canReadTimeline(): Boolean =
        ownershipPolicy.canReadStudyRuntimeFromRoom()

    suspend fun timelineForKanji(
        kanji: String?,
        eventLimit: Int = DEFAULT_EVENT_LIMIT,
    ): RecordsStudyModels.KanjiRecoveryTimeline {
        requireCanRead()
        val normalizedKanji = kanji?.trim().orEmpty()
        if (normalizedKanji.isEmpty()) {
            return emptyTimeline()
        }
        return LegacyStudyMappers.toLegacy(
            studyKanjiDetailRepository.timelineForKanji(
                kanji = normalizedKanji,
                eventLimit = eventLimit.coerceAtLeast(0),
            ),
        )
    }

    @JvmOverloads
    fun timelineForKanjiBlocking(
        kanji: String?,
        eventLimit: Int = DEFAULT_EVENT_LIMIT,
    ): RecordsStudyModels.KanjiRecoveryTimeline = runBlocking {
        timelineForKanji(kanji, eventLimit)
    }

    private fun requireCanRead() {
        check(canReadTimeline()) {
            "Room timeline reads require completed legacy reset/migration or active double-write ownership."
        }
    }

    private fun emptyTimeline(): RecordsStudyModels.KanjiRecoveryTimeline =
        RecordsStudyModels.KanjiRecoveryTimeline(null, null, null, emptyList())

    companion object {
        const val DEFAULT_EVENT_LIMIT = 50
    }
}
