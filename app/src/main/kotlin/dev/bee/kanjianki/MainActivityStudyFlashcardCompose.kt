@file:JvmName("MainActivityStudyFlashcardCompose")

package dev.bee.kanjianki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.FlashcardGesturePolicy
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import dev.bee.kanjianki.core.StudyTextCopy
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal const val STUDY_MNEMONIC_EDITOR_ACTION_TEST_TAG = "study-mnemonic-editor-action"

internal class FlashcardActionBarState(
    revealed: Boolean,
    val onReveal: Runnable,
    val onFail: Runnable,
    val onPass: Runnable,
) {
    var revealed by mutableStateOf(revealed)
}

/**
 * Shared drag-feedback state for the revealed Fail/Pass swipe gesture. The gesture
 * (on the action bar or on the card itself) reports horizontal drag distance here and
 * the flashcard renders a translation plus a pass/fail tint from it.
 */
class StudySwipeFeedbackState {
    var thresholdPx by mutableFloatStateOf(0f)
    var dragOffsetX by mutableFloatStateOf(0f)
        private set
    internal var cardWidthPx by mutableFloatStateOf(0f)
        private set
    internal var releaseRequest by mutableStateOf(StudySwipeReleaseRequest())
        private set
    internal var committed by mutableStateOf(false)
        private set

    private var nextReleaseSequence = 0L

    /** -1..1 fraction of the swipe threshold; positive means pass, negative means fail. */
    val progress: Float
        get() = if (thresholdPx <= 0f) 0f else (dragOffsetX / thresholdPx).coerceIn(-1f, 1f)

    fun update(offsetX: Float) {
        if (committed) {
            return
        }
        dragOffsetX = offsetX
    }

    /**
     * Starts a new finger-owned drag. A new gesture cancels an in-flight
     * settle-back spring so the old animation cannot fight the new pointer.
     * A committed review remains locked until its route advances or the review
     * pipeline explicitly rejects/releases it.
     */
    fun beginDrag(): Boolean {
        if (committed) {
            return false
        }
        if (releaseRequest.kind == StudySwipeReleaseKind.SETTLE_BACK) {
            dragOffsetX = 0f
            releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, StudySwipeReleaseKind.IDLE)
        }
        return true
    }

    /** Keep an accepted swipe moving in its rating direction until this card leaves. */
    fun commit(rating: String): Boolean {
        if (committed) {
            return false
        }
        val kind = when (rating) {
            StudyRatings.AGAIN -> StudySwipeReleaseKind.COMMIT_FAIL
            StudyRatings.GOOD -> StudySwipeReleaseKind.COMMIT_PASS
            else -> {
                settleBack()
                return false
            }
        }
        committed = true
        releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, kind)
        return true
    }

    /** Return a just-committed card when its review was not actually accepted. */
    fun cancelCommit() {
        if (!committed) {
            return
        }
        committed = false
        if (abs(dragOffsetX) < 0.5f) {
            dragOffsetX = 0f
            releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, StudySwipeReleaseKind.IDLE)
        } else {
            releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, StudySwipeReleaseKind.SETTLE_BACK)
        }
    }

    /** Animate an unaccepted swipe back instead of snapping it to the centre. */
    fun settleBack() {
        if (committed) {
            return
        }
        if (abs(dragOffsetX) < 0.5f) {
            dragOffsetX = 0f
            return
        }
        releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, StudySwipeReleaseKind.SETTLE_BACK)
    }

    fun reset() {
        committed = false
        dragOffsetX = 0f
        releaseRequest = StudySwipeReleaseRequest(++nextReleaseSequence, StudySwipeReleaseKind.IDLE)
    }

    internal fun updateCardWidth(widthPx: Float) {
        cardWidthPx = widthPx.coerceAtLeast(0f)
    }

    internal fun updateFromReleaseAnimation(sequence: Long, offsetX: Float) {
        if (releaseRequest.sequence == sequence) {
            dragOffsetX = offsetX
        }
    }

    internal fun finishReleaseAnimation(sequence: Long) {
        if (releaseRequest.sequence != sequence) {
            return
        }
        if (releaseRequest.kind == StudySwipeReleaseKind.SETTLE_BACK) {
            dragOffsetX = 0f
        }
        releaseRequest = StudySwipeReleaseRequest(sequence, StudySwipeReleaseKind.IDLE)
    }
}

internal enum class StudySwipeReleaseKind {
    IDLE,
    SETTLE_BACK,
    COMMIT_FAIL,
    COMMIT_PASS,
}

internal data class StudySwipeReleaseRequest(
    val sequence: Long = 0L,
    val kind: StudySwipeReleaseKind = StudySwipeReleaseKind.IDLE,
)

