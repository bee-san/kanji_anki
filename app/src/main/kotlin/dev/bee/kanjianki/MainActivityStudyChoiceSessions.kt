package dev.bee.kanjianki

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiExplanationPolicy
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import java.security.SecureRandom
import java.util.LinkedHashSet
import java.util.Random

internal fun similarKanjiExplanationSourceWords(session: RecordsSchedulerModels.StudySession?): List<String> {
    val examples = session?.row?.examples ?: return emptyList()
    val out = LinkedHashSet<String>()
    for (example in examples) {
        val expression = example.expression.trim()
        if (expression.isNotEmpty()) {
            out.add(expression)
        }
    }
    return ArrayList(out)
}

internal class MainActivityStudyChoiceSessions(private val home: MainActivityStudy) {
    private val meaningKanjiChoicePlanner = MeaningKanjiChoicePlanner()
    private val meaningChoiceRandom: Random = SecureRandom()

    fun renderMeaningKanjiSession(session: RecordsSchedulerModels.StudySession) {
        resetChoiceSession(true)

        val choiceCard = meaningKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < 4) {
            home.renderComposeFlashcardSession(session)
            return
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session)
        val model = MeaningChoiceSessionModel(
            StudyTaskCopy.studyModeLabel(session),
            StudyTextCopy.studyChoiceTitle(),
            StudyTaskCopy.labelForTask(session.taskType),
            StudyTextCopy.studyChoiceBody(),
            "",
            StudyTextCopy.meaningKanjiChoiceQuestion(home.currentDictionaryLookup(), choiceCard, session.prompt),
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
                    StudyTextCopy.meaningKanjiChoiceResult(home.currentDictionaryLookup(), choiceCard, prompt, correct),
                    if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                    if (correct) StudyTextCopy.passLabel() else StudyTextCopy.failLabel(),
                    correctChoice = choiceCard.targetKanji,
                    selectedChoiceCorrect = correct,
                )
            },
        )
        val state = MeaningChoiceSessionState()
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            content = {
                MeaningChoiceSessionCard(
                    model = model,
                    state = state,
                    showInlineResultAction = false,
                )
            },
            actionBar = { MeaningChoiceResultActionBar(model = model, state = state) },
        )
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
        resetChoiceSession(false)

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
        val explanation = SimilarKanjiExplanationPolicy.explain(
            choiceCard.targetKanji,
            home.store.searchKanjiInventory(""),
            home.store.similarPairsForKanji(choiceCard.targetKanji),
            similarKanjiExplanationSourceWords(session),
        )
        val model = SimilarChoiceSessionModel(
            StudyTaskCopy.studyModeLabel(session),
            StudyTextCopy.studyChoiceTitle(),
            StudyTaskCopy.labelForTask(session.taskType),
            StudyTextCopy.studyChoiceBody(),
            reason,
            StudyTextCopy.studyChoiceQuestion(meaning),
            SimilarChoiceGridModel(
                choices,
                true
            ) { glyph -> home.submitSimilarKanjiChoice(choiceCard, glyph) },
            similarKanjiExplanationLines(explanation),
        )
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            content = {
                SimilarChoiceSessionCard(
                    model = model,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    showInlineChoices = false,
                )
            },
            actionBar = { SimilarChoiceActionBar(model.gridModel) },
        )
    }

    fun similarChoiceCardForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.SimilarKanjiChoiceCard {
        val now = System.currentTimeMillis()
        val targetKanji = session.item?.kanji ?: ""
        val stored = home.store.dueSimilarChoiceForActiveTarget(targetKanji, now)
        val meaning = if (session.row == null) "" else StudyTextCopy.rowMeaning(session.row)
        return SimilarKanjiChoicePlanner.choiceCardForSession(
            stored,
            targetKanji,
            meaning,
            home.store.similarPairsForKanji(targetKanji)
        )
    }

    fun buildSimilarKanjiChoices(targetKanji: String): List<String> {
        return SimilarKanjiChoicePlanner.fallbackChoices(
            targetKanji,
            home.store.similarPairsForKanji(targetKanji)
        )
    }

    fun resetChoiceSession(resetTouchTracking: Boolean) {
        MainActivityStudyInteractionReset.resetChoice(home, resetTouchTracking)
    }

}
