package dev.bee.kanjianki

import android.annotation.SuppressLint
import android.content.SharedPreferences
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Durable description of the answered card that must remain visible until Continue. */
data class StudyPendingAnswerSnapshot(
    val feedback: StudyAnswerFeedbackSnapshot,
    val kanji: String,
    val taskType: String,
    val writingRequired: Boolean,
    val prompt: String,
    /** Null only for a legacy v1 snapshot written before exact family binding. */
    val answerSignature: String? = null,
    /** Pre-review revision; the committed canonical item must be exactly one revision newer. */
    val schedulerRevision: Long? = null,
) {
    fun restoreSession(
        item: RecordsStudyModels.StudyItem,
        row: RecordsImportModels.DashboardRow?,
    ): RecordsSchedulerModels.StudySession {
        val restoredItem = item.copyBuilder()
            .activeToken(feedback.sessionToken)
            .build()
        return RecordsSchedulerModels.StudySession(
            restoredItem,
            row,
            feedback.sessionToken,
            taskType,
            writingRequired,
            prompt,
        )
    }
}

internal enum class StudyPromptSource {
    REASON_TEXT,
    PRIMARY_MEANING,
}

/**
 * Minimal, answer-free UI state for one ungraded canonical flashcard.
 *
 * The database remains authoritative for the card, prompt, examples, mnemonic and correct answer.
 * These identity scalars are only enough to prove that the same persisted card is still current.
 */
internal data class StudyActiveSessionSnapshot(
    val sessionToken: String,
    val kanji: String,
    val answerSignatureDigest: String,
    val schedulerRevision: Long,
    val routingVersion: Int,
    val taskType: String,
    val promptSource: StudyPromptSource,
    val sourceSyncFinishedAtMillis: Long,
    /** Digest of the canonical similar-kanji candidate set; null for flashcard routes. */
    val similarChoiceSignatureDigest: String? = null,
    val typedDraft: String = "",
    val revealed: Boolean = false,
)

internal sealed class StoredStudyRecovery {
    abstract val resumeOnOrdinaryLaunch: Boolean
    internal abstract val raw: String
}

internal data class StoredActiveStudyRecovery(
    val snapshot: StudyActiveSessionSnapshot,
    val writeEpoch: String,
    override val resumeOnOrdinaryLaunch: Boolean,
    internal override val raw: String,
) : StoredStudyRecovery()

internal data class StoredPendingStudyRecovery(
    val snapshot: StudyPendingAnswerSnapshot,
    val fallbackActive: StudyActiveSessionSnapshot?,
    val fallbackWriteEpoch: String?,
    override val resumeOnOrdinaryLaunch: Boolean,
    internal override val raw: String,
) : StoredStudyRecovery()

/**
 * One crash-safe recovery envelope for the active -> submitting -> applied -> continued UI lifecycle.
 *
 * Identity changes are synchronous, lock-serialized SharedPreferences commits. Draft keystrokes
 * update the in-process preference map immediately and are flushed at the Activity pause boundary;
 * submission and exit also commit the latest map. The envelope's raw value is its compare-and-set
 * revision, so a stale Activity or background route result cannot delete or overwrite a newer card.
 * Active UI callbacks additionally carry a claimed write epoch; leaving Study clears that epoch
 * before the deferred destination starts rendering.
 */
