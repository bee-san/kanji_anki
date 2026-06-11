package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StatsTextCopyTest {
    @Test
    fun verdictFlagsPreserveStatsPanelBranches() {
        assertFalse(StatsTextCopy.verdictWorking(0, 0))
        assertTrue(StatsTextCopy.verdictWorking(1, 0))
        assertTrue(StatsTextCopy.verdictWorking(0, 1))
        assertFalse(StatsTextCopy.verdictHasLadder(0))
        assertTrue(StatsTextCopy.verdictHasLadder(1))
    }

    @Test
    fun verdictTitlePreservesWorkingAndWaitingCopy() {
        assertEquals("Kani is working", StatsTextCopy.verdictTitle(true))
        assertEquals("Waiting for evidence", StatsTextCopy.verdictTitle(false))
    }

    @Test
    fun verdictBodyKeepsEmptyAndLadderOnlyCopyBrief() {
        assertEquals(
            "Study and sync to see trends.",
            StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0)
        )
        assertEquals(
            "Tracking 2 active kanji. Trends need reviews and sync.",
            StatsTextCopy.verdictBody(true, false, true, 0, 0, 0, 0, 2)
        )
        assertEquals(
            "Review and sync to compare.",
            StatsTextCopy.verdictBody(true, false, false, 0, 0, 0, 0, 0)
        )
    }

    @Test
    fun verdictBodyPreservesWorkingSignalsAndRiskCopy() {
        assertEquals(
            "1 weak kanji improved. 2 mature cards gained. 3 review items ready to climb. Watch 1 review item with a miss streak.",
            StatsTextCopy.verdictBody(true, true, true, 1, 2, 3, 1, 4)
        )
    }

    @Test
    fun ladderHealthBodyDemotesThresholdDetailsAndKeepsEmptyCopyBrief() {
        assertEquals(
            "Sync or study weak kanji to fill the ladder.",
            StatsTextCopy.ladderHealthBody(0, 0, 0, 0, 21, 3)
        )
        assertEquals(
            "2 ready to climb · 1 at risk · 1 ready to fall. Climb after more than 21 days; fall after 3 misses.",
            StatsTextCopy.ladderHealthBody(5, 2, 1, 1, 21, 3)
        )
    }

    @Test
    fun ladderDistributionRowsPreserveRungLabels() {
        assertEquals("Write kanji: 2", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.WRITE_KANJI, 2))
        assertEquals("Type meaning: 0", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.TYPE_MEANING, 0))
        assertEquals("Similar kanji: 1", StatsTextCopy.ladderDistributionRow(RecordsBase.LadderRung.SIMILAR_KANJI, 1))
        assertEquals("Meaning kanji", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.MEANING_KANJI))
        assertEquals("Kanji meaning", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals("Font meaning", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.FONT_MEANING))
        assertEquals("Word reading", StatsTextCopy.ladderRungLabel(RecordsBase.LadderRung.WORD_READING))
    }

    @Test
    fun weaknessAndSupportFormattingPreservesStatsRows() {
        assertEquals(
            "Weakness trends need reviews and sync.",
            StatsTextCopy.weaknessImprovementBody(0, 0.0, 0.0)
        )
        assertEquals(
            "Average weakness: 0.80 -> 0.25.",
            StatsTextCopy.weaknessImprovementBody(2, 0.8, 0.25)
        )
        assertEquals("裂  0.80 -> 0.25", StatsTextCopy.weaknessImprovementExample("裂", 0.8, 0.25))
        assertEquals("裂  1 -> 3 mature cards", StatsTextCopy.supportGainExample("裂", 1, 3))
    }

    @Test
    fun impactAndTimeFormattingPreservesStatsHelpers() {
        assertEquals(
            "Review and sync to compare.",
            StatsTextCopy.notHelpingBody(true, false)
        )
        assertEquals(
            "No kanji need attention right now.",
            StatsTextCopy.notHelpingBody(false, false)
        )
        assertEquals(
            "Needs enough reviews and sync.",
            StatsTextCopy.notHelpingBody(false, true)
        )
        assertEquals("裂  3 Kani reviews · 2 same-card checks · retention +12% · difficulty -0.4", StatsTextCopy.notHelpingRowText("裂", 3, 2, 0.12, -0.4))
        assertEquals("0 sec", StatsTextCopy.formatStudyTime(-1))
        assertEquals("59 sec", StatsTextCopy.formatStudyTime(59_000))
        assertEquals("1 min", StatsTextCopy.formatStudyTime(60_000))
        assertEquals("1 min 5 sec", StatsTextCopy.formatStudyTime(65_000))
        assertEquals("1 hr", StatsTextCopy.formatStudyTime(3_600_000))
        assertEquals("1 hr 2 min", StatsTextCopy.formatStudyTime(3_720_000))
    }

    @Test
    fun streakImpactAndMistakeFormattingPreservesStatsHelpers() {
        assertEquals("No active streak", StatsTextCopy.studyStreakSummary(0))
        assertEquals("3-day streak", StatsTextCopy.studyStreakSummary(3))
        assertEquals(
            "Study and sync to start a streak.",
            StatsTextCopy.studyStreakBody(0, false, 0, 0L, 44_444L)
        )
        assertEquals(
            "Best streak 9 days. 8 reviews today. Last study 15 min ago.",
            StatsTextCopy.studyStreakBody(9, true, 8, 3_600_000L, 4_500_000L)
        )
        assertEquals(
            "Study and sync to see impact.",
            StatsTextCopy.studyImpactBody(0, 0, 0, 0, 0, 0)
        )
        assertEquals(
            "12 reviews across 4 kanji. Writing prompts: 4 passed, 2 failed, 1 manual override.",
            StatsTextCopy.studyImpactBody(12, 4, 6, 4, 2, 1)
        )
        assertEquals(
            "No recent mistakes.",
            StatsTextCopy.recentMistakesBody(false)
        )
        assertEquals(
            "Recent misses worth another look.",
            StatsTextCopy.recentMistakesBody(true)
        )
        assertEquals(
            "痛  Again · 5 min ago",
            StatsTextCopy.recentMistakeRowText("痛", "again", 4_200_000L, 4_500_000L)
        )
        assertEquals(
            "疲  Hard · just now",
            StatsTextCopy.recentMistakeRowText("疲", "hard", 4_500_000L, 4_500_000L)
        )
    }

    @Test
    fun edgeCaseFormattingCoversRemainingBranches() {
        assertEquals(
            "Best streak 5 days. No reviews today. Last study No study yet.",
            StatsTextCopy.studyStreakBody(5, false, 0, 0L, 44_444L)
        )
        assertEquals(
            "3 reviews across 2 kanji. No writing prompts yet.",
            StatsTextCopy.studyImpactBody(3, 2, 0, 0, 0, 0)
        )
        assertEquals(
            "  1 -> 2 mature cards",
            StatsTextCopy.supportGainExample(null, 1, 2)
        )
        assertEquals(
            "痛  Mistake · just now",
            StatsTextCopy.recentMistakeRowText("痛", "", 4_500_000L, 4_500_000L)
        )
        assertEquals(
            "痛  Again · just now",
            StatsTextCopy.recentMistakeRowText("痛", "Again", 4_500_000L, 4_500_000L)
        )
        assertEquals(
            "痛  1abc · just now",
            StatsTextCopy.recentMistakeRowText("痛", "1abc", 4_500_000L, 4_500_000L)
        )
        assertEquals(
            "痛  Λambda · just now",
            StatsTextCopy.recentMistakeRowText("痛", "λambda", 4_500_000L, 4_500_000L)
        )
    }

    @Test
    fun japaneseLocaleTranslatesStatsCopyAndFormatting() {
        withLocale(Locale.JAPAN) {
            assertEquals("統計", StatsTextCopy.statsTitle())
            assertEquals("弱い漢字の推移", StatsTextCopy.weakKanjiTrendTitle())
            assertEquals("弱い漢字3件が改善", StatsTextCopy.weakKanjiImprovedSummary(3))
            assertEquals("Ankiの支え", StatsTextCopy.ankiSupportTitle())
            assertEquals("今日: 1分5秒", StatsTextCopy.studyTimeTodayLabel(StatsTextCopy.formatStudyTime(65_000)))
            assertEquals("証拠待ち", StatsTextCopy.verdictTitle(false))
            assertEquals("学習して同期すると推移が見えます。", StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0))
            assertEquals(
                "昇格待ち2件 · リスク1件 · 降格待ち1件。21日を超えたら昇格。3回のミスで降格。",
                StatsTextCopy.ladderHealthBody(5, 2, 1, 1, 21, 3)
            )
            assertEquals("3日連続", StatsTextCopy.studyStreakSummary(3))
            assertEquals("12件の復習", StatsTextCopy.studyImpactSummary(12))
            assertEquals("12件の復習を4件の漢字にわたって行いました。 書き取りプロンプト: 4件成功、2件失敗、手動上書き1件。", StatsTextCopy.studyImpactBody(12, 4, 6, 4, 2, 1))
            assertEquals("痛  再挑戦 · 5分前", StatsTextCopy.recentMistakeRowText("痛", "again", 4_200_000L, 4_500_000L))
            assertEquals("1時間2分", StatsTextCopy.formatStudyTime(3_720_000))
            assertEquals("5秒", StatsTextCopy.formatStudyTime(5_000))
        }
    }

    @Test
    fun englishLocaleTranslatesAdditionalStatsCopyOutputs() {
        withLocale(Locale.US) {
            assertEquals("2 weak kanji improved", StatsTextCopy.weakKanjiImprovedSummary(2))
            assertEquals("1 mature card gained", StatsTextCopy.matureSupportSummary(1))
            assertEquals("3 mature cards gained", StatsTextCopy.matureSupportSummary(3))
            assertEquals("2 kanji gained first mature support", StatsTextCopy.firstMatureSupportSummary(2))
            assertEquals("Study impact", StatsTextCopy.studyImpactTitle())
            assertEquals("Recent mistakes", StatsTextCopy.recentMistakesTitle())
            assertEquals("2 kanji still need more Anki evidence", StatsTextCopy.moreAnkiEvidenceSummary(2))
            assertEquals("2 recent mistakes", StatsTextCopy.recentMistakesSummary(2))
            assertEquals("Needs attention", StatsTextCopy.needsAttentionTitle())
            assertEquals("4 kanji with enough evidence", StatsTextCopy.kanjiWithEnoughEvidenceSummary(4))
            assertEquals("Ladder status", StatsTextCopy.ladderStatusTitle())
            assertEquals("3 active kanji on the ladder", StatsTextCopy.activeKanjiOnLadderSummary(3))
            assertEquals("Study time", StatsTextCopy.studyTimeTitle())
            assertEquals("Today: 1 min 5 sec", StatsTextCopy.studyTimeTodayLabel(StatsTextCopy.formatStudyTime(65_000)))
            assertEquals("Last 7 days: 1 hr", StatsTextCopy.studyTimeLast7DaysLabel(StatsTextCopy.formatStudyTime(3_600_000)))
            assertEquals("Answered tasks: 5", StatsTextCopy.studyTimeAnsweredTasksLabel(5))
            assertEquals("Avg / task: 2 min", StatsTextCopy.studyTimeAveragePerTaskLabel(StatsTextCopy.formatStudyTime(120_000)))
        }
    }

    @Test
    fun japaneseLocaleTranslatesAdditionalStatsCopyOutputs() {
        withLocale(Locale.JAPAN) {
            assertEquals("弱い漢字2件が改善", StatsTextCopy.weakKanjiImprovedSummary(2))
            assertEquals("成熟カード1件が増加", StatsTextCopy.matureSupportSummary(1))
            assertEquals("漢字2件が初めて成熟サポートを獲得", StatsTextCopy.firstMatureSupportSummary(2))
            assertEquals("学習の影響", StatsTextCopy.studyImpactTitle())
            assertEquals("最近のミス", StatsTextCopy.recentMistakesTitle())
            assertEquals("まだ2件の漢字にAnkiの証拠が必要です", StatsTextCopy.moreAnkiEvidenceSummary(2))
            assertEquals("最近のミス2件", StatsTextCopy.recentMistakesSummary(2))
            assertEquals("要対応", StatsTextCopy.needsAttentionTitle())
            assertEquals("十分な証拠がある漢字4件", StatsTextCopy.kanjiWithEnoughEvidenceSummary(4))
            assertEquals("ラダー状況", StatsTextCopy.ladderStatusTitle())
            assertEquals("ラダー上のアクティブ漢字3件", StatsTextCopy.activeKanjiOnLadderSummary(3))
            assertEquals("学習時間", StatsTextCopy.studyTimeTitle())
            assertEquals("直近7日: 1分5秒", StatsTextCopy.studyTimeLast7DaysLabel(StatsTextCopy.formatStudyTime(65_000)))
            assertEquals("回答したタスク: 5件", StatsTextCopy.studyTimeAnsweredTasksLabel(5))
            assertEquals("1件あたり平均: 2分", StatsTextCopy.studyTimeAveragePerTaskLabel(StatsTextCopy.formatStudyTime(120_000)))
            assertEquals("Kaniは動いています", StatsTextCopy.verdictTitle(true))
            assertEquals(
                "アクティブ漢字2件を追跡中です。推移には復習と同期が必要です。",
                StatsTextCopy.verdictBody(true, false, true, 0, 0, 0, 0, 2)
            )
            assertEquals(
                "1件の弱い漢字が改善しました。2件の成熟カードが増えました。3件の復習項目が昇格待ちです。 ミスの連続がある復習項目1件に注意してください。",
                StatsTextCopy.verdictBody(true, true, true, 1, 2, 3, 1, 4)
            )
            assertEquals(
                "ラダーを埋めるには、同期するか弱い漢字を学習してください。",
                StatsTextCopy.ladderHealthBody(0, 0, 0, 0, 21, 3)
            )
            assertEquals("弱点の推移を見るには復習と同期が必要です。", StatsTextCopy.weaknessImprovementBody(0, 0.0, 0.0))
            assertEquals("平均の弱さ: 0.80 → 0.25。", StatsTextCopy.weaknessImprovementBody(2, 0.8, 0.25))
            assertEquals("裂  0.80 → 0.25", StatsTextCopy.weaknessImprovementExample("裂", 0.8, 0.25))
            assertEquals("裂  1 → 3枚の成熟カード", StatsTextCopy.supportGainExample("裂", 1, 3))
            assertEquals("裂  3回のKani復習 · 2件の同一カード確認 · 定着率 +12% · 難しさ -0.4", StatsTextCopy.notHelpingRowText("裂", 3, 2, 0.12, -0.4))
            assertEquals("比較するには復習して同期してください。", StatsTextCopy.notHelpingBody(true, false))
            assertEquals("今は対応が必要な漢字はありません。", StatsTextCopy.notHelpingBody(false, false))
            assertEquals("十分な復習と同期が必要です。", StatsTextCopy.notHelpingBody(false, true))
            assertEquals("連続記録なし", StatsTextCopy.studyStreakSummary(0))
            assertEquals("連続記録を始めるには学習して同期してください。", StatsTextCopy.studyStreakBody(0, false, 0, 0L, 44_444L))
            assertEquals("最長連続 9日。今日の復習8件。最終学習 15分前。", StatsTextCopy.studyStreakBody(9, true, 8, 3_600_000L, 4_500_000L))
            assertEquals("影響を見るには学習して同期してください。", StatsTextCopy.studyImpactBody(0, 0, 0, 0, 0, 0))
            assertEquals("3件の復習を2件の漢字にわたって行いました。 まだ書き取りプロンプトはありません。", StatsTextCopy.studyImpactBody(3, 2, 0, 0, 0, 0))
            assertEquals("最近の見逃しをもう一度確認しましょう。", StatsTextCopy.recentMistakesBody(true))
            assertEquals("最近のミスはありません。", StatsTextCopy.recentMistakesBody(false))
            assertEquals("痛  再挑戦 · 5分前", StatsTextCopy.recentMistakeRowText("痛", "again", 4_200_000L, 4_500_000L))
            assertEquals("痛  難しい · たった今", StatsTextCopy.recentMistakeRowText("痛", "hard", 4_500_000L, 4_500_000L))
            assertEquals("痛  良い · たった今", StatsTextCopy.recentMistakeRowText("痛", "good", 4_500_000L, 4_500_000L))
            assertEquals("痛  簡単 · たった今", StatsTextCopy.recentMistakeRowText("痛", "easy", 4_500_000L, 4_500_000L))
            assertEquals("痛  再挑戦 · たった今", StatsTextCopy.recentMistakeRowText("痛", "", 4_500_000L, 4_500_000L))
            assertEquals("痛  再挑戦 · たった今", StatsTextCopy.recentMistakeRowText("痛", "1abc", 4_500_000L, 4_500_000L))
            assertEquals("59秒", StatsTextCopy.formatStudyTime(59_000))
            assertEquals("1分", StatsTextCopy.formatStudyTime(60_000))
            assertEquals("1分5秒", StatsTextCopy.formatStudyTime(65_000))
            assertEquals("1時間", StatsTextCopy.formatStudyTime(3_600_000))
            assertEquals("1時間2分", StatsTextCopy.formatStudyTime(3_720_000))
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
