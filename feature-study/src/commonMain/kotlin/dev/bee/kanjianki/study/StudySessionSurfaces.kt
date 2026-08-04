package dev.bee.kanjianki.study

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.StudyActionHints
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val STUDY_SESSION_TEST_TAG: String = "kani-study-session"
const val STUDY_PROGRESS_TEST_TAG: String = "kani-study-progress"
const val STUDY_LOADING_TEST_TAG: String = "kani-study-loading"
const val STUDY_DONE_TEST_TAG: String = "kani-study-done"
const val STUDY_DONE_HOME_TEST_TAG: String = "kani-study-done-home"
const val STUDY_EMPTY_TEST_TAG: String = "kani-study-empty"
const val STUDY_UNDO_TEST_TAG: String = "kani-study-undo"

/**
 * A whole study session, from one [StudySession].
 *
 * One entry point per host, branching on [StudySession.state]: a spinner before the
 * first card, the card and its progress while studying, the done screen when the
 * target is met, the empty screen when nothing was due, and — because a load can fail
 * — nothing here for [StudySessionState.ERROR], which the shell's own failure surface
 * already covers above this. The card branch is [StudyCardSurface], which picks the
 * variant.
 *
 * The scheduler decides which state this is; the surface only lays it out. That is
 * the checkable form of "both hosts run the same session": each host maps its
 * authoritative snapshot to this and calls this one composable.
 */
@Composable
fun StudySessionScreen(
    session: StudySession,
    copy: StudyCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The keys in force, defaulting to the reviewed set.
     *
     * Passed in rather than read here, because the host owns the device-settings store
     * and the Settings editor writes through it — the surface renders whatever the route
     * load resolved, so the keyboard and the editor row cannot disagree.
     */
    keybindings: StudyKeybindings = StudyKeybindings.DEFAULT,
    /**
     * Reports what currently holds the keyboard, whenever it changes.
     *
     * The reveal state is local UI state — the reducer deliberately does not reload for a
     * reveal — but a native menu outside this surface has to honour the same guard: an
     * enabled "Pass" in the menu bar while the card is face down would be a way to grade
     * an answer the user has not seen. Reporting the context outward is how the menu stays
     * the *same* decision rather than a second one.
     *
     * A no-op by default, so the Android host and every existing caller are unaffected.
     */
    onInputContextChange: (StudyInputContext) -> Unit = {},
    /**
     * The keyboard the host actually has, or null when it has none worth advertising.
     *
     * Supplied only by a host that routes key events, because this is what turns each
     * visible control's accessible action into "Pass, 3": a screen reader user is not in the
     * menu bar, so a shortcut the menu prints and the button does not announce is one they
     * cannot discover. Null on Android, where there is no keyboard to name and TalkBack
     * would otherwise be told to press a key that is not there.
     */
    keyboardPlatform: KeyboardPlatform? = null,
) {
    // The typed card's field holds focus while it is unanswered, which is exactly
    // when letter and space keys must reach the field instead of grading — so the
    // focus guard is a function of the visible card, not a live focus query the two
    // hosts might answer differently.
    val textFieldFocused = session.state == StudySessionState.CARD &&
        session.card is StudyCard.Typed &&
        !session.feedback.visible
    // Whether a self-graded card is face up. Hoisted out of the card surface because
    // the keyboard needs it too: no key may grade an answer the user has not seen, and
    // the reveal key must turn the card over as the button does. Local UI state — the
    // reducer deliberately does not reload for a reveal — keyed on the card so the next
    // one starts face down.
    var revealed by remember(session.card) { mutableStateOf(false) }
    val context = StudyInputContext(
        textFieldFocused = textFieldFocused,
        answerRevealed = revealed || session.feedback.visible,
    )
    LaunchedEffect(context) {
        onInputContextChange(context)
    }
    // Derived from the same bindings and the same context the key handler reads, so a
    // control cannot announce a key that would not fire — on the typed card the answer box
    // owns Space, and the hints answer `Enter` for exactly that reason.
    val hints = keyboardPlatform?.let { StudyActionHints.of(it, keybindings, context) }
    // A focus anchor so the shortcuts receive keys the moment the session appears,
    // without the user clicking first. The typed card's field takes focus for itself
    // when it mounts, so this only wins on the self-graded and choice cards — exactly
    // the ones whose keys must not wait for a click.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(session.state, session.card, textFieldFocused) {
        if (session.state == StudySessionState.CARD && !textFieldFocused) {
            focusRequester.requestFocus()
        }
    }
    // One dispatch for the keyboard and the buttons, so a revealed-by-key card shows
    // its answer exactly as a revealed-by-click one does.
    val dispatchAndReveal: (KaniAction) -> Unit = { action ->
        if (action == KaniAction.Study.Reveal) revealed = true
        dispatch(action)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .studyKeyboardShortcuts(session, context, dispatchAndReveal, keybindings)
            .testTag(STUDY_SESSION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (session.state) {
            StudySessionState.LOADING -> StudyLoading()
            StudySessionState.CARD -> {
                StudyProgressBar(session, copy)
                session.card?.let { card ->
                    StudyCardSurface(card, session, copy, resolver, context, dispatchAndReveal, hints)
                }
                if (session.undoable) {
                    StudyUndoRow(copy, dispatch, hints?.accelerator(StudyCommand.UNDO))
                }
            }
            StudySessionState.DONE -> StudyDone(copy, dispatch)
            StudySessionState.EMPTY -> StudyEmpty(copy)
            // The shell draws the retryable failure above this; a second banner here
            // would be the double error state the shared failure surface exists to
            // prevent.
            StudySessionState.ERROR -> Unit
        }
    }
}

@Composable
private fun StudyProgressBar(session: StudySession, copy: StudyCopy) {
    val target = session.progress.displayedTarget
    val label = copy.progress(session.progress.completed, target)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_PROGRESS_TEST_TAG)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        // Determinate: unlike a sync, a study session knows its target up front, so a
        // bar that fills toward it is honest.
        LinearProgressIndicator(
            progress = { if (target == 0) 0f else session.progress.completed.toFloat() / target },
            modifier = Modifier.fillMaxWidth(),
            color = KaniTheme.colors.primary,
            trackColor = KaniTheme.colors.track,
        )
    }
}

@Composable
private fun StudyUndoRow(copy: StudyCopy, dispatch: (KaniAction) -> Unit, accelerator: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = { dispatch(KaniAction.Study.Undo) },
            modifier = Modifier
                .heightIn(min = SECONDARY_MIN_HEIGHT)
                .announcesKey(accelerator)
                .testTag(STUDY_UNDO_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.undo)
        }
    }
}

@Composable
private fun StudyLoading() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_LOADING_TEST_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = KaniTheme.colors.primary,
            trackColor = KaniTheme.colors.track,
        )
    }
}

@Composable
private fun StudyDone(copy: StudyCopy, dispatch: (KaniAction) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_DONE_TEST_TAG),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = copy.doneTitle,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = copy.doneBody,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            )
            Button(
                onClick = { dispatch(KaniAction.Navigation.Open(KaniDestination.Home)) },
                modifier = Modifier
                    .heightIn(min = SECONDARY_MIN_HEIGHT)
                    .testTag(STUDY_DONE_HOME_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.doneHome)
            }
        }
    }
}

@Composable
private fun StudyEmpty(copy: StudyCopy) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STUDY_EMPTY_TEST_TAG)
            .semantics { contentDescription = "${copy.emptyTitle}. ${copy.emptyBody}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = copy.emptyTitle,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = copy.emptyBody,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}