// SharedPreferences KTX edit discards commit's Boolean result; durable transitions must fail closed.
@SuppressLint("UseKtx")
internal class StudySessionRecoveryStore(
    private val preferences: SharedPreferences,
    private val epochFactory: () -> String,
) {
    constructor(preferences: SharedPreferences) : this(
        preferences,
        epochFactory = { UUID.randomUUID().toString() },
    )

    fun read(): StoredStudyRecovery? = synchronized(LOCK) { readLocked(clearMalformed = true) }

    fun readActive(): StoredActiveStudyRecovery? = read() as? StoredActiveStudyRecovery

    fun readPending(): StoredPendingStudyRecovery? = read() as? StoredPendingStudyRecovery

    fun shouldResumeOnOrdinaryLaunch(): Boolean = read()?.resumeOnOrdinaryLaunch == true

    fun currentSessionToken(): String? = synchronized(LOCK) {
        readLocked(clearMalformed = true)?.let(::recoveryToken)
    }

    fun replaceWithActive(snapshot: StudyActiveSessionSnapshot): StoredActiveStudyRecovery? = synchronized(LOCK) {
        val epoch = epochFactory().takeIf(::validEpoch) ?: return@synchronized null
        writeActiveLocked(snapshot, epoch, resumeOnOrdinaryLaunch = true, durable = true)
    }

    fun claimActive(expected: StoredActiveStudyRecovery): StoredActiveStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw) return@synchronized null
        val epoch = epochFactory().takeIf(::validEpoch) ?: return@synchronized null
        writeActiveLocked(expected.snapshot, epoch, resumeOnOrdinaryLaunch = true, durable = true)
    }

    /**
     * Publish the next current card only while the exact consumed-card handoff still owns routing.
     * The continued envelope never selects the card; it merely prevents a process-death gap while
     * the normal scheduler recomputes current work.
     */
    fun replaceContinuedWithActive(
        expected: StoredPendingStudyRecovery,
        snapshot: StudyActiveSessionSnapshot,
    ): StoredActiveStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw ||
            expected.snapshot.feedback.phase != StudyAnswerFeedbackPhase.CONTINUED ||
            !expected.resumeOnOrdinaryLaunch ||
            expected.fallbackActive != null ||
            expected.fallbackWriteEpoch != null
        ) {
            return@synchronized null
        }
        val epoch = epochFactory().takeIf(::validEpoch) ?: return@synchronized null
        writeActiveLocked(snapshot, epoch, resumeOnOrdinaryLaunch = true, durable = true)
    }

    fun updateActive(
        expectedToken: String,
        expectedEpoch: String,
        transform: (StudyActiveSessionSnapshot) -> StudyActiveSessionSnapshot,
    ): StoredActiveStudyRecovery? = updateActiveInternal(
        expectedToken,
        expectedEpoch,
        transform,
        durable = true,
    )

    fun updateActiveDeferred(
        expectedToken: String,
        expectedEpoch: String,
        transform: (StudyActiveSessionSnapshot) -> StudyActiveSessionSnapshot,
    ): StoredActiveStudyRecovery? = updateActiveInternal(
        expectedToken,
        expectedEpoch,
        transform,
        durable = false,
    )

    private fun updateActiveInternal(
        expectedToken: String,
        expectedEpoch: String,
        transform: (StudyActiveSessionSnapshot) -> StudyActiveSessionSnapshot,
        durable: Boolean,
    ): StoredActiveStudyRecovery? = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) as? StoredActiveStudyRecovery ?: return@synchronized null
        if (current.snapshot.sessionToken != expectedToken || current.writeEpoch != expectedEpoch) {
            return@synchronized null
        }
        val updated = transform(current.snapshot)
        if (!sameActiveIdentity(current.snapshot, updated)) return@synchronized null
        writeActiveLocked(updated, current.writeEpoch, current.resumeOnOrdinaryLaunch, durable)
    }

    /** Force any in-memory draft update into the preferences file at the lifecycle boundary. */
    fun flush(): Boolean = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) ?: return@synchronized true
        val previous = runCatching { preferences.getLong(KEY_FLUSH_SEQUENCE, 0L) }.getOrDefault(0L)
        val next = if (previous == Long.MAX_VALUE) 0L else previous + 1L
        preferences.edit()
            .putString(KEY_SNAPSHOT, current.raw)
            .putLong(KEY_FLUSH_SEQUENCE, next)
            .commit()
    }

    fun transitionActiveToPending(
        expected: StoredActiveStudyRecovery,
        pending: StudyPendingAnswerSnapshot,
    ): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (pending.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED) return@synchronized null
        if (rawLocked() != expected.raw) return@synchronized null
        if (!pendingMatchesActive(pending, expected.snapshot)) return@synchronized null
        writePendingLocked(
            pending,
            expected.snapshot,
            expected.writeEpoch,
            resumeOnOrdinaryLaunch = true,
        )
    }

    fun replaceWithPending(
        pending: StudyPendingAnswerSnapshot,
        fallbackActive: StudyActiveSessionSnapshot? = null,
    ): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (pending.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED) return@synchronized null
        val fallbackEpoch = if (fallbackActive == null) {
            null
        } else {
            epochFactory().takeIf(::validEpoch) ?: return@synchronized null
        }
        writePendingLocked(pending, fallbackActive, fallbackEpoch, resumeOnOrdinaryLaunch = true)
    }

    fun createPendingIfEmpty(pending: StudyPendingAnswerSnapshot): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (pending.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED) return@synchronized null
        if (rawLocked() != null) return@synchronized null
        writePendingLocked(
            pending,
            fallbackActive = null,
            fallbackWriteEpoch = null,
            resumeOnOrdinaryLaunch = true,
        )
    }

    fun updatePending(
        expectedToken: String,
        pending: StudyPendingAnswerSnapshot,
        retainFallback: Boolean,
    ): StoredPendingStudyRecovery? = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) as? StoredPendingStudyRecovery ?: return@synchronized null
        if (current.snapshot.feedback.sessionToken != expectedToken) return@synchronized null
        if (!validPendingUpdate(current.snapshot, pending)) return@synchronized null
        writePendingLocked(
            pending,
            if (retainFallback) current.fallbackActive else null,
            if (retainFallback) current.fallbackWriteEpoch else null,
            resumeOnOrdinaryLaunch = current.resumeOnOrdinaryLaunch,
        )
    }

    /**
     * Atomically acknowledge Continue without deleting the only durable route state. Explicit
     * Continue re-arms ordinary launch; a later Home/Stats/etc. exit can still make this exact raw
     * envelope dormant and thereby defeat a late route publication.
     */
    fun continuePending(expected: StoredPendingStudyRecovery): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw ||
            expected.snapshot.feedback.phase != StudyAnswerFeedbackPhase.APPLIED ||
            expected.fallbackActive != null ||
            expected.fallbackWriteEpoch != null
        ) {
            return@synchronized null
        }
        val continued = expected.snapshot.copy(
            feedback = expected.snapshot.feedback.copy(phase = StudyAnswerFeedbackPhase.CONTINUED),
        )
        writePendingLocked(
            continued,
            fallbackActive = null,
            fallbackWriteEpoch = null,
            resumeOnOrdinaryLaunch = true,
        )
    }

    fun claimPending(
        expected: StoredPendingStudyRecovery,
        pending: StudyPendingAnswerSnapshot = expected.snapshot,
    ): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw) return@synchronized null
        if (!validPendingUpdate(expected.snapshot, pending)) return@synchronized null
        writePendingLocked(
            pending,
            expected.fallbackActive,
            expected.fallbackWriteEpoch,
            resumeOnOrdinaryLaunch = true,
        )
    }

    /** Claim proven-applied feedback and discard the now-obsolete ungraded submission fallback. */
    fun claimAppliedPending(
        expected: StoredPendingStudyRecovery,
        pending: StudyPendingAnswerSnapshot,
    ): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw ||
            pending.feedback.phase != StudyAnswerFeedbackPhase.APPLIED ||
            !validPendingUpdate(expected.snapshot, pending)
        ) {
            return@synchronized null
        }
        writePendingLocked(
            pending,
            fallbackActive = null,
            fallbackWriteEpoch = null,
            resumeOnOrdinaryLaunch = true,
        )
    }

    /** Re-arm a dormant continued handoff before an explicit Study route starts asynchronous work. */
    fun claimContinued(expected: StoredPendingStudyRecovery): StoredPendingStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw ||
            expected.snapshot.feedback.phase != StudyAnswerFeedbackPhase.CONTINUED ||
            expected.resumeOnOrdinaryLaunch ||
            expected.fallbackActive != null ||
            expected.fallbackWriteEpoch != null
        ) {
            return@synchronized null
        }
        writePendingLocked(
            expected.snapshot,
            fallbackActive = null,
            fallbackWriteEpoch = null,
            resumeOnOrdinaryLaunch = true,
        )
    }

    fun claimPendingFallback(expected: StoredPendingStudyRecovery): StoredActiveStudyRecovery? = synchronized(LOCK) {
        if (rawLocked() != expected.raw) return@synchronized null
        val fallback = expected.fallbackActive ?: return@synchronized null
        val epoch = expected.fallbackWriteEpoch?.takeIf(::validEpoch) ?: return@synchronized null
        writeActiveLocked(fallback, epoch, resumeOnOrdinaryLaunch = true, durable = true)
    }

    /** Disable automatic launch routing while retaining the card for an explicit return to Study. */
    fun disableOrdinaryResume(): Boolean = synchronized(LOCK) {
        when (val current = readLocked(clearMalformed = true)) {
            null -> true
            is StoredActiveStudyRecovery -> disableActiveOrdinaryResumeLocked(current)
            is StoredPendingStudyRecovery -> disablePendingOrdinaryResumeLocked(current)
        }
    }

    private fun disableActiveOrdinaryResumeLocked(current: StoredActiveStudyRecovery): Boolean {
        if (!current.resumeOnOrdinaryLaunch && current.writeEpoch.isEmpty()) return true
        return writeActiveLocked(
            current.snapshot,
            writeEpoch = "",
            resumeOnOrdinaryLaunch = false,
            durable = true,
        ) != null
    }

    private fun disablePendingOrdinaryResumeLocked(current: StoredPendingStudyRecovery): Boolean {
        if (!current.resumeOnOrdinaryLaunch) return true
        val dormantFallbackEpoch = if (current.fallbackActive == null) {
            null
        } else {
            epochFactory().takeIf(::validEpoch) ?: return false
        }
        return writePendingLocked(
            current.snapshot,
            current.fallbackActive,
            dormantFallbackEpoch,
            resumeOnOrdinaryLaunch = false,
        ) != null
    }

    fun clearIfUnchanged(expected: StoredStudyRecovery): Boolean = synchronized(LOCK) {
        if (rawLocked() != expected.raw) return@synchronized false
        preferences.edit().remove(KEY_SNAPSHOT).commit()
    }

    fun clearPending(expectedToken: String? = null): Boolean = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) as? StoredPendingStudyRecovery ?: return@synchronized true
        if (expectedToken != null && current.snapshot.feedback.sessionToken != expectedToken) {
            return@synchronized false
        }
        preferences.edit().remove(KEY_SNAPSHOT).commit()
    }

    fun clearActive(expectedToken: String? = null): Boolean = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) as? StoredActiveStudyRecovery ?: return@synchronized true
        if (expectedToken != null && current.snapshot.sessionToken != expectedToken) return@synchronized false
        preferences.edit().remove(KEY_SNAPSHOT).commit()
    }

    /** Remove only the recovery family superseded by an explicitly accepted new session. */
    fun clearSession(expectedToken: String): Boolean = synchronized(LOCK) {
        val current = readLocked(clearMalformed = true) ?: return@synchronized true
        if (recoveryToken(current) != expectedToken) return@synchronized false
        preferences.edit().remove(KEY_SNAPSHOT).commit()
    }

    private fun writeActiveLocked(
        snapshot: StudyActiveSessionSnapshot,
        writeEpoch: String,
        resumeOnOrdinaryLaunch: Boolean,
        durable: Boolean,
    ): StoredActiveStudyRecovery? {
        val raw = encodeActive(snapshot, writeEpoch, resumeOnOrdinaryLaunch) ?: return null
        val editor = preferences.edit().putString(KEY_SNAPSHOT, raw)
        if (durable) {
            if (!editor.commit()) return null
        } else {
            editor.apply()
        }
        return StoredActiveStudyRecovery(snapshot, writeEpoch, resumeOnOrdinaryLaunch, raw)
    }

    private fun writePendingLocked(
        snapshot: StudyPendingAnswerSnapshot,
        fallbackActive: StudyActiveSessionSnapshot?,
        fallbackWriteEpoch: String?,
        resumeOnOrdinaryLaunch: Boolean,
    ): StoredPendingStudyRecovery? {
        val raw = encodePending(
            snapshot,
            fallbackActive,
            fallbackWriteEpoch,
            resumeOnOrdinaryLaunch,
        ) ?: return null
        if (!preferences.edit().putString(KEY_SNAPSHOT, raw).commit()) return null
        return StoredPendingStudyRecovery(snapshot, fallbackActive, fallbackWriteEpoch, resumeOnOrdinaryLaunch, raw)
    }

    private fun readLocked(clearMalformed: Boolean): StoredStudyRecovery? {
        val raw = rawLocked() ?: return null
        val decoded = decode(raw)
        if (decoded != null) return decoded
        if (clearMalformed && rawLocked() == raw) {
            preferences.edit().remove(KEY_SNAPSHOT).apply()
        }
        return null
    }

    private fun rawLocked(): String? = runCatching { preferences.getString(KEY_SNAPSHOT, null) }.getOrNull()

    private fun encodeActive(
        snapshot: StudyActiveSessionSnapshot,
        writeEpoch: String,
        resumeOnOrdinaryLaunch: Boolean,
    ): String? {
        if (!validActive(snapshot) || (resumeOnOrdinaryLaunch && !validEpoch(writeEpoch))) return null
        return bounded(
            JSONObject()
                .put(KEY_VERSION, FORMAT_VERSION)
                .put(KEY_KIND, KIND_ACTIVE)
                .put(KEY_RESUME, resumeOnOrdinaryLaunch)
                .put(KEY_WRITE_EPOCH, writeEpoch)
                .put(KEY_ACTIVE, encodeActivePayload(snapshot))
                .toString(),
        )
    }

    private fun encodePending(
        snapshot: StudyPendingAnswerSnapshot,
        fallbackActive: StudyActiveSessionSnapshot?,
        fallbackWriteEpoch: String?,
        resumeOnOrdinaryLaunch: Boolean,
    ): String? {
        if (!validPending(snapshot) ||
            (fallbackActive == null) != (fallbackWriteEpoch == null) ||
            (snapshot.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED && fallbackActive != null) ||
            (fallbackActive != null &&
                (!validActive(fallbackActive) ||
                    !validEpoch(fallbackWriteEpoch.orEmpty()) ||
                    !pendingMatchesActive(snapshot, fallbackActive)))
        ) {
            return null
        }
        val json = JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_KIND, KIND_PENDING)
            .put(KEY_RESUME, resumeOnOrdinaryLaunch)
            .put(KEY_PENDING, encodePendingPayload(snapshot))
        fallbackActive?.let {
            json.put(KEY_FALLBACK_ACTIVE, encodeActivePayload(it))
            json.put(KEY_FALLBACK_WRITE_EPOCH, fallbackWriteEpoch)
        }
        return bounded(json.toString())
    }

    private fun decode(raw: String): StoredStudyRecovery? {
        if (raw.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(raw)
            decodeVersioned(json, raw)
        }.getOrNull()
    }

    private fun decodeVersioned(json: JSONObject, raw: String): StoredStudyRecovery? =
        when (json.getInt(KEY_VERSION)) {
            LEGACY_FORMAT_VERSION -> decodeLegacyPending(json, raw)
            FORMAT_VERSION -> decodeCurrent(json, raw)
            else -> null
        }

    private fun decodeCurrent(json: JSONObject, raw: String): StoredStudyRecovery? =
        when (json.getString(KEY_KIND)) {
            KIND_ACTIVE -> decodeStoredActive(json, raw)
            KIND_PENDING -> decodeStoredPending(json, raw)
            else -> null
        }

    private fun decodeStoredActive(json: JSONObject, raw: String): StoredActiveStudyRecovery? {
        val snapshot = decodeActivePayload(json.getJSONObject(KEY_ACTIVE)) ?: return null
        val resume = json.getBoolean(KEY_RESUME)
        val epoch = json.optString(KEY_WRITE_EPOCH)
        if (resume && !validEpoch(epoch)) return null
        return StoredActiveStudyRecovery(snapshot, epoch, resume, raw)
    }

    private fun decodeStoredPending(json: JSONObject, raw: String): StoredPendingStudyRecovery? {
        val snapshot = decodePendingPayload(json.getJSONObject(KEY_PENDING)) ?: return null
        val fallback = json.optJSONObject(KEY_FALLBACK_ACTIVE)?.let(::decodeActivePayload)
        if (json.has(KEY_FALLBACK_ACTIVE) && fallback == null) return null
        val fallbackEpoch = json.optString(KEY_FALLBACK_WRITE_EPOCH)
            .takeIf { json.has(KEY_FALLBACK_WRITE_EPOCH) }
        if ((fallback == null) != (fallbackEpoch == null) ||
            (snapshot.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED && fallback != null) ||
            (fallbackEpoch != null && !validEpoch(fallbackEpoch))
        ) {
            return null
        }
        return StoredPendingStudyRecovery(
            snapshot,
            fallback,
            fallbackEpoch,
            json.getBoolean(KEY_RESUME),
            raw,
        )
    }

    private fun encodeActivePayload(snapshot: StudyActiveSessionSnapshot): JSONObject = JSONObject()
        .put(KEY_TOKEN, snapshot.sessionToken)
        .put(KEY_KANJI, snapshot.kanji)
        .put(KEY_ANSWER_SIGNATURE_DIGEST, snapshot.answerSignatureDigest)
        .put(KEY_SCHEDULER_REVISION, snapshot.schedulerRevision)
        .put(KEY_ROUTING_VERSION, snapshot.routingVersion)
        .put(KEY_TASK_TYPE, snapshot.taskType)
        .put(KEY_PROMPT_SOURCE, snapshot.promptSource.name)
        .put(KEY_SOURCE_SYNC_FINISHED_AT, snapshot.sourceSyncFinishedAtMillis)
        .also { json ->
            snapshot.similarChoiceSignatureDigest?.let {
                json.put(KEY_SIMILAR_CHOICE_SIGNATURE_DIGEST, it)
            }
        }
        .put(KEY_TYPED_DRAFT, snapshot.typedDraft)
        .put(KEY_REVEALED, snapshot.revealed)

    private fun decodeActivePayload(json: JSONObject): StudyActiveSessionSnapshot? = runCatching {
        StudyActiveSessionSnapshot(
            sessionToken = json.getString(KEY_TOKEN),
            kanji = json.getString(KEY_KANJI),
            answerSignatureDigest = json.getString(KEY_ANSWER_SIGNATURE_DIGEST),
            schedulerRevision = json.getLong(KEY_SCHEDULER_REVISION),
            routingVersion = json.getInt(KEY_ROUTING_VERSION),
            taskType = json.getString(KEY_TASK_TYPE),
            promptSource = StudyPromptSource.valueOf(json.getString(KEY_PROMPT_SOURCE)),
            sourceSyncFinishedAtMillis = json.getLong(KEY_SOURCE_SYNC_FINISHED_AT),
            similarChoiceSignatureDigest = json.optString(KEY_SIMILAR_CHOICE_SIGNATURE_DIGEST)
                .takeIf { json.has(KEY_SIMILAR_CHOICE_SIGNATURE_DIGEST) },
            typedDraft = json.optString(KEY_TYPED_DRAFT),
            revealed = json.optBoolean(KEY_REVEALED),
        ).takeIf(::validActive)
    }.getOrNull()

    private fun encodePendingPayload(snapshot: StudyPendingAnswerSnapshot): JSONObject {
        val json = JSONObject()
            .put(KEY_TOKEN, snapshot.feedback.sessionToken)
            .put(KEY_PHASE, snapshot.feedback.phase.name)
            .put(KEY_OUTCOME, snapshot.feedback.outcome?.name.orEmpty())
            .put(KEY_SELECTED_ANSWER, snapshot.feedback.selectedAnswer)
            .put(KEY_KANJI, snapshot.kanji)
            .put(KEY_TASK_TYPE, snapshot.taskType)
            .put(KEY_WRITING_REQUIRED, snapshot.writingRequired)
            .put(KEY_PROMPT, snapshot.prompt)
        snapshot.answerSignature?.let { json.put(KEY_ANSWER_SIGNATURE, it) }
        snapshot.schedulerRevision?.let { json.put(KEY_SCHEDULER_REVISION, it) }
        return json
    }

    private fun decodePendingPayload(json: JSONObject): StudyPendingAnswerSnapshot? = runCatching {
        val outcomeName = json.optString(KEY_OUTCOME)
        StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = json.getString(KEY_TOKEN),
                phase = StudyAnswerFeedbackPhase.valueOf(json.getString(KEY_PHASE)),
                outcome = outcomeName.takeIf { it.isNotBlank() }?.let(StudyAnswerOutcome::valueOf),
                selectedAnswer = json.optString(KEY_SELECTED_ANSWER),
            ),
            kanji = json.getString(KEY_KANJI),
            taskType = json.getString(KEY_TASK_TYPE),
            writingRequired = json.getBoolean(KEY_WRITING_REQUIRED),
            prompt = json.optString(KEY_PROMPT),
            answerSignature = json.optString(KEY_ANSWER_SIGNATURE).takeIf { json.has(KEY_ANSWER_SIGNATURE) },
            schedulerRevision = json.optLong(KEY_SCHEDULER_REVISION).takeIf { json.has(KEY_SCHEDULER_REVISION) },
        ).takeIf(::validPending)
    }.getOrNull()

    private fun decodeLegacyPending(json: JSONObject, raw: String): StoredPendingStudyRecovery? {
        val outcomeName = json.optString(KEY_OUTCOME)
        val snapshot = runCatching {
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = json.getString(KEY_TOKEN),
                    phase = StudyAnswerFeedbackPhase.valueOf(json.getString(KEY_PHASE)),
                    outcome = outcomeName.takeIf { it.isNotBlank() }?.let(StudyAnswerOutcome::valueOf),
                    selectedAnswer = json.optString(KEY_SELECTED_ANSWER),
                ),
                kanji = json.getString(KEY_KANJI),
                taskType = json.getString(KEY_TASK_TYPE),
                writingRequired = json.getBoolean(KEY_WRITING_REQUIRED),
                prompt = json.optString(KEY_PROMPT),
                answerSignature = null,
                schedulerRevision = null,
            ).takeIf(::validPending)
        }.getOrNull() ?: return null
        return StoredPendingStudyRecovery(
            snapshot,
            fallbackActive = null,
            fallbackWriteEpoch = null,
            resumeOnOrdinaryLaunch = true,
            raw = raw,
        )
    }

    private fun validActive(snapshot: StudyActiveSessionSnapshot): Boolean {
        val typing = snapshot.taskType in TYPING_TASK_TYPES
        val similarChoice = snapshot.taskType == StudyTaskTypes.SIMILAR_KANJI
        val revealable = snapshot.taskType in REVEALABLE_FLASHCARD_TASK_TYPES
        val validChoiceDigest = if (similarChoice) {
            snapshot.similarChoiceSignatureDigest?.let(::validSignatureDigest) == true
        } else {
            snapshot.similarChoiceSignatureDigest == null
        }
        return validText(snapshot.sessionToken, MAX_TOKEN_CHARS, allowBlank = false) &&
            validText(snapshot.kanji, MAX_KANJI_CHARS, allowBlank = false) &&
            validSignatureDigest(snapshot.answerSignatureDigest) &&
            snapshot.schedulerRevision >= 0L &&
            snapshot.routingVersion >= 1 &&
            snapshot.taskType in RESTORABLE_ACTIVE_TASK_TYPES &&
            snapshot.sourceSyncFinishedAtMillis >= 0L &&
            validChoiceDigest &&
            validText(snapshot.typedDraft, MAX_STUDY_TYPED_DRAFT_CHARS, allowBlank = true) &&
            (typing || snapshot.typedDraft.isEmpty()) &&
            (!snapshot.revealed || revealable)
    }

    private fun validPending(snapshot: StudyPendingAnswerSnapshot): Boolean {
        val identityComplete = (snapshot.answerSignature == null) == (snapshot.schedulerRevision == null)
        val continuedIdentityValid = snapshot.feedback.phase != StudyAnswerFeedbackPhase.CONTINUED ||
            (snapshot.taskType != MainActivityBase.TASK_REPAIR_WRITING &&
                snapshot.answerSignature?.let {
                    validText(it, MAX_SIGNATURE_CHARS, allowBlank = true)
                } == true &&
                snapshot.schedulerRevision != null)
        return validText(snapshot.feedback.sessionToken, MAX_TOKEN_CHARS, allowBlank = false) &&
            snapshot.feedback.phase in PENDING_PHASES &&
            snapshot.feedback.outcome != null &&
            validText(snapshot.feedback.selectedAnswer, MAX_SELECTED_ANSWER_CHARS, allowBlank = true) &&
            validText(snapshot.kanji, MAX_KANJI_CHARS, allowBlank = false) &&
            validText(snapshot.taskType, MAX_TASK_TYPE_CHARS, allowBlank = false) &&
            validText(snapshot.prompt, MAX_PROMPT_CHARS, allowBlank = true) &&
            identityComplete &&
            continuedIdentityValid &&
            (snapshot.answerSignature == null || validText(snapshot.answerSignature, MAX_SIGNATURE_CHARS, allowBlank = true)) &&
            (snapshot.schedulerRevision == null || snapshot.schedulerRevision >= 0L)
    }

    private fun sameActiveIdentity(
        left: StudyActiveSessionSnapshot,
        right: StudyActiveSessionSnapshot,
    ): Boolean = left.copy(typedDraft = right.typedDraft, revealed = right.revealed) == right

    private fun pendingMatchesActive(
        pending: StudyPendingAnswerSnapshot,
        active: StudyActiveSessionSnapshot,
    ): Boolean = pending.feedback.sessionToken == active.sessionToken &&
        pending.kanji == active.kanji &&
        pending.taskType == active.taskType &&
        !pending.writingRequired &&
        pending.schedulerRevision == active.schedulerRevision &&
        pending.answerSignature?.let(::studyAnswerSignatureDigest) == active.answerSignatureDigest

    private fun validPendingUpdate(
        current: StudyPendingAnswerSnapshot,
        updated: StudyPendingAnswerSnapshot,
    ): Boolean {
        val phaseTransition = current.feedback.phase == updated.feedback.phase ||
            (current.feedback.phase == StudyAnswerFeedbackPhase.SUBMITTING &&
                updated.feedback.phase == StudyAnswerFeedbackPhase.APPLIED)
        return phaseTransition &&
            current.copy(feedback = current.feedback.copy(phase = updated.feedback.phase)) == updated
    }

    private fun recoveryToken(recovery: StoredStudyRecovery): String = when (recovery) {
        is StoredActiveStudyRecovery -> recovery.snapshot.sessionToken
        is StoredPendingStudyRecovery -> recovery.snapshot.feedback.sessionToken
    }

    private fun validEpoch(value: String): Boolean = validText(value, MAX_EPOCH_CHARS, allowBlank = false)

    private fun validSignatureDigest(value: String): Boolean =
        value.length == SHA_256_HEX_CHARS && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun validText(value: String, maxChars: Int, allowBlank: Boolean): Boolean =
        value.length <= maxChars && (allowBlank || value.isNotBlank())

    private fun bounded(value: String): String? = value.takeIf { it.length <= MAX_ENCODED_CHARS }

    companion object {
        private val LOCK = Any()

        private const val LEGACY_FORMAT_VERSION = 1
        private const val FORMAT_VERSION = 2
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_FLUSH_SEQUENCE = "flush_sequence"
        private const val KEY_VERSION = "version"
        private const val KEY_KIND = "kind"
        private const val KEY_RESUME = "resume_on_ordinary_launch"
        private const val KEY_WRITE_EPOCH = "write_epoch"
        private const val KEY_ACTIVE = "active"
        private const val KEY_PENDING = "pending"
        private const val KEY_FALLBACK_ACTIVE = "fallback_active"
        private const val KEY_FALLBACK_WRITE_EPOCH = "fallback_write_epoch"
        private const val KIND_ACTIVE = "active"
        private const val KIND_PENDING = "pending_answer"
        private const val KEY_TOKEN = "token"
        private const val KEY_PHASE = "phase"
        private const val KEY_OUTCOME = "outcome"
        private const val KEY_SELECTED_ANSWER = "selected_answer"
        private const val KEY_KANJI = "kanji"
        private const val KEY_TASK_TYPE = "task_type"
        private const val KEY_WRITING_REQUIRED = "writing_required"
        private const val KEY_PROMPT = "prompt"
        private const val KEY_ANSWER_SIGNATURE = "answer_signature"
        private const val KEY_ANSWER_SIGNATURE_DIGEST = "answer_signature_digest"
        private const val KEY_SCHEDULER_REVISION = "scheduler_revision"
        private const val KEY_ROUTING_VERSION = "routing_version"
        private const val KEY_PROMPT_SOURCE = "prompt_source"
        private const val KEY_SOURCE_SYNC_FINISHED_AT = "source_sync_finished_at"
        private const val KEY_SIMILAR_CHOICE_SIGNATURE_DIGEST = "similar_choice_signature_digest"
        private const val KEY_TYPED_DRAFT = "typed_draft"
        private const val KEY_REVEALED = "revealed"

        private const val MAX_ENCODED_CHARS = 65_536
        private const val MAX_TOKEN_CHARS = 512
        private const val MAX_EPOCH_CHARS = 128
        private const val MAX_KANJI_CHARS = 32
        private const val MAX_SIGNATURE_CHARS = 16_384
        private const val SHA_256_HEX_CHARS = 64
        private const val MAX_TASK_TYPE_CHARS = 128
        private const val MAX_SELECTED_ANSWER_CHARS = 8_192
        private const val MAX_PROMPT_CHARS = 16_384

        private val TYPING_TASK_TYPES = setOf(
            StudyTaskTypes.TYPE_MEANING,
            StudyTaskTypes.TYPING_MEANING,
            StudyTaskTypes.TYPE_READING,
        )
        private val REVEALABLE_FLASHCARD_TASK_TYPES = setOf(
            StudyTaskTypes.KANJI_MEANING,
            StudyTaskTypes.FONT_MEANING,
            StudyTaskTypes.WORD_READING,
            StudyTaskTypes.SENTENCE_READING,
        )
        private val RESTORABLE_ACTIVE_TASK_TYPES =
            TYPING_TASK_TYPES + REVEALABLE_FLASHCARD_TASK_TYPES + StudyTaskTypes.SIMILAR_KANJI
        private val PENDING_PHASES = setOf(
            StudyAnswerFeedbackPhase.SUBMITTING,
            StudyAnswerFeedbackPhase.APPLIED,
            StudyAnswerFeedbackPhase.CONTINUED,
        )
    }
}

internal fun studyAnswerSignatureDigest(answerSignature: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(answerSignature.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}

/** Compatibility facade retained for focused tests and callers that only handle answered cards. */
class StudyPendingAnswerStore(preferences: SharedPreferences) {
    private val recoveryStore = StudySessionRecoveryStore(preferences)

    fun save(snapshot: StudyPendingAnswerSnapshot): Boolean =
        recoveryStore.replaceWithPending(snapshot) != null

    fun read(): StudyPendingAnswerSnapshot? = recoveryStore.readPending()?.snapshot

    fun clear(): Boolean = recoveryStore.clearPending()
}
