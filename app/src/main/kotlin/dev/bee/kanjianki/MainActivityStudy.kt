package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.bee.kanjianki.core.DictionaryLookup
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

    private val flashcardUi = MainActivityStudyFlashcard(this)
    private val writingUi = MainActivityStudyWritingUi(this)
    private val writingFlow = MainActivityStudyWritingFlow(this)
    private val writingCheck = MainActivityStudyWritingCheck(this)
    private val writingReview = MainActivityStudyReviewFlow(this)

    @JvmField
    val doneActions = MainActivityStudyDoneActions(this)

    private val choiceSessions = MainActivityStudyChoiceSessions(this)
    private val studyProgress = MainActivityStudyProgress(this)
    private val moreNewCards = MainActivityStudyMoreNewCards(this)
    private val studyState = MainActivityStudyState(this)
    private val writingSession = MainActivityStudyWritingSession(this)
    private val dictionaryLookupProvider = MainActivityDictionaryLookupProvider(this)
    private val studyQueueCoordinator = MainActivityStudyQueueCoordinator(this)

    fun learningPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return learningPanelModel(this, session)
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
        studyQueueCoordinator.renderStudy()
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
        studyQueueCoordinator.renderStudyForKanji(kanji)
    }

    fun renderStudyForKanjiNotAvailable() {
        doneActions.renderStudyForKanjiNotAvailable()
    }

    fun renderSession(session: RecordsSchedulerModels.StudySession) {
        when (StudySessionRoute.destination(session)) {
            StudySessionRoute.Destination.WRITING -> writingSession.renderComposeWritingSession(session)
            StudySessionRoute.Destination.SIMILAR_KANJI -> choiceSessions.renderSimilarKanjiSession(session)
            StudySessionRoute.Destination.MEANING_KANJI -> choiceSessions.renderMeaningKanjiSession(session)
            StudySessionRoute.Destination.FLASHCARD -> flashcardUi.renderComposeFlashcardSession(session)
        }
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

    fun renderComposeFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        flashcardUi.renderComposeFlashcardSession(session)
    }

    fun renderComposeWritingSession(session: RecordsSchedulerModels.StudySession) {
        writingSession.renderComposeWritingSession(session)
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

    fun studyReasonLine(session: RecordsSchedulerModels.StudySession): String {
        return dev.bee.kanjianki.core.StudyTextCopy.studyReasonLine(
            activeSimilarWritingRepair != null,
            session,
            settings().matureSupportThreshold,
            System.currentTimeMillis()
        )
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

    fun flashcardAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return flashcardUi.flashcardAnswerPanelModel(session)
    }

    fun meaningChoiceAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return flashcardUi.meaningChoiceAnswerPanelModel(session)
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

    fun submitSimilarKanjiChoice(card: RecordsImportModels.SimilarKanjiChoiceCard, selectedKanji: String) {
        writingReview.submitSimilarKanjiChoice(card, selectedKanji)
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

    fun submitReview(rating: String, override: Boolean) {
        writingReview.submitReview(rating, override)
    }

    fun completeActiveRepairStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        studyState.completeActiveRepairStudyTask(key, outcome, answeredAt)
    }

    fun tuneSchedulerIfNeeded(parameters: RecordsSchedulerModels.SchedulerParameters, now: Long) {
        studyState.tuneSchedulerIfNeeded(parameters, now)
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

    fun setResultStatus(value: String, color: Int) {
        writingUi.setResultStatus(value, color)
    }

    fun canRevealMoreHelp(): Boolean {
        return writingUi.canRevealMoreHelp()
    }
}
