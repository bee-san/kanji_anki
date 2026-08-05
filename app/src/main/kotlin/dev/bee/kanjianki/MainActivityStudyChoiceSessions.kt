package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.KanjiReadingAligner
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner
import dev.bee.kanjianki.core.ReadingKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiExplanationPolicy
import dev.bee.kanjianki.core.StudyCueFormatter
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyChoiceDataSnapshot
import java.security.SecureRandom
import java.util.LinkedHashSet
import java.util.Random
import kotlinx.coroutines.runBlocking

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

internal fun meaningChoiceSessionStateForFeedback(
    feedback: StudyAnswerFeedbackState,
): MeaningChoiceSessionState {
    val selectedChoice = feedback.selectedAnswer.takeIf {
        feedback.feedbackVisible && it.isNotBlank()
    }
    return MeaningChoiceSessionState(selectedChoice)
}

/** Stable display order derived from persisted identity, independent of query order. */
@Suppress("kotlin:S2245")
internal fun tokenOrderedSimilarKanjiChoices(
    choices: List<String>,
    sessionToken: String,
): List<String> {
    val ordered = choices.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
        .toMutableList()
    val random = Random(sessionToken.hashCode().toLong())
    for (index in ordered.lastIndex downTo 1) {
        val swapIndex = random.nextInt(index + 1)
        val value = ordered[index]
        ordered[index] = ordered[swapIndex]
        ordered[swapIndex] = value
    }
    return ordered
}

internal fun similarKanjiChoiceRecoveryDigest(choices: List<String>): String =
    studyAnswerSignatureDigest(
        "similar-kanji-choice-recovery-v1\u0000" + SimilarKanjiChoicePlanner.choiceSignature(choices),
    )

