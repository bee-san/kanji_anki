package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.theme.KaniThemeChoice
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Explicit transition-table coverage for the study-session completion contract.
 *
 * The previous randomized property model only compared `StudySessionTracker`
 * against a copied oracle, so it never reached the real queue/done seam in
 * `MainActivityStudyQueueCoordinator`. This replacement keeps the preserved
 * seeds as provenance labels, but drives named scenarios instead of random
 * walks: visible 5/7 -> 6/7 -> 7/7, target reconciliation, learn-ahead horizon
 * mismatch, and the feedback restore/continue lifecycle.
 *
 * The private `pendingRepairOrDoneRender(...)` branch is exercised via
 * reflection on purpose so the test hits the real production decision point.
 * Recovery-store generation/version ordering remains owned by
 * `StudySessionRecoveryStoreTest` and `MainActivityStudyRouteInitializationTest`
 * rather than being duplicated here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudySessionTransitionModelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var activity: MainActivity
    private lateinit var coordinator: MainActivityStudyQueueCoordinator
    private var installedStudyRoute: (@Composable () -> Unit)? = null
    private var studyRouteMounted = false

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val intent = Intent(context, MainActivity::class.java)
        activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()
        activity.cancelPendingHomeRouteLoads()
        replaceLazyDelegate(
            activity,
            "statsPrecomputeScheduler",
            StatsPrecomputeScheduler(
                background = Executor { },
                isFresh = { true },
                refresh = { },
            ),
        )
        replaceLazyDelegate(
            activity,
            "asyncHomeRouteLoader",
            AsyncHomeRouteLoader(
                background = Executor { },
                postToMain = { _: Runnable -> },
            ),
        )
        replaceLazyDelegate(
            activity,
            "shellHost",
            MainActivityShellHost(activity) { content ->
                installedStudyRoute = content
            },
        )
        activity.screenshotThemeChoiceOverride = KaniThemeChoice.GIRLYPOP
        activity.store = LocalStore(context)
        activity.studySessionTracker.resetProgress()
        activity.continueAllKanjiSession = false
        activity.recoveredStudyRunNeedsTargetReconciliation = false
        activity.activeSession = dummyStudySession()
        coordinator = MainActivityStudyQueueCoordinator(activity)
        studyRouteMounted = false
        installedStudyRoute = null
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
    }

    @Test
    fun visibleFiveSixSevenProgressOnlyThenDone() {
        val visibleFive = transitionSnapshot(
            seed = SESSION_SEEDS[0],
            completed = 5,
            target = 7,
            queueCount = 0,
            generation = 1,
            feedbackPhase = StudyAnswerFeedbackPhase.UNANSWERED,
            isComplete = false,
        )
        val visibleSix = visibleFive.copy(seed = SESSION_SEEDS[1], completed = 6)
        val visibleSeven = visibleFive.copy(
            seed = SESSION_SEEDS[3],
            completed = 7,
            generation = 1,
        )
        val doneSeven = visibleSeven.copy(generation = 2, isComplete = true)

        activity.studySessionTracker.resetProgress()
        activity.studySessionTracker.setTargetCount(7)

        repeat(5) { index ->
            activity.studySessionTracker.markTaskCompleted("progress-$index")
        }
        renderActiveStudyRoute()
        mountStudyRouteIfNeeded()
        assertRouteSnapshot(visibleFive, activeVisible = true, doneVisible = false)
        assertNull(
            "${visibleFive.label()} should not render done before the coordinator reaches the hard-cap branch",
            pendingRepairOrDoneRender(),
        )

        activity.studySessionTracker.markTaskCompleted("progress-5")
        renderActiveStudyRoute()
        composeRule.waitForIdle()
        assertRouteSnapshot(visibleSix, activeVisible = true, doneVisible = false)
        assertNull(
            "${visibleSix.label()} should still render the active route",
            pendingRepairOrDoneRender(),
        )

        activity.studySessionTracker.markTaskCompleted("progress-6")
        renderActiveStudyRoute()
        composeRule.waitForIdle()
        assertRouteSnapshot(visibleSeven, activeVisible = true, doneVisible = false)

        val doneRender = pendingRepairOrDoneRender()
        assertNotNull("${visibleSeven.label()} should now render the done branch", doneRender)
        doneRender!!.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertRouteSnapshot(doneSeven, activeVisible = false, doneVisible = true)
    }

    @Test
    fun reconciledTargetBehindCompletedRaisesVisibleNBeforeDone() {
        val snapshot = transitionSnapshot(
            seed = SESSION_SEEDS[4],
            completed = 7,
            target = 7,
            queueCount = 0,
            generation = 1,
            feedbackPhase = StudyAnswerFeedbackPhase.UNANSWERED,
            isComplete = false,
        )
        val reconciledTarget = recoveredStudyRunTarget(currentTarget = 5, completed = 7, selectableRemaining = 0)
        assertEquals("${snapshot.label()} should reconcile stale target before rendering", 7, reconciledTarget)

        activity.studySessionTracker.resetProgress()
        activity.studySessionTracker.setTargetCount(reconciledTarget)
        repeat(7) { index ->
            activity.studySessionTracker.markTaskCompleted("reconciled-$index")
        }
        renderActiveStudyRoute()
        mountStudyRouteIfNeeded()
        assertRouteSnapshot(snapshot, activeVisible = true, doneVisible = false)

        val doneRender = pendingRepairOrDoneRender()
        assertNotNull("${snapshot.label()} should now render the done branch", doneRender)
        doneRender!!.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertRouteSnapshot(snapshot.copy(generation = 2, isComplete = true), activeVisible = false, doneVisible = true)
    }

    @Test
    fun learnAheadHorizonMismatchSeed20260711BlocksDoneEvenAtHardCap() {
        val snapshot = transitionSnapshot(
            seed = SESSION_SEEDS[2],
            completed = 7,
            target = 7,
            queueCount = 2,
            generation = 1,
            feedbackPhase = StudyAnswerFeedbackPhase.UNANSWERED,
            isComplete = false,
        )
        val now = 2_000L
        val items = listOf(
            learningRepeatItem("裂", now + 60_000L),
            learningRepeatItem("謎", now + 120_000L),
        )

        activity.studySessionTracker.resetProgress()
        activity.studySessionTracker.setTargetCount(7)
        repeat(7) { index ->
            activity.studySessionTracker.markTaskCompleted("learn-ahead-$index")
        }
        items.forEach { item ->
            activity.studySessionTracker.markPlannedSessionTaskCompleted(item.rung.wireName(), item.kanji)
        }

        assertEquals(
            "${snapshot.label()} should surface the same-session repeats in due order",
            listOf("kanji_meaning:裂", "kanji_meaning:謎"),
            activity.studySessionTracker.dueCompletedLearningRepeatTaskKeys(
                items,
                now + StudyLadderRules.LEARN_AHEAD_MILLIS,
            ),
        )

        renderActiveStudyRoute()
        mountStudyRouteIfNeeded()
        assertRouteSnapshot(snapshot, activeVisible = true, doneVisible = false)

        val doneRender = pendingRepairOrDoneRender(now = now, items = items)
        assertNull(
            "${snapshot.label()} should keep the run alive because the learning repeats are still due within the learn-ahead horizon",
            doneRender,
        )
    }

    @Test
    fun feedbackPhaseRestoreAndContinueStayExplicitSeed20260712() {
        val seed = FEEDBACK_SEEDS[2]
        val token = "feedback-$seed"
        val initial = transitionSnapshot(
            seed = seed,
            completed = 0,
            target = 0,
            queueCount = 0,
            generation = 1,
            feedbackPhase = StudyAnswerFeedbackPhase.UNANSWERED,
            isComplete = false,
        )
        val submitting = initial.copy(feedbackPhase = StudyAnswerFeedbackPhase.SUBMITTING)
        val applied = submitting.copy(feedbackPhase = StudyAnswerFeedbackPhase.APPLIED)
        val continued = applied.copy(generation = 2, feedbackPhase = StudyAnswerFeedbackPhase.CONTINUED)

        val feedback = StudyAnswerFeedbackState(token)
        assertEquals(initial.feedbackPhase, feedback.snapshot().phase)
        assertFalse(feedback.feedbackVisible)
        assertFalse(feedback.continueEnabled)

        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, selectedAnswer = "答", autoContinue = false))
        assertEquals(submitting.feedbackPhase, feedback.snapshot().phase)
        assertTrue(feedback.feedbackVisible)
        assertFalse(feedback.continueEnabled)
        assertEquals("答", feedback.selectedAnswer)
        assertEquals(StudyAnswerOutcome.CORRECT, feedback.outcome)

        assertTrue(feedback.markApplied(token))
        assertEquals(applied.feedbackPhase, feedback.snapshot().phase)
        assertTrue(feedback.feedbackVisible)
        assertTrue(feedback.continueEnabled)
        assertFalse(
            "${applied.label()} should reject a late retry callback after apply",
            feedback.resetForRetry(token),
        )

        val restored = StudyAnswerFeedbackState.restore(feedback.snapshot())
        assertEquals(applied.feedbackPhase, restored.snapshot().phase)
        assertEquals("答", restored.selectedAnswer)
        assertEquals(StudyAnswerOutcome.CORRECT, restored.outcome)
        assertTrue(restored.feedbackVisible)
        assertTrue(restored.continueEnabled)

        assertTrue(restored.tryContinue())
        assertEquals(continued.feedbackPhase, restored.snapshot().phase)
        assertTrue(restored.feedbackVisible)
        assertFalse(restored.continueEnabled)
        assertTrue(restored.rollbackContinue())
        assertEquals(applied.feedbackPhase, restored.snapshot().phase)
        assertTrue(restored.feedbackVisible)
        assertTrue(restored.continueEnabled)
    }

    private fun renderActiveStudyRoute() {
        activity.renderComposeStudyRoute(studySessionActive = true) {
            Text(ACTIVE_ROUTE_TEXT)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun mountStudyRouteIfNeeded() {
        if (studyRouteMounted) {
            return
        }
        val route = requireNotNull(installedStudyRoute) { "Study route was not installed before mounting the compose test rule" }
        composeRule.setContent {
            route()
        }
        composeRule.waitForIdle()
        studyRouteMounted = true
    }

    private fun assertRouteSnapshot(
        snapshot: TransitionSnapshot,
        activeVisible: Boolean,
        doneVisible: Boolean,
    ) {
        assertEquals("${snapshot.label()} completedCount", snapshot.completed, activity.studySessionTracker.completedCount())
        assertEquals("${snapshot.label()} targetCount", snapshot.target, activity.studySessionTracker.targetCount())
        assertEquals("${snapshot.label()} done visibility", snapshot.isComplete, doneVisible)

        composeRule.onNodeWithText("${snapshot.completed} / ${snapshot.target}").assertIsDisplayed()
        if (activeVisible) {
            composeRule.onNodeWithText(ACTIVE_ROUTE_TEXT).assertIsDisplayed()
        } else {
            composeRule.onNodeWithText(ACTIVE_ROUTE_TEXT).assertDoesNotExist()
        }
        if (doneVisible) {
            composeRule.onNodeWithText(StudyTextCopy.studyDoneTitle()).assertIsDisplayed()
        } else {
            composeRule.onNodeWithText(StudyTextCopy.studyDoneTitle()).assertDoesNotExist()
        }
    }

    private fun pendingRepairOrDoneRender(
        now: Long = 0L,
        items: List<RecordsStudyModels.StudyItem> = emptyList(),
        dueRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair> = emptyList(),
    ): (() -> Unit)? {
        val method = MainActivityStudyQueueCoordinator::class.java.declaredMethods.single {
            it.name == "pendingRepairOrDoneRender" && it.parameterTypes.size == 6
        }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            coordinator,
            null,
            now,
            RecordsBase.StudyLadderSettings.defaults(),
            items,
            dueRepairs,
            null,
        ) as (() -> Unit)?
    }

    private fun learningRepeatItem(kanji: String, dueAt: Long): RecordsStudyModels.StudyItem {
        return baseItem(kanji)
            .copyBuilder()
            .state("learning")
            .dueAtMillis(dueAt)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
    }

    private fun baseItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }

    private fun dummyStudySession(kanji: String = "仮", token: String = "study-token"): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            baseItem(kanji),
            null,
            token,
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            "prompt",
        )
    }

    private fun transitionSnapshot(
        seed: Int,
        completed: Int,
        target: Int,
        queueCount: Int,
        generation: Int,
        feedbackPhase: StudyAnswerFeedbackPhase,
        isComplete: Boolean,
    ): TransitionSnapshot {
        return TransitionSnapshot(seed, completed, target, queueCount, generation, feedbackPhase, isComplete)
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val fieldName = propertyName + "$" + "delegate"
        var type: Class<*>? = activity::class.java
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(activity, lazyOf(value))
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Could not find lazy delegate $fieldName on ${activity::class.java.name} or any superclass")
    }

    private fun TransitionSnapshot.label(): String {
        return buildString {
            append("seed=0x")
            append(seed.toString(16).uppercase())
            append(" completed=")
            append(completed)
            append('/')
            append(target)
            append(" queue=")
            append(queueCount)
            append(" generation=")
            append(generation)
            append(" phase=")
            append(feedbackPhase)
            append(" done=")
            append(isComplete)
        }
    }

    private data class TransitionSnapshot(
        val seed: Int,
        val completed: Int,
        val target: Int,
        val queueCount: Int,
        val generation: Int,
        val feedbackPhase: StudyAnswerFeedbackPhase,
        val isComplete: Boolean,
    )

    companion object {
        private const val ACTIVE_ROUTE_TEXT = "Active study content"
        private val SESSION_SEEDS = listOf(0xC0FFEE, 0xBEE, 0x20260711, 0x5EED, 0xD00D)
        private val FEEDBACK_SEEDS = listOf(0xF00D, 0xBADA55, 0x20260712, 0x7EED, 0xD1CE)
    }
}
