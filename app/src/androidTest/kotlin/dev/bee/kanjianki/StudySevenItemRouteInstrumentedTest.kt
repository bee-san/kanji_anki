package dev.bee.kanjianki

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.testing.DeviceRisk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceRisk
class StudySevenItemRouteInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        pendingAnswerPreferences().edit().clear().commit()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.study_seven_route"),
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        pendingAnswerPreferences().edit().clear().commit()
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @Test
    fun exactSevenTargetRequiresFinalContinueBeforeVersionMatchedDone() {
        val sessions = (1..7).map(::session)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: StudySessionViewModel
            lateinit var visibleSnapshot: StudyRouteSnapshot
            scenario.onActivity { activity ->
                activity.cancelPendingHomeRouteLoads()
                activity.activeStudyPlan = plan()
                activity.studySessionTracker.setTargetCount(7)
                viewModel = activity.studySessionViewModel
                visibleSnapshot = mountAndRender(activity, sessions.first())
            }
            assertFrame(viewModel, visibleSnapshot, completed = 0, done = false)

            for (completed in 1..6) {
                scenario.onActivity { activity ->
                    applyCorrectAnswer(activity, sessions[completed - 1])
                    assertTrue(requireNotNull(activity.studyAnswerFeedbackState).tryContinue())
                    visibleSnapshot = mountAndRender(activity, sessions[completed])
                }
                assertFrame(viewModel, visibleSnapshot, completed = completed, done = false)
            }

            scenario.onActivity { activity ->
                applyCorrectAnswer(activity, sessions.last())
                persistConsumedReview(sessions.last())
                visibleSnapshot = renderActiveSession(activity)
                assertNull(activity.pendingStudyRecovery())
            }
            assertFrame(viewModel, visibleSnapshot, completed = 7, done = false)
            composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
                .assertIsDisplayed()
                .assertIsEnabled()

            composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
                .performClick()
            composeRule.waitUntil(timeoutMillis = 15_000L) {
                viewModel.acceptedRouteSnapshot().isComplete
            }
            scenario.onActivity { activity ->
                visibleSnapshot = activity.studySessionViewModel.acceptedRouteSnapshot()
                assertNull(activity.pendingStudyRecovery())
            }
            assertFrame(viewModel, visibleSnapshot, completed = 7, done = true)
            assertEquals(StudyRouteCompletionReason.HARD_CAP, visibleSnapshot.completionReason)
        }
    }

    private fun mountAndRender(
        activity: MainActivity,
        session: RecordsSchedulerModels.StudySession,
    ): StudyRouteSnapshot {
        activity.activeSession = session
        return renderActiveSession(activity)
    }

    private fun renderActiveSession(activity: MainActivity): StudyRouteSnapshot {
        activity.renderSession(requireNotNull(activity.activeSession))
        return activity.studySessionViewModel.acceptedRouteSnapshot()
    }

    private fun applyCorrectAnswer(
        activity: MainActivity,
        session: RecordsSchedulerModels.StudySession,
    ) {
        assertEquals(session.token, activity.activeSession?.token)
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, StudyRatings.GOOD))
        activity.studySessionTracker.markTaskCompleted(activity.sessionTaskKey(session))
        assertTrue(feedback.markApplied(session.token))
    }

    private fun persistConsumedReview(session: RecordsSchedulerModels.StudySession) {
        val item = requireNotNull(session.item)
        val request = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = item.kanji,
                token = session.token,
                rating = StudyRatings.GOOD,
                writingRequired = session.writingRequired,
                writingPassed = true,
                writingClean = true,
                manualOverride = false,
                hintsUsed = 0,
                taskType = session.taskType,
                answerSignature = item.answerSignature,
                prompt = session.prompt,
            ),
        )
        LocalStore(context).use { store ->
            store.saveReview(request, StudyRatings.GOOD, System.currentTimeMillis())
            assertTrue(
                store.hasMatchingConsumedReview(
                    session.token,
                    item.kanji,
                    session.taskType,
                    item.answerSignature,
                ),
            )
        }
    }

    private fun assertFrame(
        viewModel: StudySessionViewModel,
        visibleSnapshot: StudyRouteSnapshot,
        completed: Int,
        done: Boolean,
    ) {
        val acceptedSnapshot = viewModel.acceptedRouteSnapshot()
        assertEquals(completed, acceptedSnapshot.displayedCompletedCount)
        assertEquals(7, acceptedSnapshot.displayedTargetCount)
        assertEquals(done, acceptedSnapshot.isComplete)
        assertEquals(completed, visibleSnapshot.displayedCompletedCount)
        assertEquals(7, visibleSnapshot.displayedTargetCount)
        composeRule.onNodeWithTag(StudyUiTestTags.PROGRESS)
            .assertIsDisplayed()
            .assertTextEquals("$completed / 7")
            .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, visibleSnapshot.version.value))
        if (done) {
            composeRule.onNodeWithTag(StudyUiTestTags.DONE)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, visibleSnapshot.version.value))
        } else {
            composeRule.onNodeWithTag(StudyUiTestTags.DONE).assertDoesNotExist()
            assertFalse(acceptedSnapshot.isComplete)
        }
    }

    private fun plan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(
            40,
            7,
            7,
            (1..7).map { "字$it" },
            0,
            false,
            "7 deterministic items",
        )

    private fun session(index: Int): RecordsSchedulerModels.StudySession {
        val row = row("字$index", "meaning $index", "ジ$index")
        val token = "seven-route-token-$index"
        val item = RecordsStudyModels.StudyItem(
            row.kanji,
            "review",
            0L,
            1.0,
            5.0,
            1,
            0,
            0,
            1,
            0,
            0,
            0,
            false,
            "",
            0L,
            0,
            "sig-$token",
            token,
            0L,
        )
        return RecordsSchedulerModels.StudySession(
            item,
            row,
            token,
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            row.primaryMeaning,
        )
    }

    private fun row(
        kanji: String,
        meaning: String,
        reading: String,
    ): RecordsImportModels.DashboardRow = RecordsImportModels.DashboardRow(
        kanji,
        1_000,
        meaning,
        reading,
        kanji,
        10,
        "reason",
        "reason text",
        1,
        0,
        0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun pendingAnswerPreferences() =
        context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
}
