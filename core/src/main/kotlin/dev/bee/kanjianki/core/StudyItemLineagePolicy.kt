package dev.bee.kanjianki.core

import dev.bee.kanjianki.core.RecordsStudyModels.StudyItem

/**
 * Matches a seeded study item to the persisted family it was derived from.
 *
 * An answer signature includes the preferred expression and reading. A sync can
 * legitimately change those fields while preserving the meaning and all earned
 * scheduler state. In that case the database key moves, but the item is still the
 * same scheduler lineage. Exact keys always win; a moved key is accepted only when
 * there is one unambiguous, meaning-compatible item for the kanji.
 */
object StudyItemLineagePolicy {
    @JvmStatic
    fun counterpart(target: StudyItem, candidates: List<StudyItem>): StudyItem? {
        for (candidate in candidates) {
            if (sameKey(target, candidate)) {
                return candidate
            }
        }

        var match: StudyItem? = null
        for (candidate in candidates) {
            if (candidate.kanji != target.kanji ||
                !answerMeaningCompatible(target.answerSignature, candidate.answerSignature)
            ) {
                continue
            }
            if (match != null) {
                return null
            }
            match = candidate
        }
        return match
    }

    @JvmStatic
    fun meaningCompatible(left: StudyItem, right: StudyItem): Boolean =
        left.kanji == right.kanji && answerMeaningCompatible(left.answerSignature, right.answerSignature)

    private fun sameKey(left: StudyItem, right: StudyItem): Boolean =
        left.kanji == right.kanji && left.answerSignature == right.answerSignature

    private fun answerMeaningCompatible(left: String, right: String): Boolean {
        if (left == right || left.isEmpty() || right.isEmpty()) {
            return true
        }
        val leftMeaning = signatureMeaning(left) ?: return false
        val rightMeaning = signatureMeaning(right) ?: return false
        return leftMeaning == rightMeaning
    }

    private fun signatureMeaning(signature: String): String? {
        val parts = signature.split("|", limit = 4)
        return if (parts.size == 4) parts[3] else null
    }
}
