package dev.bee.kanjianki;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.FlashcardGesturePolicy;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TypingAnswerMatcher;

import static dev.bee.kanjianki.MainActivityStudyFlashcardCompose.studyFlashcardActionBarView;

final class MainActivityStudyFlashcardInteraction {
    private final MainActivityStudy activity;

    MainActivityStudyFlashcardInteraction(MainActivityStudy activity) {
        this.activity = activity;
    }

    void buildFlashcardActionBar(boolean revealed) {
        if (activity.studyActionBar == null) {
            return;
        }
        activity.styleStudyActionBarShell();
        activity.studyActionBar.removeAllViews();
        activity.studyActionBar.setVisibility(View.VISIBLE);

        activity.studyActionBar.addView(studyFlashcardActionBarView(
                activity,
                revealed,
                this::revealFlashcardAnswer,
                () -> activity.submitReview(activity.RATING_AGAIN, false),
                () -> activity.submitReview(activity.RATING_GOOD, false)
        ));
    }

    void revealFlashcardAnswer() {
        if (activity.flashcardAnswerRevealed) {
            return;
        }
        if (StudyTaskCopy.isTypingMeaningTask(activity.activeSession)
                && TypingAnswerMatcher.matches(
                activity.currentDictionaryLookup(),
                activity.activeSession.item.kanji,
                activity.typingAnswerInput == null ? "" : activity.typingAnswerInput.getText().toString(),
                StudyTextCopy.collectionMeaningForSession(activity.activeSession))) {
            Toast.makeText(activity, StudyTextCopy.typingAnswerAcceptedToast(), Toast.LENGTH_SHORT).show();
            activity.submitReview(activity.RATING_GOOD, false);
            return;
        }
        activity.flashcardAnswerRevealed = true;
        if (activity.flashcardHeroPanel != null) {
            activity.flashcardHeroPanel.setVisibility(View.GONE);
        }
        expandFlashcardForAnswer();
        if (activity.studyAnswerPanel != null) {
            activity.studyAnswerPanel.setVisibility(View.VISIBLE);
        }
        buildFlashcardActionBar(true);
    }

    void expandFlashcardForAnswer() {
        if (activity.flashcardCard == null) {
            return;
        }
        int currentFullHeight = activity.flashcardCard.getHeight();
        if (currentFullHeight > 0) {
            activity.flashcardCard.setMinimumHeight(currentFullHeight);
        }
        ViewGroup.LayoutParams params = activity.flashcardCard.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams linearParams) {
            linearParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            linearParams.weight = 0f;
            activity.flashcardCard.setLayoutParams(linearParams);
            activity.flashcardCard.requestLayout();
        }
    }

    boolean handleFlashcardGesture(MotionEvent event) {
        if (activity.activeSession == null || activity.activeSession.writingRequired || activity.flashcardGestureArea == null) {
            activity.flashcardTouchTracking = false;
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (StudyTaskCopy.isTypingMeaningTask(activity.activeSession)
                        && activity.typingAnswerInput != null
                        && isTouchInsideView(activity.typingAnswerInput, event)) {
                    activity.flashcardTouchTracking = false;
                    return false;
                }
                activity.flashcardTouchTracking = isTouchInsideView(activity.flashcardGestureArea, event);
                if (activity.flashcardTouchTracking) {
                    activity.flashcardTouchStartX = event.getRawX();
                    activity.flashcardTouchStartY = event.getRawY();
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (!activity.flashcardTouchTracking) {
                    return false;
                }
                activity.flashcardTouchTracking = false;
                if (!isTouchInsideView(activity.flashcardGestureArea, event)) {
                    return false;
                }
                return handleFlashcardRelease(event);
            case MotionEvent.ACTION_CANCEL:
                activity.flashcardTouchTracking = false;
                return false;
            default:
                return false;
        }
    }

    boolean handleFlashcardRelease(MotionEvent event) {
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        FlashcardGesturePolicy.Decision decision = FlashcardGesturePolicy.release(
                activity.flashcardTouchStartX,
                activity.flashcardTouchStartY,
                event.getRawX(),
                event.getRawY(),
                touchSlop,
                activity.dp(72),
                activity.flashcardAnswerRevealed
        );
        switch (decision.action) {
            case REVEAL:
                revealFlashcardAnswer();
                return true;
            case REVIEW:
                activity.submitReview(decision.rating, false);
                return true;
            default:
                return false;
        }
    }

    boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null || !view.isShown()) {
            return false;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds)) {
            return false;
        }
        return bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }
}
