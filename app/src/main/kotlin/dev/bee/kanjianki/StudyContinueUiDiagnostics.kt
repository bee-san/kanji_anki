package dev.bee.kanjianki

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

internal enum class StudyContinueUiStage(val wireName: String) {
    MOUNTED("mounted"),
    STATE_CHANGED("state-changed"),
    CLICK_ENTRY("click-entry"),
    CLICK_COMPLETED("click-completed"),
    UNMOUNTED("unmounted"),
}

internal enum class StudyContinueUiOutcome(val wireName: String) {
    NONE("none"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    ERROR("error"),
}

internal data class StudyContinueUiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be at least left" }
        require(bottom >= top) { "bottom must be at least top" }
    }
}

/**
 * Stable identity for one explicit Continue action. It keeps raw route state out of diagnostic
 * events while allowing every event to sample the gate and accepted route at the same instant.
 */
internal class StudyContinueAction(
    internal val feedbackState: StudyAnswerFeedbackState,
    private val routeSnapshotProvider: () -> StudyRouteSnapshot,
    private val onContinue: () -> Boolean,
) {
    internal fun snapshot(bounds: StudyContinueUiBounds?): StudyContinueUiSnapshot {
        val feedback = feedbackState.snapshot()
        val route = routeSnapshotProvider()
        return StudyContinueUiSnapshot(
            tokenId = studyContinueTokenId(feedback.sessionToken),
            enabled = feedbackState.continueEnabled,
            feedbackPhase = feedback.phase,
            routePhase = route.phase,
            routeVersion = route.version.value,
            routeTokenMatches = route.sessionToken == feedback.sessionToken,
            bounds = bounds,
        )
    }

    internal fun continueStudy(): Boolean = onContinue()
}

/**
 * Privacy-safe value event. It cannot retain a session token, card, prompt, answer, or route
 * snapshot because those inputs are reduced before construction.
 */
internal class StudyContinueUiEvent private constructor(
    val mountId: Long,
    val stage: StudyContinueUiStage,
    val tokenId: String,
    val enabled: Boolean,
    val feedbackPhase: StudyAnswerFeedbackPhase,
    val routePhase: StudySessionPhase,
    val routeVersion: Long,
    val routeTokenMatches: Boolean,
    val bounds: StudyContinueUiBounds?,
    val outcome: StudyContinueUiOutcome,
) {
    fun format(): String = "study-continue-ui mount_id=$mountId event=${stage.wireName} " +
        "token_id=$tokenId enabled=$enabled feedback_phase=${feedbackPhase.name} " +
        "route_phase=${routePhase.name} route_version=$routeVersion " +
        "route_token_match=$routeTokenMatches bounds_px=${bounds.format()} outcome=${outcome.wireName}"

    companion object {
        internal fun from(
            mountId: Long,
            stage: StudyContinueUiStage,
            state: StudyContinueUiSnapshot,
            outcome: StudyContinueUiOutcome = StudyContinueUiOutcome.NONE,
        ): StudyContinueUiEvent = StudyContinueUiEvent(
            mountId = mountId,
            stage = stage,
            tokenId = state.tokenId,
            enabled = state.enabled,
            feedbackPhase = state.feedbackPhase,
            routePhase = state.routePhase,
            routeVersion = state.routeVersion,
            routeTokenMatches = state.routeTokenMatches,
            bounds = state.bounds,
            outcome = outcome,
        )
    }
}

internal data class StudyContinueUiSnapshot(
    val tokenId: String,
    val enabled: Boolean,
    val feedbackPhase: StudyAnswerFeedbackPhase,
    val routePhase: StudySessionPhase,
    val routeVersion: Long,
    val routeTokenMatches: Boolean,
    val bounds: StudyContinueUiBounds?,
)

internal fun interface StudyContinueUiObserver {
    fun onEvent(event: StudyContinueUiEvent)
}

internal object StudyContinueUiDiagnostics {
    private val nextMountId = AtomicLong(0L)

    @Volatile
    private var testObserver: StudyContinueUiObserver? = null

    fun newMountId(): Long = nextMountId.incrementAndGet()

    fun isRecording(): Boolean = testObserver != null || AppDebugLog.isCapturing()

