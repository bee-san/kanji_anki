package dev.bee.kanjianki.core

import dev.bee.kanjianki.core.RecordsStudyModels.StudyItem

/**
 * Resolves the lost-update race between a running sync and a review the user saves
 * mid-sync.
 *
 * A sync reads the current study items (the *baseline*), computes freshly seeded
 * items in memory, then replaces all study items in a later transaction. If the
 * user completes a review between the baseline read and the replace, the seeded
 * item carries pre-review scheduler state and would silently overwrite the saved
 * review (the `review_log` row survives, but FSRS memory / rung / streak regress).
 *
 * This policy detects, per study-item lineage, whether the persisted row changed
 * since the baseline was read. A lineage normally has the same `(kanji,
 * answerSignature)` key, but a same-meaning preferred-example reshuffle may move
 * the signature during sync. If a review landed, its scheduler state wins while
 * the seeded signature is adopted; otherwise the freshly seeded item is used.
 */
object MidSyncReviewMergePolicy {
    /**
     * @param seeded items the sync computed and intends to write.
     * @param baseline the study items the sync read before seeding (pre-sync state).
     * @param persisted the study items currently in the database, re-read inside the
     *   write transaction (may include reviews saved after [baseline] was read).
     * @return the items to persist: seeded items, except where a mid-sync review is
     *   detected, in which case the persisted post-review item is kept.
     */
    @JvmStatic
    fun merge(
        seeded: List<StudyItem>,
        baseline: List<StudyItem>,
        persisted: List<StudyItem>,
    ): List<StudyItem> {
        if (persisted.isEmpty()) {
            return seeded
        }
        val result = ArrayList<StudyItem>(seeded.size)
        for (item in seeded) {
            val baselineItem = canonicalKanjiItem(item.kanji, baseline)
            val current = canonicalKanjiItem(item.kanji, persisted)
            if (current != null &&
                StudyItemLineagePolicy.meaningCompatible(item, current) &&
                reviewLandedMidSync(baselineItem, current)
            ) {
                // Preserve the post-review scheduler state, but move it to the
                // identity selected by this sync. The persistence boundary will
                // then bump the revision because the key change is material.
                result.add(current.copyBuilder().answerSignature(item.answerSignature).build())
            } else {
                result.add(item)
            }
        }
        return result
    }

    private fun canonicalKanjiItem(kanji: String, candidates: List<StudyItem>): StudyItem? {
        var canonical: StudyItem? = null
        for (candidate in candidates) {
            if (candidate.kanji != kanji) {
                continue
            }
            val current = canonical
            if (current == null || isNewer(candidate, current)) {
                canonical = candidate
            }
        }
        return canonical
    }

    private fun isNewer(candidate: StudyItem, current: StudyItem): Boolean {
        return when {
            candidate.schedulerRevision != current.schedulerRevision -> candidate.schedulerRevision > current.schedulerRevision
            candidate.totalReviews != current.totalReviews -> candidate.totalReviews > current.totalReviews
            candidate.createdAtMillis != current.createdAtMillis -> candidate.createdAtMillis < current.createdAtMillis
            else -> false
        }
    }

    /**
     * True when [current] shows review evidence that [baseline] did not — i.e. a real
     * review was persisted after the baseline snapshot was taken. Compares the total
     * review count and the last real-review due slot, which both advance only when a
     * review is saved.
     */
    private fun reviewLandedMidSync(baseline: StudyItem?, current: StudyItem): Boolean {
        if (baseline == null) {
            // No pre-sync counterpart. The sync is introducing this family fresh, so
            // prefer its seed rather than resurrecting a persisted row it means to
            // reset. Reviews of existing review cards (the real race) always have a
            // baseline entry, so they are handled below.
            return false
        }
        return current.schedulerRevision > baseline.schedulerRevision ||
            current.totalReviews > baseline.totalReviews ||
            current.lastRealReviewDueAtMillis != baseline.lastRealReviewDueAtMillis
    }
}
