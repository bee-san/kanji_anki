package dev.bee.kanjianki.core

/**
 * Prevents a scoped reconciliation from physically deleting scheduler rows for
 * kanji that were outside its input. Queue seeding produces one canonical current
 * row per kanji, so a candidate for the same kanji is authoritative across an
 * answer-signature change, including an intentional retired candidate produced by
 * [StudyQueueSeeder].
 */
object DurableStudyItemRetentionPolicy {
    @JvmStatic
    fun retainUnseeded(
        seeded: List<RecordsStudyModels.StudyItem>,
        persisted: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        if (persisted.isEmpty()) {
            return seeded
        }
        val seededKanji = seeded.mapTo(HashSet()) { it.kanji }
        val omitted = persisted.filter { it.kanji !in seededKanji }
        if (omitted.isEmpty()) {
            return seeded
        }
        val retained = ArrayList<RecordsStudyModels.StudyItem>(seeded.size + omitted.size)
        retained.addAll(seeded)
        retained.addAll(omitted)
        return retained
    }
}