    fun record(event: StudyContinueUiEvent) {
        testObserver?.let { observer ->
            runCatching { observer.onEvent(event) }
        }
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log(event.format())
        }
    }

    fun setObserverForTests(observer: StudyContinueUiObserver?) {
        testObserver = observer
    }

    fun resetForTests() {
        testObserver = null
        nextMountId.set(0L)
    }
}

/**
 * Per-mounted-button deduplicator. Compose can recompose or report identical layout coordinates
 * many times; only a changed diagnostic snapshot becomes a state-change event.
 */
internal class StudyContinueUiRecorder(
    private val action: StudyContinueAction,
    private val mountId: Long = StudyContinueUiDiagnostics.newMountId(),
) {
    private var mounted = false
    private var bounds: StudyContinueUiBounds? = null
    private var lastRecordedState: StudyContinueUiSnapshot? = null

    fun mounted() {
        if (mounted) return
        mounted = true
        recordState(StudyContinueUiStage.MOUNTED, deduplicate = false)
    }

    fun stateChanged() {
        if (!mounted) return
        recordState(StudyContinueUiStage.STATE_CHANGED, deduplicate = true)
    }

    fun boundsChanged(updated: StudyContinueUiBounds) {
        bounds = updated
        stateChanged()
    }

    fun clicked() {
        recordState(StudyContinueUiStage.CLICK_ENTRY, deduplicate = false)
        val outcome = try {
            if (action.continueStudy()) {
                StudyContinueUiOutcome.ACCEPTED
            } else {
                StudyContinueUiOutcome.REJECTED
            }
        } catch (error: Throwable) {
            recordState(
                stage = StudyContinueUiStage.CLICK_COMPLETED,
                outcome = StudyContinueUiOutcome.ERROR,
                deduplicate = false,
            )
            throw error
        }
        recordState(
            stage = StudyContinueUiStage.CLICK_COMPLETED,
            outcome = outcome,
            deduplicate = false,
        )
    }

    fun unmounted() {
        if (!mounted) return
        recordState(StudyContinueUiStage.UNMOUNTED, deduplicate = false)
        mounted = false
    }

    private fun recordState(
        stage: StudyContinueUiStage,
        outcome: StudyContinueUiOutcome = StudyContinueUiOutcome.NONE,
        deduplicate: Boolean,
    ) {
        if (!StudyContinueUiDiagnostics.isRecording()) return
        val state = runCatching { action.snapshot(bounds) }.getOrNull() ?: return
        if (deduplicate && state == lastRecordedState) return
        lastRecordedState = state
        StudyContinueUiDiagnostics.record(
            StudyContinueUiEvent.from(
                mountId = mountId,
                stage = stage,
                state = state,
                outcome = outcome,
            ),
        )
    }
}

internal class StudyContinueUiBinding(
    val modifier: Modifier,
    val onClick: () -> Unit,
)

@Composable
internal fun rememberStudyContinueUiBinding(action: StudyContinueAction): StudyContinueUiBinding {
    val recorder = remember(action) { StudyContinueUiRecorder(action) }
    DisposableEffect(recorder) {
        recorder.mounted()
        onDispose { recorder.unmounted() }
    }
    SideEffect { recorder.stateChanged() }
    return remember(recorder) {
        StudyContinueUiBinding(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                runCatching { coordinates.continueBounds() }
                    .getOrNull()
                    ?.let(recorder::boundsChanged)
            },
            onClick = recorder::clicked,
        )
    }
}

internal fun MainActivityStudy.studyContinueAction(
    feedbackState: StudyAnswerFeedbackState,
    onContinue: () -> Boolean,
): StudyContinueAction = StudyContinueAction(
    feedbackState = feedbackState,
    routeSnapshotProvider = studySessionViewModel::acceptedRouteSnapshot,
    onContinue = onContinue,
)

internal fun studyContinueTokenId(token: String): String =
    Integer.toUnsignedString(token.hashCode(), 16)

private fun StudyContinueUiBounds?.format(): String =
    this?.let { "$left,$top,$right,$bottom" } ?: "unavailable"

private fun LayoutCoordinates.continueBounds(): StudyContinueUiBounds? {
    if (!isAttached) return null
    val position = positionInWindow()
    if (!position.x.isFinite() || !position.y.isFinite()) return null
    return StudyContinueUiBounds(
        left = position.x.roundToInt(),
        top = position.y.roundToInt(),
        right = (position.x + size.width).roundToInt(),
        bottom = (position.y + size.height).roundToInt(),
    )
}
