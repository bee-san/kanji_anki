package dev.bee.kanjianki

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
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

internal data class PreparedStudySessionRender(
    val render: () -> Unit,
    val similarChoiceSignatureDigest: String? = null,
) {
    operator fun invoke() = render()

    fun matches(snapshot: StudyActiveSessionSnapshot): Boolean =
        similarChoiceSignatureDigest == snapshot.similarChoiceSignatureDigest
}

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
    private val answerSubmissionCoordinator by lazy {
        StudyAnswerSubmissionCoordinator(
            stateStore = studySessionViewModel,
            persistence = object : StudyAnswerPersistence {
                override fun persistPending(state: StudyAnswerFeedbackState): Boolean =
                    persistPendingStudyAnswer(state)

                override fun restoreAfterRejectedAnswer(sessionToken: String) {
                    restoreActiveRecoveryAfterRejectedAnswer(sessionToken)
                }
            },
        )
    }

    val doneActions by lazy { MainActivityStudyDoneActions(this) }
    internal val studyDoneViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[StudyDoneViewModel::class.java]
    }

    private val choiceSessions by lazy { MainActivityStudyChoiceSessions(this) }
    private val studyProgress by lazy { MainActivityStudyProgress(this) }
    private val moreNewCards by lazy { MainActivityStudyMoreNewCards(this) }
    private val studyState by lazy { MainActivityStudyState(this) }
    private val writingSession by lazy { MainActivityStudyWritingSession(this) }
    private val dictionaryLookupProvider by lazy { MainActivityDictionaryLookupProvider(this) }
    private val studyQueueCoordinator by lazy { MainActivityStudyQueueCoordinator(this) }
    private val studyRecoveryStore by lazy {
        StudySessionRecoveryStore(getSharedPreferences(STUDY_RECOVERY_PREFERENCES, Context.MODE_PRIVATE))
    }
    private var activeStudyRecovery: StoredActiveStudyRecovery? = null
    private var studyRecoveryRouteActive = false
    internal var recoveredStudyRunNeedsTargetReconciliation = false

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
        currentRoute = MainActivityBase.NAV_STUDY
        currentHomeRouteRestoration = null
        doneActions.clearRetainedStudyDone()
        if (isScreenshotLaunchRequested()) {
            doneActions.renderEmptyStudyQueue(studySessionViewModel.acceptedRouteSnapshot())
            return
        }
        studyQueueCoordinator.renderStudy(recoveryOnly = false)
    }

    internal open fun renderStudyRecoveryOnly() {
        cancelPendingHomeRouteLoads()
        currentRoute = MainActivityBase.NAV_STUDY
        currentHomeRouteRestoration = null
        studyQueueCoordinator.renderStudy(recoveryOnly = true)
    }

    override fun renderHome() {
        disableStudyOrdinaryResume()
        super.renderHome()
    }

    override fun renderStats() {
        disableStudyOrdinaryResume()
        super.renderStats()
    }

    fun renderStudyLoading(studySessionActive: Boolean) {
        studySessionViewModel.showLoading()
        val routeSnapshot = studySessionViewModel.acceptedRouteSnapshot()
        renderComposeStudyRoute(routeSnapshot, studySessionActive = studySessionActive) {
            HomeRouteLoadingScreen(
                title = dev.bee.kanjianki.core.StudyTextCopy.studyPracticeTitle(),
                homeLabel = dev.bee.kanjianki.core.HomeTextCopy.homeLabel(),
                onHome = ::renderHome,
            )
        }
    }

    fun renderEmptyStudyQueue() {
        doneActions.renderEmptyStudyQueue(studySessionViewModel.acceptedRouteSnapshot())
    }

    fun renderNoStudySession(seededPlan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        doneActions.renderNoStudySession(seededPlan, studySessionViewModel.acceptedRouteSnapshot())
    }

    fun renderFocusDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        doneActions.renderFocusDone(plan, studySessionViewModel.acceptedRouteSnapshot())
    }

    fun renderStudyRunDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        doneActions.renderStudyRunDone(plan, studySessionViewModel.acceptedRouteSnapshot())
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
        currentRoute = MainActivityBase.NAV_STUDY
        currentHomeRouteRestoration = null
        doneActions.clearRetainedStudyDone()
        if (isScreenshotLaunchRequested()) {
            doneActions.renderStudyForKanjiNotAvailable(studySessionViewModel.acceptedRouteSnapshot())
            return
        }
        studyQueueCoordinator.renderStudyForKanji(kanji)
    }

    fun renderStudyForKanjiNotAvailable() {
        doneActions.renderStudyForKanjiNotAvailable(studySessionViewModel.acceptedRouteSnapshot())
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
    fun prepareSessionRender(session: RecordsSchedulerModels.StudySession): PreparedStudySessionRender {
        val mnemonic = prepareStudyAnswerMnemonic(session)
        return when (StudySessionRoute.destination(session)) {
            StudySessionRoute.Destination.WRITING -> {
                warmStrokeGuides()
                warmSessionDictionaryEntry(session)
                val render: () -> Unit = { writingSession.renderComposeWritingSession(session, mnemonic) }
                PreparedStudySessionRender(render)
            }
            StudySessionRoute.Destination.SIMILAR_KANJI -> choiceSessions.prepareSimilarKanjiRender(session, mnemonic)
            StudySessionRoute.Destination.MEANING_KANJI -> PreparedStudySessionRender(
                choiceSessions.prepareMeaningKanjiRender(session, mnemonic),
            )
            StudySessionRoute.Destination.KANJI_READING -> PreparedStudySessionRender(
                choiceSessions.prepareKanjiReadingRender(session, mnemonic),
            )
            StudySessionRoute.Destination.READING_KANJI -> PreparedStudySessionRender(
                choiceSessions.prepareReadingKanjiRender(session, mnemonic),
            )
            StudySessionRoute.Destination.FLASHCARD -> {
                warmSessionDictionaryEntry(session)
                val render: () -> Unit = { flashcardUi.renderComposeFlashcardSession(session, mnemonic) }
                PreparedStudySessionRender(render)
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

    fun submitSimilarKanjiChoice(
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
        card: RecordsImportModels.SimilarKanjiChoiceCard,
        selectedKanji: String,
    ): Boolean {
        if (!matchesUngradedStudyRoute(expectedToken, expectedRecovery)) return false
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
        return answerSubmissionCoordinator.submit(
            correct = correct,
            selectedAnswer = selectedAnswer,
            enqueueReview = submit,
        )
    }

    fun prepareStudyAnswerFeedback(token: String): StudyAnswerFeedbackState {
        studyAnswerFeedbackState?.takeIf { it.sessionToken == token }?.let { return it }
        val restored = studyRecoveryStore.readPending()?.snapshot
            ?.takeIf { it.feedback.sessionToken == token }
            ?.feedback
            ?.let(StudyAnswerFeedbackState::restore)
        return studySessionViewModel.feedbackFor(token, restored)
    }

    fun markStudyAnswerApplied(token: String) {
        // The APPLIED transition + its durable envelope write must survive a
        // finishing/destroyed Activity. Unlike setContent/startActivity/toast, these
        // touch only Compose-observable state and SharedPreferences, so gating them
        // behind postToMainIfActive (which drops on isFinishing/isDestroyed) would
        // leave a consumed-token card permanently stuck at SUBMITTING on a config
        // change / retained-holder teardown: "Fail saved" persists but Continue can
        // never advance. Post to the main thread (Compose state is main-confined)
        // without the active-Activity guard so the gate always reaches APPLIED.
        postToMain {
            val state = studyAnswerFeedbackState
            if (state?.markApplied(token) == true) {
                persistPendingStudyAnswer(state)
            }
        }
    }

    fun resetStudyAnswerForRetry(token: String) {
        if (studyAnswerFeedbackState?.resetForRetry(token) == true) {
            restoreActiveRecoveryAfterRejectedAnswer(token)
        }
    }

    internal fun saveStudyMnemonicAfterAnswer(
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
        kanji: String,
        note: String,
    ): Boolean {
        if (!matchesMountedStudyRoute(expectedToken, expectedRecovery)) return false
        val feedback = studyAnswerFeedbackState ?: return false
        val phase = feedback.snapshot().phase
        if (feedback.sessionToken != expectedToken ||
            (phase != StudyAnswerFeedbackPhase.SUBMITTING && phase != StudyAnswerFeedbackPhase.APPLIED)
        ) {
            return false
        }
        val normalizedKanji = kanji.trim()
        if (normalizedKanji.isEmpty() || activeSession?.item?.kanji?.trim() != normalizedKanji) return false
        val normalizedNote = note.trim()
        // This is an explicit user-authored save, so commit the tiny local SQLite write
        // before returning. Activity-owned executors are interrupted in onDestroy and
        // could otherwise acknowledge a queued mnemonic that rotation silently drops.
        store.saveKanjiMnemonicNote(normalizedKanji, normalizedNote, System.currentTimeMillis())
        return true
    }

    fun continueAfterStudyAnswer(): Boolean {
        val state = studyAnswerFeedbackState ?: return false
        var pending = studyRecoveryStore.readPending()
        val retryCanonicalPending = pending == null ||
            (pending.snapshot.feedback.sessionToken == state.sessionToken &&
                pending.snapshot.feedback.phase == StudyAnswerFeedbackPhase.SUBMITTING)
        if (retryCanonicalPending && canCreateContinuedHandoff(state.sessionToken)) {
            if (!persistPendingStudyAnswer(state)) return false
            pending = studyRecoveryStore.readPending()
                ?: return false
        }
        if (!state.tryContinue()) {
            return false
        }
        val continued = when {
            pending == null -> null
            canPersistContinuedHandoff(pending) -> studyRecoveryStore.continuePending(pending)
                ?: return restoreAppliedFeedbackAfterContinueFailure(state)
            !canClearLegacyPendingAfterContinue(pending, state.sessionToken) ->
                return restoreAppliedFeedbackAfterContinueFailure(state)
            !studyRecoveryStore.clearIfUnchanged(pending) ->
                return restoreAppliedFeedbackAfterContinueFailure(state)
            else -> null
        }
        activeStudyRecovery = null
        studyRecoveryRouteActive = continued != null
        if (activeSimilarWritingRepair != null) {
            activeSimilarWritingRepair = null
        }
        renderStudy()
        return true
    }

    private fun canPersistContinuedHandoff(pending: StoredPendingStudyRecovery): Boolean =
        pending.snapshot.feedback.phase == StudyAnswerFeedbackPhase.APPLIED &&
            pending.snapshot.feedback.sessionToken == studyAnswerFeedbackState?.sessionToken &&
            pending.snapshot.taskType != MainActivityBase.TASK_REPAIR_WRITING &&
            pending.snapshot.answerSignature != null &&
            pending.snapshot.schedulerRevision != null

    private fun canClearLegacyPendingAfterContinue(
        pending: StoredPendingStudyRecovery,
        expectedToken: String,
    ): Boolean = pending.snapshot.feedback.phase == StudyAnswerFeedbackPhase.APPLIED &&
        pending.snapshot.feedback.sessionToken == expectedToken &&
        (pending.snapshot.taskType == MainActivityBase.TASK_REPAIR_WRITING ||
            pending.snapshot.answerSignature == null && pending.snapshot.schedulerRevision == null)

    private fun canCreateContinuedHandoff(expectedToken: String): Boolean {
        val session = activeSession ?: return false
        val item = session.item ?: return false
        return session.token == expectedToken &&
            session.taskType != MainActivityBase.TASK_REPAIR_WRITING &&
            item.schedulerRevision >= 0L
    }

    private fun restoreAppliedFeedbackAfterContinueFailure(state: StudyAnswerFeedbackState): Boolean {
        state.rollbackContinue()
        return false
    }

    internal fun continueAfterStudyAnswer(
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
    ): Boolean {
        if (!matchesMountedStudyRoute(expectedToken, expectedRecovery)) return false
        return continueAfterStudyAnswer()
    }

    fun submitSimilarWritingRepair(rating: String): Boolean {
        return submitWithAnswerFeedback(rating != MainActivityBase.RATING_AGAIN, rating) {
            writingReview.submitSimilarWritingRepair(rating)
        }
    }

    fun pendingStudyAnswerSnapshot(): StudyPendingAnswerSnapshot? = studyRecoveryStore.readPending()?.snapshot

    internal fun pendingStudyRecovery(): StoredPendingStudyRecovery? = studyRecoveryStore.readPending()

    internal fun activeStudyRecovery(): StoredActiveStudyRecovery? = studyRecoveryStore.readActive()

    internal fun studyRecoverySessionToken(): String? = studyRecoveryStore.currentSessionToken()

    fun restorePendingStudyAnswer(snapshot: StudyPendingAnswerSnapshot) {
        studyAnswerFeedbackState = StudyAnswerFeedbackState.restore(snapshot.feedback)
    }

    fun clearPendingStudyAnswer() {
        studyRecoveryStore.clearPending()
    }

    internal fun clearStudyAnswerAfterUndo(
        token: String,
        expectedRecovery: StoredPendingStudyRecovery?,
    ) {
        if (expectedRecovery != null &&
            expectedRecovery.snapshot.feedback.sessionToken == token
        ) {
            studyRecoveryStore.clearIfUnchanged(expectedRecovery)
        } else {
            studyRecoveryStore.clearPending(token)
        }
        if (studyAnswerFeedbackState?.sessionToken == token) {
            studyAnswerFeedbackState = null
        }
        activeStudyRecovery = null
    }

    internal fun clearStudyRecoveryIfUnchanged(stored: StoredStudyRecovery): Boolean =
        studyRecoveryStore.clearIfUnchanged(stored)

    internal fun armContinuedStudyRecoveryForExplicitRoute(): Boolean {
        if (preserveStudyRecoveryForHarnessRoute) return true
        val stored = studyRecoveryStore.readPending() ?: return true
        if (stored.snapshot.feedback.phase != StudyAnswerFeedbackPhase.CONTINUED ||
            stored.resumeOnOrdinaryLaunch
        ) {
            return true
        }
        return studyRecoveryStore.claimContinued(stored) != null
    }

    private fun persistPendingStudyAnswer(state: StudyAnswerFeedbackState): Boolean {
        val session = activeSession ?: return false
        val item = session.item ?: return false
        val kanji = item.kanji.takeIf { it.isNotBlank() } ?: return false
        val repair = if (session.taskType == MainActivityBase.TASK_REPAIR_WRITING) {
            activeSimilarWritingRepair
                ?.takeIf { it.id > 0L && it.activeToken == session.token }
                ?: return false
        } else {
            null
        }
        val pending = StudyPendingAnswerSnapshot(
            feedback = state.snapshot(),
            kanji = kanji,
            taskType = session.taskType,
            writingRequired = session.writingRequired,
            prompt = session.prompt,
            answerSignature = item.answerSignature,
            schedulerRevision = item.schedulerRevision,
            repairId = repair?.id,
            repairAttempts = repair?.attempts,
        )
        val active = activeStudyRecovery
        val stored = when {
            active != null && active.snapshot.sessionToken == session.token -> {
                studyRecoveryStore.transitionActiveToPending(active, pending)
            }
            studyRecoveryStore.readPending()?.snapshot?.feedback?.sessionToken == session.token -> {
                studyRecoveryStore.updatePending(
                    session.token,
                    pending,
                    retainFallback = pending.feedback.phase != StudyAnswerFeedbackPhase.APPLIED,
                )
            }
            else -> studyRecoveryStore.createPendingIfEmpty(pending)
        }
        if (stored != null) {
            activeStudyRecovery = null
        }
        return stored != null
    }

    private fun restoreActiveRecoveryAfterRejectedAnswer(token: String) {
        val retainedActive = activeStudyRecovery?.takeIf { active ->
            active.snapshot.sessionToken == token && studyRecoveryStore.readActive()?.raw == active.raw
        }
        val pending = studyRecoveryStore.readPending()
        val restored = pending
            ?.takeIf { it.snapshot.feedback.sessionToken == token }
            ?.let(studyRecoveryStore::claimPendingFallback)
        if (restored == null) {
            studyRecoveryStore.clearPending(token)
        }
        activeStudyRecovery = restored ?: retainedActive
        studyRecoveryRouteActive = activeStudyRecovery != null
    }

    internal fun hasStudyRecoveryPayload(): Boolean = studyRecoveryStore.read() != null

    internal fun shouldResumeStudyOnOrdinaryLaunch(): Boolean =
        studyRecoveryStore.shouldResumeOnOrdinaryLaunch()

    override fun shouldRestoreStudyRouteAfterRecreation(): Boolean =
        !isScreenshotLaunchRequested() &&
            !preserveStudyRecoveryForHarnessRoute &&
            (
                currentRoute == MainActivityBase.NAV_STUDY ||
                    (studyRecoveryRouteActive && hasStudyRecoveryPayload())
            )

    internal fun restoreStudyRouteAfterRecreation(): Boolean {
        if (doneActions.restoreRetainedStudyDone()) {
            return true
        }
        if (shouldResumeStudyOnOrdinaryLaunch()) {
            renderStudyRecoveryOnly()
            return true
        }
        if (hasStudyRecoveryPayload()) {
            return false
        }
        renderStudy()
        return true
    }

    override fun disableStudyOrdinaryResume() {
        if (preserveStudyRecoveryForHarnessRoute) return
        doneActions.clearRetainedStudyDone()
        studyRecoveryStore.disableOrdinaryResume()
        activeStudyRecovery = null
        studyRecoveryRouteActive = false
        recoveredStudyRunNeedsTargetReconciliation = false
        activeSession = null
        studyAnswerFeedbackState = null
    }

    override fun flushStudyRecovery() {
        if (activeStudyRecovery != null) {
            studyRecoveryStore.flush()
        }
    }

    /** Publish a newly selected card only after its async route result has been accepted. */
    internal fun acceptNewActiveStudySession(
        session: RecordsSchedulerModels.StudySession,
        promptSource: StudyPromptSource,
        latestSuccessfulSyncAtMillis: Long,
        supersededRecoveryToken: String? = null,
        similarChoiceSignatureDigest: String? = null,
        advancingRecovery: StoredPendingStudyRecovery? = null,
    ): Boolean {
        if (preserveStudyRecoveryForHarnessRoute) {
            activeSession = session
            studyRecoveryRouteActive = true
            recoveredStudyRunNeedsTargetReconciliation = false
            activeStudyRecovery = null
            return true
        }
        val item = session.item
        val destination = StudySessionRoute.destination(session)
        val restorable = item != null && (
            destination == StudySessionRoute.Destination.FLASHCARD ||
                destination == StudySessionRoute.Destination.SIMILAR_KANJI && similarChoiceSignatureDigest != null
            )
        val snapshot = item?.takeIf { restorable }?.let {
            StudyActiveSessionSnapshot(
                sessionToken = session.token,
                kanji = it.kanji,
                answerSignatureDigest = studyAnswerSignatureDigest(it.answerSignature),
                schedulerRevision = it.schedulerRevision,
                routingVersion = it.routingVersion,
                taskType = session.taskType,
                promptSource = promptSource,
                sourceSyncFinishedAtMillis = latestSuccessfulSyncAtMillis,
                similarChoiceSignatureDigest = similarChoiceSignatureDigest,
            )
        }
        val storedActive = when {
            advancingRecovery != null && snapshot != null ->
                studyRecoveryStore.replaceContinuedWithActive(advancingRecovery, snapshot) ?: return false
            advancingRecovery != null -> {
                if (!studyRecoveryStore.clearIfUnchanged(advancingRecovery)) return false
                null
            }
            snapshot != null -> studyRecoveryStore.replaceWithActive(snapshot)
            else -> null
        }
        activeSession = session
        studyRecoveryRouteActive = true
        recoveredStudyRunNeedsTargetReconciliation = false
        if (!restorable) {
            supersededRecoveryToken?.let(studyRecoveryStore::clearSession)
            activeStudyRecovery = null
            return true
        }
        activeStudyRecovery = storedActive
        return true
    }

    /** Conditionally consume an advancing marker before mounting a nonrestorable or terminal route. */
    internal fun clearAdvancingStudyRecovery(
        expected: StoredPendingStudyRecovery,
        nextSession: RecordsSchedulerModels.StudySession?,
    ): Boolean {
        if (!studyRecoveryStore.clearIfUnchanged(expected)) return false
        activeStudyRecovery = null
        activeSession = nextSession
        studyAnswerFeedbackState = null
        studyRecoveryRouteActive = nextSession != null
        recoveredStudyRunNeedsTargetReconciliation = false
        if (nextSession == null) {
            activeSimilarWritingRepair = null
        }
        return true
    }

    internal fun clearAdvancingStudyRecoveryForTerminal(
        expected: StoredPendingStudyRecovery,
        terminalEvidence: StudyRouteSnapshot,
    ): Boolean {
        val activeToken = activeSession?.token
        if (!terminalEvidence.canComplete) {
            return false
        }
        if (terminalEvidence.sessionToken == null) {
            if (activeToken != null) return false
        } else if (
            terminalEvidence.progress.targetCount <= 0 ||
            activeToken.isNullOrEmpty() ||
            terminalEvidence.sessionToken != activeToken ||
            expected.snapshot.feedback.sessionToken != activeToken
        ) {
            return false
        }
        return clearAdvancingStudyRecovery(expected, activeSession)
    }

    internal fun acceptTerminalSessionAbsence(expectedRoute: StudyRouteSnapshot): StudyRouteSnapshot? {
        val accepted = studySessionViewModel.acceptTerminalSessionAbsence(expectedRoute) ?: return null
        activeSimilarWritingRepair = null
        return accepted
    }

    internal fun acceptRestoredActiveStudySession(
        stored: StoredActiveStudyRecovery,
        session: RecordsSchedulerModels.StudySession,
    ): Boolean {
        val claimed = studyRecoveryStore.claimActive(stored) ?: return false
        activeStudyRecovery = claimed
        activeSession = session
        studyAnswerFeedbackState = null
        studyRecoveryRouteActive = true
        recoveredStudyRunNeedsTargetReconciliation = true
        registerStudyTaskShown(sessionTaskKey(session))
        startActiveStudyTask(sessionTaskKey(session), session.item?.kanji, session.taskType, System.currentTimeMillis())
        return true
    }

    internal fun acceptRestoredPendingStudySession(
        stored: StoredPendingStudyRecovery,
        snapshot: StudyPendingAnswerSnapshot,
        session: RecordsSchedulerModels.StudySession,
    ): Boolean {
        val claimed = studyRecoveryStore.claimAppliedPending(stored, snapshot) ?: return false
        activeStudyRecovery = null
        activeSession = session
        restorePendingStudyAnswer(claimed.snapshot)
        studyRecoveryRouteActive = true
        recoveredStudyRunNeedsTargetReconciliation = false
        return true
    }

    internal fun acceptPendingFallbackStudySession(
        stored: StoredPendingStudyRecovery,
        session: RecordsSchedulerModels.StudySession,
    ): Boolean {
        val claimed = studyRecoveryStore.claimPendingFallback(stored) ?: return false
        activeStudyRecovery = claimed
        activeSession = session
        studyAnswerFeedbackState = null
        studyRecoveryRouteActive = true
        recoveredStudyRunNeedsTargetReconciliation = true
        registerStudyTaskShown(sessionTaskKey(session))
        startActiveStudyTask(sessionTaskKey(session), session.item?.kanji, session.taskType, System.currentTimeMillis())
        return true
    }

    internal fun activeStudyUiRecovery(token: String): StoredActiveStudyRecovery? =
        activeStudyRecovery?.takeIf { it.snapshot.sessionToken == token }

    internal fun matchesActiveStudyRecovery(expected: StoredActiveStudyRecovery): Boolean {
        val active = activeStudyRecovery ?: return false
        return active.snapshot.sessionToken == expected.snapshot.sessionToken &&
            active.writeEpoch == expected.writeEpoch
    }

    internal fun matchesUngradedStudyRoute(
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
    ): Boolean {
        if (activeSession?.token != expectedToken) return false
        if (expectedRecovery != null && !matchesActiveStudyRecovery(expectedRecovery)) return false
        val feedback = studyAnswerFeedbackState ?: return false
        return feedback.sessionToken == expectedToken &&
            feedback.snapshot().phase == StudyAnswerFeedbackPhase.UNANSWERED
    }

    internal fun matchesMountedStudyRoute(
        expectedToken: String,
        expectedRecovery: StoredActiveStudyRecovery?,
    ): Boolean {
        if (activeSession?.token != expectedToken) return false
        val feedback = studyAnswerFeedbackState
        if (feedback?.sessionToken == expectedToken &&
            feedback.snapshot().phase == StudyAnswerFeedbackPhase.CONTINUED
        ) {
            return false
        }
        if (expectedRecovery == null || matchesActiveStudyRecovery(expectedRecovery)) return true
        return studyRecoveryStore.readPending()?.snapshot?.feedback?.sessionToken == expectedToken
    }

    internal fun persistActiveStudyTypedDraft(expected: StoredActiveStudyRecovery, value: String) {
        if (!matchesActiveStudyRecovery(expected)) return
        val updated = studyRecoveryStore.updateActiveDeferred(
            expected.snapshot.sessionToken,
            expected.writeEpoch,
        ) { it.copy(typedDraft = value, revealed = false) }
        if (updated != null && matchesActiveStudyRecovery(expected)) activeStudyRecovery = updated
    }

    internal fun persistActiveStudyReveal(expected: StoredActiveStudyRecovery): Boolean {
        if (!matchesActiveStudyRecovery(expected)) return false
        val updated = studyRecoveryStore.updateActive(
            expected.snapshot.sessionToken,
            expected.writeEpoch,
        ) { it.copy(revealed = true) }
        if (updated == null || !matchesActiveStudyRecovery(expected)) return false
        activeStudyRecovery = updated
        return true
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
        const val STUDY_RECOVERY_PREFERENCES = "pending_study_answer"
    }

    fun setResultStatus(value: String, color: Int) {
        writingUi.setResultStatus(value, color)
    }

    fun canRevealMoreHelp(): Boolean {
        return writingUi.canRevealMoreHelp()
    }
}