private data class PreparedSimilarChoiceCard(
    val card: RecordsImportModels.SimilarKanjiChoiceCard,
    val persistedDueSource: Boolean,
)

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

    /**
     * Exact repair choices must keep the same ordering while a persisted study
     * session is restored. This seed is UI state, never a security boundary.
     */
    @Suppress("kotlin:S2245")
    private fun deterministicChoiceRandom(sessionToken: String): Random {
        return Random(sessionToken.hashCode().toLong())
    }

    fun renderMeaningKanjiSession(session: RecordsSchedulerModels.StudySession) {
        prepareMeaningKanjiRender(session, home.prepareStudyAnswerMnemonic(session)).invoke()
    }

    /**
     * Background-safe preparation for the meaning-kanji rung: performs every store
     * read and dictionary lookup here (safe on the io executor), and returns a thunk
     * that only builds compose models and renders when invoked on the main thread.
     * This keeps the cold-boot study path from scanning the full kanji inventory and
     * blocking on the dictionary install on the UI thread.
     */
    fun prepareMeaningKanjiRender(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): () -> Unit {
        val choiceCard = meaningKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < 4) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(true)
                home.renderComposeFlashcardSession(session, mnemonic)
            }
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session, mnemonic)
        val question = StudyTextCopy.meaningKanjiChoiceQuestion(home.currentDictionaryLookup(), choiceCard, session.prompt)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        // Precompute both result texts here on the background executor: the result
        // resolver runs in the answer click handler on the main thread, and the copy
        // only depends on whether the pick was correct, not on which glyph was picked.
        // This keeps the dictionary lookup out of the main-thread answer path.
        val resultCorrectText = StudyTextCopy.meaningKanjiChoiceResult(home.currentDictionaryLookup(), choiceCard, session.prompt, true)
        val resultWrongText = StudyTextCopy.meaningKanjiChoiceResult(home.currentDictionaryLookup(), choiceCard, session.prompt, false)

        return {
            resetChoiceSession(true)
            val feedback = home.prepareStudyAnswerFeedback(session.token)
            val model = MeaningChoiceSessionModel(
                modeLabel,
                question,
                choiceCard.choices,
                answerPanel,
                KanjiChoiceHandler { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    home.submitLoggedChoiceReview(
                        choiceCard.targetKanji,
                        SimilarKanjiChoicePlanner.choiceSignature(choiceCard.choices),
                        glyph,
                        correct,
                        RecordsBase.LadderRung.MEANING_KANJI,
                    )
                },
                MeaningChoiceResultResolver { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    MeaningChoiceResultModel(
                        if (correct) resultCorrectText else resultWrongText,
                        if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                        if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
                        correctChoice = choiceCard.targetKanji,
                        selectedChoiceCorrect = correct,
                    )
                },
            ).copy(
                feedbackState = feedback,
                continueAction = home.studyContinueAction(feedback) {
                    home.continueAfterStudyAnswer()
                },
            )
            renderMeaningChoiceRoute(model, meaningChoiceSessionStateForFeedback(feedback))
        }
    }

    private fun renderMeaningChoiceRoute(model: MeaningChoiceSessionModel, state: MeaningChoiceSessionState) {
        val routeSnapshot = home.studySessionViewModel.acceptedRouteSnapshot()
        val expectedToken = model.feedbackState?.sessionToken
        val browseAction = model.answerPanel.glyph.takeIf { it.isNotBlank() }?.let { glyph ->
            expectedToken?.let { token ->
                Runnable {
                    if (home.matchesMountedStudyRoute(token, null)) {
                        home.renderDetail(
                            glyph,
                            false,
                            null,
                            Runnable {
                                if (home.matchesMountedStudyRoute(token, null)) {
                                    renderMeaningChoiceRoute(model, state)
                                }
                            },
                        )
                    }
                }
            }
        }
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            content = {
                Column {
                    ChoiceStudyTopBar(routeSnapshot)
                    MeaningChoiceSessionCard(
                        model = model,
                        state = state,
                        showInlineResultAction = false,
                        onBrowseAction = browseAction,
                    )
                }
            },
            actionBar = {
                // Collect the published snapshot rather than letting the bar read
                // `model.feedbackState`. That holder lives in `:application`, a plain JVM
                // module with no Compose dependency, so its phase is an ordinary field and
                // reading it here subscribes to nothing -- the Continue button would stay
                // disabled forever and the session would strand.
                val studyState by home.studySessionUiState.collectAsState()
                MeaningChoiceResultActionBar(
                    model = model,
                    state = state,
                    feedback = studyState.feedback?.takeIf { it.sessionToken == expectedToken },
                )
            },
        )
    }

    fun renderKanjiReadingSession(session: RecordsSchedulerModels.StudySession) {
        prepareKanjiReadingRender(session, home.prepareStudyAnswerMnemonic(session)).invoke()
    }

    /**
     * Background-safe preparation for the kanji_reading rung (Goal 78). Builds a
     * kana forced-choice card ("How is 〈kanji〉 read in 〈word〉?"); if fewer than
     * two choices can be built it falls back to a plain flashcard, matching the
     * meaning_kanji fallback pattern.
     */
    fun prepareKanjiReadingRender(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): () -> Unit {
        val choiceCard = kanjiReadingChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < KanjiReadingChoicePlanner.MIN_CHOICE_COUNT) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(true)
                home.renderComposeFlashcardSession(session, mnemonic)
            }
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session, mnemonic)
        val question = StudyTextCopy.kanjiReadingChoiceQuestion(choiceCard)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        val resultCorrectText = StudyTextCopy.kanjiReadingChoiceResult(choiceCard, true)
        val resultWrongText = StudyTextCopy.kanjiReadingChoiceResult(choiceCard, false)

        return {
            resetChoiceSession(true)
            val feedback = home.prepareStudyAnswerFeedback(session.token)
            val model = MeaningChoiceSessionModel(
                modeLabel,
                question,
                choiceCard.choices,
                answerPanel,
                KanjiChoiceHandler { reading ->
                    val correct = choiceCard.isCorrect(reading)
                    home.submitLoggedChoiceReview(
                        choiceCard.targetKanji,
                        SimilarKanjiChoicePlanner.choiceSignature(choiceCard.choices),
                        reading,
                        correct,
                        RecordsBase.LadderRung.KANJI_READING,
                        correctAnswer = choiceCard.correctReading,
                    )
                },
                MeaningChoiceResultResolver { reading ->
                    val correct = choiceCard.isCorrect(reading)
                    MeaningChoiceResultModel(
                        if (correct) resultCorrectText else resultWrongText,
                        if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                        if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
                        correctChoice = choiceCard.correctReading,
                        selectedChoiceCorrect = correct,
                    )
                },
            ).copy(
                feedbackState = feedback,
                continueAction = home.studyContinueAction(feedback) {
                    home.continueAfterStudyAnswer()
                },
            )
            renderMeaningChoiceRoute(model, meaningChoiceSessionStateForFeedback(feedback))
        }
    }

    fun kanjiReadingChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession?,
    ): RecordsImportModels.KanjiReadingChoiceCard? {
        val kanji = session?.item?.kanji?.takeIf { it.isNotBlank() } ?: return null
        val choiceData = loadChoiceData(kanji)
        val route = AdaptiveStudyItemPolicy.routeState(session.item)
        val evidence = route?.answerEvidence
        if (route?.isRepairActive() == true && evidence != null) {
            val canonical = evidence.correctAnswer.ifBlank {
                KanjiReadingAligner.alignPlain(
                    evidence.renderedExpression,
                    evidence.renderedReading,
                    home.currentDictionaryLookup(),
                )?.firstOrNull { it.kanji == kanji }?.canonicalReading.orEmpty()
            }.ifBlank { return null }
            return KanjiReadingChoicePlanner.buildExactChoiceCard(
                kanji,
                evidence.renderedExpression,
                canonical,
                choiceData.kanjiReadingUsages,
                choiceData.kanjiReadingPool,
                deterministicChoiceRandom(session.token),
            )
        }
        return KanjiReadingChoicePlanner.buildChoiceCard(
            kanji,
            choiceData.kanjiReadingUsages,
            choiceData.kanjiReadingPool,
            meaningChoiceRandom,
        )
    }

    /**
     * Background-safe preparation for the reading_kanji homophone rung (Goal 79).
     * Builds a kanji-glyph forced-choice card; falls back to a flashcard when
     * fewer than three choices can be built (a 2-option homophone card is a coin
     * flip).
     */
    fun prepareReadingKanjiRender(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): () -> Unit {
        val choiceCard = readingKanjiChoiceCardForSession(session)
        if (choiceCard == null || choiceCard.choices.size < ReadingKanjiChoicePlanner.MIN_CHOICE_COUNT) {
            home.warmSessionDictionaryEntry(session)
            return {
                resetChoiceSession(true)
                home.renderComposeFlashcardSession(session, mnemonic)
            }
        }

        val answerPanel = home.meaningChoiceAnswerPanelModel(session, mnemonic)
        val question = StudyTextCopy.readingKanjiChoiceQuestion(choiceCard)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)
        val resultCorrectText = StudyTextCopy.readingKanjiChoiceResult(choiceCard, true)
        val resultWrongText = StudyTextCopy.readingKanjiChoiceResult(choiceCard, false)

        return {
            resetChoiceSession(true)
            val feedback = home.prepareStudyAnswerFeedback(session.token)
            val model = MeaningChoiceSessionModel(
                modeLabel,
                question,
                choiceCard.choices,
                answerPanel,
                KanjiChoiceHandler { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    home.submitLoggedChoiceReview(
                        choiceCard.targetKanji,
                        SimilarKanjiChoicePlanner.choiceSignature(choiceCard.choices),
                        glyph,
                        correct,
                        RecordsBase.LadderRung.READING_KANJI,
                    )
                },
                MeaningChoiceResultResolver { glyph ->
                    val correct = choiceCard.isCorrect(glyph)
                    MeaningChoiceResultModel(
                        if (correct) resultCorrectText else resultWrongText,
                        if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                        if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
                        correctChoice = choiceCard.targetKanji,
                        selectedChoiceCorrect = correct,
                    )
                },
            ).copy(
                feedbackState = feedback,
                continueAction = home.studyContinueAction(feedback) {
                    home.continueAfterStudyAnswer()
                },
            )
            renderMeaningChoiceRoute(model, meaningChoiceSessionStateForFeedback(feedback))
        }
    }

    fun readingKanjiChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession?,
    ): RecordsImportModels.ReadingKanjiChoiceCard? {
        val kanji = session?.item?.kanji?.takeIf { it.isNotBlank() } ?: return null
        val choiceData = loadChoiceData(kanji)
        val route = AdaptiveStudyItemPolicy.routeState(session.item)
        val evidence = route?.answerEvidence
        if (route?.isRepairActive() == true && evidence != null) {
            val canonical = evidence.correctAnswer.ifBlank {
                KanjiReadingAligner.alignPlain(
                    evidence.renderedExpression,
                    evidence.renderedReading,
                    home.currentDictionaryLookup(),
                )?.firstOrNull { it.kanji == kanji }?.canonicalReading.orEmpty()
            }.ifBlank { return null }
            return ReadingKanjiChoicePlanner.buildExactChoiceCard(
                kanji,
                evidence.renderedExpression,
                canonical,
                choiceData.readingKanjiUsages,
                choiceData.readingKanjiCandidates,
                deterministicChoiceRandom(session.token),
            )
        }
        return ReadingKanjiChoicePlanner.buildChoiceCard(
            kanji,
            choiceData.readingKanjiUsages,
            choiceData.readingKanjiCandidates,
            meaningChoiceRandom,
        )
    }

    fun meaningKanjiChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        val row = session?.row ?: return null
        val choiceData = loadChoiceData(row.kanji)
        return meaningKanjiChoicePlanner.buildChoiceCard(
            row,
            choiceData.activeRows,
            choiceData.inventory,
            meaningChoiceRandom,
            choiceData.wrongPickCounts,
            home.currentDictionaryLookup(),
        )
    }

    fun renderSimilarKanjiSession(session: RecordsSchedulerModels.StudySession) {
        prepareSimilarKanjiRender(session, home.prepareStudyAnswerMnemonic(session)).invoke()
    }

    /**
     * Background-safe preparation for the similar-kanji rung. Store reads (choice
     * state, similar pairs, kanji inventory scan) and dictionary lookups run here;
     * the returned thunk only assembles compose models and renders on main.
     */
    fun prepareSimilarKanjiRender(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): PreparedStudySessionRender {
        val targetKanji = session.item?.kanji.orEmpty()
        val now = System.currentTimeMillis()
        val choiceData = loadChoiceData(targetKanji, now)
        val preparedChoice = prepareSimilarChoiceCardForSession(session, choiceData, now)
        val choiceCard = preparedChoice.card
        val choices = tokenOrderedSimilarKanjiChoices(choiceCard.choices, session.token)
        if (choices.size < 2 || choiceCard.targetKanji !in choices) {
            home.warmSessionDictionaryEntry(session)
            return PreparedStudySessionRender(
                render = {
                    resetChoiceSession(false)
                    home.renderComposeFlashcardSession(session, mnemonic)
                },
            )
        }
        val choiceSignatureDigest = similarKanjiChoiceRecoveryDigest(choiceCard.choices)

        val meaning = StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session)
        val explanation = SimilarKanjiExplanationPolicy.explain(
            choiceCard.targetKanji,
            choiceData.inventory,
            choiceData.similarPairs,
            similarKanjiExplanationSourceWords(session),
        )
        val explanationLines = similarKanjiExplanationLines(explanation)
        val modeLabel = StudyTaskCopy.studyModeLabel(session)

        return PreparedStudySessionRender(
            render = {
                resetChoiceSession(false)
                val feedback = home.prepareStudyAnswerFeedback(session.token)
                val activeUiRecovery = home.activeStudyUiRecovery(session.token)
                val model = SimilarChoiceSessionModel(
                    modeLabel,
                    StudyTextCopy.studyChoiceQuestion(meaning),
                    SimilarChoiceGridModel(
                        choices,
                        false,
                        KanjiChoiceHandler { glyph ->
                            home.submitSimilarKanjiChoice(
                                session.token,
                                activeUiRecovery,
                                choiceCard,
                                glyph,
                            )
                        },
                        correctChoice = choiceCard.targetKanji,
                    ),
                    explanationLines,
                    mnemonic = mnemonic,
                    feedbackState = feedback,
                    continueAction = home.studyContinueAction(feedback) {
                        home.continueAfterStudyAnswer(session.token, activeUiRecovery)
                    },
                )
                lateinit var differenceModel: SimilarKanjiDifferenceModel
                differenceModel = similarKanjiDifferenceModel(
                    choiceCard.targetKanji,
                    choices,
                    model.modeLabel,
                    explanationLines,
                    onBack = Runnable {
                        if (home.matchesMountedStudyRoute(session.token, activeUiRecovery)) {
                            renderSimilarChoiceRoute(
                                model,
                                differenceModel,
                                session.token,
                                activeUiRecovery,
                            )
                        }
                    },
                )
                renderSimilarChoiceRoute(model, differenceModel, session.token, activeUiRecovery)
            },
            similarChoiceSignatureDigest = choiceSignatureDigest.takeIf { preparedChoice.persistedDueSource },
        )
    }

    private fun renderSimilarChoiceRoute(
        model: SimilarChoiceSessionModel,
        differenceModel: SimilarKanjiDifferenceModel,
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
    ) {
        val routeSnapshot = home.studySessionViewModel.acceptedRouteSnapshot()
        home.composeRoute(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            content = {
                // Same reason as the meaning-choice action bar: only this StateFlow makes
                // the Continue button enable once the answer applies.
                val studyState by home.studySessionUiState.collectAsState()
                Column {
                    ChoiceStudyTopBar(routeSnapshot)
                    SimilarChoiceSessionCard(
                        model = model,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        showInlineChoices = true,
                        detailsExpandedByDefault = false,
                        feedback = studyState.feedback?.takeIf { it.sessionToken == expectedToken },
                        onExploreDifferences = Runnable {
                            if (home.matchesMountedStudyRoute(expectedToken, expectedRecovery)) {
                                renderSimilarDifferenceRoute(
                                    model,
                                    differenceModel,
                                    expectedToken,
                                    expectedRecovery,
                                )
                            }
                        },
                    )
                }
            },
        )
    }

    private fun renderSimilarDifferenceRoute(
        model: SimilarChoiceSessionModel,
        differenceModel: SimilarKanjiDifferenceModel,
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
    ) {
        val routeSnapshot = home.studySessionViewModel.acceptedRouteSnapshot()
        val withBrowseActions = differenceModel.copy(
            choices = differenceModel.choices.map { choice ->
                choice.copy(
                    onOpenBrowse = choice.kanji.takeIf { it.isNotBlank() }?.let { glyph ->
                        Runnable {
                            if (home.matchesMountedStudyRoute(expectedToken, expectedRecovery)) {
                                home.renderDetail(
                                    glyph,
                                    false,
                                    null,
                                    Runnable {
                                        if (home.matchesMountedStudyRoute(expectedToken, expectedRecovery)) {
                                            renderSimilarDifferenceRoute(
                                                model,
                                                differenceModel,
                                                expectedToken,
                                                expectedRecovery,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                )
            },
            onBack = Runnable {
                if (home.matchesMountedStudyRoute(expectedToken, expectedRecovery)) {
                    renderSimilarChoiceRoute(model, differenceModel, expectedToken, expectedRecovery)
                }
            },
        )
        home.composeRoute(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            content = {
                Column {
                    ChoiceStudyTopBar(routeSnapshot)
                    SimilarKanjiDifferenceScreen(
                        model = withBrowseActions,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                }
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

    fun similarChoiceCardForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.SimilarKanjiChoiceCard =
        prepareSimilarChoiceCardForSession(session).card

    private fun prepareSimilarChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession,
        choiceData: StudyChoiceDataSnapshot = loadChoiceData(
            session.item?.kanji.orEmpty(),
        ),
        now: Long = System.currentTimeMillis(),
    ): PreparedSimilarChoiceCard {
        val targetKanji = session.item?.kanji ?: ""
        val stored = runBlocking {
            home.studyUseCases.loadDueSimilarChoice(targetKanji, now)
        }
        val meaning = StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session)
        val route = AdaptiveStudyItemPolicy.routeState(session.item)
        val preferredConfusion = if (route?.isRepairActive() == true) {
            route.answerEvidence?.confusedWith
        } else {
            null
        }
        val card = SimilarKanjiChoicePlanner.choiceCardForSession(
            stored,
            targetKanji,
            meaning,
            choiceData.similarPairs,
            preferredConfusion,
        )
        val canonicalStoredSignature = stored?.let {
            SimilarKanjiChoicePlanner.choiceSignature(it.choices)
        }
        return PreparedSimilarChoiceCard(
            card = card,
            persistedDueSource = stored != null &&
                stored.choiceSignature == canonicalStoredSignature &&
                card.choiceSignature == stored.choiceSignature &&
                card.choiceSignature == SimilarKanjiChoicePlanner.choiceSignature(card.choices),
        )
    }

    fun buildSimilarKanjiChoices(targetKanji: String): List<String> {
        return SimilarKanjiChoicePlanner.fallbackChoices(
            targetKanji,
            loadChoiceData(targetKanji).similarPairs,
        )
    }

    private fun loadChoiceData(
        kanji: String,
        now: Long = System.currentTimeMillis(),
    ): StudyChoiceDataSnapshot = runBlocking {
        home.studyUseCases.loadChoiceData(kanji, now)
    }

    fun resetChoiceSession(resetTouchTracking: Boolean) {
        MainActivityStudyInteractionReset.resetChoice(home, resetTouchTracking)
    }

    @Composable
    private fun ChoiceStudyTopBar(routeSnapshot: StudyRouteSnapshot) {
        StudyTopBar(
            routeSnapshot = routeSnapshot,
            onClose = home::renderHome,
            onSettings = home::renderSettings,
        )
    }

}
