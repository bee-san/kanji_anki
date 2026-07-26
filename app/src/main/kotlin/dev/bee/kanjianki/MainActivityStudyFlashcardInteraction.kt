package dev.bee.kanjianki

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import dev.bee.kanjianki.core.FlashcardGesturePolicy
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.EvidenceSource
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.PresentationVariant
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.TypingAnswerMatcher
import dev.bee.kanjianki.core.TypedReadingPolicy

internal class MainActivityStudyFlashcardInteraction(private val activity: MainActivityStudy) {
    private var gestureSessionToken: String? = null
    private var gestureRecovery: StoredActiveStudyRecovery? = null

    fun buildFlashcardActionBar(revealed: Boolean) {
        val activeUiRecovery = activity.activeSession?.token?.let(activity::activeStudyUiRecovery)
        val sessionToken = activity.activeSession?.token
        val state = activity.flashcardActionBarState ?: FlashcardActionBarState(
            revealed,
            Runnable { revealFlashcardAnswer(sessionToken, activeUiRecovery) },
            Runnable {
                if (sessionToken != null && activity.matchesUngradedStudyRoute(sessionToken, activeUiRecovery)) {
                    activity.submitReview(MainActivityBase.RATING_AGAIN, false)
                }
            },
            Runnable {
                if (sessionToken != null && activity.matchesUngradedStudyRoute(sessionToken, activeUiRecovery)) {
                    activity.submitReview(MainActivityBase.RATING_GOOD, false)
                }
            },
        )
        activity.flashcardActionBarState = state
        state.revealed = revealed
    }

    fun revealFlashcardAnswer(
        expectedToken: String?,
        expectedRecovery: StoredActiveStudyRecovery?,
    ) {
        if (!canRevealFlashcard(expectedToken, expectedRecovery)) return
        activity.flashcardAnswerRevealed = true
        val session = activity.activeSession
        when {
            session != null && StudyTaskCopy.isTypingReadingTask(session) -> submitTypingReading(session)
            session != null && StudyTaskCopy.isTypingMeaningTask(session) -> submitTypingMeaning(session)
            else -> revealUngradedFlashcard(expectedRecovery)
        }
    }

    private fun canRevealFlashcard(
        expectedToken: String?,
        expectedRecovery: StoredActiveStudyRecovery?,
    ): Boolean =
        (expectedToken == null || activity.activeSession?.token == expectedToken) &&
            (expectedRecovery == null || activity.matchesActiveStudyRecovery(expectedRecovery)) &&
            !activity.flashcardAnswerRevealed

    private fun submitTypingReading(session: RecordsSchedulerModels.StudySession) {
        val typed = activity.typingAnswerState?.text.orEmpty()
        val expected = StudyTextCopy.collectionReadingForSession(session)
        val matched = TypedReadingPolicy.matches(typed, expected)
        Toast.makeText(
            activity,
            if (matched) StudyTextCopy.typingAnswerAcceptedToast() else StudyTextCopy.typingReadingIncorrectToast(),
            Toast.LENGTH_SHORT,
        ).show()
        completeTypingSubmission(
            activity.submitReview(
                if (matched) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN,
                false,
                answerEvidence = AnswerEvidence(
                    coreSkill = CoreSkill.CONTEXTUAL_READING,
                    failureKind = if (matched) null else FailureKind.WRONG_READING,
                    evidenceSource = EvidenceSource.OBJECTIVE_CHOICE,
                    presentationVariant = PresentationVariant.PLAIN_WORD,
                    selectedAnswer = typed,
                    correctAnswer = expected,
                    renderedExpression = StudyTextCopy.wordPrompt(session),
                    renderedReading = expected,
                ),
            ),
        )
    }

    private fun submitTypingMeaning(session: RecordsSchedulerModels.StudySession) {
        val matched = TypingAnswerMatcher.matches(
            activity.currentDictionaryLookup(),
            session.item?.kanji ?: "",
            activity.typingAnswerState?.text ?: "",
            StudyTextCopy.collectionMeaningForSession(session),
        )
        if (matched) {
            Toast.makeText(activity, StudyTextCopy.typingAnswerAcceptedToast(), Toast.LENGTH_SHORT).show()
        }
        completeTypingSubmission(
            activity.submitReview(
                if (matched) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN,
                false,
            ),
        )
    }

    private fun completeTypingSubmission(accepted: Boolean) {
        if (accepted) {
            showFlashcardAnswerSurface()
        } else {
            activity.flashcardAnswerRevealed = false
        }
    }

    private fun revealUngradedFlashcard(expectedRecovery: StoredActiveStudyRecovery?) {
        if (expectedRecovery != null && !activity.persistActiveStudyReveal(expectedRecovery)) {
            activity.flashcardAnswerRevealed = false
            return
        }
        showFlashcardAnswerSurface()
    }

