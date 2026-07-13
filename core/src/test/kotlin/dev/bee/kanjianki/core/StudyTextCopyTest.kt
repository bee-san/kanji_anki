package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StudyTextCopyLocaleLabelsTest {
    @Test
    fun studyCopyUsesEnglishLabelsByDefault() {
        assertEquals("Meaning", StudyTextCopy.meaningLabel())
        assertEquals("Pass", StudyTextCopy.passLabel())
        assertEquals("Fail", StudyTextCopy.failLabel())
        assertEquals("Answer", StudyTextCopy.answerLabel())
        assertEquals("Reference", StudyTextCopy.referenceLabel())
        assertEquals("Trace it below, then check.", StudyTextCopy.writingReferenceHelper())
        assertEquals("More about 裂", StudyTextCopy.moreAboutKanjiLabel("裂"))
        assertEquals("Show all 4", StudyTextCopy.showAllLabel(4))
        assertEquals("Show fewer", StudyTextCopy.showFewerLabel())
        assertEquals("Current", StudyTextCopy.currentLabel())
        assertEquals("Expanded", StudyTextCopy.expandedStateDescription())
        assertEquals("Collapsed", StudyTextCopy.collapsedStateDescription())
        assertEquals("Kani shell study", StudyTextCopy.shellContentDescription("study"))
        assertEquals("Kani route study", StudyTextCopy.routeContentDescription("study", null))
        assertEquals("Kani route study scroll middle", StudyTextCopy.routeContentDescription("study", "middle"))
        assertEquals("Kani route study", StudyTextCopy.routeContentDescription("study", ""))
        assertEquals("New cards", StudyTextCopy.newCardsLabel())
        assertEquals("Repair skipped.", StudyTextCopy.similarWritingRepairSkippedToast())
        assertEquals("Back home", StudyTextCopy.backHomeLabel())
        assertEquals("Close study", StudyTextCopy.closeStudyLabel())
        assertEquals("Study progress", StudyTextCopy.studyProgressDescription())
        assertEquals("Study", StudyTextCopy.studyLabel())
        assertEquals("Practice", StudyTextCopy.practiceLabel())
        assertEquals("Details", StudyTextCopy.similarKanjiDetailsLabel())
        assertEquals("Hide details", StudyTextCopy.similarKanjiHideDetailsLabel())
        assertEquals("Correct answer", StudyTextCopy.choiceCorrectStateDescription())
        assertEquals("Incorrect answer", StudyTextCopy.choiceIncorrectStateDescription())
        assertEquals("Correct.", StudyTextCopy.answerCorrectFeedback())
        assertEquals("Incorrect.", StudyTextCopy.answerIncorrectFeedback())
        assertEquals("Continue", StudyTextCopy.continueLabel())
        assertEquals(
            "Not quite — the correct kanji is 裂.",
            StudyTextCopy.similarKanjiWrongChoiceResult("裂"),
        )
    }

    @Test
    fun studyCopyTranslatesLabelsToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("意味", StudyTextCopy.meaningLabel())
            assertEquals("合格", StudyTextCopy.passLabel())
            assertEquals("不合格", StudyTextCopy.failLabel())
            assertEquals("答え", StudyTextCopy.answerLabel())
            assertEquals("お手本", StudyTextCopy.referenceLabel())
            assertEquals("下になぞってから確認してください。", StudyTextCopy.writingReferenceHelper())
            assertEquals("裂についてもっと見る", StudyTextCopy.moreAboutKanjiLabel("裂"))
            assertEquals("4件すべて表示", StudyTextCopy.showAllLabel(4))
            assertEquals("一部だけ表示", StudyTextCopy.showFewerLabel())
            assertEquals("現在", StudyTextCopy.currentLabel())
            assertEquals("展開済み", StudyTextCopy.expandedStateDescription())
            assertEquals("折りたたみ済み", StudyTextCopy.collapsedStateDescription())
            assertEquals("Kani画面 study", StudyTextCopy.shellContentDescription("study"))
            assertEquals("Kaniルート study", StudyTextCopy.routeContentDescription("study", null))
            assertEquals("Kaniルート study スクロール位置 middle", StudyTextCopy.routeContentDescription("study", "middle"))
            assertEquals("Kaniルート study", StudyTextCopy.routeContentDescription("study", ""))
            assertEquals("新規カード", StudyTextCopy.newCardsLabel())
            assertEquals("修正をスキップしました。", StudyTextCopy.similarWritingRepairSkippedToast())
            assertEquals("ホームに戻る", StudyTextCopy.backHomeLabel())
            assertEquals("学習を閉じる", StudyTextCopy.closeStudyLabel())
            assertEquals("学習進捗", StudyTextCopy.studyProgressDescription())
            assertEquals("学習", StudyTextCopy.studyLabel())
            assertEquals("練習", StudyTextCopy.practiceLabel())
            assertEquals("詳細", StudyTextCopy.similarKanjiDetailsLabel())
            assertEquals("詳細を隠す", StudyTextCopy.similarKanjiHideDetailsLabel())
            assertEquals("正解", StudyTextCopy.choiceCorrectStateDescription())
            assertEquals("不正解", StudyTextCopy.choiceIncorrectStateDescription())
            assertEquals("正解です。", StudyTextCopy.answerCorrectFeedback())
            assertEquals("不正解です。", StudyTextCopy.answerIncorrectFeedback())
            assertEquals("次へ", StudyTextCopy.continueLabel())
            assertEquals("不正解。正解は 裂 です。", StudyTextCopy.similarKanjiWrongChoiceResult("裂"))
            assertEquals("合格を保存しました", StudyTextCopy.reviewUndoMessage(StudyRatings.GOOD))
            assertEquals("不合格を保存しました", StudyTextCopy.reviewUndoMessage(StudyRatings.AGAIN))
        }
    }

    @Test
    fun studyCopyShowsUndoBannerTextInEnglishByDefault() {
        assertEquals("Pass saved", StudyTextCopy.reviewUndoMessage(StudyRatings.GOOD))
        assertEquals("Fail saved", StudyTextCopy.reviewUndoMessage(StudyRatings.AGAIN))
    }

    @Test
    fun studyCopyUsesUndoMessagesForOtherRatingsToo() {
        assertEquals("Hard saved", StudyTextCopy.reviewUndoMessage(StudyRatings.HARD))
        assertEquals("Easy saved", StudyTextCopy.reviewUndoMessage(StudyRatings.EASY))
        assertEquals("Fail saved", StudyTextCopy.reviewUndoMessage("mystery"))
    }

    @Test
    fun studyCopySummarizesCompletedTaskBreakdown() {
        val breakdown = StudySessionProgressTracker.CompletedTaskBreakdown(
            writingChecks = 2,
            similarKanjiChoices = 1,
            similarKanjiRepairs = 1,
            wordReadingReviews = 1,
            otherReviews = 1,
        )

        assertEquals(
            "6 tasks completed — 2 writing checks, 1 similar kanji choice, " +
                "1 similar kanji repair, 1 word reading review, 1 other review",
            StudyTextCopy.completedTaskBreakdownSummary(breakdown),
        )
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