/** Drives only release motion; direct drag updates remain one-to-one with the finger. */
@Composable
internal fun StudySwipeReleaseEffect(swipeFeedback: StudySwipeFeedbackState?) {
    if (swipeFeedback == null) {
        return
    }
    val request = swipeFeedback.releaseRequest
    val cardWidthPx = swipeFeedback.cardWidthPx
    LaunchedEffect(swipeFeedback, request.sequence, request.kind, cardWidthPx) {
        if (request.kind == StudySwipeReleaseKind.IDLE) {
            return@LaunchedEffect
        }
        val animation = Animatable(swipeFeedback.dragOffsetX)
        when (request.kind) {
            StudySwipeReleaseKind.SETTLE_BACK -> animation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            ) {
                swipeFeedback.updateFromReleaseAnimation(request.sequence, value)
            }

            StudySwipeReleaseKind.COMMIT_FAIL,
            StudySwipeReleaseKind.COMMIT_PASS,
            -> {
                val direction = if (request.kind == StudySwipeReleaseKind.COMMIT_PASS) 1f else -1f
                val offscreenDistance = max(
                    cardWidthPx * STUDY_CARD_SWIPE_OFFSCREEN_WIDTH_MULTIPLIER,
                    swipeFeedback.thresholdPx * 1.5f,
                )
                animation.animateTo(
                    targetValue = direction * offscreenDistance,
                    animationSpec = tween(durationMillis = STUDY_CARD_SWIPE_COMMIT_MILLIS),
                ) {
                    swipeFeedback.updateFromReleaseAnimation(request.sequence, value)
                }
            }

            StudySwipeReleaseKind.IDLE -> Unit
        }
        swipeFeedback.finishReleaseAnimation(request.sequence)
    }
}

/**
 * One-shot enter transition used for each new study card. Every card render is a fresh
 * composition, so a self-starting transition gives a directional slide+fade between cards.
 */
@Composable
internal fun StudyCardEnterTransition(
    cardToken: String? = null,
    content: @Composable () -> Unit,
) {
    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(cardToken, enterState) {
        if (cardToken.isNullOrBlank()) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        // withFrameNanos confirms that Compose scheduled this composition on a
        // frame; it is not a post-draw signal, so diagnostics name it precisely.
        StudyCardFrameDiagnostics.onFrameScheduled(cardToken)
        snapshotFlow { enterState.isIdle && enterState.currentState }.first { it }
        StudyCardFrameDiagnostics.onTransitionStateComplete(cardToken)
    }
    AnimatedVisibility(
        visibleState = enterState,
        enter = fadeIn(animationSpec = tween(durationMillis = STUDY_CARD_ENTER_FADE_MILLIS)) +
            slideInHorizontally(animationSpec = tween(durationMillis = STUDY_CARD_ENTER_SLIDE_MILLIS)) {
                fullWidth -> fullWidth / STUDY_CARD_ENTER_DISTANCE_DIVISOR
            },
    ) {
        content()
    }
}

internal const val STUDY_CARD_ENTER_FADE_MILLIS = 60
internal const val STUDY_CARD_ENTER_SLIDE_MILLIS = 90
internal const val STUDY_CARD_SWIPE_COMMIT_MILLIS = 72
private const val STUDY_CARD_ENTER_DISTANCE_DIVISOR = 6
private const val STUDY_CARD_SWIPE_OFFSCREEN_WIDTH_MULTIPLIER = 1.08f

/**
 * Couples card drag feedback to the review gate without dismissing the card.
 * Every accepted or rejected attempt settles back to centre so the learner can
 * read the persistent result before choosing Continue.
 */
internal fun submitReviewWithSwipeFeedback(
    swipeFeedback: StudySwipeFeedbackState?,
    submit: () -> Boolean,
): Boolean {
    swipeFeedback?.settleBack()
    return submit()
}

private fun submitFlashcardReview(
    onReview: ((source: String, rating: String) -> Boolean)?,
    onFail: () -> Unit,
    onPass: () -> Unit,
    source: String,
    rating: String,
): Boolean {
    if (onReview != null) {
        return onReview(source, rating)
    }
    when (rating) {
        StudyRatings.AGAIN -> onFail()
        StudyRatings.GOOD -> onPass()
    }
    return true
}

@Composable
fun StudyFlashcardActionBar(
    revealed: Boolean,
    onReveal: () -> Unit,
    onFail: () -> Unit,
    onPass: () -> Unit,
    undoMessage: String? = null,
    onUndo: (() -> Unit)? = null,
    swipeFeedback: StudySwipeFeedbackState? = null,
    onReview: ((source: String, rating: String) -> Boolean)? = null,
    feedbackState: StudyAnswerFeedbackState? = null,
    mnemonicNote: BrowseMnemonicNoteModel? = null,
    onContinue: () -> Unit = {},
) {
    val submitReview: (String, String) -> Boolean = { source, rating ->
        submitFlashcardReview(onReview, onFail, onPass, source, rating)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (revealed || undoMessage != null) {
            StudyUndoSlot(undoMessage = undoMessage, onUndo = onUndo)
        }
        if (feedbackState?.feedbackVisible == true) {
            mnemonicNote?.let { StudyMnemonicNoteAction(it) }
            StudyFlashcardFeedbackActions(feedbackState, onContinue)
        } else if (!revealed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StudyRevealButton(onReveal = onReveal)
            }
        } else {
            StudyFlashcardReviewActions(swipeFeedback, submitReview)
        }
    }
}

