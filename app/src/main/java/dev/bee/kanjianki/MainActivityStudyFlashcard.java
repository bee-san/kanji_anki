package dev.bee.kanjianki;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.FlashcardGesturePolicy;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TypingAnswerMatcher;

import java.util.List;

final class MainActivityStudyFlashcard {
    private final MainActivityStudy activity;

    MainActivityStudyFlashcard(MainActivityStudy activity) {
        this.activity = activity;
    }

    void renderFlashcardSession(RecordsSchedulerModels.StudySession session) {
        activity.resetFlashcardSession();

        LinearLayout card = recognitionHeroCard(session);
        activity.flashcardCard = card;
        activity.flashcardGestureArea = card;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, 0, 0, activity.dp(14));
        activity.content.addView(card, cardLp);
        buildFlashcardActionBar(false);
    }

    LinearLayout recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(18), activity.dp(18), activity.dp(18), activity.dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(activity.panel(Color.WHITE, Color.TRANSPARENT, activity.dp(32)));
        card.setElevation(activity.dp(8));
        card.setClickable(true);
        card.setFocusable(true);

        card.addView(recognitionPill(StudyTaskCopy.studyModeLabel(session)));

        TextView title = activity.text(StudyTaskCopy.flashcardTitle(session), 21, activity.STUDY_HERO_PLUM, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, activity.dp(14), 0, 0);
        card.addView(title, titleLp);

        TextView question = activity.text(StudyTextCopy.heroQuestion(session), 27, activity.STUDY_HERO_PLUM, true);
        question.setGravity(Gravity.CENTER);
        question.setIncludeFontPadding(false);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(-1, -2);
        questionLp.setMargins(0, activity.dp(8), 0, 0);
        card.addView(question, questionLp);

        TextView hiddenHint = activity.text("Answer hidden until reveal", 14, activity.STUDY_HERO_MUTED, false);
        hiddenHint.setGravity(Gravity.CENTER);
        hiddenHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, activity.dp(6), 0, 0);
        card.addView(hiddenHint, hintLp);
        activity.addStudyReasonLine(card, session);

        activity.flashcardHeroPanel = heroKanjiPanel(session);
        card.addView(activity.flashcardHeroPanel);

        if (StudyTaskCopy.isTypingMeaningTask(session)) {
            TextView label = activity.text(activity.LABEL_MEANING, 15, activity.STUDY_HERO_MUTED, true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
            labelLp.setMargins(0, activity.dp(14), 0, activity.dp(8));
            card.addView(label, labelLp);
            card.addView(typingAnswerField());
        }

        activity.studyAnswerPanel = flashcardAnswerPanel(session);
        activity.studyAnswerPanel.setVisibility(View.GONE);
        card.addView(activity.studyAnswerPanel);

        return card;
    }

    View recognitionPill(String label) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(activity.dp(18), 0, activity.dp(18), 0);
        pill.setMinimumHeight(activity.dp(44));
        pill.setBackground(activity.panel(Color.rgb(253, 239, 246), Color.TRANSPARENT, activity.dp(24)));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_eye_24);
        icon.setColorFilter(activity.STUDY_HERO_PINK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(22), activity.dp(22));
        iconLp.setMargins(0, 0, activity.dp(10), 0);
        pill.addView(icon, iconLp);

        TextView text = activity.text(label, 18, activity.STUDY_HERO_PINK, true);
        text.setIncludeFontPadding(false);
        pill.addView(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, activity.dp(44));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        pill.setLayoutParams(lp);
        return pill;
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        FrameLayout panel = new FrameLayout(activity);
        panel.setBackground(activity.panel(activity.STUDY_HERO_PANEL, activity.STUDY_BORDER, activity.dp(28)));
        panel.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));

        TextView glyph = activity.text(
                StudyTaskCopy.isWordReadingTask(session) ? StudyTextCopy.wordPrompt(session) : session.item.kanji,
                StudyTaskCopy.isWordReadingTask(session) ? 44 : 116,
                activity.STUDY_HERO_PLUM,
                true
        );
        if (StudyTaskCopy.isFontRecognitionTask(session)) {
            glyph.setTypeface(randomFontVariantTypeface(), Typeface.BOLD);
        } else {
            glyph.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        glyph.setGravity(Gravity.CENTER);
        glyph.setIncludeFontPadding(false);
        panel.addView(glyph, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(210));
        lp.setMargins(0, activity.dp(16), 0, 0);
        panel.setLayoutParams(lp);
        return panel;
    }

    Typeface randomFontVariantTypeface() {
        return StudyFontVariants.random(activity);
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        LinearLayout box = activity.softInsetPanel();
        box.addView(activity.text("Answer", 19, activity.STUDY_PLUM, true));
        box.addView(studyAnswerDetailsRow(session, 76));
        return box;
    }

    LinearLayout studyAnswerDetailsRow(RecordsSchedulerModels.StudySession session, int glyphSize) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = activity.text(session.item.kanji, glyphSize, activity.STUDY_PLUM, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(activity.dp(118), activity.dp(108)));

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            addStudyCueLines(details, session);
        } else {
            details.addView(activity.text(session.prompt, 15, activity.MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    void addStudyCueLines(LinearLayout details, RecordsSchedulerModels.StudySession session) {
        List<String> lines = StudyCueTexts.answerLines(
                activity.currentDictionaryLookup(),
                session,
                activity.exampleForSession(session),
                StudyTaskCopy.isWordReadingTask(session)
        );
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = line.startsWith("Reading:") ? activity.STUDY_PINK_DARK : activity.STUDY_PLUM;
            details.addView(activity.text(line, i == 0 ? 17 : 15, color, true));
        }
    }

    View typingAnswerField() {
        activity.typingAnswerInput = new EditText(activity);
        activity.typingAnswerInput.setSingleLine(true);
        activity.typingAnswerInput.setTextSize(20);
        activity.typingAnswerInput.setTextColor(activity.STUDY_PLUM);
        activity.typingAnswerInput.setHintTextColor(activity.STUDY_MUTED);
        activity.typingAnswerInput.setHint(activity.LABEL_MEANING);
        activity.typingAnswerInput.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        activity.typingAnswerInput.setBackground(activity.panel(Color.WHITE, activity.STUDY_BORDER, activity.dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(58));
        lp.setMargins(0, activity.dp(4), 0, activity.dp(4));
        activity.typingAnswerInput.setLayoutParams(lp);
        return activity.typingAnswerInput;
    }

    Typeface fontResource(int fontRes, Typeface fallback) {
        try {
            return activity.getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    void buildFlashcardActionBar(boolean revealed) {
        if (activity.studyActionBar == null) {
            return;
        }
        activity.styleStudyActionBarShell();
        activity.studyActionBar.removeAllViews();
        activity.studyActionBar.setVisibility(View.VISIBLE);

        activity.resultStatus = activity.text("", 15, activity.STUDY_MUTED, false);
        activity.resultStatus.setVisibility(View.GONE);
        activity.studyActionBar.addView(activity.resultStatus);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!revealed) {
            Button reveal = activity.pinkPrimaryButton("Reveal");
            reveal.setOnClickListener(v -> revealFlashcardAnswer());
            actions.addView(reveal, new LinearLayout.LayoutParams(0, activity.dp(62), 1));
        } else {
            Button fail = activity.studyFailButton("Fail");
            fail.setOnClickListener(v -> activity.submitReview(activity.RATING_AGAIN, false));
            LinearLayout.LayoutParams failParams = new LinearLayout.LayoutParams(0, activity.dp(62), 1);
            failParams.setMargins(0, 0, activity.dp(6), 0);
            actions.addView(fail, failParams);

            Button pass = activity.pinkPrimaryButton(activity.LABEL_PASS);
            pass.setOnClickListener(v -> activity.submitReview(activity.RATING_GOOD, false));
            LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(0, activity.dp(62), 1);
            passParams.setMargins(activity.dp(6), 0, 0, 0);
            actions.addView(pass, passParams);
        }
        activity.studyActionBar.addView(actions);
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
