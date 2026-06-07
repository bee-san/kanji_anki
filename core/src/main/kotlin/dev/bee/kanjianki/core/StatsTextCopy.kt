package dev.bee.kanjianki.core

import java.util.Locale
import kotlin.math.max

object StatsTextCopy {
    @JvmStatic
    fun verdictWorking(weakKanjiImproved: Int, matureSupportGained: Int): Boolean {
        return weakKanjiImproved > 0 || matureSupportGained > 0
    }

    @JvmStatic
    fun verdictHasLadder(totalActiveItems: Int): Boolean {
        return totalActiveItems > 0
    }

    @JvmStatic
    fun verdictTitle(working: Boolean): String {
        return if (working) "Kani is working" else "Waiting for evidence"
    }

    @JvmStatic
    fun verdictBody(
        hasStats: Boolean,
        working: Boolean,
        hasLadder: Boolean,
        weakKanjiImproved: Int,
        matureSupportGained: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        totalActiveItems: Int,
    ): String {
        if (!hasStats) {
            return "Study and sync for trends."
        }
        if (working) {
            return workingVerdictBody(
                weakKanjiImproved,
                matureSupportGained,
                promotionReadyCount,
                demotionRiskCount,
            )
        }
        if (hasLadder) {
            return "Tracking " +
                StudyTextCopy.countText(totalActiveItems, "active kanji", "active kanji") +
                ". Trends need reviews and sync."
        }
        return "Review and sync to compare."
    }

    @JvmStatic
    fun ladderHealthBody(
        totalActiveItems: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        demotionReadyCount: Int,
        ladderPromotionIntervalDays: Int,
        ladderDemotionFailStreak: Int,
    ): String {
        if (totalActiveItems == 0) {
            return "Sync or study weak kanji to fill the ladder."
        }
        var body = StudyTextCopy.countText(
            promotionReadyCount,
            "ready to climb",
            "ready to climb",
        ) +
            " · " +
            StudyTextCopy.countText(
                demotionRiskCount,
                "at risk",
                "at risk",
            )
        if (demotionReadyCount > 0) {
            body += " · " +
                StudyTextCopy.countText(
                    demotionReadyCount,
                    "ready to fall",
                    "ready to fall",
                )
        }
        return body +
            ". Climb after more than " +
            ladderPromotionIntervalDays +
            " days; fall after " +
            ladderDemotionFailStreak +
            " misses."
    }

    @JvmStatic
    fun ladderDistributionRow(rung: RecordsBase.LadderRung, count: Int): String {
        return ladderRungLabel(rung) + ": " + count
    }

    @JvmStatic
    fun ladderRungLabel(rung: RecordsBase.LadderRung): String {
        return when (rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> "Write kanji"
            RecordsBase.LadderRung.TYPE_MEANING -> "Type meaning"
            RecordsBase.LadderRung.SIMILAR_KANJI -> "Similar kanji"
            RecordsBase.LadderRung.MEANING_KANJI -> "Meaning kanji"
            RecordsBase.LadderRung.KANJI_MEANING -> "Kanji meaning"
            RecordsBase.LadderRung.FONT_MEANING -> "Font meaning"
            RecordsBase.LadderRung.WORD_READING -> "Word reading"
        }
    }

    @JvmStatic
    fun weaknessImprovementBody(
        improvedCount: Int,
        averageBeforeWeakness: Double,
        averageAfterWeakness: Double,
    ): String {
        if (improvedCount == 0) {
            return "Weakness trends need reviews and sync."
        }
        return "Average weakness: " +
            formatWeakness(averageBeforeWeakness) +
            " -> " +
            formatWeakness(averageAfterWeakness) +
            "."
    }

    @JvmStatic
    fun weaknessImprovementExample(kanji: String?, beforeWeakness: Double, afterWeakness: Double): String {
        return clean(kanji) + "  " + formatWeakness(beforeWeakness) + " -> " + formatWeakness(afterWeakness)
    }

    @JvmStatic
    fun supportGainExample(kanji: String?, beforeMatureSupport: Int, afterMatureSupport: Int): String {
        return clean(kanji) + "  " + beforeMatureSupport + " -> " + afterMatureSupport + " mature cards"
    }

    @JvmStatic
    fun notHelpingRowText(
        kanji: String?,
        reviewCount: Int,
        sameCardCount: Int,
        retentionDelta: Double,
        difficultyDelta: Double,
    ): String {
        return clean(kanji) +
            "  " +
            reviewCount +
            " Kani reviews · " +
            sameCardCount +
            " same-card checks · retention " +
            formatSignedPercent(retentionDelta) +
            " · difficulty " +
            formatSignedDecimal(difficultyDelta)
    }

    @JvmStatic
    fun notHelpingBody(noImpactEvidence: Boolean, hasNotHelpingRows: Boolean): String {
        if (noImpactEvidence) {
            return "Review and sync to compare."
        }
        if (!hasNotHelpingRows) {
            return "No kanji need attention right now."
        }
        return "Needs enough reviews and sync."
    }

