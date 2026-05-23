package dev.bee.kanjianki.core

internal class TargetedStudySessionPolicy {
    fun targetedSession(
        seededItems: List<RecordsStudyModels.StudyItem>?,
        row: RecordsImportModels.DashboardRow?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsSchedulerModels.StudySession? {
        if (row == null) {
            return null
        }
        val item = targetedStudyItem(seededItems, row.kanji, nowMillis, ladder)
        val token = StudyTokenPolicy.studyItem(item.kanji, item.activeToken)
        val effectiveItem = StudyLadderRules.alignRungToLadder(item, ladder).withToken(token)
        return RecordsSchedulerModels.StudySession(
            effectiveItem,
            row,
            token,
            StudyTaskTypes.forRung(effectiveItem.rung),
            effectiveItem.rung == RecordsBase.LadderRung.WRITE_KANJI,
            promptFor(row),
        )
    }

    fun targetedStudyItem(
        seededItems: List<RecordsStudyModels.StudyItem>?,
        kanji: String?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsStudyModels.StudyItem {
        val item = StudyCollectionLookup.studyItemByKanji(seededItems, kanji)
        return item ?: newTargetedStudyItem(kanji, nowMillis, ladder)
    }

    fun newTargetedStudyItem(
        kanji: String?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsStudyModels.StudyItem {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        return RecordsStudyModels.StudyItem(
            kanji,
            StudyLadderRules.STATE_NEW,
            nowMillis,
            0.4,
            5.0,
            0,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            nowMillis,
        ).withRung(safeLadder.startingRung(false))
    }

    private fun promptFor(row: RecordsImportModels.DashboardRow): String {
        return if (row.primaryMeaning.isEmpty()) row.reasonText else row.primaryMeaning
    }
}
