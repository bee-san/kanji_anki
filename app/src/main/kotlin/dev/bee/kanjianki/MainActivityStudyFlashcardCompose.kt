@file:JvmName("MainActivityStudyFlashcardCompose")

package dev.bee.kanjianki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.FlashcardGesturePolicy
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import dev.bee.kanjianki.core.StudyTextCopy
import kotlin.math.roundToInt

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

    /** -1..1 fraction of the swipe threshold; positive means pass, negative means fail. */
    val progress: Float
        get() = if (thresholdPx <= 0f) 0f else (dragOffsetX / thresholdPx).coerceIn(-1f, 1f)

    fun update(offsetX: Float) {
        dragOffsetX = offsetX
    }

    fun reset() {
        dragOffsetX = 0f
    }
}

/**
 * One-shot enter transition used for each new study card. Every card render is a fresh
 * composition, so a self-starting transition gives a directional slide+fade between cards.
 */
@Composable
internal fun StudyCardEnterTransition(content: @Composable () -> Unit) {
    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
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

internal const val STUDY_CARD_ENTER_FADE_MILLIS = 90
internal const val STUDY_CARD_ENTER_SLIDE_MILLIS = 120
private const val STUDY_CARD_ENTER_DISTANCE_DIVISOR = 6

@Composable
fun StudyFlashcardActionBar(
    revealed: Boolean,
    onReveal: () -> Unit,
    onFail: () -> Unit,
    onPass: () -> Unit,
    undoMessage: String? = null,
    onUndo: (() -> Unit)? = null,
    swipeFeedback: StudySwipeFeedbackState? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (revealed || undoMessage != null) {
            StudyUndoSlot(undoMessage = undoMessage, onUndo = onUndo)
        }
        if (!revealed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StudyRevealButton(onReveal = onReveal)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .revealedReviewSwipeGestures(onFail = onFail, onPass = onPass, swipeFeedback = swipeFeedback),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudyAgainButton(
                    onClick = onFail,
                    modifier = Modifier.weight(1f)
                )
                StudyGoodButton(
                    onClick = onPass,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Modifier.revealedReviewSwipeGestures(
    onFail: () -> Unit,
    onPass: () -> Unit,
    swipeFeedback: StudySwipeFeedbackState? = null,
): Modifier {
    val touchSlop = LocalViewConfiguration.current.touchSlop.roundToInt()
    val minimumSwipeDistance = with(LocalDensity.current) { 72.dp.toPx().roundToInt() }
    swipeFeedback?.thresholdPx = minimumSwipeDistance.toFloat()
    return pointerInput(onFail, onPass, touchSlop, minimumSwipeDistance, swipeFeedback) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val gestureStartedAtNanos = System.nanoTime()
            var endPosition = down.position
            var consumingReviewSwipe = false
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { change ->
                    endPosition = change.position
                    val dx = endPosition.x - down.position.x
                    val dy = endPosition.y - down.position.y
                    if (!consumingReviewSwipe && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        consumingReviewSwipe = true
                    }
                    if (consumingReviewSwipe) {
                        change.consume()
                        swipeFeedback?.update(dx)
                    }
                }
            } while (event.changes.any { it.pressed })
            swipeFeedback?.reset()

            val rating = FlashcardGesturePolicy.release(
                down.position.x,
                down.position.y,
                endPosition.x,
                endPosition.y,
                touchSlop,
                minimumSwipeDistance,
                true,
            ).rating
            if (rating.isNotEmpty()) {
                val gestureDurationMs = ((System.nanoTime() - gestureStartedAtNanos) / 1_000_000L)
                    .coerceAtLeast(0L)
                logReviewSwipeGesture(
                    source = "action-bar",
                    rating = rating,
                    durationMs = gestureDurationMs,
                )
            }
            when (rating) {
                StudyRatings.AGAIN -> onFail()
                StudyRatings.GOOD -> onPass()
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