    @JvmStatic
    fun studyStreakSummary(currentDays: Int): String {
        return if (currentDays <= 0) "No active streak" else "$currentDays-day streak"
    }

    @JvmStatic
    fun studyStreakBody(
        bestDays: Int,
        studiedToday: Boolean,
        reviewsToday: Int,
        lastStudyAtMillis: Long,
        nowMillis: Long,
    ): String {
        if (bestDays <= 0 && !studiedToday && reviewsToday <= 0 && lastStudyAtMillis <= 0L) {
            return "Study and sync to start a streak."
        }
        val today = if (studiedToday) {
            StudyTextCopy.countText(reviewsToday, "review today", "reviews today")
        } else {
            "No reviews today"
        }
        val lastStudy = if (lastStudyAtMillis <= 0L) {
            "No study yet"
        } else {
            elapsedSinceLabel(nowMillis, lastStudyAtMillis)
        }
        return "Best streak " +
            StudyTextCopy.countText(bestDays, "day", "days") +
            ". " +
            today +
            ". Last study " +
            lastStudy +
            "."
    }

    @JvmStatic
    fun studyImpactBody(
        totalReviews: Int,
        distinctReviewedKanji: Int,
        writingRequired: Int,
        writingPassed: Int,
        writingFailed: Int,
        manualOverrides: Int,
    ): String {
        if (totalReviews <= 0) {
            return "Study and sync to start measuring impact."
        }
        val reviewSummary =
            StudyTextCopy.countText(totalReviews, "review", "reviews") +
                " across " +
                StudyTextCopy.countText(distinctReviewedKanji, "kanji", "kanji")
        val writingSummary = if (writingRequired <= 0) {
            "No writing prompts yet"
        } else {
            "Writing prompts: " +
                writingPassed +
                " passed, " +
                writingFailed +
                " failed, " +
                StudyTextCopy.countText(manualOverrides, "manual override", "manual overrides")
        }
        return reviewSummary + ". " + writingSummary + "."
    }

    @JvmStatic
    fun recentMistakesBody(hasMistakes: Boolean): String {
        return if (hasMistakes) {
            "Recent misses worth another pass."
        } else {
            "No recent mistakes right now."
        }
    }

    @JvmStatic
    fun recentMistakeRowText(
        kanji: String?,
        rating: String?,
        reviewedAtMillis: Long,
        nowMillis: Long,
    ): String {
        return clean(kanji) +
            "  " +
            recentMistakeRatingLabel(rating) +
            " · " +
            elapsedSinceLabel(nowMillis, reviewedAtMillis)
    }

    @JvmStatic
    fun formatWeakness(weakness: Double): String {
        return String.format(Locale.ROOT, "%.2f", weakness)
    }

    @JvmStatic
    fun formatSignedPercent(value: Double): String {
        return String.format(Locale.ROOT, "%+.0f%%", value * 100.0)
    }

    @JvmStatic
    fun formatSignedDecimal(value: Double): String {
        return String.format(Locale.ROOT, "%+.1f", value)
    }

    @JvmStatic
    fun formatStudyTime(millis: Long): String {
        val seconds = maxOf(0L, Math.round(millis / 1000.0))
        if (seconds < 60L) {
            return "$seconds sec"
        }
        val minutes = seconds / 60L
        val remainingSeconds = seconds % 60L
        if (minutes < 60L) {
            return if (remainingSeconds == 0L) "$minutes min" else "$minutes min $remainingSeconds sec"
        }
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return if (remainingMinutes == 0L) "$hours hr" else "$hours hr $remainingMinutes min"
    }

    private fun workingVerdictBody(
        weakKanjiImproved: Int,
        matureSupportGained: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
    ): String {
        val signals = mutableListOf<String>()
        if (weakKanjiImproved > 0) {
            signals += StudyTextCopy.countText(
                weakKanjiImproved,
                "weak kanji improved",
                "weak kanji improved",
            )
        }
        if (matureSupportGained > 0) {
            signals += StudyTextCopy.countText(
                matureSupportGained,
                "mature card gained",
                "mature cards gained",
            )
        }
        if (promotionReadyCount > 0) {
            signals += StudyTextCopy.countText(
                promotionReadyCount,
                "review item ready to climb",
                "review items ready to climb",
            )
        }
        var body = signals.joinToString(". ") + "."
        if (demotionRiskCount > 0) {
            body += " Watch " +
                StudyTextCopy.countText(
                    demotionRiskCount,
                    "review item with a miss streak",
                    "review items with miss streaks",
                ) +
                "."
        }
        return body
    }

    private fun clean(value: String?): String {
        return value ?: ""
    }

    private fun elapsedSinceLabel(nowMillis: Long, pastMillis: Long): String {
        val elapsed = max(0L, nowMillis - pastMillis)
        return if (elapsed == 0L) {
            "just now"
        } else {
            formatStudyTime(elapsed) + " ago"
        }
    }

    private fun recentMistakeRatingLabel(rating: String?): String {
        val cleaned = clean(rating)
        if (cleaned.isBlank()) {
            return "Mistake"
        }
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
