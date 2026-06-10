package dev.bee.kanjianki.core

import java.util.Locale
import kotlin.math.max

object StatsTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun statsTitle(): String = localizedText("Stats", "統計")

    @JvmStatic
    fun weakKanjiTrendTitle(): String = localizedText("Weak kanji trend", "弱い漢字の推移")

    @JvmStatic
    fun weakKanjiImprovedSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "weak kanji improved", "weak kanji improved"),
            "弱い漢字${count}件が改善",
        )

    @JvmStatic
    fun ankiSupportTitle(): String = localizedText("Anki support", "Ankiの支え")

    @JvmStatic
    fun matureSupportSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "mature card gained", "mature cards gained"),
            "成熟カード${count}件が増加",
        )

    @JvmStatic
    fun firstMatureSupportSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "kanji gained first mature support", "kanji gained first mature support"),
            "漢字${count}件が初めて成熟サポートを獲得",
        )

    @JvmStatic
    fun studyStreakTitle(): String = localizedText("Study streak", "学習連続")

    @JvmStatic
    fun studyImpactTitle(): String = localizedText("Study impact", "学習の影響")

    @JvmStatic
    fun studyImpactSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "review", "reviews"),
            "${count}件の復習",
        )

    @JvmStatic
    fun recentMistakesTitle(): String = localizedText("Recent mistakes", "最近のミス")

    @JvmStatic
    fun moreAnkiEvidenceSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "kanji still needs more Anki evidence", "kanji still need more Anki evidence"),
            "まだ${count}件の漢字にAnkiの証拠が必要です",
        )

    @JvmStatic
    fun recentMistakesSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "recent mistake", "recent mistakes"),
            "最近のミス${count}件",
        )

    @JvmStatic
    fun needsAttentionTitle(): String = localizedText("Needs attention", "要対応")

    @JvmStatic
    fun kanjiWithEnoughEvidenceSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "kanji with enough evidence", "kanji with enough evidence"),
            "十分な証拠がある漢字${count}件",
        )

    @JvmStatic
    fun ladderStatusTitle(): String = localizedText("Ladder status", "ラダー状況")

    @JvmStatic
    fun activeKanjiOnLadderSummary(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "active kanji on the ladder", "active kanji on the ladder"),
            "ラダー上のアクティブ漢字${count}件",
        )

    @JvmStatic
    fun studyTimeTitle(): String = localizedText("Study time", "学習時間")

    @JvmStatic
    fun studyTimeTodayLabel(value: String): String = localizedText("Today: $value", "今日: $value")

    @JvmStatic
    fun studyTimeLast7DaysLabel(value: String): String = localizedText("Last 7 days: $value", "直近7日: $value")

    @JvmStatic
    fun studyTimeAnsweredTasksLabel(count: Int): String =
        localizedText("Answered tasks: $count", "回答したタスク: ${count}件")

    @JvmStatic
    fun studyTimeAveragePerTaskLabel(value: String): String = localizedText("Avg / task: $value", "1件あたり平均: $value")

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
        if (isJapaneseLocale()) {
            return if (working) "Kaniは動いています" else "証拠待ち"
        }
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
        if (isJapaneseLocale()) {
            if (!hasStats) {
                return "学習して同期すると推移が見えます。"
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
                return "アクティブ漢字${totalActiveItems}件を追跡中です。推移には復習と同期が必要です。"
            }
            return "比較するには復習して同期してください。"
        }
        if (!hasStats) {
            return "Study and sync to see trends."
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
        if (isJapaneseLocale()) {
            if (totalActiveItems == 0) {
                return "ラダーを埋めるには、同期するか弱い漢字を学習してください。"
            }
            var body = localizedText(
                StudyTextCopy.countText(promotionReadyCount, "ready to climb", "ready to climb") +
                    " · " +
                    StudyTextCopy.countText(demotionRiskCount, "at risk", "at risk"),
                "昇格待ち${promotionReadyCount}件 · リスク${demotionRiskCount}件",
            )
            if (demotionReadyCount > 0) {
                body += localizedText(
                    " · " + StudyTextCopy.countText(demotionReadyCount, "ready to fall", "ready to fall"),
                    " · 降格待ち${demotionReadyCount}件",
                )
            }
            return body + "。${ladderPromotionIntervalDays}日を超えたら昇格。${ladderDemotionFailStreak}回のミスで降格。"
        }
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
        return if (isJapaneseLocale()) {
            when (rung) {
                RecordsBase.LadderRung.WRITE_KANJI -> "漢字を書く"
                RecordsBase.LadderRung.TYPE_MEANING -> "意味を入力"
                RecordsBase.LadderRung.SIMILAR_KANJI -> "似た漢字"
                RecordsBase.LadderRung.MEANING_KANJI -> "意味から漢字"
                RecordsBase.LadderRung.KANJI_MEANING -> "漢字の意味"
                RecordsBase.LadderRung.FONT_MEANING -> "フォントの意味"
                RecordsBase.LadderRung.WORD_READING -> "単語の読み"
            }
        } else {
            when (rung) {
                RecordsBase.LadderRung.WRITE_KANJI -> "Write kanji"
                RecordsBase.LadderRung.TYPE_MEANING -> "Type meaning"
                RecordsBase.LadderRung.SIMILAR_KANJI -> "Similar kanji"
                RecordsBase.LadderRung.MEANING_KANJI -> "Meaning kanji"
                RecordsBase.LadderRung.KANJI_MEANING -> "Kanji meaning"
                RecordsBase.LadderRung.FONT_MEANING -> "Font meaning"
                RecordsBase.LadderRung.WORD_READING -> "Word reading"
            }
        }
    }

    @JvmStatic
    fun weaknessImprovementBody(
        improvedCount: Int,
        averageBeforeWeakness: Double,
        averageAfterWeakness: Double,
    ): String {
        if (isJapaneseLocale()) {
            if (improvedCount == 0) {
                return "弱点の推移を見るには復習と同期が必要です。"
            }
            return "平均の弱さ: " +
                formatWeakness(averageBeforeWeakness) +
                " → " +
                formatWeakness(averageAfterWeakness) +
                "。"
        }
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
        return if (isJapaneseLocale()) {
            clean(kanji) + "  " + formatWeakness(beforeWeakness) + " → " + formatWeakness(afterWeakness)
        } else {
            clean(kanji) + "  " + formatWeakness(beforeWeakness) + " -> " + formatWeakness(afterWeakness)
        }
    }

    @JvmStatic
    fun supportGainExample(kanji: String?, beforeMatureSupport: Int, afterMatureSupport: Int): String {
        return if (isJapaneseLocale()) {
            clean(kanji) + "  " + beforeMatureSupport + " → " + afterMatureSupport + "枚の成熟カード"
        } else {
            clean(kanji) + "  " + beforeMatureSupport + " -> " + afterMatureSupport + " mature cards"
        }
    }

    @JvmStatic
    fun notHelpingRowText(
        kanji: String?,
        reviewCount: Int,
        sameCardCount: Int,
        retentionDelta: Double,
        difficultyDelta: Double,
    ): String {
        return if (isJapaneseLocale()) {
            clean(kanji) +
                "  " +
                reviewCount +
                "回のKani復習 · " +
                sameCardCount +
                "件の同一カード確認 · 定着率 " +
                formatSignedPercent(retentionDelta) +
                " · 難しさ " +
                formatSignedDecimal(difficultyDelta)
        } else {
            clean(kanji) +
                "  " +
                reviewCount +
                " Kani reviews · " +
                sameCardCount +
                " same-card checks · retention " +
                formatSignedPercent(retentionDelta) +
                " · difficulty " +
                formatSignedDecimal(difficultyDelta)
        }
    }

    @JvmStatic
    fun notHelpingBody(noImpactEvidence: Boolean, hasNotHelpingRows: Boolean): String {
        if (isJapaneseLocale()) {
            if (noImpactEvidence) {
                return "比較するには復習して同期してください。"
            }
            if (!hasNotHelpingRows) {
                return "今は対応が必要な漢字はありません。"
            }
            return "十分な復習と同期が必要です。"
        }
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
        if (isJapaneseLocale()) {
            return if (currentDays <= 0) "連続記録なし" else "${currentDays}日連続"
        }
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
        if (isJapaneseLocale()) {
            if (bestDays <= 0 && !studiedToday && reviewsToday <= 0 && lastStudyAtMillis <= 0L) {
                return "連続記録を始めるには学習して同期してください。"
            }
            val today = if (studiedToday) {
                "今日の復習${reviewsToday}件"
            } else {
                "今日は復習なし"
            }
            val lastStudy = if (lastStudyAtMillis <= 0L) {
                "まだ学習なし"
            } else {
                elapsedSinceLabel(nowMillis, lastStudyAtMillis)
            }
            return "最長連続 ${bestDays}日。${today}。最終学習 ${lastStudy}。"
        }
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
        if (isJapaneseLocale()) {
            if (totalReviews <= 0) {
                return "影響を見るには学習して同期してください。"
            }
            val reviewSummary =
                totalReviews.toString() + "件の復習を" +
                    distinctReviewedKanji +
                    "件の漢字にわたって行いました。"
            val writingSummary = if (writingRequired <= 0) {
                "まだ書き取りプロンプトはありません。"
            } else {
                "書き取りプロンプト: " +
                    writingPassed +
                    "件成功、" +
                    writingFailed +
                    "件失敗、手動上書き" +
                    manualOverrides +
                    "件。"
            }
            return reviewSummary + " " + writingSummary
        }
        if (totalReviews <= 0) {
            return "Study and sync to see impact."
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
        if (isJapaneseLocale()) {
            return if (hasMistakes) "最近の見逃しをもう一度確認しましょう。" else "最近のミスはありません。"
        }
        return if (hasMistakes) {
            "Recent misses worth another look."
        } else {
            "No recent mistakes."
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
        if (isJapaneseLocale()) {
            if (seconds < 60L) {
                return "${seconds}秒"
            }
            val minutes = seconds / 60L
            val remainingSeconds = seconds % 60L
            if (minutes < 60L) {
                return if (remainingSeconds == 0L) "${minutes}分" else "${minutes}分${remainingSeconds}秒"
            }
            val hours = minutes / 60L
            val remainingMinutes = minutes % 60L
            return if (remainingMinutes == 0L) "${hours}時間" else "${hours}時間${remainingMinutes}分"
        }
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
        if (isJapaneseLocale()) {
            val signals = mutableListOf<String>()
            if (weakKanjiImproved > 0) {
                signals += "${weakKanjiImproved}件の弱い漢字が改善しました"
            }
            if (matureSupportGained > 0) {
                signals += "${matureSupportGained}件の成熟カードが増えました"
            }
            if (promotionReadyCount > 0) {
                signals += "${promotionReadyCount}件の復習項目が昇格待ちです"
            }
            val body = if (signals.isEmpty()) {
                "証拠が改善中です。"
            } else {
                signals.joinToString("。") + "。"
            }
            return if (demotionRiskCount > 0) {
                body + " ミスの連続がある復習項目${demotionRiskCount}件に注意してください。"
            } else {
                body
            }
        }
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
            localizedText("just now", "たった今")
        } else {
            localizedText(formatStudyTime(elapsed) + " ago", formatStudyTime(elapsed) + "前")
        }
    }

    private fun recentMistakeRatingLabel(rating: String?): String {
        val cleaned = clean(rating)
        if (isJapaneseLocale()) {
            return when (StudyRatings.normalize(cleaned)) {
                StudyRatings.AGAIN -> "再挑戦"
                StudyRatings.HARD -> "難しい"
                StudyRatings.GOOD -> "良い"
                StudyRatings.EASY -> "簡単"
                else -> if (cleaned.isBlank()) "ミス" else cleaned
            }
        }
        if (cleaned.isBlank()) {
            return "Mistake"
        }
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
