package dev.bee.kanjianki

import android.content.ContentValues
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreHistory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.StringReader
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyRouteInitializationTest {
    @After
    fun clearProcessRecoveryOverrides() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE).edit().clear().commit()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
    }

    @Test
    fun flashcardRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = flashcardSession()
        activity.activeSession = session

        activity.renderComposeFlashcardSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.flashcardRevealState)
        assertNotNull(activity.flashcardActionBarState)
        assertEquals(session.token, activity.studyAnswerFeedbackState?.sessionToken)
        assertSame(session, activity.studySessionUiState.value.currentSession)
        assertEquals(StudySessionPhase.ACTIVE, activity.studySessionUiState.value.phase)
    }

    @Test
    fun writingRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = writingSession()
        activity.activeSession = session

        activity.renderComposeWritingSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.writingAnswerPanelState)
        assertEquals(session.token, activity.studyAnswerFeedbackState?.sessionToken)
    }

    @Test
    fun answeredFlashcardRouteRestoresAppliedFeedbackAndTypedAnswer() {
        val activity = createActivity()
        val baseSession = flashcardSession()
        val session = RecordsSchedulerModels.StudySession(
            baseSession.item,
            baseSession.row,
            baseSession.token,
            StudyTaskTypes.TYPING_MEANING,
            baseSession.writingRequired,
            baseSession.prompt,
        )
        activity.activeSession = session
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, selectedAnswer = "strong"))
        assertTrue(feedback.markApplied(session.token))

        activity.composeRoute(selected = MainActivityBase.NAV_HOME_ROUTE, content = {})
        activity.renderComposeFlashcardSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(feedback, activity.studyAnswerFeedbackState)
        assertTrue(activity.flashcardRevealState?.isRevealed == true)
        assertEquals("strong", activity.typingAnswerState?.text?.toString())
        assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
    }

    @Test
    fun answeredWritingRouteRestoresAppliedFeedbackAfterActivityStateRecreation() {
        val activity = createActivity()
        val session = writingSession()
        val snapshot = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = session.token,
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = StudyRatings.GOOD,
            ),
            kanji = session.item?.kanji.orEmpty(),
            taskType = session.taskType,
            writingRequired = session.writingRequired,
            prompt = session.prompt,
        )
        activity.activeSession = session
        activity.restorePendingStudyAnswer(snapshot)

        activity.renderComposeWritingSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.writingAnswerPanelState?.visible == true)
        assertEquals(StudyAnswerOutcome.CORRECT, activity.studyAnswerFeedbackState?.outcome)
        assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
    }

    @Test
    fun processRestartRouteRestoresPendingAnsweredFlashcardBeforeSelectingNextItem() {
        val activity = createActivity()
        val baseSession = flashcardSession()
        val session = RecordsSchedulerModels.StudySession(
            baseSession.item,
            baseSession.row,
            "process-restart-token",
            StudyTaskTypes.TYPING_MEANING,
            baseSession.writingRequired,
            baseSession.prompt,
        )
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        activity.store.saveKanjiMnemonicNote(session.item!!.kanji, "old memory", 1_000L)
        activity.store.saveStudyItem(session.item!!.copyBuilder().activeToken("").build())
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = session.token,
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = "strong",
                ),
                kanji = session.item!!.kanji,
                taskType = session.taskType,
                writingRequired = session.writingRequired,
                prompt = session.prompt,
            ),
        )
        val encodedSnapshot = preferences.getString("snapshot", "").orEmpty()
        assertFalse(encodedSnapshot.contains("old memory"))
        activity.store.saveKanjiMnemonicNote(
            session.item!!.kanji,
            "current memory\nfrom local storage",
            2_000L,
        )
        val ioTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val restoredActivity = controller.get()
        replaceField(restoredActivity, "io", ioTasks)

        try {
            controller.create().start().resume()
            ioTasks.runAll()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(session.token, restoredActivity.activeSession?.token)
            assertEquals(session.token, restoredActivity.studyAnswerFeedbackState?.sessionToken)
            assertTrue(restoredActivity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(restoredActivity.flashcardRevealState?.isRevealed == true)
            assertEquals("strong", restoredActivity.typingAnswerState?.text?.toString())
        } finally {
            activity.store.saveKanjiMnemonicNote(session.item!!.kanji, "", 3_000L)
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun processRestartRestoresPendingAnsweredRepairWithoutCanonicalStudyItem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = "repair-process-restart-token",
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = StudyRatings.AGAIN,
                ),
                kanji = "修",
                taskType = MainActivityBase.TASK_REPAIR_WRITING,
                writingRequired = true,
                prompt = "Write the similar kanji 修",
            ),
        )
        val ioTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val restoredActivity = controller.get()
        replaceField(restoredActivity, "io", ioTasks)

        try {
            controller.create().start().resume()
            ioTasks.runAll()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("repair-process-restart-token", restoredActivity.activeSession?.token)
            assertEquals(MainActivityBase.TASK_REPAIR_WRITING, restoredActivity.activeSession?.taskType)
            assertTrue(restoredActivity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(restoredActivity.writingAnswerPanelState?.visible == true)
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun processRestartPromotesCommittedRepairSubmittingEnvelopeToAppliedFeedback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val token = "repair-committed-crash-token"

        LocalStore(context).use { store ->
            val repair = persistRepair(store, token)
            val item = RecordsStudyModels.StudyItem(
                "修", "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L,
            ).copyBuilder()
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .activeToken(token)
                .build()
            val session = RecordsSchedulerModels.StudySession(
                item,
                null,
                token,
                MainActivityBase.TASK_REPAIR_WRITING,
                true,
                "Write the similar kanji 修",
            )
            val seedActivity = Robolectric.buildActivity(MainActivity::class.java).get().apply {
                this.store = store
                activeSession = session
                activeSimilarWritingRepair = repair
            }
            val seedIo = QueueingExecutorService()
            replaceField(seedActivity, "io", seedIo)

            assertTrue(seedActivity.submitSimilarWritingRepair(StudyRatings.AGAIN))
            assertEquals(
                StudyAnswerFeedbackPhase.SUBMITTING,
                seedActivity.pendingStudyAnswerSnapshot()?.feedback?.phase,
            )
            assertTrue(store.finishSimilarWritingRepair(repair.id, token, false, 2_000L))
        }

        val ioTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val restoredActivity = controller.get()
        replaceField(restoredActivity, "io", ioTasks)

        try {
            controller.create().start().resume()
            ioTasks.runAll()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(token, restoredActivity.activeSession?.token)
            assertEquals(MainActivityBase.TASK_REPAIR_WRITING, restoredActivity.activeSession?.taskType)
            assertEquals(
                StudyAnswerFeedbackPhase.APPLIED,
                restoredActivity.studyAnswerFeedbackState?.snapshot()?.phase,
            )
            assertEquals(StudyAnswerOutcome.INCORRECT, restoredActivity.studyAnswerFeedbackState?.outcome)
            assertTrue(restoredActivity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(restoredActivity.writingAnswerPanelState?.visible == true)
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun processRestartRestoresExactTypedCardAndDraftWithoutRevealingAnswer() {
        val restored = restoreActiveCardAfterProcessRestart(
            kanji = "裂",
            taskType = StudyTaskTypes.TYPE_MEANING,
            rung = RecordsBase.LadderRung.TYPE_MEANING,
            typedDraft = "divide",
            revealed = false,
        )

        try {
            assertEquals("active-process-token-裂", restored.activity.activeSession?.token)
            assertEquals("divide", restored.activity.typingAnswerState?.text)
            assertFalse(requireNotNull(restored.activity.flashcardRevealState).isRevealed)
            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, restored.activity.studyAnswerFeedbackState?.snapshot()?.phase)
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRestoresPlainRevealAsUngradedFailPassCard() {
        val restored = restoreActiveCardAfterProcessRestart(
            kanji = "認",
            taskType = StudyTaskTypes.KANJI_MEANING,
            rung = RecordsBase.LadderRung.KANJI_MEANING,
            typedDraft = "",
            revealed = true,
        )

        try {
            assertEquals("active-process-token-認", restored.activity.activeSession?.token)
            assertTrue(restored.activity.flashcardRevealState?.isRevealed == true)
            assertTrue(restored.activity.flashcardActionBarState?.revealed == true)
            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, restored.activity.studyAnswerFeedbackState?.snapshot()?.phase)
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartPromotesConsumedSubmittingEnvelopeToAppliedFeedback() {
        val restored = restoreSubmittingCrashWindow(consumed = true)

        try {
            assertEquals(StudyAnswerFeedbackPhase.APPLIED, restored.activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertTrue(restored.activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals("divide", restored.activity.typingAnswerState?.text)
            assertTrue(restored.activity.flashcardRevealState?.isRevealed == true)
            assertTrue(restored.activity.continueAfterStudyAnswer())
            assertEquals(
                StudyAnswerFeedbackPhase.CONTINUED,
                restored.activity.pendingStudyRecovery()?.snapshot?.feedback?.phase,
            )
            restored.runQueuedIo()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(restored.activity.pendingStudyRecovery())
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartAfterContinuePublishesTheNextCurrentCard() {
        val restored = restoreContinuedHandoffAfterProcessRestart(hasNextCard = true)

        try {
            val active = requireNotNull(restored.activity.activeSession)
            assertEquals("次", active.item?.kanji)
            assertNotEquals("continued-process-token", active.token)
            assertEquals(active.token, restored.activity.activeStudyRecovery()?.snapshot?.sessionToken)
            assertNull(restored.activity.pendingStudyRecovery())
            assertEquals(
                StudyAnswerFeedbackPhase.UNANSWERED,
                restored.activity.studyAnswerFeedbackState?.snapshot()?.phase,
            )
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartAfterContinueClearsHandoffWhenNoWorkRemains() {
        val restored = restoreContinuedHandoffAfterProcessRestart(hasNextCard = false)

        try {
            assertNull(restored.activity.activeSession)
            assertNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
            assertFalse(restored.preferences.contains("snapshot"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRejectsContinuedHandoffWithoutExactReviewEvidence() {
        val restored = restoreContinuedHandoffAfterProcessRestart(
            hasNextCard = true,
            matchingReviewEvidence = false,
        )

        try {
            assertNull(restored.activity.activeSession)
            assertNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
            assertFalse(restored.preferences.contains("snapshot"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRestoresUnconsumedSubmittingFallbackAsUngradedDraft() {
        val restored = restoreSubmittingCrashWindow(consumed = false)

        try {
            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, restored.activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertEquals("divide", restored.activity.typingAnswerState?.text)
            assertFalse(requireNotNull(restored.activity.flashcardRevealState).isRevealed)
            assertNotNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
        } finally {
            restored.close()
        }
    }

    @Test
    fun recoveryOnlyReconcilesRetainedConsumedSubmittingThroughDurableClaim() {
        val restored = restoreRetainedSubmittingCrashWindow(consumed = true)

        try {
            assertEquals(
                StudyAnswerFeedbackPhase.APPLIED,
                restored.activity.studyAnswerFeedbackState?.snapshot()?.phase,
            )
            assertTrue(restored.activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(
                StudyAnswerFeedbackPhase.APPLIED,
                restored.activity.pendingStudyRecovery()?.snapshot?.feedback?.phase,
            )
            assertTrue(restored.activity.shouldRestoreStudyRouteAfterRecreation())
        } finally {
            restored.close()
        }
    }

    @Test
    fun recoveryOnlyRestoresRetainedUnconsumedSubmittingFallbackAsAnswerable() {
        val restored = restoreRetainedSubmittingCrashWindow(consumed = false)

        try {
            assertEquals(
                StudyAnswerFeedbackPhase.UNANSWERED,
                restored.activity.studyAnswerFeedbackState?.snapshot()?.phase,
            )
            assertNotNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
            assertTrue(restored.activity.shouldRestoreStudyRouteAfterRecreation())
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRestoresDigestBoundSimilarChoiceSubmittingFallback() {
        val restored = restoreSubmittingSimilarChoiceAfterProcessRestart()

        try {
            assertEquals("similar-process-token", restored.activity.activeSession?.token)
            assertEquals(StudyTaskTypes.SIMILAR_KANJI, restored.activity.activeSession?.taskType)
            assertEquals(
                StudyAnswerFeedbackPhase.UNANSWERED,
                restored.activity.studyAnswerFeedbackState?.snapshot()?.phase,
            )
            assertNotNull(restored.activity.activeStudyRecovery()?.snapshot?.similarChoiceSignatureDigest)
            assertNull(restored.activity.pendingStudyRecovery())
            val raw = restored.preferences.getString("snapshot", "").orEmpty()
            assertFalse(raw.contains("恨"))
            assertFalse(raw.contains("恒"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRejectsSimilarFallbackThatIsNoLongerDue() {
        val restored = restoreSubmittingSimilarChoiceAfterProcessRestart(invalidateChoice = true)

        try {
            assertNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
            assertFalse(restored.preferences.contains("snapshot"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun processRestartRejectsSimilarFallbackWhoseDueCandidateSetChanged() {
        val restored = restoreSubmittingSimilarChoiceAfterProcessRestart(replaceChoiceSet = true)

        try {
            assertNull(restored.activity.activeStudyRecovery())
            assertNull(restored.activity.pendingStudyRecovery())
            assertFalse(restored.preferences.contains("snapshot"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun acceptedTargetedChoiceSupersedesDormantPendingAnswer() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val prior = flashcardSession()
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = "superseded-token",
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.CORRECT,
                    selectedAnswer = StudyRatings.GOOD,
                ),
                kanji = prior.item?.kanji.orEmpty(),
                taskType = prior.taskType,
                writingRequired = false,
                prompt = prior.prompt,
            ),
        )
        val choice = RecordsSchedulerModels.StudySession(
            prior.item,
            prior.row,
            "choice-token",
            StudyTaskTypes.MEANING_KANJI,
            false,
            prior.prompt,
        )

        activity.acceptNewActiveStudySession(
            choice,
            StudyPromptSource.PRIMARY_MEANING,
            latestSuccessfulSyncAtMillis = 0L,
            supersededRecoveryToken = "superseded-token",
        )

        assertNull(activity.pendingStudyAnswerSnapshot())
        assertNull(activity.activeStudyRecovery())
        preferences.edit().clear().commit()
    }

    @Test
    fun staleActivityContinueCannotDeleteNewerSameTokenHandoff() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val item = studyItem("継", "shared-token").copyBuilder()
            .answerSignature("継|継続|けいぞく|continuation")
            .schedulerRevision(4L)
            .build()
        val session = RecordsSchedulerModels.StudySession(
            item,
            null,
            "shared-token",
            StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "prompt",
        )
        val snapshot = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = session.token,
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = StudyRatings.GOOD,
            ),
            kanji = item.kanji,
            taskType = session.taskType,
            writingRequired = false,
            prompt = session.prompt,
            answerSignature = item.answerSignature,
            schedulerRevision = item.schedulerRevision,
        )
        val recoveryStore = StudySessionRecoveryStore(preferences)
        val applied = requireNotNull(recoveryStore.replaceWithPending(snapshot))
        val newerHandoff = requireNotNull(recoveryStore.continuePending(applied))
        activity.activeSession = session
        activity.restorePendingStudyAnswer(snapshot)
        val mountedFeedback = requireNotNull(activity.studyAnswerFeedbackState)

        assertFalse(activity.continueAfterStudyAnswer())

        assertSame(mountedFeedback, activity.studyAnswerFeedbackState)
        assertTrue(mountedFeedback.continueEnabled)
        assertEquals(newerHandoff, recoveryStore.readPending())
        preferences.edit().clear().commit()
    }

    @Test
    fun continuedFeedbackRejectsOldCardCallbacksDuringRouteHandoff() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val session = flashcardSession()
        assertTrue(
            activity.acceptNewActiveStudySession(
                session,
                StudyPromptSource.REASON_TEXT,
                latestSuccessfulSyncAtMillis = 0L,
            ),
        )
        val recovery = requireNotNull(activity.activeStudyRecovery())
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, StudyRatings.GOOD))
        assertTrue(feedback.markApplied(session.token))
        assertTrue(feedback.tryContinue())

        assertFalse(activity.matchesMountedStudyRoute(session.token, recovery))
        preferences.edit().clear().commit()
    }

    @Test
    fun acceptedSimilarChoicePublishesOnlyWithPreparedChoiceIdentity() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val base = flashcardSession()
        val similar = RecordsSchedulerModels.StudySession(
            base.item,
            base.row,
            base.token,
            StudyTaskTypes.SIMILAR_KANJI,
            writingRequired = false,
            prompt = base.prompt,
        )
        val choiceDigest = similarKanjiChoiceRecoveryDigest(listOf("弱", "若"))

        activity.acceptNewActiveStudySession(
            similar,
            StudyPromptSource.REASON_TEXT,
            latestSuccessfulSyncAtMillis = 0L,
            similarChoiceSignatureDigest = choiceDigest,
        )

        assertEquals(choiceDigest, activity.activeStudyRecovery()?.snapshot?.similarChoiceSignatureDigest)
        preferences.edit().clear().commit()
        activity.acceptNewActiveStudySession(
            similar,
            StudyPromptSource.REASON_TEXT,
            latestSuccessfulSyncAtMillis = 0L,
        )
        assertNull(activity.activeStudyRecovery())

        val writing = RecordsSchedulerModels.StudySession(
            base.item,
            base.row,
            base.token,
            StudyTaskTypes.SIMILAR_KANJI,
            writingRequired = true,
            prompt = base.prompt,
        )
        activity.acceptNewActiveStudySession(
            writing,
            StudyPromptSource.REASON_TEXT,
            latestSuccessfulSyncAtMillis = 0L,
            similarChoiceSignatureDigest = choiceDigest,
        )
        assertNull(activity.activeStudyRecovery())
        preferences.edit().clear().commit()
    }

    @Test
    fun canceledStudyRouteCannotPublishComputedCardRecovery() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val row = dashboardRow("消")
        val item = studyItem("消", "").copyBuilder()
            .answerSignature(StudyQueueSeeder.answerSignature(row))
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .build()
        activity.store.saveRows(activity.store.writableDatabase, listOf(row), 4_000L)
        activity.store.saveStudyItem(item)
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
        replaceLazyDelegate(
            activity,
            "asyncHomeRouteLoader",
            AsyncHomeRouteLoader(
                background = Executor { backgroundTasks.addLast(it) },
                postToMain = { mainTasks.addLast(it) },
                loadingTaskScheduler = LoadingTaskScheduler { _, _ -> LoadingTaskHandle { } },
            ),
        )

        activity.renderStudy()
        backgroundTasks.removeFirst().run()
        assertEquals(1, mainTasks.size)
        assertNull(StudySessionRecoveryStore(preferences).read())

        activity.renderHome()
        mainTasks.removeFirst().run()

        assertNull(StudySessionRecoveryStore(preferences).read())
    }

    @Test
    fun staleFlashcardCallbacksCannotMutateReplacementRecovery() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        fun typingSession(kanji: String, token: String, signature: String): RecordsSchedulerModels.StudySession {
            val item = studyItem(kanji, token).copyBuilder()
                .rung(RecordsBase.LadderRung.TYPE_MEANING)
                .answerSignature(signature)
                .schedulerRevision(3L)
                .routingVersion(1)
                .build()
            return RecordsSchedulerModels.StudySession(
                item,
                null,
                token,
                StudyTaskTypes.TYPE_MEANING,
                writingRequired = false,
                prompt = "prompt",
            )
        }
        val first = typingSession("旧", "old-token", "old-signature")
        activity.acceptNewActiveStudySession(first, StudyPromptSource.REASON_TEXT, 0L)
        activity.renderComposeFlashcardSession(first)
        shadowOf(Looper.getMainLooper()).idle()
        val staleTypingState = requireNotNull(activity.typingAnswerState)
        val staleActions = requireNotNull(activity.flashcardActionBarState)

        val replacement = typingSession("新", "new-token", "new-signature")
        activity.acceptNewActiveStudySession(replacement, StudyPromptSource.REASON_TEXT, 0L)
        val rawReplacement = preferences.getString("snapshot", null)

        staleTypingState.updateText("private old-card draft")
        staleActions.onReveal.run()
        staleActions.onFail.run()
        staleActions.onPass.run()

        assertEquals(rawReplacement, preferences.getString("snapshot", null))
        val stored = StudySessionRecoveryStore(preferences).readActive()
        assertEquals("new-token", stored?.snapshot?.sessionToken)
        assertEquals("", stored?.snapshot?.typedDraft)
        assertFalse(requireNotNull(stored).snapshot.revealed)
        assertNull(StudySessionRecoveryStore(preferences).readPending())
    }

    @Test
    fun staleSimilarChoiceCallbacksCannotGradeOrAdvanceReplacement() {
        val activity = createActivity()
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val oldItem = studyItem("旧", "old-choice-token").copyBuilder()
            .answerSignature("old-choice-signature")
            .schedulerRevision(3L)
            .routingVersion(1)
            .build()
        val oldSession = RecordsSchedulerModels.StudySession(
            oldItem,
            null,
            "old-choice-token",
            StudyTaskTypes.SIMILAR_KANJI,
            writingRequired = false,
            prompt = "old prompt",
        )
        activity.acceptNewActiveStudySession(
            oldSession,
            StudyPromptSource.REASON_TEXT,
            0L,
            similarChoiceSignatureDigest = similarKanjiChoiceRecoveryDigest(listOf("旧", "臼")),
        )
        activity.prepareStudyAnswerFeedback(oldSession.token)
        val oldRecovery = requireNotNull(activity.activeStudyUiRecovery(oldSession.token))
        val oldCard = RecordsImportModels.SimilarKanjiChoiceCard(
            "旧",
            "old",
            listOf("旧", "臼"),
            "旧\t臼",
        )
        val replacementItem = studyItem("新", "new-choice-token").copyBuilder()
            .answerSignature("new-choice-signature")
            .schedulerRevision(4L)
            .routingVersion(1)
            .build()
        val replacement = RecordsSchedulerModels.StudySession(
            replacementItem,
            null,
            "new-choice-token",
            StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "new prompt",
        )
        activity.acceptNewActiveStudySession(replacement, StudyPromptSource.REASON_TEXT, 0L)
        activity.prepareStudyAnswerFeedback(replacement.token)
        val replacementRaw = preferences.getString("snapshot", null)

        assertFalse(activity.submitSimilarKanjiChoice(oldSession.token, oldRecovery, oldCard, "臼"))
        assertFalse(activity.continueAfterStudyAnswer(oldSession.token, oldRecovery))

        assertEquals(replacementRaw, preferences.getString("snapshot", null))
        assertEquals("new-choice-token", activity.activeStudyRecovery()?.snapshot?.sessionToken)
        assertNull(activity.pendingStudyRecovery())
        preferences.edit().clear().commit()
    }

    @Test
    fun staleRevealWithoutRecoveryCannotSubmitReplacementSession() {
        val activity = createActivity()
        val firstBase = flashcardSession()
        val first = RecordsSchedulerModels.StudySession(
            firstBase.item?.copyBuilder()
                ?.rung(RecordsBase.LadderRung.TYPE_MEANING)
                ?.build(),
            null,
            "old-null-recovery-token",
            StudyTaskTypes.TYPE_MEANING,
            writingRequired = false,
            prompt = "prompt",
        )
        activity.activeSession = first
        activity.renderComposeFlashcardSession(first)
        shadowOf(Looper.getMainLooper()).idle()
        val staleReveal = requireNotNull(activity.flashcardActionBarState).onReveal
        val replacement = RecordsSchedulerModels.StudySession(
            first.item?.copyBuilder()?.activeToken("new-null-recovery-token")?.build(),
            null,
            "new-null-recovery-token",
            StudyTaskTypes.TYPE_MEANING,
            writingRequired = false,
            prompt = "prompt",
        )
        activity.activeSession = replacement
        activity.prepareStudyAnswerFeedback(replacement.token)
        activity.flashcardAnswerRevealed = false
        activity.flashcardRevealState = FlashcardRevealState(false)
        activity.typingAnswerState = TypingAnswerState("weak")

        staleReveal.run()

        assertFalse(activity.flashcardAnswerRevealed)
        assertFalse(requireNotNull(activity.flashcardRevealState).isRevealed)
        assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
        assertNull(activity.pendingStudyAnswerSnapshot())
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get().also { activity ->
                activity.cancelPendingHomeRouteLoads()
                activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun restoreActiveCardAfterProcessRestart(
        kanji: String,
        taskType: String,
        rung: RecordsBase.LadderRung,
        typedDraft: String,
        revealed: Boolean,
    ): RestoredActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val row = dashboardRow(kanji)
        val signature = StudyQueueSeeder.answerSignature(row)
        val token = "active-process-token-$kanji"
        val sourceSyncAt = LocalStore(context).use { store ->
            store.saveRows(store.writableDatabase, listOf(row), 4_000L)
            store.saveStudyItem(
                studyItem(kanji, token).copyBuilder()
                    .rung(rung)
                    .answerSignature(signature)
                    .schedulerRevision(4L)
                    .routingVersion(1)
                    .build(),
            )
            store.latestSuccessfulSyncFinishedAt() ?: 0L
        }
        val snapshot = StudyActiveSessionSnapshot(
            sessionToken = token,
            kanji = kanji,
            answerSignatureDigest = studyAnswerSignatureDigest(signature),
            schedulerRevision = 4L,
            routingVersion = 1,
            taskType = taskType,
            promptSource = StudyPromptSource.REASON_TEXT,
            sourceSyncFinishedAtMillis = sourceSyncAt,
            typedDraft = typedDraft,
            revealed = revealed,
        )
        assertNotNull(StudySessionRecoveryStore(preferences).replaceWithActive(snapshot))
        val raw = preferences.getString("snapshot", "").orEmpty()
        assertFalse(raw.contains("separate"))
        assertFalse(raw.contains("separation"))

        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val ioTasks = QueueingExecutorService()
        replaceField(activity, "io", ioTasks)
        controller.create().start().resume()
        ioTasks.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return RestoredActivity(activity, controller, preferences, ioTasks)
    }

    private data class SubmittingCrashFixture(
        val preferences: android.content.SharedPreferences,
        val session: RecordsSchedulerModels.StudySession,
        val pendingSnapshot: StudyPendingAnswerSnapshot,
    )

    private fun seedSubmittingCrashWindow(consumed: Boolean): SubmittingCrashFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val row = dashboardRow("裂")
        val signature = StudyQueueSeeder.answerSignature(row)
        val token = if (consumed) "consumed-submitting-token" else "unconsumed-submitting-token"
        val before = studyItem("裂", token).copyBuilder()
            .rung(RecordsBase.LadderRung.TYPE_MEANING)
            .answerSignature(signature)
            .schedulerRevision(4L)
            .routingVersion(1)
            .build()
        val sourceSyncAt = LocalStore(context).use { store ->
            store.saveRows(store.writableDatabase, listOf(row), 4_000L)
            store.saveStudyItem(before)
            store.latestSuccessfulSyncFinishedAt() ?: 0L
        }
        val recoveryStore = StudySessionRecoveryStore(preferences)
        val active = recoveryStore.replaceWithActive(
            StudyActiveSessionSnapshot(
                sessionToken = token,
                kanji = before.kanji,
                answerSignatureDigest = studyAnswerSignatureDigest(signature),
                schedulerRevision = before.schedulerRevision,
                routingVersion = before.routingVersion,
                taskType = StudyTaskTypes.TYPE_MEANING,
                promptSource = StudyPromptSource.REASON_TEXT,
                sourceSyncFinishedAtMillis = sourceSyncAt,
                typedDraft = "divide",
            ),
        )!!
        val pendingSnapshot = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = token,
                phase = StudyAnswerFeedbackPhase.SUBMITTING,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = "divide",
            ),
            kanji = before.kanji,
            taskType = StudyTaskTypes.TYPE_MEANING,
            writingRequired = false,
            prompt = row.reasonText,
            answerSignature = signature,
            schedulerRevision = before.schedulerRevision,
        )
        recoveryStore.transitionActiveToPending(
            active,
            pendingSnapshot,
        )!!
        if (consumed) {
            LocalStore(context).use { store ->
                val request = RecordsSchedulerModels.ReviewRequest(
                    before.kanji,
                    token,
                    StudyRatings.GOOD,
                    false,
                    true,
                    false,
                    0,
                )
                val commit = store.saveReviewOutcome(
                    before.copyBuilder().activeToken(null).build(),
                    request,
                    StudyRatings.GOOD,
                    5_000L,
                    before,
                )
                assertTrue(commit.applied())
            }
        }

        return SubmittingCrashFixture(
            preferences = preferences,
            session = pendingSnapshot.restoreSession(before, row),
            pendingSnapshot = pendingSnapshot,
        )
    }

    private fun restoreSubmittingCrashWindow(consumed: Boolean): RestoredActivity {
        val fixture = seedSubmittingCrashWindow(consumed)

        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val ioTasks = QueueingExecutorService()
        replaceField(activity, "io", ioTasks)
        controller.create().start().resume()
        ioTasks.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return RestoredActivity(activity, controller, fixture.preferences, ioTasks)
    }

    private fun restoreRetainedSubmittingCrashWindow(consumed: Boolean): RestoredActivity {
        val fixture = seedSubmittingCrashWindow(consumed)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val ioTasks = QueueingExecutorService()
        replaceField(activity, "io", ioTasks)
        controller.create().start().resume()
        ioTasks.discardAll()

        // Model the retained ViewModel state present after a configuration
        // replacement, then require recoveryOnly to revalidate durable state.
        activity.activeSession = fixture.session
        activity.restorePendingStudyAnswer(fixture.pendingSnapshot)
        activity.renderStudyRecoveryOnly()
        ioTasks.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return RestoredActivity(activity, controller, fixture.preferences, ioTasks)
    }

    private fun restoreContinuedHandoffAfterProcessRestart(
        hasNextCard: Boolean,
        matchingReviewEvidence: Boolean = true,
    ): RestoredActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val answeredRow = dashboardRow("前")
        val answeredSignature = StudyQueueSeeder.answerSignature(answeredRow)
        val token = "continued-process-token"
        LocalStore(context).use { store ->
            store.writableDatabase.delete(LocalStoreBase.TABLE_DASHBOARD_ROWS, null, null)
            store.writableDatabase.delete(LocalStoreBase.TABLE_STUDY_ITEMS, null, null)
            store.writableDatabase.delete(LocalStoreBase.TABLE_REVIEW_LOG, null, null)
            store.writableDatabase.delete(LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE, null, null)
            val rows = if (hasNextCard) listOf(answeredRow, dashboardRow("次")) else emptyList()
            store.saveRows(store.writableDatabase, rows, 10_000L)
            if (hasNextCard) {
                val now = System.currentTimeMillis()
                store.saveStudyItem(
                    studyItem(answeredRow.kanji, "").copyBuilder()
                        .answerSignature(answeredSignature)
                        .schedulerRevision(5L)
                        .dueAtMillis(now + TimeUnit.DAYS.toMillis(30L))
                        .build(),
                )
                val nextRow = rows.last()
                store.saveStudyItem(
                    studyItem(nextRow.kanji, "").copyBuilder()
                        .answerSignature(StudyQueueSeeder.answerSignature(nextRow))
                        .schedulerRevision(2L)
                        .dueAtMillis(now - 1_000L)
                        .build(),
                )
            }
            val reviewSignature = if (matchingReviewEvidence) answeredSignature else "different-signature"
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest(
                    answeredRow.kanji,
                    token,
                    StudyRatings.GOOD,
                    false,
                    true,
                    false,
                    false,
                    0,
                    StudyTaskTypes.KANJI_MEANING,
                    reviewSignature,
                    answeredRow.reasonText,
                ),
                StudyRatings.GOOD,
                11_000L,
            )
        }
        val recoveryStore = StudySessionRecoveryStore(preferences)
        val applied = requireNotNull(
            recoveryStore.replaceWithPending(
                StudyPendingAnswerSnapshot(
                    feedback = StudyAnswerFeedbackSnapshot(
                        sessionToken = token,
                        phase = StudyAnswerFeedbackPhase.APPLIED,
                        outcome = StudyAnswerOutcome.CORRECT,
                        selectedAnswer = StudyRatings.GOOD,
                    ),
                    kanji = answeredRow.kanji,
                    taskType = StudyTaskTypes.KANJI_MEANING,
                    writingRequired = false,
                    prompt = answeredRow.reasonText,
                    answerSignature = answeredSignature,
                    schedulerRevision = 4L,
                ),
            ),
        )
        assertNotNull(recoveryStore.continuePending(applied))

        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val ioTasks = QueueingExecutorService()
        replaceField(activity, "io", ioTasks)
        controller.create().start().resume()
        ioTasks.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return RestoredActivity(activity, controller, preferences, ioTasks)
    }

    private fun restoreSubmittingSimilarChoiceAfterProcessRestart(
        invalidateChoice: Boolean = false,
        replaceChoiceSet: Boolean = false,
    ): RestoredActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val target = "恢"
        val confusedWith = "恨"
        val rows = listOf(dashboardRow(target), dashboardRow(confusedWith), dashboardRow("恒"))
        val row = rows.first()
        val signature = StudyQueueSeeder.answerSignature(row)
        val token = "similar-process-token"
        val sourceSyncAt = LocalStore(context).use { store ->
            val index = SimilarKanjiIndex.parseTsv(
                StringReader("$target\t$confusedWith\tfixture\n$target\t恒\tfixture\n"),
            )
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList<RecordsImportModels.SuspendedImport>(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
                LocalStoreBase.SyncTiming(8_000L, 9_000L),
                null,
                index,
            )
            val item = studyItem(target, token).copyBuilder()
                .answerSignature(signature)
                .schedulerRevision(4L)
                .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
                .hasSimilarKanji(true)
                .adaptiveRouteStateJson(
                    AdaptiveRouteStateCodec.encode(
                        AdaptiveRouteState(
                            activeCore = CoreSkill.RECOGNITION,
                            activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI),
                            answerEvidence = AnswerEvidence(
                                coreSkill = CoreSkill.RECOGNITION,
                                failureKind = FailureKind.VISUAL_CONFUSION,
                                confusedWith = confusedWith,
                            ),
                        ),
                    ),
                )
                .build()
            store.saveStudyItem(item)
            val card = requireNotNull(
                store.dueSimilarChoiceForActiveTarget(target, System.currentTimeMillis()),
            )
            val recoveryStore = StudySessionRecoveryStore(preferences)
            val active = requireNotNull(
                recoveryStore.replaceWithActive(
                    StudyActiveSessionSnapshot(
                        sessionToken = token,
                        kanji = target,
                        answerSignatureDigest = studyAnswerSignatureDigest(signature),
                        schedulerRevision = item.schedulerRevision,
                        routingVersion = item.routingVersion,
                        taskType = StudyTaskTypes.SIMILAR_KANJI,
                        promptSource = StudyPromptSource.REASON_TEXT,
                        sourceSyncFinishedAtMillis = store.latestSuccessfulSyncFinishedAt() ?: 0L,
                        similarChoiceSignatureDigest = similarKanjiChoiceRecoveryDigest(card.choices),
                    ),
                ),
            )
            requireNotNull(
                recoveryStore.transitionActiveToPending(
                    active,
                    StudyPendingAnswerSnapshot(
                        feedback = StudyAnswerFeedbackSnapshot(
                            sessionToken = token,
                            phase = StudyAnswerFeedbackPhase.SUBMITTING,
                            outcome = StudyAnswerOutcome.INCORRECT,
                            selectedAnswer = confusedWith,
                        ),
                        kanji = target,
                        taskType = StudyTaskTypes.SIMILAR_KANJI,
                        writingRequired = false,
                        prompt = row.reasonText,
                        answerSignature = signature,
                        schedulerRevision = item.schedulerRevision,
                    ),
                ),
            )
            if (invalidateChoice) {
                val values = ContentValues().apply {
                    put(LocalStoreBase.COLUMN_PASSED_AT, 9_001L)
                }
                assertEquals(
                    1,
                    store.writableDatabase.update(
                        LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
                        values,
                        "target_kanji=? AND choice_signature=?",
                        arrayOf(card.targetKanji, card.choiceSignature),
                    ),
                )
            }
            if (replaceChoiceSet) {
                val replacementChoices = listOf(target, confusedWith, "悟")
                val values = ContentValues().apply {
                    put(
                        LocalStoreBase.COLUMN_CHOICE_SIGNATURE,
                        SimilarKanjiChoicePlanner.choiceSignature(replacementChoices),
                    )
                    put("choices", LocalStoreHistory.serializeChoices(replacementChoices))
                }
                assertEquals(
                    1,
                    store.writableDatabase.update(
                        LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
                        values,
                        "target_kanji=? AND choice_signature=?",
                        arrayOf(card.targetKanji, card.choiceSignature),
                    ),
                )
            }
            store.latestSuccessfulSyncFinishedAt() ?: 0L
        }
        assertEquals(9_000L, sourceSyncAt)

        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val ioTasks = QueueingExecutorService()
        replaceField(activity, "io", ioTasks)
        controller.create().start().resume()
        ioTasks.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return RestoredActivity(activity, controller, preferences, ioTasks)
    }

    private fun dashboardRow(kanji: String): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
            "active",
            101L,
            202L,
            "分離",
            "ぶんり",
            "separation",
            "A sentence",
            false,
            0,
        )
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "separate",
            "ぶんり",
            "deck:Kiku",
            10,
            "weak",
            "Needs support",
            1,
            0,
            0,
            listOf(example),
        )
    }

    private class RestoredActivity(
        val activity: MainActivity,
        private val controller: org.robolectric.android.controller.ActivityController<MainActivity>,
        val preferences: android.content.SharedPreferences,
        private val ioTasks: QueueingExecutorService,
    ) {
        fun runQueuedIo() {
            ioTasks.runAll()
        }

        fun close() {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    private fun flashcardSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("弱", "flashcard-token"),
            row = null,
            token = "flashcard-token",
            taskType = StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "prompt text",
        )
    }

    private fun writingSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("書", "writing-token"),
            row = null,
            token = "writing-token",
            taskType = StudyTaskTypes.WRITE_KANJI,
            writingRequired = true,
            prompt = "prompt text",
        )
    }

    private fun studyItem(kanji: String, token: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken(token)
            .build()
    }

    private fun persistRepair(
        store: LocalStore,
        token: String,
    ): RecordsImportModels.SimilarKanjiWritingRepair {
        fun repair(id: Long) = RecordsImportModels.SimilarKanjiWritingRepair(
            id,
            "末",
            "修",
            "restart-repair-signature",
            "未",
            "repair",
            "pending",
            0L,
            token,
            0,
            1_000L,
            1_000L,
            0L,
        )
        val draft = repair(0L)
        val values = ContentValues().apply {
            put("target_kanji", draft.targetKanji)
            put("repair_kanji", draft.repairKanji)
            put("choice_signature", draft.choiceSignature)
            put("wrong_selection", draft.wrongSelection)
            put("prompt_meaning", draft.promptMeaning)
            put("status", draft.status)
            put("due_at", draft.dueAtMillis)
            put("active_token", draft.activeToken)
            put("attempts", draft.attempts)
            put("created_at", draft.createdAtMillis)
            put("updated_at", draft.updatedAtMillis)
            put("completed_at", draft.completedAtMillis)
        }
        val id = store.writableDatabase.insertOrThrow("similar_kanji_repair_queue", null, values)
        return repair(id)
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun replaceField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityHome::class.java.getDeclaredField("$propertyName\$delegate")
        field.isAccessible = true
        field.set(activity, lazyOf(value))
    }

    private class QueueingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val remaining = tasks.toMutableList()
            tasks.clear()
            return remaining
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated

        override fun execute(command: Runnable) {
            check(!shutdown)
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }

        fun discardAll() {
            tasks.clear()
        }
    }
}
