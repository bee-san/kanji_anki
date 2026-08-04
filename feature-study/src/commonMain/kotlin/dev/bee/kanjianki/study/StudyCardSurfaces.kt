package dev.bee.kanjianki.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.StudyActionHints
import dev.bee.kanjianki.presentation.StudyAnswerDetails
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyChoice
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyGradeAction
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val STUDY_CARD_TEST_TAG: String = "kani-study-card"
const val STUDY_REVEAL_TEST_TAG: String = "kani-study-reveal"
const val STUDY_PASS_TEST_TAG: String = "kani-study-pass"
const val STUDY_FAIL_TEST_TAG: String = "kani-study-fail"
const val STUDY_SAVE_HARD_TEST_TAG: String = "kani-study-save-hard"
const val STUDY_CONTINUE_TEST_TAG: String = "kani-study-continue"
const val STUDY_ANSWER_TEST_TAG: String = "kani-study-answer"
const val STUDY_ANSWER_DETAILS_TEST_TAG: String = "kani-study-answer-details"
const val STUDY_TYPING_INPUT_TEST_TAG: String = "kani-study-typing-input"
const val STUDY_TYPING_SUBMIT_TEST_TAG: String = "kani-study-typing-submit"

/** One choice on a multiple-choice card, tagged by its value. */
fun studyChoiceTestTag(value: String): String = "kani-study-choice-$value"

/**
 * Announces the key that invokes this control, where the host has one.
 *
 * The accessible half of Goal 203's accelerator work. The native menu prints its
 * accelerators, but a menu bar is not where a screen reader user is: without this, the
 * shortcut is discoverable only by people who can see the menu. Compose's `onClick`
 * action label is what a screen reader reads as "double-tap to <label>", so naming the key
 * there is how "Pass" becomes "Pass, 3".
 *
 * A no-op when [accelerator] is null, which is the Android answer and also the answer for
 * a command whose key a focused text field would swallow — see [StudyActionHints]. The
 * action itself is deliberately not supplied here: the control's own `onClick` already
 * runs it, and a semantics action with its own lambda would be a second path to the same
 * dispatch, which on a grade means a second review.
 */
internal fun Modifier.announcesKey(accelerator: String?): Modifier =
    if (accelerator == null) this else semantics { onClick(label = accelerator, action = null) }

/**
 * The active card, dispatched by variant.
 *
 * Every branch is one card type Android chose by branching on `session.taskType`.
 * The feedback and continue handling is shared here rather than in each card, because
 * the one-card gate — an answered card stays up until one Continue — is the same for
 * every variant and is what stops a double-commit between cards.
 *
 * [context] carries the reveal state, which the session surface owns because the
 * keyboard needs the same answer: a face-down card may be revealed and not graded, by
 * either input path.
 */
@Composable
internal fun StudyCardSurface(
    card: StudyCard,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    context: StudyInputContext,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_CARD_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (card) {
            is StudyCard.Flashcard -> FlashcardCard(card, session, copy, resolver, context, dispatch, hints)
            is StudyCard.Typed -> TypedCard(card, session, copy, resolver, dispatch, hints)
            is StudyCard.Choice -> ChoiceCard(card, session, copy, resolver, dispatch, hints)
            is StudyCard.Writing -> WritingCard(card, session, copy, resolver, dispatch, hints)
        }
        if (session.acceptsContinue) {
            // Continue is the primary command once feedback is applied, so it announces the
            // primary's key — the same key the policy resolves to Continue there.
            ContinueButton(copy, dispatch, hints?.accelerator(StudyCommand.PRIMARY))
        }
    }
}

