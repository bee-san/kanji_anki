@file:JvmName("MainActivityStudyFlashcardCompose")

package dev.bee.kanjianki

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
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun StudyFlashcardActionBar(
    revealed: Boolean,
    onReveal: () -> Unit,
    onFail: () -> Unit,
    onPass: () -> Unit,
    undoMessage: String? = null,
    onUndo: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (undoMessage != null && onUndo != null) {
            StudyUndoBanner(
                undoMessage = undoMessage,
                onUndo = onUndo,
            )
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
                    .revealedReviewSwipeGestures(onFail = onFail, onPass = onPass),
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
private fun Modifier.revealedReviewSwipeGestures(onFail: () -> Unit, onPass: () -> Unit): Modifier {
    val touchSlop = LocalViewConfiguration.current.touchSlop.roundToInt()
    val minimumSwipeDistance = with(LocalDensity.current) { 72.dp.toPx().roundToInt() }
    return pointerInput(onFail, onPass, touchSlop, minimumSwipeDistance) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
                    }
                }
            } while (event.changes.any { it.pressed })

            when (
                FlashcardGesturePolicy.release(
                    down.position.x,
                    down.position.y,
                    endPosition.x,
                    endPosition.y,
                    touchSlop,
                    minimumSwipeDistance,
                    true,
                ).rating
            ) {
                StudyRatings.AGAIN -> onFail()
                StudyRatings.GOOD -> onPass()
            }
        }
    }
}

@Composable
private fun StudyRevealButton(onReveal: () -> Unit) {
    StudyPrimaryActionButton(
        label = StudyReviewButtonCopy.revealLabel(),
        onClick = onReveal,
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
    StudySecondaryActionButton(
        StudyReviewButtonCopy.againLabel(),
        {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            onClick()
        },
        modifier.semantics { contentDescription = StudyReviewButtonCopy.againContentDescription() }
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
        modifier.semantics { contentDescription = StudyReviewButtonCopy.goodContentDescription() }
    )
}
