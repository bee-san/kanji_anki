package dev.bee.kanjianki

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiExplanationPolicy
import dev.bee.kanjianki.core.StudyCueFormatter
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
            out.add(formatSimilarKanjiSourceWord(example))
        }
    }
    return ArrayList(out)
}

private fun formatSimilarKanjiSourceWord(example: RecordsImportModels.Example): String {
    val expression = example.expression.trim()
    if (expression.isEmpty()) {
        return ""
    }
    val details = ArrayList<String>(2)
    val reading = StudyCueFormatter.hiraganaReading(example.reading).trim()
    if (reading.isNotEmpty()) {
        details.add(reading)
    }
    val meaning = StudyCueFormatter.cleanCollectionMeaning(example.meaning, 42)
    if (meaning.isNotEmpty()) {
        details.add(meaning)
    }
    val value = if (details.isEmpty()) {
        expression
    } else {
        "$expression (${details.joinToString(" · ")})"
    }
    return StudyCueFormatter.compact(value, 64)
}

internal class MainActivityStudyChoiceSessions(private val home: MainActivityStudy) {
    private val meaningKanjiChoicePlanner = MeaningKanjiChoicePlanner()
    private val meaningChoiceRandom: Random = SecureRandom()

    fun renderMeaningKanjiSession(session: RecordsSchedulerModels.StudySession) {
        prepareMeaningKanjiRender(session).invoke()
    }

