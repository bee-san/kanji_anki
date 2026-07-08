package dev.bee.kanjianki.core

/**
 * Filters raw persisted [RecordsStudyModels.StudyItem] rows down to the items a
 * reminder may legitimately count, mirroring [StudySessionSelector] semantics so
 * a "N reviews ready" notification never opens to an empty study queue (D2).
 *
 * The raw `study_items` table keeps `retired` rows (seeding retires instead of
 * deleting) and rows for kanji that are locally suspended or no longer on the
 * dashboard. [StudySessionSelector.activeQueueItems] already excludes those and
 * collapses each family to one active item; the same machinery is reused here so
 * the reminder's eligible set and the study screen's due set agree by
 * construction. A due time produced here matches what
 * [StudySessionSelector.dueCount] would count for the same fixture.
 */
object ReminderEligibilityPolicy {
    /**
     * Active, studyable items — retired excluded, off-dashboard / locally
     * suspended excluded, one active item per family. `nowMillis` and
     * `studyAheadMillis` do not change which items are returned (each family is a
     * singleton keyed by the table primary key), so callers may pass any value;
     * they only shape the family-activity tiebreak that never fires for a
     * single-item family.
     */
    @JvmStatic
    fun eligibleReminderItems(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsStudyModels.StudyItem> {
        return StudySessionSelector().activeQueueItems(
            items,
            rows,
            0L,
            0L,
            null,
            ladder,
        )
    }

    /**
     * The due-at times of [eligibleReminderItems], for feeding the review-batch
     * clustering and daily-plan policies.
     */
    @JvmStatic
    fun eligibleDueTimes(
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<Long> {
        return eligibleReminderItems(items, rows, ladder).map { it.dueAtMillis }
    }
}