    private fun showFlashcardAnswerSurface() {
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
        // The direct Compose card resizes from state; the legacy View expansion path is gone.
    }

    fun handleFlashcardGesture(event: MotionEvent): Boolean {
        val session = activity.activeSession
        if (activity.studyAnswerFeedbackState?.feedbackVisible == true) {
            activity.flashcardTouchTracking = false
            gestureSessionToken = null
            gestureRecovery = null
            return false
        }
        if (session == null || session.writingRequired || activity.flashcardGestureBounds == null) {
            activity.flashcardTouchTracking = false
            gestureSessionToken = null
            gestureRecovery = null
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleFlashcardTouchDown(session, event)

            MotionEvent.ACTION_MOVE -> {
                if (activity.flashcardTouchTracking && activity.flashcardAnswerRevealed) {
                    activity.flashcardSwipeFeedback?.update(event.rawX - activity.flashcardTouchStartX)
                }
                false
            }

            MotionEvent.ACTION_UP -> {
                if (!activity.flashcardTouchTracking) {
                    gestureSessionToken = null
                    gestureRecovery = null
                    return false
                }
                activity.flashcardTouchTracking = false
                handleFlashcardRelease(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                activity.flashcardTouchTracking = false
                gestureSessionToken = null
                gestureRecovery = null
                activity.flashcardSwipeFeedback?.settleBack()
                false
            }

            else -> false
        }
    }

    private fun handleFlashcardTouchDown(
        session: RecordsSchedulerModels.StudySession,
        event: MotionEvent,
    ): Boolean {
        gestureSessionToken = null
        gestureRecovery = null
        val typingAnswerState = activity.typingAnswerState
        if ((StudyTaskCopy.isTypingMeaningTask(session) || StudyTaskCopy.isTypingReadingTask(session)) &&
            typingAnswerState != null &&
            typingAnswerState.containsWindowPoint(event.x, event.y)
        ) {
            activity.flashcardTouchTracking = false
            return false
        }
        val insideFlashcard = isTouchInsideFlashcard(event)
        activity.flashcardTouchTracking = insideFlashcard &&
            (activity.flashcardSwipeFeedback?.beginDrag() != false)
        if (activity.flashcardTouchTracking) {
            gestureSessionToken = session.token
            gestureRecovery = activity.activeStudyUiRecovery(session.token)
            activity.flashcardTouchStartX = event.rawX
            activity.flashcardTouchStartY = event.rawY
        } else {
            gestureSessionToken = null
            gestureRecovery = null
        }
        return false
    }

    fun handleFlashcardRelease(event: MotionEvent): Boolean {
        val expectedToken = gestureSessionToken
        val expectedRecovery = gestureRecovery
        gestureSessionToken = null
        gestureRecovery = null
        if (expectedToken != null && activity.activeSession?.token != expectedToken) {
            activity.flashcardSwipeFeedback?.settleBack()
            return false
        }
        if (expectedRecovery != null && !activity.matchesActiveStudyRecovery(expectedRecovery)) {
            activity.flashcardSwipeFeedback?.settleBack()
            return false
        }
        val session = activity.activeSession
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
                activity.flashcardSwipeFeedback?.settleBack()
                revealFlashcardAnswer(expectedToken, expectedRecovery)
                true
            }

            FlashcardGesturePolicy.Decision.Action.REVIEW -> {
                logReviewSwipeGesture(
                    source = "card",
                    rating = decision.rating,
                    durationMs = (event.eventTime - event.downTime).coerceAtLeast(0L),
                )
                if (decision.rating == MainActivityBase.RATING_AGAIN &&
                    session != null &&
                    requiresRecognitionFailureCause(session)
                ) {
                    activity.flashcardSwipeFeedback?.settleBack()
                    activity.recognitionFailureCauseState?.show("card")
                } else {
                    submitReviewWithSwipeFeedback(activity.flashcardSwipeFeedback) {
                        activity.submitReview(
                            rating = decision.rating,
                            override = false,
                            interactionSource = "card",
                        )
                    }
                }
                true
            }

            else -> {
                activity.flashcardSwipeFeedback?.settleBack()
                false
            }
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

    private fun isTouchInsideFlashcard(event: MotionEvent): Boolean {
        val bounds = activity.flashcardGestureBounds ?: return false
        return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun requiresRecognitionFailureCause(session: RecordsSchedulerModels.StudySession): Boolean {
        return session.item?.phase == RecordsBase.SchedulerPhase.REVIEW &&
            (session.taskType == StudyTaskTypes.KANJI_MEANING || session.taskType == StudyTaskTypes.FONT_MEANING)
    }
}

internal fun logReviewSwipeGesture(source: String, rating: String, durationMs: Long) {
    if (!AppDebugLog.isCapturing()) {
        return
    }
    AppDebugLog.log(
        "gesture source=$source rating=$rating duration_ms=$durationMs",
    )
}