    /**
     * Background-safe preparation for the meaning-kanji rung: performs every store
     * read and dictionary lookup here (safe on the io executor), and returns a thunk
     * that only builds compose models and renders when invoked on the main thread.
     * This keeps the cold-boot study path from scanning the full kanji inventory and
     * blocking on the dictionary install on the UI thread.
     */
    fun prepareMeaningKanjiRender(session: RecordsSchedulerModels.StudySession): () -> Unit {
        val choiceCard = meaningKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < 4) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(true)
                home.renderComposeFlashcardSession(session)
            }
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session)
        val question = StudyTextCopy.meaningKanjiChoiceQuestion(home.currentDictionaryLookup(), choiceCard, session.prompt)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        val taskLabel = StudyTaskCopy.labelForTask(session.taskType)
        // Precompute both result texts here on the background executor: the result
        // resolver runs in the answer click handler on the main thread, and the copy
        // only depends on whether the pick was correct, not on which glyph was picked.
        // This keeps the dictionary lookup out of the main-thread answer path.
        val resultCorrectText = StudyTextCopy.meaningKanjiChoiceResult(home.currentDictionaryLookup(), choiceCard, session.prompt, true)
        val resultWrongText = StudyTextCopy.meaningKanjiChoiceResult(home.currentDictionaryLookup(), choiceCard, session.prompt, false)

        return {
            resetChoiceSession(true)
            val model = MeaningChoiceSessionModel(
                modeLabel,
                StudyTextCopy.studyChoiceTitle(),
                taskLabel,
                StudyTextCopy.studyChoiceBody(),
                "",
                question,
                choiceCard.choices,
                answerPanel,
                KanjiChoiceHandler { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    // The choice log write and the review submit both run on the
                    // single-threaded io executor, in order; the click handler stays
                    // off the database entirely.
                    home.io.execute {
                        home.store.recordChoiceReviewLog(
                            choiceCard.targetKanji,
                            SimilarKanjiChoicePlanner.choiceSignature(choiceCard.choices),
                            glyph,
                            correct,
                            RecordsBase.LadderRung.MEANING_KANJI.wireName(),
                            System.currentTimeMillis(),
                        )
                    }
                    home.submitReview(if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false)
                },
                MeaningChoiceResultResolver { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    MeaningChoiceResultModel(
                        if (correct) resultCorrectText else resultWrongText,
                        if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                        if (correct) StudyTextCopy.passLabel() else StudyTextCopy.failLabel(),
                        correctChoice = choiceCard.targetKanji,
                        selectedChoiceCorrect = correct,
                    )
                },
            )
            renderMeaningChoiceRoute(model, MeaningChoiceSessionState())
        }
    }

    private fun renderMeaningChoiceRoute(model: MeaningChoiceSessionModel, state: MeaningChoiceSessionState) {
        val browseAction = model.answerPanel.glyph.takeIf { it.isNotBlank() }?.let { glyph ->
            Runnable { home.renderDetail(glyph, false, null, Runnable { renderMeaningChoiceRoute(model, state) }) }
        }
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            content = {
                MeaningChoiceSessionCard(
                    model = model,
                    state = state,
                    showInlineResultAction = false,
                    onBrowseAction = browseAction,
                )
            },
            actionBar = { MeaningChoiceResultActionBar(model = model, state = state) },
        )
    }

    fun renderKanjiReadingSession(session: RecordsSchedulerModels.StudySession) {
        prepareKanjiReadingRender(session).invoke()
    }

    /**
     * Background-safe preparation for the kanji_reading rung (Goal 78). Builds a
     * kana forced-choice card ("How is 〈kanji〉 read in 〈word〉?"); if fewer than
     * two choices can be built it falls back to a plain flashcard, matching the
     * meaning_kanji fallback pattern.
     */
    fun prepareKanjiReadingRender(session: RecordsSchedulerModels.StudySession): () -> Unit {
        val choiceCard = kanjiReadingChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < KanjiReadingChoicePlanner.MIN_CHOICE_COUNT) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(true)
                home.renderComposeFlashcardSession(session)
            }
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session)
        val question = StudyTextCopy.kanjiReadingChoiceQuestion(choiceCard)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        val taskLabel = StudyTaskCopy.labelForTask(session.taskType)
        val resultCorrectText = StudyTextCopy.kanjiReadingChoiceResult(choiceCard, true)
        val resultWrongText = StudyTextCopy.kanjiReadingChoiceResult(choiceCard, false)

        return {
            resetChoiceSession(true)
            val model = MeaningChoiceSessionModel(
                modeLabel,
                StudyTextCopy.kanjiReadingChoiceTitle(),
                taskLabel,
                StudyTextCopy.kanjiReadingChoiceBody(),
                "",
                question,
                choiceCard.choices,
                answerPanel,
                KanjiChoiceHandler { reading ->
                    val correct = choiceCard.isCorrect(reading)
                    home.io.execute {
                        home.store.recordChoiceReviewLog(
                            choiceCard.targetKanji,
                            SimilarKanjiChoicePlanner.choiceSignature(choiceCard.choices),
                            reading,
                            correct,
                            RecordsBase.LadderRung.KANJI_READING.wireName(),
                            System.currentTimeMillis(),
                        )
                    }
                    home.submitReview(if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false)
                },
                MeaningChoiceResultResolver { reading ->
                    val correct = choiceCard.isCorrect(reading)
                    MeaningChoiceResultModel(
                        if (correct) resultCorrectText else resultWrongText,
                        if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                        if (correct) StudyTextCopy.passLabel() else StudyTextCopy.failLabel(),
                        correctChoice = choiceCard.correctReading,
                        selectedChoiceCorrect = correct,
                    )
                },
            )
            renderMeaningChoiceRoute(model, MeaningChoiceSessionState())
        }
    }

    fun kanjiReadingChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession?,
    ): RecordsImportModels.KanjiReadingChoiceCard? {
        val kanji = session?.item?.kanji?.takeIf { it.isNotBlank() } ?: return null
        return KanjiReadingChoicePlanner.buildChoiceCard(
            kanji,
            home.store.kanjiReadingUsagesFor(kanji),
            home.store.kanjiReadingPoolFor(kanji),
            meaningChoiceRandom,
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
            meaningChoiceRandom,
            home.store.choiceWrongPickCounts(System.currentTimeMillis()),
        )
    }

    fun renderSimilarKanjiSession(session: RecordsSchedulerModels.StudySession) {
        prepareSimilarKanjiRender(session).invoke()
    }

    /**
     * Background-safe preparation for the similar-kanji rung. Store reads (choice
     * state, similar pairs, kanji inventory scan) and dictionary lookups run here;
     * the returned thunk only assembles compose models and renders on main.
     */
    fun prepareSimilarKanjiRender(session: RecordsSchedulerModels.StudySession): () -> Unit {
        val choiceCard = similarChoiceCardForSession(session)
        val choices = ArrayList(choiceCard.choices)
        if (choices.size < 2) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(false)
                home.renderComposeFlashcardSession(session)
            }
        }
        choices.shuffle()

        val meaning = StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session)
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
        val explanationLines = similarKanjiExplanationLines(explanation)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        val taskLabel = StudyTaskCopy.labelForTask(session.taskType)

        return {
            resetChoiceSession(false)
            val model = SimilarChoiceSessionModel(
                modeLabel,
                StudyTextCopy.studyChoiceTitle(),
                taskLabel,
                StudyTextCopy.studyChoiceBody(),
                reason,
                StudyTextCopy.studyChoiceQuestion(meaning),
                SimilarChoiceGridModel(
                    choices,
                    false,
                    KanjiChoiceHandler { glyph -> home.submitSimilarKanjiChoice(choiceCard, glyph) },
                    correctChoice = choiceCard.targetKanji,
                ),
                explanationLines,
            )
            lateinit var differenceModel: SimilarKanjiDifferenceModel
            differenceModel = similarKanjiDifferenceModel(
                choiceCard.targetKanji,
                choices,
                model.modeLabel,
                explanationLines,
                onBack = Runnable { renderSimilarChoiceRoute(model, differenceModel) },
            )
            renderSimilarChoiceRoute(model, differenceModel)
        }
    }

    private fun renderSimilarChoiceRoute(model: SimilarChoiceSessionModel, differenceModel: SimilarKanjiDifferenceModel) {
        home.composeRoute(
            selected = MainActivityBase.NAV_STUDY,
            content = {
                SimilarChoiceSessionCard(
                    model = model,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    showInlineChoices = true,
                    detailsExpandedByDefault = false,
                    onExploreDifferences = Runnable { renderSimilarDifferenceRoute(model, differenceModel) },
                )
            },
        )
    }

    private fun renderSimilarDifferenceRoute(model: SimilarChoiceSessionModel, differenceModel: SimilarKanjiDifferenceModel) {
        val withBrowseActions = differenceModel.copy(
            choices = differenceModel.choices.map { choice ->
                choice.copy(
                    onOpenBrowse = choice.kanji.takeIf { it.isNotBlank() }?.let { glyph ->
                        Runnable {
                            home.renderDetail(
                                glyph,
                                false,
                                null,
                                Runnable { renderSimilarDifferenceRoute(model, differenceModel) }
                            )
                        }
                    }
                )
            },
            onBack = Runnable { renderSimilarChoiceRoute(model, differenceModel) },
        )
        home.composeRoute(
            selected = MainActivityBase.NAV_STUDY,
            content = {
                SimilarKanjiDifferenceScreen(
                    model = withBrowseActions,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            },
        )
    }

    private fun similarKanjiDifferenceModel(
        targetKanji: String,
        choices: List<String>,
        modeLabel: String,
        explanationLines: List<SimilarKanjiExplanationLineModel>,
        onBack: Runnable,
    ): SimilarKanjiDifferenceModel {
        val orderedChoices = buildList {
            if (targetKanji.isNotBlank()) {
                add(targetKanji)
            }
            choices.forEach { glyph ->
                if (glyph.isNotBlank() && glyph != targetKanji && !contains(glyph)) {
                    add(glyph)
                }
            }
        }
        return SimilarKanjiDifferenceModel(
            modeLabel = modeLabel,
            title = StudyTextCopy.similarKanjiDifferencesTitle(),
            body = StudyTextCopy.similarKanjiDifferencesBody(),
            correctLabel = StudyTextCopy.similarKanjiCorrectLabel(),
            correctKanji = targetKanji,
            choicesTitle = StudyTextCopy.similarKanjiChoicesLabel(),
            choices = orderedChoices.map { glyph ->
                SimilarKanjiDifferenceChoiceModel(glyph, StudyTextCopy.similarKanjiChoiceLabel(glyph))
            },
            explanationLines = explanationLines,
            onBack = onBack,
        )
    }

    fun similarChoiceCardForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.SimilarKanjiChoiceCard {
        val now = System.currentTimeMillis()
        val targetKanji = session.item?.kanji ?: ""
        val stored = home.store.dueSimilarChoiceForActiveTarget(targetKanji, now)
        val meaning = StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session)
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
