package dev.bee.kanjianki

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyExampleSelector
import dev.bee.kanjianki.core.StudyLayoutPolicy
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.StrokeGuideGuard
import dev.bee.kanjianki.core.study.WritingActionPresentation
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingSample
import dev.bee.kanjianki.core.StudySessionRoute
import dev.bee.kanjianki.study.CapturedWriting
import dev.bee.kanjianki.study.WritingRecognizer

internal abstract class MainActivityStudy : MainActivityStats() {
    internal class CapturedWritingAttempt(
        @JvmField val captured: CapturedWriting,
        @JvmField val sample: WritingSample,
    )

    private val flashcardUi by lazy { MainActivityStudyFlashcard(this) }
    private val writingUi by lazy { MainActivityStudyWritingUi(this) }
    private val writingFlow by lazy { MainActivityStudyWritingFlow(this) }
    private val writingCheck by lazy { MainActivityStudyWritingCheck(this) }
    private val writingReview by lazy { MainActivityStudyReviewFlow(this) }

    val doneActions by lazy { MainActivityStudyDoneActions(this) }

    private val choiceSessions by lazy { MainActivityStudyChoiceSessions(this) }
    private val studyProgress by lazy { MainActivityStudyProgress(this) }
    private val moreNewCards by lazy { MainActivityStudyMoreNewCards(this) }
    private val studyState by lazy { MainActivityStudyState(this) }
    private val writingSession by lazy { MainActivityStudyWritingSession(this) }
    private val dictionaryLookupProvider by lazy { MainActivityDictionaryLookupProvider(this) }
    private val studyQueueCoordinator by lazy { MainActivityStudyQueueCoordinator(this) }
    private val pendingAnswerStore by lazy {
        StudyPendingAnswerStore(getSharedPreferences(PENDING_ANSWER_PREFERENCES, Context.MODE_PRIVATE))
    }

