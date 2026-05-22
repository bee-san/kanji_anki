package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyExampleSelector
import dev.bee.kanjianki.core.StudyLayoutPolicy
import dev.bee.kanjianki.core.StudySessionFocusPolicy
import dev.bee.kanjianki.core.StudyTextCopy
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

    fun learningPanel(session: RecordsSchedulerModels.StudySession): View {
        return learningPanelView(this, session)
    }

    fun learningPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return learningPanelModel(this, session)
    }

    fun firstExample(row: RecordsImportModels.DashboardRow): RecordsImportModels.Example? {
        return StudyExampleSelector.firstExample(row)
    }

    override fun wordReadingExample(row: RecordsImportModels.DashboardRow): RecordsImportModels.Example? {
        return StudyExampleSelector.wordReadingExample(row)
    }

    fun exampleForSession(session: RecordsSchedulerModels.StudySession): RecordsImportModels.Example? {
        return StudyExampleSelector.exampleForSession(session)
    }

    override fun renderStudy() {
        val rows = store.activeDashboardRows()
        val now = System.currentTimeMillis()
        val ladder = studyLadderSettings()
        activeStudyPlan = if (rows.isEmpty()) null else studyPlanForMode(rows, store.studyItems(), now)
        if (renderPendingRepairOrDone(activeStudyPlan, now, ladder)) {
            return
        }
        if (rows.isEmpty()) {
            renderEmptyStudyQueue()
            return
        }
        val beforeSeed = store.studyItems()
        val plan = studyPlanForMode(rows, beforeSeed, now)
        val seeded = studyQueue(rows, now, true, plan)
        val seededPlan = studyPlanForMode(rows, seeded, now)
        activeStudyPlan = seededPlan
        if (renderPendingRepairOrDone(seededPlan, now, ladder)) {
            return
        }
        activeSession = BridgeScheduler().nextSession(
            seeded,
            rows,
            now,
            studyAheadMillis(),
            StudySessionFocusPolicy.allowedKanji(seededPlan, continueAllKanjiSession),
            settings(),
            studyLadderSettings()
        )
        activeSimilarWritingRepair = null
        val session = activeSession
        if (session == null) {
            renderNoStudySession(seededPlan)
            return
        }
        StudySessionActions.activateStudySession(
            session,
            now,
            store::saveStudyItem,
            ::registerStudyTaskShown,
            ::startActiveStudyTask
        )
        renderSession(session)
    }

    fun renderPendingRepairOrDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        now: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): Boolean {
        initializeSessionProgressTarget(plan)
        if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            for (repair in store.dueSimilarWritingRepairs(now)) {
                studySessionTracker.includePendingTask(similarRepairProgressKey(repair))
            }
            val repair = store.nextDueSimilarWritingRepair(now)
            if (repair != null) {
                val active = StudyRepairActions.activateSimilarWritingRepair(
                    repair,
                    now,
                    store::saveSimilarWritingRepair,
                )
                val activeRepair = active.repair
                activeSimilarWritingRepair = activeRepair
                val item = BridgeScheduler().newTargetedStudyItem(activeRepair.repairKanji, now, studyLadderSettings())
                val session = RecordsSchedulerModels.StudySession(
                    item.withToken(active.token),
                    null,
                    active.token,
                    MainActivityBase.TASK_REPAIR_WRITING,
                    true,
                    StudyTextCopy.similarRepairPrompt(activeRepair)
                )
                activeSession = session
                activeStudyPlan = plan
                registerStudyTaskShown(active.progressKey)
                startActiveStudyTask(
                    active.studyTaskKey,
                    activeRepair.repairKanji,
                    MainActivityBase.TASK_REPAIR_WRITING,
                    now,
                )
                writingSession.renderComposeWritingSession(session)
                return true
            }
        }
        if (studySessionTracker.atHardCap(continueAllKanjiSession)) {
            doneActions.renderStudyRunDone(plan)
            return true
        }
        return false
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

    fun startStudyMoreNewCards(requestedCount: Int): Boolean {
        return moreNewCards.startStudyMoreNewCards(requestedCount)
    }

    override fun startFocusedStudy() {
        clearStudyModeOverrides()
        resetStudyRunProgress()
        renderStudy()
    }

    override fun renderStudyForKanji(kanji: String?) {
        clearStudyModeOverrides()
        resetStudyRunProgress()
        base(MainActivityBase.NAV_STUDY)
        activeSimilarWritingRepair = null
        val rows = store.activeDashboardRows()
        val now = System.currentTimeMillis()
        activeStudyPlan = if (rows.isEmpty()) null else adaptivePlan(rows, store.studyItems(), now)
        val row = findRow(rows, kanji ?: "")
        if (row == null) {
            renderStudyForKanjiNotAvailable()
            return
        }
        val seeded = studyQueue(rows, now, true, activeStudyPlan)
        activeStudyPlan = adaptivePlan(rows, seeded, now)
        val session = BridgeScheduler().targetedSession(
            seeded,
            row,
            now,
            studyLadderSettings()
        )
        activeSession = session
        StudySessionActions.activateStudySession(
            session,
            now,
            store::saveStudyItem,
            ::registerStudyTaskShown,
            ::startActiveStudyTask
        )
        renderSession(session)
    }

    fun renderStudyForKanjiNotAvailable() {
        doneActions.renderStudyForKanjiNotAvailable()
    }

    fun renderSession(session: RecordsSchedulerModels.StudySession) {
        when (StudySessionRoute.destination(session)) {
            StudySessionRoute.Destination.WRITING -> writingSession.renderComposeWritingSession(session)
            StudySessionRoute.Destination.SIMILAR_KANJI -> renderSimilarKanjiSession(session)
            StudySessionRoute.Destination.MEANING_KANJI -> renderMeaningKanjiSession(session)
            StudySessionRoute.Destination.FLASHCARD -> renderComposeFlashcardSession(session)
        }
    }

    fun renderMeaningKanjiSession(session: RecordsSchedulerModels.StudySession) {
        choiceSessions.renderMeaningKanjiSession(session)
    }

    fun meaningKanjiChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        return choiceSessions.meaningKanjiChoiceCardForSession(session)
    }

    fun renderSimilarKanjiSession(session: RecordsSchedulerModels.StudySession) {
        choiceSessions.renderSimilarKanjiSession(session)
    }

    fun similarChoiceCardForSession(
        session: RecordsSchedulerModels.StudySession,
    ): RecordsImportModels.SimilarKanjiChoiceCard {
        return choiceSessions.similarChoiceCardForSession(session)
    }

    fun buildSimilarKanjiChoices(targetKanji: String): List<String> {
        return choiceSessions.buildSimilarKanjiChoices(targetKanji)
    }

    fun renderFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        flashcardUi.renderFlashcardSession(session)
    }

    fun renderComposeFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        flashcardUi.renderComposeFlashcardSession(session)
    }

    fun renderWritingSession(session: RecordsSchedulerModels.StudySession) {
        writingSession.renderComposeWritingSession(session)
    }

    fun recognitionHeroCard(session: RecordsSchedulerModels.StudySession): View {
        return flashcardUi.recognitionHeroCard(session)
    }

    fun heroKanjiPanel(session: RecordsSchedulerModels.StudySession): View {
        return flashcardUi.heroKanjiPanel(session)
    }

    fun randomFontVariantTypeface(): Typeface {
        return flashcardUi.randomFontVariantTypeface()
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

    fun flashcardAnswerPanel(session: RecordsSchedulerModels.StudySession): View {
        return flashcardUi.flashcardAnswerPanel(session)
    }

    fun flashcardAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return flashcardUi.flashcardAnswerPanelModel(session)
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

    fun buildStudyActionBar() {
        writingUi.buildStudyActionBar()
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
