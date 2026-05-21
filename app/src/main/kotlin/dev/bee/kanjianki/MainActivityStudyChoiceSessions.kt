package dev.bee.kanjianki

import android.view.View
import android.widget.LinearLayout
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.HintState
import java.util.Random

internal class MainActivityStudyChoiceSessions(private val home: MainActivityStudy) {
    private val meaningKanjiChoicePlanner = MeaningKanjiChoicePlanner()
    private val meaningChoiceRandom = Random()

    fun renderMeaningKanjiSession(session: RecordsSchedulerModels.StudySession) {
        resetChoiceSession(true)

        val choiceCard = meaningKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < 4) {
            home.renderFlashcardSession(session)
            return
        }

        val answerPanel = home.flashcardAnswerPanelModel(session)
        val reason = StudyTextCopy.studyReasonLine(
            home.activeSimilarWritingRepair != null,
            session,
            home.settings().matureSupportThreshold,
            System.currentTimeMillis()
        )
        val cardShell = meaningKanjiSessionView(
            home,
            MeaningChoiceSessionModel(
                "Recall",
                LABEL_CHOOSE_KANJI,
                StudyTaskCopy.labelForTask(session.taskType),
                "Pick the kanji that matches the meaning.",
                reason,
                StudyTextCopy.meaningKanjiChoiceQuestion(choiceCard, session.prompt),
                choiceCard.choices,
                answerPanel
            ) { glyph -> showMeaningKanjiChoiceResult(choiceCard, glyph) }
        )

        val cardLp = LinearLayout.LayoutParams(-1, 0, 1f)
        cardLp.setMargins(0, home.dp(6), 0, home.dp(12))
        home.content.addView(cardShell, cardLp)
    }

    fun meaningKanjiChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        if (session?.row == null) {
            return null
        }
        return meaningKanjiChoicePlanner.buildChoiceCard(
            session.row,
            home.store.activeDashboardRows(),
            home.store.searchKanjiInventory(""),
            meaningChoiceRandom
        )
    }

    fun showMeaningKanjiChoiceResult(card: RecordsImportModels.MeaningKanjiChoiceCard, selectedKanji: String) {
        val correct = card.isCorrect(selectedKanji)
        val studyActionBar = home.studyActionBar
        if (studyActionBar == null) {
            home.submitReview(if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false)
            return
        }
        home.styleStudyActionBarShell()
        studyActionBar.removeAllViews()
        studyActionBar.visibility = View.VISIBLE
        val prompt = home.activeSession?.prompt ?: ""
        val status = StudyTextCopy.meaningKanjiChoiceResult(card, prompt, correct)
        studyActionBar.addView(
            meaningKanjiChoiceResultActionBarView(
                home,
                status,
                if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                Runnable { home.submitReview(if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false) }
            )
        )
    }

    fun renderSimilarKanjiSession(session: RecordsSchedulerModels.StudySession) {
        resetChoiceSession(false)

        val choiceCard = similarChoiceCardForSession(session)
        val choices = ArrayList(choiceCard.choices)
        if (choices.size < 2) {
            home.renderFlashcardSession(session)
            return
        }
        choices.shuffle()

        val meaning = choiceCard.primaryMeaning
        val reason = StudyTextCopy.studyReasonLine(
            home.activeSimilarWritingRepair != null,
            session,
            home.settings().matureSupportThreshold,
            System.currentTimeMillis()
        )
        val cardShell = similarKanjiSessionView(
            home,
            SimilarChoiceSessionModel(
                "Recognise",
                LABEL_CHOOSE_KANJI,
                MainActivityBase.LABEL_SIMILAR_KANJI,
                "Pick the kanji that matches the meaning.",
                reason,
                "Which kanji means $meaning?",
                SimilarChoiceGridModel(
                    choices,
                    true
                ) { glyph -> home.submitSimilarKanjiChoice(choiceCard, glyph) }
            )
        )
        val cardLp = LinearLayout.LayoutParams(-1, 0, 1f)
        cardLp.setMargins(0, home.dp(6), 0, home.dp(12))
        home.content.addView(cardShell, cardLp)
    }

    fun similarChoiceCardForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.SimilarKanjiChoiceCard {
        val now = System.currentTimeMillis()
        val stored = home.store.dueSimilarChoiceForActiveTarget(session.item.kanji, now)
        val meaning = if (session.row == null) "" else StudyTextCopy.rowMeaning(session.row)
        return SimilarKanjiChoicePlanner.choiceCardForSession(
            stored,
            session.item.kanji,
            meaning,
            home.store.similarPairsForKanji(session.item.kanji)
        )
    }

    fun buildSimilarKanjiChoices(targetKanji: String): List<String> {
        return SimilarKanjiChoicePlanner.fallbackChoices(
            targetKanji,
            home.store.similarPairsForKanji(targetKanji)
        )
    }

    fun resetChoiceSession(resetTouchTracking: Boolean) {
        home.prepareStudyContent(home.activeStudyPlan, true)
        home.activeSimilarWritingRepair = null
        home.activeAnalysis = null
        home.checkingWriting = false
        home.flashcardAnswerRevealed = false
        if (resetTouchTracking) {
            home.flashcardTouchTracking = false
        }
        home.flashcardGestureArea = null
        home.typingAnswerState = null
        home.drawingPad = null
        home.hintsUsed = 0
        home.setHintState(HintState.initial())
        home.hideStudyActionBar()
    }

    private companion object {
        const val LABEL_CHOOSE_KANJI = "Choose the kanji"
    }
}
