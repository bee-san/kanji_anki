package dev.bee.kanjianki

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import dev.bee.kanjianki.core.FlashcardGesturePolicy
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.TypingAnswerMatcher

internal class MainActivityStudyFlashcardInteraction(private val activity: MainActivityStudy) {
    fun buildFlashcardActionBar(revealed: Boolean) {
        val studyActionBar = activity.studyActionBar ?: return
        activity.styleStudyActionBarShell()
        studyActionBar.visibility = View.VISIBLE
        val existing = activity.flashcardActionBarState
        if (existing != null && studyActionBar.childCount > 0) {
            existing.revealed = revealed
            return
        }

        studyActionBar.removeAllViews()
        val state = FlashcardActionBarState(
            revealed,
            Runnable { revealFlashcardAnswer() },
            Runnable { activity.submitReview(MainActivityBase.RATING_AGAIN, false) },
            Runnable { activity.submitReview(MainActivityBase.RATING_GOOD, false) },
        )
        activity.flashcardActionBarState = state
        studyActionBar.addView(
            studyFlashcardActionBarView(
                activity,
                state,
            )
        )
    }

    fun revealFlashcardAnswer() {
        if (activity.flashcardAnswerRevealed) {
            return
        }
        val session = activity.activeSession
        if (session != null &&
            StudyTaskCopy.isTypingMeaningTask(session) &&
            TypingAnswerMatcher.matches(
                activity.currentDictionaryLookup(),
                session.item.kanji,
                activity.typingAnswerState?.text?.toString() ?: "",
                StudyTextCopy.collectionMeaningForSession(session)
            )
        ) {
            Toast.makeText(activity, StudyTextCopy.typingAnswerAcceptedToast(), Toast.LENGTH_SHORT).show()
            activity.submitReview(MainActivityBase.RATING_GOOD, false)
            return
        }
        activity.flashcardAnswerRevealed = true
        val revealState = activity.flashcardRevealState
        if (revealState != null) {
            revealState.reveal()
        } else {
            val heroPanel = activity.flashcardHeroPanel
            if (heroPanel != null) {
                heroPanel.visibility = View.GONE
            }
        }
        expandFlashcardForAnswer()
        val answerPanel = activity.studyAnswerPanel
        if (activity.flashcardRevealState == null && answerPanel != null) {
            answerPanel.visibility = View.VISIBLE
        }
        buildFlashcardActionBar(true)
    }

    fun expandFlashcardForAnswer() {
        val card = activity.flashcardCard ?: return
        val currentFullHeight = card.height
        if (currentFullHeight > 0) {
            card.minimumHeight = currentFullHeight
        }
        val params = card.layoutParams
        if (params is LinearLayout.LayoutParams) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.weight = 0f
            card.layoutParams = params
            card.requestLayout()
        }
    }

    fun handleFlashcardGesture(event: MotionEvent): Boolean {
        val session = activity.activeSession
        val gestureArea = activity.flashcardGestureArea
        if (session == null || session.writingRequired || gestureArea == null) {
            activity.flashcardTouchTracking = false
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val typingAnswerState = activity.typingAnswerState
                if (StudyTaskCopy.isTypingMeaningTask(session) &&
                    typingAnswerState != null &&
                    typingAnswerState.containsWindowPoint(event.x, event.y)
                ) {
                    activity.flashcardTouchTracking = false
                    return false
                }
                activity.flashcardTouchTracking = isTouchInsideView(gestureArea, event)
                if (activity.flashcardTouchTracking) {
                    activity.flashcardTouchStartX = event.rawX
                    activity.flashcardTouchStartY = event.rawY
                }
                false
            }

            MotionEvent.ACTION_UP -> {
                if (!activity.flashcardTouchTracking) {
                    return false
                }
                activity.flashcardTouchTracking = false
                if (!isTouchInsideView(gestureArea, event)) {
                    return false
                }
                handleFlashcardRelease(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                activity.flashcardTouchTracking = false
                false
            }

            else -> false
        }
    }

    fun handleFlashcardRelease(event: MotionEvent): Boolean {
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        val decision = FlashcardGesturePolicy.release(
            activity.flashcardTouchStartX,
            activity.flashcardTouchStartY,
            event.rawX,
            event.rawY,
            touchSlop,
            activity.dp(72),
            activity.flashcardAnswerRevealed
        )
        return when (decision.action) {
            FlashcardGesturePolicy.Decision.Action.REVEAL -> {
                revealFlashcardAnswer()
                true
            }

            FlashcardGesturePolicy.Decision.Action.REVIEW -> {
                activity.submitReview(decision.rating, false)
                true
            }

            else -> false
        }
    }

    fun isTouchInsideView(view: View?, event: MotionEvent): Boolean {
        if (view == null || !view.isShown) {
            return false
        }
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds)) {
            return false
        }
        return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }
}
