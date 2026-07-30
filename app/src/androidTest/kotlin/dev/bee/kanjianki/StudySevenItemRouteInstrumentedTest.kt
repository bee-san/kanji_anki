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
import dev.bee.kanjianki.core.StudyReviewButtonCopy
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
        KaniTestDatabase.delete(context)
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
        KaniTestDatabase.delete(context)
    }

    @Test
    fun exactSevenTargetRequiresFinalContinueBeforeVersionMatchedDone() {
        val sessions = (1..7).map(::session)
        LocalStore(context).use { store -> sessions.forEach { store.saveStudyItem(requireNotNull(it.item)) } }
        ActivityScenario.launch(SevenItemRouteActivity::class.java).use { scenario ->
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

            for (completed in 1..7) {
                val displayedRoute = visibleSnapshot
                lateinit var answeredToken: String
                scenario.onActivity { activity ->
                    answeredToken = requireNotNull(activity.activeSession).token
                }
                composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.revealLabel()))
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .performClick()
                composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel()))
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 15_000L) {
                    activityState(scenario) { activity ->
                        activity.activeSession?.token == answeredToken &&
                            activity.studyAnswerFeedbackState?.snapshot()?.phase == StudyAnswerFeedbackPhase.APPLIED
                    }
                }
                scenario.onActivity { activity ->
                    visibleSnapshot = activity.studySessionViewModel.acceptedRouteSnapshot()
                    assertEquals(answeredToken, activity.activeSession?.token)
                    assertEquals(
                        StudyAnswerFeedbackPhase.APPLIED,
                        activity.pendingStudyRecovery()?.snapshot?.feedback?.phase,
                    )
                }
                assertFrame(
                    viewModel,
                    visibleSnapshot,
                    completed = completed,
                    done = false,
                    displayedCompleted = completed - 1,
                    displayedVersion = displayedRoute.version.value,
                )

                composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 15_000L) {
                    if (completed == 7) {
                        viewModel.acceptedRouteSnapshot().isComplete
                    } else {
                        activityState(scenario) { activity ->
                            activity.pendingStudyRecovery()?.snapshot?.feedback?.phase ==
                                StudyAnswerFeedbackPhase.CONTINUED
                        }
                    }
                }
                if (completed < 7) {
                    scenario.onActivity { activity ->
                        visibleSnapshot = mountAndRender(
                            activity,
                            sessions[completed],
                            advancingRecovery = requireNotNull(activity.pendingStudyRecovery()),
                        )
                        assertNull(activity.pendingStudyRecovery())
                    }
                    assertFrame(viewModel, visibleSnapshot, completed = completed, done = false)
                }
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
        advancingRecovery: StoredPendingStudyRecovery? = null,
    ): StudyRouteSnapshot {
        if (advancingRecovery == null) {
            activity.activeSession = session
        } else {
            assertTrue(
                activity.acceptNewActiveStudySession(
                    session,
                    StudyPromptSource.REASON_TEXT,
                    System.currentTimeMillis(),
                    advancingRecovery = advancingRecovery,
                ),
            )
        }
        val taskKey = activity.sessionTaskKey(session)
        activity.registerStudyTaskShown(taskKey)
        activity.startActiveStudyTask(taskKey, session.item?.kanji, session.taskType, System.currentTimeMillis())
        return renderActiveSession(activity)
    }

    private fun renderActiveSession(activity: MainActivity): StudyRouteSnapshot {
        activity.renderSession(requireNotNull(activity.activeSession))
        return activity.studySessionViewModel.acceptedRouteSnapshot()
    }

    private fun activityState(
        scenario: ActivityScenario<SevenItemRouteActivity>,
        predicate: (MainActivity) -> Boolean,
    ): Boolean {
        var matches = false
        scenario.onActivity { activity -> matches = predicate(activity) }
        return matches
    }

    private fun assertFrame(
        viewModel: StudySessionViewModel,
        visibleSnapshot: StudyRouteSnapshot,
        completed: Int,
        done: Boolean,
        displayedCompleted: Int = completed,
        displayedVersion: Long = visibleSnapshot.version.value,
    ) {
        val acceptedSnapshot = viewModel.acceptedRouteSnapshot()
        assertEquals(completed, acceptedSnapshot.displayedCompletedCount)
        assertEquals(7, acceptedSnapshot.displayedTargetCount)
        assertEquals(done, acceptedSnapshot.isComplete)
        assertEquals(completed, visibleSnapshot.displayedCompletedCount)
        assertEquals(7, visibleSnapshot.displayedTargetCount)
        composeRule.onNodeWithTag(StudyUiTestTags.PROGRESS)
            .assertIsDisplayed()
            .assertTextEquals("$displayedCompleted / 7")
            .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, displayedVersion))
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
