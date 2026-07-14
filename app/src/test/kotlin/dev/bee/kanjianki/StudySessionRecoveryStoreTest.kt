package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.StudyTaskTypes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudySessionRecoveryStoreTest {
    private lateinit var preferences: android.content.SharedPreferences
    private var nextEpoch = 0

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences("study-session-recovery-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        nextEpoch = 0
    }

    @Test
    fun activeDraftRoundTripsWithoutPersistingAnswerContent() {
        val store = store()
        val snapshot = activeSnapshot(typedDraft = "分ける")

        val stored = store.replaceWithActive(snapshot)

        assertNotNull(stored)
        assertEquals(snapshot, store.readActive()?.snapshot)
        assertTrue(store.shouldResumeOnOrdinaryLaunch())
        val raw = preferences.getString("snapshot", "").orEmpty()
        assertTrue(raw.contains("分ける"))
        assertFalse(raw.contains("分裂"))
        assertFalse(raw.contains("ぶんれつ"))
        assertFalse(raw.contains("split"))
        assertFalse(raw.contains("mnemonic from the answer panel"))
        assertFalse(raw.contains("canonical correct answer"))
        assertFalse(raw.contains("adaptive_route_state_json"))
    }

    @Test
    fun similarChoiceRoundTripsOnlyItsCanonicalDigest() {
        val store = store()
        val snapshot = similarSnapshot()

        val stored = store.replaceWithActive(snapshot)

        assertNotNull(stored)
        assertEquals(snapshot, store.readActive()?.snapshot)
        val raw = preferences.getString("snapshot", "").orEmpty()
        assertTrue(raw.contains(snapshot.similarChoiceSignatureDigest.orEmpty()))
        assertFalse(raw.contains("烈"))
        assertFalse(raw.contains("例"))
        assertFalse(raw.contains("裂\\t烈\\t例"))
    }

    @Test
    fun similarChoiceRequiresExactUngradedUiContract() {
        val store = store()
        val valid = similarSnapshot()

        assertNull(store.replaceWithActive(valid.copy(similarChoiceSignatureDigest = null)))
        assertNull(store.replaceWithActive(valid.copy(similarChoiceSignatureDigest = "not-a-digest")))
        assertNull(store.replaceWithActive(valid.copy(typedDraft = "private draft")))
        assertNull(store.replaceWithActive(valid.copy(revealed = true)))
        assertNull(
            store.replaceWithActive(
                activeSnapshot().copy(similarChoiceSignatureDigest = valid.similarChoiceSignatureDigest),
            ),
        )
    }

    @Test
    fun similarChoiceSubmittingFallbackPreservesChoiceIdentity() {
        val store = store()
        val active = store.replaceWithActive(similarSnapshot())!!
        val pending = similarPendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING)

        val stored = store.transitionActiveToPending(active, pending)!!
        val restored = store.claimPendingFallback(stored)!!

        assertEquals(active.snapshot.similarChoiceSignatureDigest, restored.snapshot.similarChoiceSignatureDigest)
        assertEquals(active.writeEpoch, restored.writeEpoch)
    }

    @Test
    fun preChoiceV2ActiveAndFallbackPayloadsRemainReadable() {
        val signature = "裂|分裂|ぶんれつ|split"
        val active = JSONObject()
            .put("token", "active-token")
            .put("kanji", "裂")
            .put("answer_signature_digest", studyAnswerSignatureDigest(signature))
            .put("scheduler_revision", 7L)
            .put("routing_version", 2)
            .put("task_type", StudyTaskTypes.TYPE_MEANING)
            .put("prompt_source", StudyPromptSource.REASON_TEXT.name)
            .put("source_sync_finished_at", 9_000L)
            .put("typed_draft", "legacy draft")
            .put("revealed", false)
        val activeEnvelope = JSONObject()
            .put("version", 2)
            .put("kind", "active")
            .put("resume_on_ordinary_launch", true)
            .put("write_epoch", "legacy-v2-active-epoch")
            .put("active", active)
            .toString()
        preferences.edit().putString("snapshot", activeEnvelope).commit()

        val decodedActive = store().readActive()

        assertEquals("legacy draft", decodedActive?.snapshot?.typedDraft)
        assertNull(decodedActive?.snapshot?.similarChoiceSignatureDigest)

        val pending = JSONObject()
            .put("token", "active-token")
            .put("phase", StudyAnswerFeedbackPhase.SUBMITTING.name)
            .put("outcome", StudyAnswerOutcome.INCORRECT.name)
            .put("selected_answer", "draft")
            .put("kanji", "裂")
            .put("task_type", StudyTaskTypes.TYPE_MEANING)
            .put("writing_required", false)
            .put("prompt", "Why is this weak?")
            .put("answer_signature", signature)
            .put("scheduler_revision", 7L)
        val pendingEnvelope = JSONObject()
            .put("version", 2)
            .put("kind", "pending_answer")
            .put("resume_on_ordinary_launch", true)
            .put("pending", pending)
            .put("fallback_active", active)
            .put("fallback_write_epoch", "legacy-v2-fallback-epoch")
            .toString()
        preferences.edit().putString("snapshot", pendingEnvelope).commit()

        val decodedPending = store().readPending()

        assertEquals("legacy draft", decodedPending?.fallbackActive?.typedDraft)
        assertNull(decodedPending?.fallbackActive?.similarChoiceSignatureDigest)
    }

    @Test
    fun deferredDraftUpdateIsVisibleImmediatelyAndFlushesAtLifecycleBoundary() {
        val store = store()
        val active = store.replaceWithActive(activeSnapshot())!!

        val updated = store.updateActiveDeferred(
            active.snapshot.sessionToken,
            active.writeEpoch,
        ) { it.copy(typedDraft = "latest") }!!

        assertEquals("latest", store.readActive()?.snapshot?.typedDraft)
        assertTrue(store.flush())
        assertEquals(updated.snapshot, StudySessionRecoveryStore(preferences).readActive()?.snapshot)
    }

    @Test
    fun explicitExitRetainsPayloadButTombstonesOldWriterUntilManualClaim() {
        val store = store()
        val first = store.replaceWithActive(activeSnapshot(typedDraft = "first"))!!

        assertTrue(store.disableOrdinaryResume())

        val dormant = store.readActive()!!
        assertFalse(dormant.resumeOnOrdinaryLaunch)
        assertEquals("first", dormant.snapshot.typedDraft)
        assertEquals("", dormant.writeEpoch)
        assertNull(
            store.updateActive(first.snapshot.sessionToken, first.writeEpoch) {
                it.copy(typedDraft = "late write")
            },
        )

        val claimed = store.claimActive(dormant)!!
        assertTrue(claimed.resumeOnOrdinaryLaunch)
        assertEquals("epoch-2", claimed.writeEpoch)
        assertEquals("first", claimed.snapshot.typedDraft)
    }

    @Test
    fun activeToPendingTransitionKeepsAnAtomicUngradedFallback() {
        val store = store()
        val active = store.replaceWithActive(activeSnapshot(typedDraft = "draft"))!!
        val pending = pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING)

        val storedPending = store.transitionActiveToPending(active, pending)!!

        assertNull(store.readActive())
        assertEquals(pending, storedPending.snapshot)
        assertEquals("draft", storedPending.fallbackActive?.typedDraft)
        assertEquals(active.writeEpoch, storedPending.fallbackWriteEpoch)
        val restored = store.claimPendingFallback(storedPending)!!
        assertEquals("draft", restored.snapshot.typedDraft)
        assertEquals(active.writeEpoch, restored.writeEpoch)
        assertNull(store.readPending())
    }

    @Test
    fun staleActiveToPendingTransitionCannotReplaceNewerDraft() {
        val store = store()
        val stale = store.replaceWithActive(activeSnapshot(typedDraft = "old"))!!
        val current = store.updateActive(stale.snapshot.sessionToken, stale.writeEpoch) {
            it.copy(typedDraft = "new")
        }!!

        assertNull(store.transitionActiveToPending(stale, pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING)))
        assertEquals(current, store.readActive())
    }

    @Test
    fun lateAppliedUpdateCannotReenableOrdinaryResumeAfterExit() {
        val store = store()
        val active = store.replaceWithActive(activeSnapshot(typedDraft = "draft"))!!
        store.transitionActiveToPending(active, pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING))!!
        assertTrue(store.disableOrdinaryResume())
        val dormant = store.readPending()!!
        assertFalse(dormant.fallbackWriteEpoch == active.writeEpoch)

        val updated = store.updatePending(
            active.snapshot.sessionToken,
            pendingSnapshot(StudyAnswerFeedbackPhase.APPLIED),
            retainFallback = false,
        )!!

        assertFalse(updated.resumeOnOrdinaryLaunch)
        assertFalse(store.shouldResumeOnOrdinaryLaunch())
        assertNull(updated.fallbackActive)
        assertNull(updated.fallbackWriteEpoch)
    }

    @Test
    fun invalidDormantFallbackEpochLeavesPendingEnvelopeUnchanged() {
        val store = StudySessionRecoveryStore(preferences) {
            nextEpoch += 1
            if (nextEpoch == 1) "active-epoch" else ""
        }
        val active = store.replaceWithActive(activeSnapshot(typedDraft = "draft"))!!
        val pending = store.transitionActiveToPending(
            active,
            pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING),
        )!!

        assertFalse(store.disableOrdinaryResume())

        assertEquals(pending, store.readPending())
        assertTrue(store.shouldResumeOnOrdinaryLaunch())
    }

    @Test
    fun activeToPendingAndPendingUpdatesRejectDifferentRecoveryFamilies() {
        val store = store()
        val active = store.replaceWithActive(activeSnapshot(typedDraft = "draft"))!!
        val wrongFamily = pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING).copy(kanji = "別")

        assertNull(store.transitionActiveToPending(active, wrongFamily))
        assertEquals(active, store.readActive())

        val pending = store.transitionActiveToPending(active, pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING))!!
        assertNull(
            store.updatePending(
                active.snapshot.sessionToken,
                pending.snapshot.copy(answerSignature = "different signature"),
                retainFallback = true,
            ),
        )
        assertNull(
            store.claimPending(
                pending,
                pending.snapshot.copy(taskType = StudyTaskTypes.KANJI_MEANING),
            ),
        )
        assertEquals(pending, store.readPending())
    }

    @Test
    fun supersededSessionClearCannotDeleteDifferentToken() {
        val store = store()
        val old = store.replaceWithActive(activeSnapshot())!!
        val replacement = store.replaceWithActive(
            activeSnapshot(token = "replacement-token", kanji = "新", signature = "新|新人|しんじん|new"),
        )!!

        assertFalse(store.clearSession(old.snapshot.sessionToken))
        assertEquals(replacement, store.readActive())
        assertTrue(store.clearSession(replacement.snapshot.sessionToken))
        assertNull(store.read())
    }

    @Test
    fun pendingCreationCannotOverwriteAnotherSession() {
        val store = store()
        val current = store.replaceWithActive(activeSnapshot())!!

        assertNull(store.createPendingIfEmpty(pendingSnapshot(StudyAnswerFeedbackPhase.SUBMITTING)))
        assertEquals(current, store.readActive())
    }

    @Test
    fun staleConditionalClearCannotDeleteAReplacementCard() {
        val store = store()
        val stale = store.replaceWithActive(activeSnapshot(typedDraft = "old"))!!
        val replacement = store.replaceWithActive(
            activeSnapshot(token = "new-token", kanji = "新", signature = "新|新人|しんじん|new"),
        )!!

        assertFalse(store.clearIfUnchanged(stale))
        assertEquals(replacement.snapshot, store.readActive()?.snapshot)
    }

    @Test
    fun malformedAndUnsupportedActivePayloadsFailClosedAndAreRemoved() {
        preferences.edit().putString("snapshot", "{not-json").commit()
        assertNull(store().read())
        assertNull(preferences.getString("snapshot", null))

        val invalid = activeSnapshot(typedDraft = "answer").copy(
            taskType = StudyTaskTypes.KANJI_MEANING,
        )
        assertNull(store().replaceWithActive(invalid))
        assertNull(
            store().replaceWithActive(
                activeSnapshot().copy(taskType = StudyTaskTypes.MEANING_KANJI),
            ),
        )
        assertNull(
            store().replaceWithActive(
                activeSnapshot().copy(taskType = StudyTaskTypes.WRITE_KANJI),
            ),
        )
    }

    @Test
    fun legacyV1PendingAnswerDecodesAsUniqueFamilyCompatibilityState() {
        val legacy = JSONObject()
            .put("version", 1)
            .put("token", "legacy-token")
            .put("phase", StudyAnswerFeedbackPhase.APPLIED.name)
            .put("outcome", StudyAnswerOutcome.CORRECT.name)
            .put("selected_answer", "good")
            .put("kanji", "旧")
            .put("task_type", StudyTaskTypes.KANJI_MEANING)
            .put("writing_required", false)
            .put("prompt", "legacy prompt")
            .toString()
        preferences.edit().putString("snapshot", legacy).commit()

        val stored = store().readPending()!!

        assertTrue(stored.resumeOnOrdinaryLaunch)
        assertNull(stored.snapshot.answerSignature)
        assertNull(stored.snapshot.schedulerRevision)
    }

    private fun store(): StudySessionRecoveryStore = StudySessionRecoveryStore(preferences) {
        nextEpoch += 1
        "epoch-$nextEpoch"
    }

    private fun activeSnapshot(
        token: String = "active-token",
        kanji: String = "裂",
        signature: String = "裂|分裂|ぶんれつ|split",
        typedDraft: String = "",
    ): StudyActiveSessionSnapshot = StudyActiveSessionSnapshot(
        sessionToken = token,
        kanji = kanji,
        answerSignatureDigest = studyAnswerSignatureDigest(signature),
        schedulerRevision = 7L,
        routingVersion = 2,
        taskType = StudyTaskTypes.TYPE_MEANING,
        promptSource = StudyPromptSource.REASON_TEXT,
        sourceSyncFinishedAtMillis = 9_000L,
        typedDraft = typedDraft,
    )

    private fun pendingSnapshot(phase: StudyAnswerFeedbackPhase): StudyPendingAnswerSnapshot =
        StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = "active-token",
                phase = phase,
                outcome = StudyAnswerOutcome.INCORRECT,
                selectedAnswer = "draft",
            ),
            kanji = "裂",
            taskType = StudyTaskTypes.TYPE_MEANING,
            writingRequired = false,
            prompt = "Why is this weak?",
            answerSignature = "裂|分裂|ぶんれつ|split",
            schedulerRevision = 7L,
        )

    private fun similarSnapshot(): StudyActiveSessionSnapshot = activeSnapshot().copy(
        taskType = StudyTaskTypes.SIMILAR_KANJI,
        typedDraft = "",
        similarChoiceSignatureDigest = similarKanjiChoiceRecoveryDigest(listOf("裂", "烈", "例")),
    )

    private fun similarPendingSnapshot(phase: StudyAnswerFeedbackPhase): StudyPendingAnswerSnapshot =
        pendingSnapshot(phase).copy(
            feedback = pendingSnapshot(phase).feedback.copy(selectedAnswer = "烈"),
            taskType = StudyTaskTypes.SIMILAR_KANJI,
        )
}
