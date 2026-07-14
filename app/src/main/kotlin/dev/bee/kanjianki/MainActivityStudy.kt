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
        if (isScreenshotLaunchRequested()) {
            doneActions.renderEmptyStudyQueue()
            return
        }
        studyQueueCoordinator.renderStudy(recoveryOnly = false)
    }

    internal open fun renderStudyRecoveryOnly() {
        cancelPendingHomeRouteLoads()
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
        if (!persistPendingStudyAnswer(state)) {
            state.resetForRetry(token)
            return false
        }
        val accepted = submit()
        if (!accepted) {
            state.resetForRetry(token)
            restoreActiveRecoveryAfterRejectedAnswer(token)
        }
        return accepted
    }

    fun prepareStudyAnswerFeedback(token: String): StudyAnswerFeedbackState {
        studyAnswerFeedbackState?.takeIf { it.sessionToken == token }?.let { return it }
        val restored = studyRecoveryStore.readPending()?.snapshot
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
            restoreActiveRecoveryAfterRejectedAnswer(token)
        }
    }

    fun continueAfterStudyAnswer(): Boolean {
        val state = studyAnswerFeedbackState ?: return false
        if (!state.tryContinue()) {
            return false
        }
        studyRecoveryStore.clearPending(state.sessionToken)
        studyRecoveryRouteActive = false
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

    internal fun clearStudyRecoveryIfUnchanged(stored: StoredStudyRecovery): Boolean =
        studyRecoveryStore.clearIfUnchanged(stored)

    private fun persistPendingStudyAnswer(state: StudyAnswerFeedbackState): Boolean {
        val session = activeSession ?: return false
        val item = session.item ?: return false
        val kanji = item.kanji.takeIf { it.isNotBlank() } ?: return false
        val pending = StudyPendingAnswerSnapshot(
            feedback = state.snapshot(),
            kanji = kanji,
            taskType = session.taskType,
            writingRequired = session.writingRequired,
            prompt = session.prompt,
            answerSignature = item.answerSignature,
            schedulerRevision = item.schedulerRevision,
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
        studyRecoveryRouteActive && hasStudyRecoveryPayload() &&
            !isScreenshotLaunchRequested() && !preserveStudyRecoveryForHarnessRoute

    override fun disableStudyOrdinaryResume() {
        if (preserveStudyRecoveryForHarnessRoute) return
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
    ) {
        activeSession = session
        studyRecoveryRouteActive = true
        recoveredStudyRunNeedsTargetReconciliation = false
        if (preserveStudyRecoveryForHarnessRoute) {
            activeStudyRecovery = null
            return
        }
        val item = session.item
        val restorable = item != null && StudySessionRoute.destination(session) == StudySessionRoute.Destination.FLASHCARD
        if (!restorable) {
            supersededRecoveryToken?.let(studyRecoveryStore::clearSession)
            activeStudyRecovery = null
            return
        }
        activeStudyRecovery = studyRecoveryStore.replaceWithActive(
            StudyActiveSessionSnapshot(
                sessionToken = session.token,
                kanji = item.kanji,
                answerSignatureDigest = studyAnswerSignatureDigest(item.answerSignature),
                schedulerRevision = item.schedulerRevision,
                routingVersion = item.routingVersion,
                taskType = session.taskType,
                promptSource = promptSource,
                sourceSyncFinishedAtMillis = latestSuccessfulSyncAtMillis,
            ),
        )
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
        val claimed = studyRecoveryStore.claimPending(stored, snapshot) ?: return false
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