@Composable
private fun FlashcardCard(
    card: StudyCard.Flashcard,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    context: StudyInputContext,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints?,
) {
    CardHero(prompt = card.prompt, resolver = resolver, emphasizeSubject = card.emphasizeSubjectInPrompt)
    if (context.answerRevealed) {
        AnswerBlock(card.answer, card.details, resolver)
        PassFailRow(card.pass, card.fail, saveHard = null, session, copy, resolver, dispatch, hints)
    } else {
        Button(
            onClick = { dispatch(KaniAction.Study.Reveal) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
                .announcesKey(hints?.accelerator(StudyCommand.PRIMARY))
                .testTag(STUDY_REVEAL_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.reveal, fontSize = KaniUiTokens.StudyActionTextSizeSp.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TypedCard(
    card: StudyCard.Typed,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints?,
) {
    var input by remember(card.subject) { mutableStateOf("") }
    val label = resolver.resolve(card.inputLabel)
    CardHero(prompt = card.prompt, resolver = resolver, emphasizeSubject = false)
    if (session.feedback.visible) {
        AnswerBlock(card.answer, card.details, resolver)
    } else {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(STUDY_TYPING_INPUT_TEST_TAG)
                .semantics { if (label.isNotBlank()) contentDescription = label },
            label = { if (label.isNotBlank()) Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitIfAccepted(session, card.submit, dispatch) }),
        )
        Button(
            onClick = { submitIfAccepted(session, card.submit, dispatch) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
                // Enter, not Space: the answer box owns Space here, and the hints know it.
                .announcesKey(hints?.accelerator(StudyCommand.PRIMARY))
                .testTag(STUDY_TYPING_SUBMIT_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.submit, fontSize = KaniUiTokens.StudyActionTextSizeSp.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChoiceCard(
    card: StudyCard.Choice,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints?,
) {
    CardHero(prompt = card.prompt, resolver = resolver, emphasizeSubject = false)
    card.choices.forEachIndexed { index, choice ->
        // The digit is the option's place on screen, not a binding: the policy resolves
        // digits positionally on a choice card, so the key announced has to be read off the
        // same index the loop renders at.
        ChoiceButton(
            choice,
            card.correct,
            session,
            resolver,
            dispatch,
            hints?.choiceAccelerator(index + 1),
        )
    }
    if (session.feedback.visible) {
        AnswerBlock(UiText.EMPTY, card.details, resolver)
    }
}

@Composable
private fun WritingCard(
    card: StudyCard.Writing,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints?,
) {
    // The ink surface is Goal 196's; this is the prompt and the grades the writing
    // rung offers. Pass/Fail only, plus the "Save hard" exception when the model
    // carries it — Hard and Easy are never user-selectable here.
    CardHero(prompt = card.prompt, resolver = resolver, emphasizeSubject = false)
    PassFailRow(card.pass, card.fail, card.saveHard, session, copy, resolver, dispatch, hints)
}

@Composable
private fun CardHero(prompt: UiText, resolver: UiTextResolver, emphasizeSubject: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = HERO_MIN_HEIGHT),
        shape = KaniUiTokens.StudyShapeLarge,
        color = KaniTheme.colors.panelSoft,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = resolver.resolve(prompt),
                color = KaniTheme.colors.ink,
                // A mined sentence front is small; a single-glyph prompt is the hero.
                fontSize = if (emphasizeSubject) {
                    KaniUiTokens.StudyQuestionTextSizeSp.sp
                } else {
                    KaniUiTokens.StudyHeroTextSizeSp.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AnswerBlock(answer: UiText, details: StudyAnswerDetails?, resolver: UiTextResolver) {
    val answerText = resolver.resolve(answer)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(STUDY_ANSWER_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (answerText.isNotBlank()) {
                Text(
                    text = answerText,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            details?.let { DetailsBlock(it, resolver) }
        }
    }
}

@Composable
private fun DetailsBlock(details: StudyAnswerDetails, resolver: UiTextResolver) {
    val heading = resolver.resolve(details.heading)
    val lines = details.lines.map(resolver::resolve).filter { it.isNotBlank() }
    if (heading.isBlank() && lines.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().testTag(STUDY_ANSWER_DETAILS_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (heading.isNotBlank()) {
            Text(
                text = heading,
                color = KaniTheme.colors.teal,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        for (line in lines) {
            Text(text = line, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

@Composable
private fun PassFailRow(
    pass: StudyGradeAction,
    fail: StudyGradeAction,
    saveHard: StudyGradeAction?,
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    hints: StudyActionHints?,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Save hard replaces Pass for a CLOSE ink attempt; when present, it is the
        // single primary action and plain Pass is not offered.
        val primary = saveHard ?: pass
        val primaryLabel = resolver.resolve(primary.label).ifBlank { copy.pass }
        Button(
            onClick = { submitIfAccepted(session, primary, dispatch) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
                // Save hard announces the pass key too: the policy routes GRADE_PASS to
                // whichever of the two the card carries, so the key is the same one.
                .announcesKey(hints?.accelerator(StudyCommand.GRADE_PASS))
                .testTag(if (saveHard != null) STUDY_SAVE_HARD_TEST_TAG else STUDY_PASS_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = KaniTheme.colors.teal),
        ) {
            Text(text = primaryLabel, fontSize = KaniUiTokens.StudyActionTextSizeSp.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { submitIfAccepted(session, fail, dispatch) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
                .announcesKey(hints?.accelerator(StudyCommand.GRADE_FAIL))
                .testTag(STUDY_FAIL_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
            border = BorderStroke(1.dp, KaniTheme.colors.coral),
        ) {
            Text(
                text = resolver.resolve(fail.label).ifBlank { copy.fail },
                color = KaniTheme.colors.coral,
                fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    choice: StudyChoice,
    correct: String?,
    session: StudySession,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    accelerator: String?,
) {
    val label = resolver.resolve(choice.label).ifBlank { choice.value }
    // After an answer, the correct choice turns teal and the wrong pick turns coral;
    // before, every choice is neutral. Feedback is a function of the model state, so
    // it survives a recomposition the way the Android grid's frozen state did.
    val answered = session.feedback.visible
    val accent = when {
        !answered -> null
        choice.value == correct -> KaniTheme.colors.teal
        choice.value == session.feedback.selected &&
            session.feedback.outcome == StudyOutcome.INCORRECT -> KaniTheme.colors.coral
        else -> null
    }
    OutlinedButton(
        onClick = { submitIfAccepted(session, choice.grade, dispatch) },
        modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
            .announcesKey(accelerator)
            .testTag(studyChoiceTestTag(choice.value)),
        shape = KaniUiTokens.ButtonShape,
        border = BorderStroke(1.dp, accent ?: KaniTheme.colors.borderSoft),
    ) {
        Text(
            text = label,
            color = accent ?: KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContinueButton(copy: StudyCopy, dispatch: (KaniAction) -> Unit, accelerator: String?) {
    Button(
        onClick = { dispatch(KaniAction.Study.Continue) },
        modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT)
            .announcesKey(accelerator)
            .testTag(STUDY_CONTINUE_TEST_TAG),
        shape = KaniUiTokens.ButtonShape,
    ) {
        Text(text = copy.cont, fontSize = KaniUiTokens.StudyActionTextSizeSp.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Dispatches a grade only when the session still accepts one.
 *
 * The double-commit guard, in one place every card goes through: a second grade from
 * a double-click, a key-repeat, or a duplicate callback is dropped because the
 * session's own `acceptsGrade` is already false once the first is in flight. The
 * scheduler is token-idempotent underneath this, so a grade that slips through is
 * still harmless — this stops the UI from trying.
 */
private fun submitIfAccepted(
    session: StudySession,
    grade: StudyGradeAction,
    dispatch: (KaniAction) -> Unit,
) {
    if (!session.acceptsGrade) return
    dispatch(grade.action)
}

private val ACTION_MIN_HEIGHT = 54.dp
private val HERO_MIN_HEIGHT = 160.dp

/**
 * The floor for a control that is not a primary grade action.
 *
 * Material's own `TextButton` default is 40dp tall, which is under every published
 * minimum for a reliable pointer or touch target — comfortable with a mouse, awkward
 * with a thumb, and genuinely hard with a trackpad on a laptop. The grade actions clear
 * this by a wide margin at [ACTION_MIN_HEIGHT]; a secondary control does not need to be
 * that prominent, but it does need to be hittable.
 */
internal val SECONDARY_MIN_HEIGHT = 44.dp
