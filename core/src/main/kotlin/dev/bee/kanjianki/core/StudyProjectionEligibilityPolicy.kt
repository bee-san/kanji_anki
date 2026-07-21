package dev.bee.kanjianki.core

/**
 * Shared eligibility boundary for projections derived from the study queue.
 *
 * Retired items remain persisted for history and can still have an overdue timestamp,
 * but they must not contribute to active plans or counts. Dashboard projections reuse the
 * seeder's admission, retirement, and reopening rules instead of treating every unseeded row
 * as current work.
 */
object StudyProjectionEligibilityPolicy {
    internal data class PlanningProjection(
        val rows: List<RecordsImportModels.DashboardRow>,
        val itemByKanji: Map<String, RecordsStudyModels.StudyItem>,
    )

    @JvmStatic
    fun eligibleStudyItems(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        return StudySessionSelector().activeQueueItems(
            items.orEmpty(),
            rows.orEmpty(),
            0L,
            0L,
            null,
            ladder,
        )
    }

    @JvmStatic
    fun eligibleDashboardKanji(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
    ): Set<String> {
        return eligibleDashboardKanji(rows, items, RecordsSyncModels.Settings.kikuDefaults(), null)
    }

    @JvmStatic
    fun eligibleDashboardKanji(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>?,
    ): Set<String> {
        return StudyQueueSeeder().currentRepairEligibleKanji(
            rows.orEmpty(),
            items.orEmpty(),
            settings ?: RecordsSyncModels.Settings.kikuDefaults(),
            evidenceStatusByKanji,
        )
    }

    @JvmStatic
    internal fun planningProjection(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
    ): PlanningProjection {
        val sourceRows = rows.orEmpty()
        if (sourceRows.isEmpty()) {
            return PlanningProjection(emptyList(), emptyMap())
        }

        val itemIndex = ProjectionItemIndex(items.orEmpty())
        val eligibleRows = ArrayList<RecordsImportModels.DashboardRow>(sourceRows.size)
        val activeByKanji = LinkedHashMap<String, RecordsStudyModels.StudyItem>()
        for (row in sourceRows) {
            val match = itemIndex.match(row)
            if (!match.hasPersistedItem || match.activeItem != null) {
                eligibleRows.add(row)
                match.activeItem?.let { activeByKanji[row.kanji] = it }
            }
        }
        return PlanningProjection(eligibleRows, activeByKanji)
    }

    private data class ItemMatch(
        val hasPersistedItem: Boolean,
        val activeItem: RecordsStudyModels.StudyItem?,
    )

    private class ProjectionItemIndex(items: List<RecordsStudyModels.StudyItem>) {
        private val knownFamilies = HashSet<String>()
        private val activeByFamily = HashMap<String, RecordsStudyModels.StudyItem>()
        private val legacyKanji = HashSet<String>()
        private val activeLegacyByKanji = HashMap<String, RecordsStudyModels.StudyItem>()

        init {
            for (item in items) {
                if (item.answerSignature.isEmpty()) {
                    legacyKanji.add(item.kanji)
                    if (item.state != StudyLadderRules.STATE_RETIRED) {
                        activeLegacyByKanji[item.kanji] = item
                    }
                } else {
                    val family = StudyQueueSeeder.familyKey(item)
                    knownFamilies.add(family)
                    if (item.state != StudyLadderRules.STATE_RETIRED) {
                        activeByFamily[family] = item
                    }
                }
            }
        }

        fun match(row: RecordsImportModels.DashboardRow): ItemMatch {
            val family = StudyQueueSeeder.rowFamilyKey(row)
            val exact = activeByFamily[family]
            val legacy = activeLegacyByKanji[row.kanji]
            return ItemMatch(
                hasPersistedItem = knownFamilies.contains(family) || legacyKanji.contains(row.kanji),
                activeItem = exact ?: legacy,
            )
        }
    }
}
