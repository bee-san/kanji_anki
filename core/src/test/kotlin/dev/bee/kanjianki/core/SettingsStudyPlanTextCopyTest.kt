package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyPlanTextCopyTest {
    @Test
    fun workloadAndSortHelpersPreserveFormatting() {
        assertEquals("Daily workload", SettingsStudyPlanTextCopy.dailyWorkloadTitle())
        assertEquals("Save workload", SettingsStudyPlanTextCopy.saveMaximumLabel())
        assertEquals("Save workload", SettingsStudyPlanTextCopy.saveWorkloadLabel())
        assertEquals("Use automatic workload", SettingsStudyPlanTextCopy.automaticParetoLabel())
        assertEquals("Set workload manually", SettingsStudyPlanTextCopy.manualWorkloadLabel())
        assertEquals("Kani picks today's count; due dates stay fixed.", SettingsStudyPlanTextCopy.automaticWorkloadBody())
        assertEquals("Set today's count; due dates stay fixed.", SettingsStudyPlanTextCopy.manualWorkloadBody())
        assertEquals("Today's study load percentage", SettingsStudyPlanTextCopy.workloadPercentSliderDescription())
        assertEquals("Maximum items", SettingsStudyPlanTextCopy.maxItemsSliderDescription())
        assertEquals(listOf("Very little", "Focused", "Balanced", "More", "All kanji"), SettingsStudyPlanTextCopy.workloadScaleLabels().toList())
        assertEquals("Very little: up to 1 item", SettingsStudyPlanTextCopy.workloadStatusText(0, 5))
        assertEquals("Focused: up to 5 items", SettingsStudyPlanTextCopy.workloadStatusText(20, 5))
        assertEquals("Balanced: up to 11 items", SettingsStudyPlanTextCopy.workloadStatusText(50, 20))
        assertEquals("More: up to 17 items", SettingsStudyPlanTextCopy.workloadStatusText(80, 20))
        assertEquals("Maximum: 1 item", SettingsStudyPlanTextCopy.maxItemsStatusText(0))
        assertEquals("Waiting for cards", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null))
        assertEquals(
            "7 items today",
            SettingsStudyPlanTextCopy.autoWorkloadStatusText(
                RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 3, listOf("語"), 1, false, "focus"),
            ),
        )
        assertEquals("Current: Balanced mix", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
        assertEquals("Kani misses", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals("Balanced mix", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
        assertEquals("Jiten frequency first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Harder cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Likely forgotten first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Missed in Kani first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
            "Balances misses, risk, and frequency.",
            SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
        )
        assertEquals("New card sort", SettingsStudyPlanTextCopy.newCardSortTitle())
        assertEquals("New cards only. Reviews and repeats stay first.", SettingsStudyPlanTextCopy.newCardSortBody())
        assertEquals("Save new card sort", SettingsStudyPlanTextCopy.saveNewCardSortLabel())
        assertEquals(
            "Similar kanji stay close: 人/入, 土/士.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入", "土/士")),
        )
        assertEquals(
            "Similar kanji stay close.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(emptyList()),
        )
        assertEquals("Daily limits", SettingsStudyPlanTextCopy.deckLimitsTitle())
        assertEquals("Set the daily new-card cap and the maximum number of active study items.", SettingsStudyPlanTextCopy.deckLimitsBody())
        assertEquals("New cards per day", SettingsStudyPlanTextCopy.newCardsPerDayLabel())
        assertEquals("Save daily limit", SettingsStudyPlanTextCopy.saveDeckLimitsLabel())
        assertEquals("Jiten ranks 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000))
        assertEquals("Desired retention: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95))
        assertEquals("Review retention", SettingsStudyPlanTextCopy.fsrsRetentionTitle())
        assertEquals(
            "FSRS stays local. Anki due dates stay fixed.",
            SettingsStudyPlanTextCopy.fsrsRetentionBody(),
        )
        assertEquals("Jiten-rank retention ranges", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel())
        assertEquals("One range per line, e.g. 1-500=95%.", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody())
        assertEquals("Use example ranges", SettingsStudyPlanTextCopy.useExampleRangesLabel())
        assertEquals("Save retention", SettingsStudyPlanTextCopy.saveRetentionLabel())
        assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95))
        assertEquals("Study ladder", SettingsStudyPlanTextCopy.studyLadderTitle())
        assertEquals("Set practice order. Keep one rung on.", SettingsStudyPlanTextCopy.studyLadderBody())
        assertEquals("Leave one rung always on.", SettingsStudyPlanTextCopy.keepAlwaysAvailableRungToast())
        assertEquals("On", SettingsStudyPlanTextCopy.ladderToggleLabel(true))
        assertEquals("Off", SettingsStudyPlanTextCopy.ladderToggleLabel(false))
        assertEquals("Write kanji turned off.", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true))
        assertEquals(
            "Included in study",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults(),
                RecordsBase.LadderRung.WRITE_KANJI,
            ),
        )
        assertEquals(
            "Skipped in study",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.WRITE_KANJI, false),
                RecordsBase.LadderRung.WRITE_KANJI,
            ),
        )
        assertEquals(
            "Included when similar kanji exist",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
        assertEquals(
            "Skipped in study",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
        assertEquals(
            "Included when the kanji has multiple known readings",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.KANJI_READING, true),
                RecordsBase.LadderRung.KANJI_READING,
            ),
        )
        assertEquals(
            "Kanji -> reading",
            SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.KANJI_READING),
        )
    }

    @Test
    fun workloadAndSortHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("1日の学習量", SettingsStudyPlanTextCopy.dailyWorkloadTitle())
            assertEquals("学習量を保存", SettingsStudyPlanTextCopy.saveMaximumLabel())
            assertEquals("学習量を保存", SettingsStudyPlanTextCopy.saveWorkloadLabel())
            assertEquals("自動学習量を使う", SettingsStudyPlanTextCopy.automaticParetoLabel())
            assertEquals("学習量を手動で設定", SettingsStudyPlanTextCopy.manualWorkloadLabel())
            assertEquals("Kaniが今日の数を選ぶ。期限日は固定のまま。", SettingsStudyPlanTextCopy.automaticWorkloadBody())
            assertEquals("今日の数を設定する。期限日は固定のまま。", SettingsStudyPlanTextCopy.manualWorkloadBody())
            assertEquals("今日の学習量の割合", SettingsStudyPlanTextCopy.workloadPercentSliderDescription())
            assertEquals("最大件数", SettingsStudyPlanTextCopy.maxItemsSliderDescription())
            assertArrayEquals(
                arrayOf("ごく少なめ", "集中", "バランス", "多め", "すべての漢字"),
                SettingsStudyPlanTextCopy.workloadScaleLabels(),
            )
            assertEquals("ごく少なめ: 最大1件", SettingsStudyPlanTextCopy.workloadStatusText(0, 5))
            assertEquals("集中: 最大5件", SettingsStudyPlanTextCopy.workloadStatusText(20, 5))
            assertEquals("バランス: 最大11件", SettingsStudyPlanTextCopy.workloadStatusText(50, 20))
            assertEquals("多め: 最大17件", SettingsStudyPlanTextCopy.workloadStatusText(80, 20))
            assertEquals("すべての漢字: 最大20件", SettingsStudyPlanTextCopy.workloadStatusText(100, 20))
            assertEquals("最大: 1件", SettingsStudyPlanTextCopy.maxItemsStatusText(0))
            assertEquals("カード待ち", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null))
            assertEquals(
                "今日は7件",
                SettingsStudyPlanTextCopy.autoWorkloadStatusText(
                    RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 3, listOf("語"), 1, false, "focus"),
                ),
            )
            assertEquals("現在: バランス", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
            assertEquals("Kaniのミス", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
            assertEquals("バランス", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
            assertEquals("Jiten頻度順。", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
            assertEquals("難しいカードから。", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
            assertEquals("忘れそうなカードから。", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            assertEquals("Kaniで間違えたカードから。", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
            assertEquals(
                "ミス、リスク、頻度のバランス。",
                SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
            )
            assertEquals("新規カードの並び順", SettingsStudyPlanTextCopy.newCardSortTitle())
            assertEquals("新規カードのみ。レビューと繰り返しは先のまま。", SettingsStudyPlanTextCopy.newCardSortBody())
            assertEquals("新規カードの並び順を保存", SettingsStudyPlanTextCopy.saveNewCardSortLabel())
            assertEquals(
                "似た漢字を近くに並べます: 人/入, 土/士。",
                SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入", "土/士")),
            )
            assertEquals(
                "似た漢字を近くに並べます。",
                SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(emptyList()),
            )
            assertEquals("1日の上限", SettingsStudyPlanTextCopy.deckLimitsTitle())
            assertEquals("1日の新規カード上限と、アクティブな学習項目の最大数を設定する。", SettingsStudyPlanTextCopy.deckLimitsBody())
            assertEquals("1日の新規カード数", SettingsStudyPlanTextCopy.newCardsPerDayLabel())
            assertEquals("1日の上限を保存", SettingsStudyPlanTextCopy.saveDeckLimitsLabel())
            assertEquals("Jiten順位 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000))
            assertEquals("目標保持率: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95))
            assertEquals("レビュー保持率", SettingsStudyPlanTextCopy.fsrsRetentionTitle())
            assertEquals("FSRSは端末内のみ。Ankiの期限日は固定のまま。", SettingsStudyPlanTextCopy.fsrsRetentionBody())
            assertEquals("Jiten順位ごとの保持率範囲", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel())
            assertEquals("1行に1範囲（例: 1-500=95%）。", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody())
            assertEquals("例の範囲を使う", SettingsStudyPlanTextCopy.useExampleRangesLabel())
            assertEquals("保持率を保存", SettingsStudyPlanTextCopy.saveRetentionLabel())
            assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95))
            assertEquals("学習ラダー", SettingsStudyPlanTextCopy.studyLadderTitle())
            assertEquals("練習順を設定。1段はオンのままにする。", SettingsStudyPlanTextCopy.studyLadderBody())
            assertEquals("常に1段はオンにしてください。", SettingsStudyPlanTextCopy.keepAlwaysAvailableRungToast())
            assertEquals("オン", SettingsStudyPlanTextCopy.ladderToggleLabel(true))
            assertEquals("オフ", SettingsStudyPlanTextCopy.ladderToggleLabel(false))
            assertEquals("上へ移動", SettingsStudyPlanTextCopy.moveUpLabel())
            assertEquals("下へ移動", SettingsStudyPlanTextCopy.moveDownLabel())
            assertEquals("既定に戻す", SettingsStudyPlanTextCopy.restoreDefaultLadderLabel())
            assertEquals("ラダーを戻しました。", SettingsStudyPlanTextCopy.studyLadderRestoredToast())
            assertEquals("漢字を書くをオフにしました。", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true))
            assertEquals("漢字を書くをオンにしました。", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, false))
            assertEquals(
                "学習に含める",
                SettingsStudyPlanTextCopy.ladderRungSubtitle(
                    RecordsBase.StudyLadderSettings.defaults(),
                    RecordsBase.LadderRung.WRITE_KANJI,
                ),
            )
            assertEquals(
                "学習でスキップ",
                SettingsStudyPlanTextCopy.ladderRungSubtitle(
                    RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.WRITE_KANJI, false),
                    RecordsBase.LadderRung.WRITE_KANJI,
                ),
            )
            assertEquals(
                "似た漢字があるときに含める",
                SettingsStudyPlanTextCopy.ladderRungSubtitle(
                    RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                    RecordsBase.LadderRung.SIMILAR_KANJI,
                ),
            )
            assertEquals("漢字を書く", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WRITE_KANJI))
            assertEquals("似た漢字", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.SIMILAR_KANJI))
            assertEquals("意味を入力", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.TYPE_MEANING))
            assertEquals("意味 → 漢字", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.MEANING_KANJI))
            assertEquals("漢字 → 意味", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.KANJI_MEANING))
            assertEquals("フォント → 意味", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.FONT_MEANING))
            assertEquals("単語 → 読み", SettingsStudyPlanTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WORD_READING))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
