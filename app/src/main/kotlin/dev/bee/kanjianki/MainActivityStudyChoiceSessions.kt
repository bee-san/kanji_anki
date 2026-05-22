package dev.bee.kanjianki

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        resetChoiceSessionState(true)

        val choiceCard = meaningKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < 4) {
            home.renderComposeFlashcardSession(session)
            return
        }

        val answerPanel = home.flashcardAnswerPanelModel(session)
        val reason = StudyTextCopy.studyReasonLine(
            home.activeSimilarWritingRepair != null,
            session,
            home.settings().matureSupportThreshold,
            System.currentTimeMillis()
        )
        val model = MeaningChoiceSessionModel(
            "Recall",
            LABEL_CHOOSE_KANJI,
            StudyTaskCopy.labelForTask(session.taskType),
            "Pick the kanji that matches the meaning.",
            reason,
            StudyTextCopy.meaningKanjiChoiceQuestion(choiceCard, session.prompt),
            choiceCard.choices,
            answerPanel,
            KanjiChoiceHandler { glyph ->
                val correct = choiceCard.isCorrect(glyph)
                home.submitReview(if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false)
            },
            MeaningChoiceResultResolver { glyph ->
                val correct = choiceCard.isCorrect(glyph)
                val prompt = home.activeSession?.prompt ?: ""
                MeaningChoiceResultModel(
                    StudyTextCopy.meaningKanjiChoiceResult(choiceCard, prompt, correct),
                    if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                )
            },
        )
        home.renderComposeStudyRoute {
            MeaningChoiceSessionCard(model = model)
        }
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

    fun renderSimilarKanjiSession(session: RecordsSchedulerModels.StudySession) {
        resetChoiceSessionState(false)

        val choiceCard = similarChoiceCardForSession(session)
        val choices = ArrayList(choiceCard.choices)
        if (choices.size < 2) {
            home.renderComposeFlashcardSession(session)
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
        val model = SimilarChoiceSessionModel(
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
        home.renderComposeStudyRoute {
            SimilarChoiceSessionCard(
                model = model,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
        }
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
        resetChoiceSessionState(resetTouchTracking)
    }

    private fun resetChoiceSessionState(resetTouchTracking: Boolean) {
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
