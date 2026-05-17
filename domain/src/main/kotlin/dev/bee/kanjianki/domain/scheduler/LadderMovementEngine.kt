package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung

class LadderMovementEngine {
    fun apply(input: LadderMovementInput): LadderMovementResult {
        val effectiveRung = input.settings.effectiveRung(
            current = input.currentRung,
            hasSimilarKanji = input.hasSimilarKanji,
        )
        if (!input.countsAsRealDueReview()) {
            return input.toResult(effectiveRung, LadderMovementType.NONE)
        }
        return if (input.rating == StudyRating.AGAIN) {
            applyAgain(input, effectiveRung)
        } else {
            applyPass(input, effectiveRung)
        }
    }

    private fun applyAgain(
        input: LadderMovementInput,
        effectiveRung: StudyRung,
    ): LadderMovementResult {
        val nextAgainStreak = input.realAgainStreak + 1
        if (nextAgainStreak >= input.settings.demotionFailStreak) {
            val demoted = input.settings.previousRung(effectiveRung, input.hasSimilarKanji)
            return LadderMovementResult(
                rung = demoted,
                realPassStreak = 0,
                realAgainStreak = 0,
                lastRealReviewDueAtMillis = input.dueAtMillis,
                movementType = if (demoted == effectiveRung) {
                    LadderMovementType.FLOOR
                } else {
                    LadderMovementType.DEMOTED
                },
            )
        }
        return LadderMovementResult(
            rung = effectiveRung,
            realPassStreak = 0,
            realAgainStreak = nextAgainStreak,
            lastRealReviewDueAtMillis = input.dueAtMillis,
            movementType = LadderMovementType.NONE,
        )
    }

    private fun applyPass(
        input: LadderMovementInput,
        effectiveRung: StudyRung,
    ): LadderMovementResult {
        if (input.fsrsScheduledIntervalMillis > input.settings.promotionThresholdMillis()) {
            val promoted = input.settings.nextRung(effectiveRung, input.hasSimilarKanji)
            return LadderMovementResult(
                rung = promoted,
                realPassStreak = 0,
                realAgainStreak = 0,
                lastRealReviewDueAtMillis = input.dueAtMillis,
                movementType = if (promoted == effectiveRung) {
                    LadderMovementType.CEILING
                } else {
                    LadderMovementType.PROMOTED
                },
            )
        }
        return LadderMovementResult(
            rung = effectiveRung,
            realPassStreak = input.realPassStreak + 1,
            realAgainStreak = 0,
            lastRealReviewDueAtMillis = input.dueAtMillis,
            movementType = LadderMovementType.NONE,
        )
    }

    private fun LadderMovementInput.countsAsRealDueReview(): Boolean {
        if (phase != StudyPhase.REVIEW) {
            return false
        }
        if (dueAtMillis > nowMillis) {
            return false
        }
        return lastRealReviewDueAtMillis == 0L || lastRealReviewDueAtMillis != dueAtMillis
    }

    private fun LadderMovementInput.toResult(
        effectiveRung: StudyRung,
        movementType: LadderMovementType,
    ): LadderMovementResult = LadderMovementResult(
        rung = effectiveRung,
        realPassStreak = realPassStreak,
        realAgainStreak = realAgainStreak,
        lastRealReviewDueAtMillis = lastRealReviewDueAtMillis,
        movementType = movementType,
    )

    private fun StudyLadderSettings.promotionThresholdMillis(): Long =
        maxOf(1, promotionIntervalDays) * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

data class LadderMovementInput(
    val currentRung: StudyRung,
    val phase: StudyPhase,
    val rating: StudyRating,
    val dueAtMillis: Long,
    val nowMillis: Long,
    val lastRealReviewDueAtMillis: Long,
    val realPassStreak: Int,
    val realAgainStreak: Int,
    val fsrsScheduledIntervalMillis: Long,
    val hasSimilarKanji: Boolean,
    val settings: StudyLadderSettings = StudyLadderSettings.defaults,
)

data class LadderMovementResult(
    val rung: StudyRung,
    val realPassStreak: Int,
    val realAgainStreak: Int,
    val lastRealReviewDueAtMillis: Long,
    val movementType: LadderMovementType,
)

enum class LadderMovementType {
    NONE,
    PROMOTED,
    DEMOTED,
    FLOOR,
    CEILING,
}

data class StudyLadderSettings(
    val orderedRungs: List<StudyRung> = StudyRung.defaultOrder,
    val enabledRungs: Set<StudyRung> = StudyRung.defaultEnabled,
    val promotionIntervalDays: Int = 21,
    val demotionFailStreak: Int = 3,
) {
    init {
        require(orderedRungs.toSet().containsAll(StudyRung.entries)) {
            "orderedRungs must include every study rung"
        }
        require(enabledRungs.any { it.alwaysAvailable }) {
            "enabledRungs must include at least one always-available rung"
        }
        require(promotionIntervalDays >= 1) { "promotionIntervalDays must be positive" }
        require(demotionFailStreak >= 1) { "demotionFailStreak must be positive" }
    }

    fun isValidForItem(rung: StudyRung, hasSimilarKanji: Boolean): Boolean =
        enabledRungs.contains(rung) && (rung != StudyRung.SIMILAR_KANJI || hasSimilarKanji)

    fun effectiveRung(current: StudyRung, hasSimilarKanji: Boolean): StudyRung {
        if (isValidForItem(current, hasSimilarKanji)) {
            return current
        }
        val start = orderedRungs.indexOf(current).takeIf { it >= 0 }
            ?: orderedRungs.indexOf(StudyRung.KANJI_MEANING).coerceAtLeast(0)
        for (distance in 1 until orderedRungs.size) {
            val before = start - distance
            if (before >= 0 && isValidForItem(orderedRungs[before], hasSimilarKanji)) {
                return orderedRungs[before]
            }
            val after = start + distance
            if (after < orderedRungs.size && isValidForItem(orderedRungs[after], hasSimilarKanji)) {
                return orderedRungs[after]
            }
        }
        return StudyRung.KANJI_MEANING
    }

    fun nextRung(current: StudyRung, hasSimilarKanji: Boolean): StudyRung {
        val effective = effectiveRung(current, hasSimilarKanji)
        val start = orderedRungs.indexOf(effective)
        return orderedRungs.drop(start + 1)
            .firstOrNull { isValidForItem(it, hasSimilarKanji) }
            ?: effective
    }

    fun previousRung(current: StudyRung, hasSimilarKanji: Boolean): StudyRung {
        val effective = effectiveRung(current, hasSimilarKanji)
        val start = orderedRungs.indexOf(effective)
        return orderedRungs.take(start).asReversed()
            .firstOrNull { isValidForItem(it, hasSimilarKanji) }
            ?: effective
    }

    companion object {
        val defaults = StudyLadderSettings()
    }
}