    fun learningPanelModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ): StudyAnswerPanelModel {
        return learningPanelModel(this, session, mnemonic)
    }

    fun firstExample(row: RecordsImportModels.DashboardRow?): RecordsImportModels.Example? {
        return StudyExampleSelector.firstExample(row)
    }

    override fun wordReadingExample(row: RecordsImportModels.DashboardRow): RecordsImportModels.Example? {
        return StudyExampleSelector.wordReadingExample(row)
    }

    fun exampleForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.Example? {
        return StudyExampleSelector.exampleForSession(session)
    }

    override fun renderStudy() {
        cancelPendingHomeRouteLoads()
        if (isScreenshotLaunchRequested()) {
            doneActions.renderEmptyStudyQueue()
            return
        }
        studyQueueCoordinator.renderStudy()
    }

    fun renderStudyLoading(studySessionActive: Boolean) {
        renderComposeStudyRoute(studySessionActive = studySessionActive) {
            HomeRouteLoadingScreen(
                title = dev.bee.kanjianki.core.StudyTextCopy.studyPracticeTitle(),
                homeLabel = dev.bee.kanjianki.core.HomeTextCopy.homeLabel(),
                onHome = ::renderHome,
            )
        }
    }

    fun renderEmptyStudyQueue() {
        doneActions.renderEmptyStudyQueue()
    }

    fun renderNoStudySession(seededPlan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        doneActions.renderNoStudySession(seededPlan)
    }

    fun renderFocusDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        doneActions.renderFocusDone(plan)
    }

    fun renderStudyRunDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        doneActions.renderStudyRunDone(plan)
    }

    fun availableStudyMoreNewCards(): Int {
        return moreNewCards.availableStudyMoreNewCards()
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        moreNewCards.showStudyMoreNewCardsDialog(availableAtOpen)
    }

    fun applyStudyMoreNewCardsRequest(countInput: EditText): Boolean {
        return moreNewCards.applyStudyMoreNewCardsRequest(countInput)
    }

    fun requestedStudyMoreNewCards(countInput: EditText): Int {
        return moreNewCards.requestedStudyMoreNewCards(countInput)
    }

    fun requestedStudyMoreNewCards(requestText: String): Int {
        return moreNewCards.requestedStudyMoreNewCards(requestText)
    }

    fun startStudyMoreNewCards(requestedCount: Int): Boolean {
        return moreNewCards.startStudyMoreNewCards(requestedCount)
    }

    override fun startFocusedStudy() {
        clearStudyModeOverrides()
        resetStudyRunProgress()
        renderStudy()
    }

    override fun renderStudyForKanji(kanji: String?) {
        cancelPendingHomeRouteLoads()
        if (isScreenshotLaunchRequested()) {
            doneActions.renderStudyForKanjiNotAvailable()
            return
        }
        studyQueueCoordinator.renderStudyForKanji(kanji)
    }

    fun renderStudyForKanjiNotAvailable() {
        doneActions.renderStudyForKanjiNotAvailable()
    }

    fun renderSession(session: RecordsSchedulerModels.StudySession) {
        prepareSessionRender(session).invoke()
    }

    /**
     * Prepares everything expensive for rendering [session] (store reads, dictionary
     * lookups, choice-card planning, heavy asset warmup) and returns a thunk that
     * performs the actual main-thread render. Safe to call on the background io
     * executor: the study route coordinator uses this so the cold-boot path
     * home -> study never scans the kanji inventory or parses the 9.5 MB stroke
     * asset on the UI thread.
     */
    fun prepareSessionRender(session: RecordsSchedulerModels.StudySession): () -> Unit {
        val mnemonic = prepareStudyAnswerMnemonic(session)
        return when (StudySessionRoute.destination(session)) {
            StudySessionRoute.Destination.WRITING -> {
                warmStrokeGuides()
                warmSessionDictionaryEntry(session)
                val render: () -> Unit = { writingSession.renderComposeWritingSession(session, mnemonic) }
                render
            }
            StudySessionRoute.Destination.SIMILAR_KANJI -> choiceSessions.prepareSimilarKanjiRender(session, mnemonic)
            StudySessionRoute.Destination.MEANING_KANJI -> choiceSessions.prepareMeaningKanjiRender(session, mnemonic)
            StudySessionRoute.Destination.KANJI_READING -> choiceSessions.prepareKanjiReadingRender(session, mnemonic)
            StudySessionRoute.Destination.READING_KANJI -> choiceSessions.prepareReadingKanjiRender(session, mnemonic)
            StudySessionRoute.Destination.FLASHCARD -> {
                warmSessionDictionaryEntry(session)
                val render: () -> Unit = { flashcardUi.renderComposeFlashcardSession(session, mnemonic) }
                render
            }
        }
    }

    /** Load the current local note while the session render is being prepared on IO. */
    internal fun prepareStudyAnswerMnemonic(
        session: RecordsSchedulerModels.StudySession,
    ): StudyAnswerMnemonicModel? {
        return studyAnswerMnemonicModel(store.kanjiMnemonicNote(session.item?.kanji))
    }

    /**
     * Pre-fetches the dictionary entry for [session]'s kanji on the calling
     * (background) thread. The main-thread render thunks build answer panels that
     * look the kanji up again; with the entry warmed here those lookups hit the
     * dictionary's in-memory cache instead of querying SQLite on the UI thread.
     */
    fun warmSessionDictionaryEntry(session: RecordsSchedulerModels.StudySession) {
        val lookup = warmDictionaryLookup()
        val kanji = session.item?.kanji?.takeIf { it.isNotBlank() } ?: return
        lookup.lookupKanji(kanji)
    }

    fun meaningKanjiChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        return choiceSessions.meaningKanjiChoiceCardForSession(session)
    }

    fun similarChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession,
    ): RecordsImportModels.SimilarKanjiChoiceCard {
        return choiceSessions.similarChoiceCardForSession(session)
    }

    fun buildSimilarKanjiChoices(targetKanji: String): List<String> {
        return choiceSessions.buildSimilarKanjiChoices(targetKanji)
    }

    fun renderComposeFlashcardSession(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ) {
        flashcardUi.renderComposeFlashcardSession(session, mnemonic)
    }

    fun renderComposeWritingSession(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ) {
        writingSession.renderComposeWritingSession(session, mnemonic)
    }

    fun randomFontVariantTypeface(): Typeface {
        return StudyFontVariants.random(this)
    }

    fun resetWritingSession(session: RecordsSchedulerModels.StudySession) {
        writingSession.resetWritingSession(session)
    }

    fun hideStudyActionBar() {
        writingSession.hideStudyActionBar()
    }

    fun resetStudyRunProgress() {
        studyProgress.resetStudyRunProgress()
    }

    override fun clearStudyModeOverrides() {
        studyProgress.clearStudyModeOverrides()
    }

    fun markStudyRunPassed(kanji: String?) {
        studyProgress.markStudyRunPassed(kanji)
    }

    override fun initializeSessionProgressTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        studyProgress.initializeSessionProgressTarget(plan)
    }

    fun registerStudyTaskShown(key: String?) {
        studyProgress.registerStudyTaskShown(key)
    }

    fun markStudyTaskCompleted(key: String?) {
        studyProgress.markStudyTaskCompleted(key)
    }

    fun sessionTaskKey(session: RecordsSchedulerModels.StudySession?): String {
        return studyProgress.sessionTaskKey(session)
    }

    fun similarRepairProgressKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
        return studyProgress.similarRepairProgressKey(repair)
    }

    fun similarRepairStudyTaskKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
        return studyProgress.similarRepairStudyTaskKey(repair)
    }

    fun startActiveStudyTask(key: String?, kanji: String?, taskType: String?, startedAt: Long) {
        studyProgress.startActiveStudyTask(key, kanji, taskType, startedAt)
    }

    fun completeActiveStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        studyProgress.completeActiveStudyTask(key, outcome, answeredAt)
    }

    override fun pauseActiveStudyTask() {
        studyProgress.pauseActiveStudyTask()
    }

    override fun resumeActiveStudyTask() {
        studyProgress.resumeActiveStudyTask()
    }

    override fun abandonActiveStudyTask() {
        studyProgress.abandonActiveStudyTask()
    }

    fun typingAnswerField(): TypingAnswerState {
        return flashcardUi.typingAnswerField()
    }

    override fun fontResource(fontRes: Int, fallback: Typeface): Typeface {
        return flashcardUi.fontResource(fontRes, fallback)
    }

    fun flashcardAnswerPanelModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ): StudyAnswerPanelModel {
        return flashcardUi.flashcardAnswerPanelModel(session, mnemonic)
    }

    fun meaningChoiceAnswerPanelModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ): StudyAnswerPanelModel {
        return flashcardUi.meaningChoiceAnswerPanelModel(session, mnemonic)
    }

    override fun currentDictionaryLookup(): DictionaryLookup {
        return dictionaryLookupProvider.currentDictionaryLookup()
    }

    fun buildFlashcardActionBar(revealed: Boolean) {
        flashcardUi.buildFlashcardActionBar(revealed)
    }

    fun revealFlashcardAnswer() {
        flashcardUi.revealFlashcardAnswer()
    }

    fun expandFlashcardForAnswer() {
        flashcardUi.expandFlashcardForAnswer()
    }

    override fun handleFlashcardGesture(event: MotionEvent): Boolean {
        return flashcardUi.handleFlashcardGesture(event)
    }

    fun handleFlashcardRelease(event: MotionEvent): Boolean {
        return flashcardUi.handleFlashcardRelease(event)
    }

    fun isTouchInsideView(view: View?, event: MotionEvent): Boolean {
        return flashcardUi.isTouchInsideView(view, event)
    }

    fun buildComposeWritingActionBarState(): WritingActionsBarState {
        return writingUi.buildComposeActionBarState()
    }

    fun refreshWritingModelStatus() {
        writingUi.refreshWritingModelStatus()
    }

    fun eraseWritingPad() {
        writingFlow.eraseWritingPad()
    }

    fun startGuidedWritingRetry() {
        writingFlow.startGuidedWritingRetry()
    }

    fun studyPadHeight(): Int {
        val density = resources.displayMetrics.density
        val screenDp = Math.round(resources.displayMetrics.heightPixels / density)
        return studyPadHeightForScreenDp(screenDp)
    }

    fun studyPadHeightForScreenDp(screenDp: Int): Int {
        return dp(StudyLayoutPolicy.writingPadHeightDp(screenDp))
    }

    fun checkWriting() {
        writingCheck.checkWriting()
    }

    fun submitSimilarKanjiChoice(card: RecordsImportModels.SimilarKanjiChoiceCard, selectedKanji: String): Boolean {
        return submitWithAnswerFeedback(selectedKanji == card.targetKanji, selectedKanji) {
            writingReview.submitSimilarKanjiChoice(card, selectedKanji)
        }
    }

    fun submitLoggedChoiceReview(
        targetKanji: String,
        choiceSignature: String,
        selectedChoice: String,
        correct: Boolean,
        rung: RecordsBase.LadderRung,
        correctAnswer: String = targetKanji,
    ): Boolean {
        return submitWithAnswerFeedback(correct, selectedChoice) {
            writingReview.submitLoggedChoiceReview(
                targetKanji,
                choiceSignature,
                selectedChoice,
                correct,
                rung,
                correctAnswer,
            )
        }
    }

    fun showNoInkWhenNeeded(): Boolean {
        return writingFlow.showNoInkWhenNeeded()
    }

    fun showModelUnavailable(message: String) {
        writingFlow.showModelUnavailable(message)
    }

    fun recognizeWriting(
        recognizer: WritingRecognizer,
        captured: CapturedWriting,
        sample: WritingSample,
        guide: StrokeGuide?,
        target: String,
        token: String,
    ) {
        writingCheck.recognizeWriting(recognizer, captured, sample, guide, target, token)
    }

    fun submitReview(
        rating: String,
        override: Boolean,
        ladder: RecordsBase.StudyLadderSettings? = null,
        interactionSource: String = "review-action",
        answerEvidence: AnswerEvidence? = null,
    ): Boolean {
        val selectedAnswer = answerEvidence?.selectedAnswer
            ?.takeIf { it.isNotBlank() }
            ?: typingAnswerState?.text?.toString()?.takeIf { it.isNotBlank() }
            ?: rating
        return submitWithAnswerFeedback(rating != MainActivityBase.RATING_AGAIN, selectedAnswer) {
            writingReview.submitReview(rating, override, ladder, interactionSource, answerEvidence)
        }
    }

    private fun submitWithAnswerFeedback(
        correct: Boolean,
        selectedAnswer: String,
        submit: () -> Boolean,
    ): Boolean {
        val token = activeSession?.token ?: return false
        val state = studyAnswerFeedbackState
            ?.takeIf { it.sessionToken == token }
            ?: StudyAnswerFeedbackState(token).also { studyAnswerFeedbackState = it }
        val outcome = if (correct) StudyAnswerOutcome.CORRECT else StudyAnswerOutcome.INCORRECT
        if (!state.begin(outcome, selectedAnswer)) {
            return false
        }
        persistPendingStudyAnswer(state)
        val accepted = submit()
        if (!accepted) {
            state.resetForRetry(token)
            pendingAnswerStore.clear()
        }
        return accepted
    }

    fun prepareStudyAnswerFeedback(token: String): StudyAnswerFeedbackState {
        studyAnswerFeedbackState?.takeIf { it.sessionToken == token }?.let { return it }
        val restored = pendingAnswerStore.read()
            ?.takeIf { it.feedback.sessionToken == token }
            ?.feedback
            ?.let(StudyAnswerFeedbackState::restore)
        return (restored ?: StudyAnswerFeedbackState(token)).also { studyAnswerFeedbackState = it }
    }

    fun markStudyAnswerApplied(token: String) {
        postToMainIfActive {
            val state = studyAnswerFeedbackState
            if (state?.markApplied(token) == true) {
                persistPendingStudyAnswer(state)
            }
        }
    }

    fun resetStudyAnswerForRetry(token: String) {
        if (studyAnswerFeedbackState?.resetForRetry(token) == true) {
            pendingAnswerStore.clear()
        }
    }

    fun continueAfterStudyAnswer(): Boolean {
        val state = studyAnswerFeedbackState ?: return false
        if (!state.tryContinue()) {
            return false
        }
        pendingAnswerStore.clear()
        if (activeSimilarWritingRepair != null) {
            activeSimilarWritingRepair = null
        }
        renderStudy()
        return true
    }

    fun submitSimilarWritingRepair(rating: String): Boolean {
        return submitWithAnswerFeedback(rating != MainActivityBase.RATING_AGAIN, rating) {
            writingReview.submitSimilarWritingRepair(rating)
            true
        }
    }

    fun pendingStudyAnswerSnapshot(): StudyPendingAnswerSnapshot? = pendingAnswerStore.read()

    fun restorePendingStudyAnswer(snapshot: StudyPendingAnswerSnapshot) {
        studyAnswerFeedbackState = StudyAnswerFeedbackState.restore(snapshot.feedback)
    }

    fun clearPendingStudyAnswer() {
        pendingAnswerStore.clear()
    }

    private fun persistPendingStudyAnswer(state: StudyAnswerFeedbackState) {
        val session = activeSession ?: return
        val kanji = session.item?.kanji?.takeIf { it.isNotBlank() } ?: return
        pendingAnswerStore.save(
            StudyPendingAnswerSnapshot(
                feedback = state.snapshot(),
                kanji = kanji,
                taskType = session.taskType,
                writingRequired = session.writingRequired,
                prompt = session.prompt,
            ),
        )
    }

    fun skipSimilarWritingRepair() {
        writingReview.skipSimilarWritingRepair()
    }

    fun undoLastRating() {
        writingReview.undoLastRating()
    }

    fun completeActiveRepairStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        studyState.completeActiveRepairStudyTask(key, outcome, answeredAt)
    }

    fun initialHintState(session: RecordsSchedulerModels.StudySession): HintState {
        return studyState.initialHintState(session)
    }

    fun setHintState(state: HintState?) {
        studyState.setHintState(state)
    }

    fun showWritingHint() {
        writingFlow.showWritingHint()
    }

    fun showAnalysis(analysis: WritingAnalysis) {
        writingFlow.showAnalysis(analysis)
    }

    fun updateResultActions() {
        writingUi.updateResultActions()
    }

    fun writingActionPresentation(): WritingActionPresentation {
        return writingUi.writingActionPresentation()
    }

    fun updateUndoStrokeButton() {
        writingUi.updateUndoStrokeButton()
    }

    fun startCleanerRetry() {
        writingFlow.startCleanerRetry()
    }

    fun undoWritingStroke() {
        writingFlow.undoWritingStroke()
    }

    fun replayWritingAnalysis() {
        writingFlow.replayWritingAnalysis()
    }

    fun handleDrawingEdited() {
        writingFlow.handleDrawingEdited()
    }

    fun clearWritingResult() {
        writingFlow.clearWritingResult()
    }

    fun handleDrawingBlocked(decision: StrokeGuideGuard.Decision) {
        writingFlow.handleDrawingBlocked(decision)
    }

    fun setStudyStatus(value: String, color: Int) {
        writingUi.setStudyStatus(value, color)
    }

    private companion object {
        const val PENDING_ANSWER_PREFERENCES = "pending_study_answer"
    }

    fun setResultStatus(value: String, color: Int) {
        writingUi.setResultStatus(value, color)
    }

    fun canRevealMoreHelp(): Boolean {
        return writingUi.canRevealMoreHelp()
    }
}