@Composable
private fun StudyMnemonicNoteAction(model: BrowseMnemonicNoteModel) {
    var editorVisible by remember(model.initialNote) { mutableStateOf(false) }
    var latestNote by remember(model.initialNote) { mutableStateOf(model.initialNote) }
    OutlinedButton(
        onClick = { editorVisible = true },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_MNEMONIC_EDITOR_ACTION_TEST_TAG),
    ) {
        Text(model.title)
    }
    if (editorVisible) {
        AlertDialog(
            onDismissRequest = { editorVisible = false },
            confirmButton = {},
            text = {
                BrowseMnemonicNoteEditor(
                    model.copy(
                        initialNote = latestNote,
                        onSave = { note ->
                            latestNote = note
                            model.onSave(note)
                            editorVisible = false
                        },
                    ),
                )
            },
        )
    }
}

@Composable
private fun StudyFlashcardFeedbackActions(
    feedbackState: StudyAnswerFeedbackState,
    onContinue: () -> Unit,
) {
    val correct = feedbackState.outcome == StudyAnswerOutcome.CORRECT
    MeaningChoiceResultActionBar(
        status = if (correct) StudyTextCopy.answerCorrectFeedback() else StudyTextCopy.answerIncorrectFeedback(),
        statusColor = if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
        actionTone = if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
        continueEnabled = feedbackState.continueEnabled,
        onNext = onContinue,
    )
}

@Composable
private fun StudyFlashcardReviewActions(
    swipeFeedback: StudySwipeFeedbackState?,
    submitReview: (source: String, rating: String) -> Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .revealedReviewSwipeGestures(
                swipeFeedback = swipeFeedback,
                submitReview = submitReview,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudyAgainButton(
            onClick = {
                submitReviewWithSwipeFeedback(swipeFeedback) {
                    submitReview("button", StudyRatings.AGAIN)
                }
            },
            modifier = Modifier.weight(1f)
        )
        StudyGoodButton(
            onClick = {
                submitReviewWithSwipeFeedback(swipeFeedback) {
                    submitReview("button", StudyRatings.GOOD)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Modifier.revealedReviewSwipeGestures(
    swipeFeedback: StudySwipeFeedbackState? = null,
    submitReview: (source: String, rating: String) -> Boolean,
): Modifier {
    val touchSlop = LocalViewConfiguration.current.touchSlop.roundToInt()
    val minimumSwipeDistance = with(LocalDensity.current) { 72.dp.toPx().roundToInt() }
    if (swipeFeedback != null) {
        // Snapshot state writes are not allowed during composition; publish the
        // threshold after composition commits. The gesture below reads the local
        // minimumSwipeDistance directly, so it never depends on this state.
        SideEffect {
            swipeFeedback.thresholdPx = minimumSwipeDistance.toFloat()
        }
    }
    return pointerInput(touchSlop, minimumSwipeDistance, swipeFeedback, submitReview) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val dragAccepted = swipeFeedback?.beginDrag() != false
            val gestureStartedAtNanos = System.nanoTime()
            var endPosition = down.position
            var consumingReviewSwipe = false
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { change ->
                    endPosition = change.position
                    val dx = endPosition.x - down.position.x
                    val dy = endPosition.y - down.position.y
                    if (dragAccepted && !consumingReviewSwipe &&
                        kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    ) {
                        consumingReviewSwipe = true
                    }
                    if (consumingReviewSwipe) {
                        change.consume()
                        swipeFeedback?.update(dx)
                    }
                }
            } while (event.changes.any { it.pressed })
            val rating = if (dragAccepted) {
                FlashcardGesturePolicy.release(
                    down.position.x,
                    down.position.y,
                    endPosition.x,
                    endPosition.y,
                    touchSlop,
                    minimumSwipeDistance,
                    true,
                ).rating
            } else {
                ""
            }
            if (rating.isNotEmpty()) {
                val gestureDurationMs = ((System.nanoTime() - gestureStartedAtNanos) / 1_000_000L)
                    .coerceAtLeast(0L)
                logReviewSwipeGesture(
                    source = "action-bar",
                    rating = rating,
                    durationMs = gestureDurationMs,
                )
                submitReviewWithSwipeFeedback(swipeFeedback) {
                    submitReview("action-bar", rating)
                }
            } else {
                swipeFeedback?.settleBack()
            }
        }
    }
}

@Composable
private fun StudyRevealButton(onReveal: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    StudyPrimaryActionButton(
        label = StudyReviewButtonCopy.revealLabel(),
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onReveal()
        },
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_eye_24),
            contentDescription = null,
            tint = KaniTheme.colors.onPrimary
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun StudyAgainButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    StudyPrimaryActionButton(
        StudyReviewButtonCopy.againLabel(),
        {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            onClick()
        },
        modifier.semantics { contentDescription = StudyReviewButtonCopy.againContentDescription() },
        tone = StudyActionTone.FAIL,
    )
}

@Composable
private fun StudyGoodButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    StudyPrimaryActionButton(
        StudyReviewButtonCopy.goodLabel(),
        {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier.semantics { contentDescription = StudyReviewButtonCopy.goodContentDescription() },
        tone = StudyActionTone.PASS,
    )
}
