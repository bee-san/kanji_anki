package dev.bee.kanjianki.study

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudyProgress
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Goal 203's scaling, accessibility, and locale matrices for the study session.
 *
 * Separate from `StudyRenderAssertions` because these are a different kind of assertion.
 * Those check *what* each control is and dispatches, once, at one size. These check that
 * the same session survives the configurations a desktop user actually has: a fractional
 * display scale, an OS text size of 200%, a window at the resize floor, and a translation
 * far longer than the English it replaced. Each is a genuine shipping failure that no
 * single-configuration render test can see — a grade button whose label is clipped at
 * 200% text is still perfectly "displayed" as far as a semantics query is concerned.
 *
 * Every assertion here is written over a matrix rather than one case, and every failure
 * message names the configuration, so one bad cell is still identifiable.
 *
 * What is deliberately *not* here: pixels. `feature-shell`'s `ShellRasterTest` owns the
 * raster side, and it is desktop-only for a documented reason (`captureToImage` cannot
 * force a redraw under Robolectric). These run on both hosts, which is the point — the
 * Android host is what caught `feature-shell`'s window-sizing bug.
 */

/** The smallest a control may be and still be a reliable pointer or touch target. */
private val MIN_TARGET: Dp = 44.dp

/**
 * Slack for a bounds comparison, in dp.
 *
 * Layout rounds through pixels and back, so an edge lands a fraction of a dp off its
 * intended value at some densities. A real overflow is off by far more than this.
 */
private val BOUNDS_SLACK: Dp = 1.dp

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryGradeStaysReachableAcrossWindowsAndFontScales() {
    // The matrix itself. A card plus its grades is taller than a phone window at any font
    // scale, so "reachable" means reachable after a scroll — which is how the hosts wrap
    // the route and how a user gets there.
    for (window in StudyWindow.entries) {
        for (fontScale in STUDY_FONT_SCALES) {
            val recorded = mutableListOf<KaniAction>()
            renderStudyAt(
                window = window,
                fontScale = fontScale,
                content = {
                    StudySessionScreen(
                        matrixSession(card = flashcard()),
                        studyCopy(),
                        TestUiTextResolver,
                        dispatch = { recorded += it },
                    )
                },
            ) {
                val where = "$window at ${fontScale}x"
                onNodeWithTag(STUDY_REVEAL_TEST_TAG).performScrollTo().assertIsDisplayed()
                    .assertHasClickAction()
                onNodeWithTag(STUDY_REVEAL_TEST_TAG).performClickAt(where)
                // Reveal is a real state change, so the grades exist only after it — this
                // also proves the click landed rather than merely being dispatchable.
                for (tag in listOf(STUDY_PASS_TEST_TAG, STUDY_FAIL_TEST_TAG)) {
                    onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
                }
                onNodeWithTag(STUDY_PASS_TEST_TAG).performClickAt(where)
                assertEquals(
                    listOf<KaniAction>(KaniAction.Study.Reveal, KaniAction.Study.Grade(rating = "good")),
                    recorded,
                    "$where must reveal then grade exactly once",
                )
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEachNamedWindowReallyRendersAtItsOwnWidth() {
    // The other half of the same guard. `feature-shell` learned this the hard way: under a
    // plain `size` modifier the host silently coerced every window down to its own 1024dp
    // root, and a whole set of width-dependent assertions passed at a width never rendered.
    // The card fills its width, so its bounds are the window's — measured rather than
    // assumed.
    for (window in StudyWindow.entries) {
        renderStudyAt(
            window = window,
            content = {
                StudySessionScreen(
                    matrixSession(card = flashcard()),
                    studyCopy(),
                    TestUiTextResolver,
                    dispatch = {},
                )
            },
        ) {
            val bounds = onNodeWithTag(STUDY_CARD_TEST_TAG).getBoundsInRoot()
            val width = (bounds.right - bounds.left).value
            assertTrue(
                kotlin.math.abs(width - window.width.value) <= BOUNDS_SLACK.value,
                "$window rendered ${width}dp wide, so the window was coerced",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEachFontScaleReallyReachesTheRenderedText() {
    // The guard on the matrix itself, and it is not ceremony. Every other assertion in this
    // file is a *tolerance* — nothing overflows, nothing shrinks — so all of them pass
    // happily on a harness that quietly rendered 1x four times. A font scale that never
    // arrived would turn this whole file into one configuration wearing four names.
    //
    // Measured on the progress line, whose height is its text's and nothing else's: the
    // action buttons floor at `heightIn(min = 54.dp)`, so they are nearly flat from 1x to
    // 1.5x and would make a poor witness.
    //
    // Registered on the desktop host only, and that is a real finding rather than an
    // omission: Robolectric's text measurement does not honour `Density.fontScale`, so the
    // progress line measured exactly 152.0dp at all four scales there. The scaled-layout
    // *tolerances* in this file still run on both hosts and are still worth running — but
    // Android cannot witness that the scale arrived, so claiming it does would be the
    // vacuous pass this assertion exists to catch. The same reasoning keeps
    // `ShellRasterTest` desktop-only.
    val heights = STUDY_FONT_SCALES.associateWith { fontScale ->
        var height = 0f
        renderStudyAt(
            window = StudyWindow.DESKTOP_SMALL,
            fontScale = fontScale,
            content = {
                StudySessionScreen(
                    matrixSession(card = flashcard()),
                    studyCopy(),
                    TestUiTextResolver,
                    dispatch = {},
                )
            },
        ) {
            val bounds = onNodeWithTag(STUDY_PROGRESS_TEST_TAG).getBoundsInRoot()
            height = (bounds.bottom - bounds.top).value
        }
        height
    }
    // Strictly increasing, not merely different: text laid out at a larger scale is taller,
    // and equality anywhere in the sequence means two scales collapsed onto one render.
    for ((smaller, larger) in STUDY_FONT_SCALES.zipWithNext()) {
        assertTrue(
            heights.getValue(larger) > heights.getValue(smaller) + BOUNDS_SLACK.value,
            "${larger}x rendered ${heights.getValue(larger)}dp of text, " +
                "no taller than ${smaller}x at ${heights.getValue(smaller)}dp — the scale did not arrive",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertNoActionShrinksBelowAUsableTargetAtAnyFontScale() {
    // The failure this guards: a fixed `heightIn(min = ...)` is a *minimum*, so growing
    // text pushes a button taller and that is fine — but a control that never got a floor
    // takes Material's own 40dp default, which is under every published target. That is
    // not hypothetical: this assertion found it on the session's Undo, on the ink pad's
    // Undo and Clear, and on the done screen's Home button, all of which now floor at
    // `SECONDARY_MIN_HEIGHT`.
    //
    // Asserted at every scale rather than the largest, because the small end is where it
    // goes wrong; and over every card variant, because the defect was in the controls the
    // first draft of this list did not name.
    for (fontScale in STUDY_FONT_SCALES) {
        for (case in TARGET_CASES) {
            renderStudyAt(
                window = StudyWindow.DESKTOP_SMALL,
                fontScale = fontScale,
                content = {
                    StudySessionScreen(
                        case.session(),
                        studyCopy(),
                        TestUiTextResolver,
                        dispatch = {},
                    )
                },
            ) {
                for (tag in case.tags) {
                    val bounds = onNodeWithTag(tag).performScrollTo().getBoundsInRoot()
                    val height = bounds.bottom - bounds.top
                    assertTrue(
                        height.value + BOUNDS_SLACK.value >= MIN_TARGET.value,
                        "$tag is $height tall at ${fontScale}x on ${case.name}, " +
                            "under the $MIN_TARGET target",
                    )
                }
            }
        }
    }
}

/**
 * A session variant and the clickable controls it puts on screen.
 *
 * Enumerated per variant rather than as one flat tag list, because no single session
 * renders all of them: a writing card has an ink pad and no reveal button, a done screen
 * has neither. Adding a control without adding it here is the gap that let four
 * under-target buttons ship, so the case list is the checklist.
 */
private class TargetCase(val name: String, val tags: List<String>, val session: () -> StudySession)

private val TARGET_CASES: List<TargetCase> = listOf(
    TargetCase(
        name = "flashcard, face down, undoable",
        tags = listOf(STUDY_REVEAL_TEST_TAG, STUDY_UNDO_TEST_TAG),
    ) { matrixSession(card = flashcard()).copy(undoable = true) },
    TargetCase(
        name = "writing card",
        tags = listOf(STUDY_PASS_TEST_TAG, STUDY_FAIL_TEST_TAG),
        // The ink pad's own Undo and Clear are floored to the same value, but they are not
        // asserted here: `InkCanvas` is still a standalone composable that no session
        // render reaches, so a tag query for them finds nothing. They are covered by the
        // ink assertions, which compose it directly.
    ) { matrixSession(card = writingCard()) },
    TargetCase(
        name = "close writing attempt",
        tags = listOf(STUDY_SAVE_HARD_TEST_TAG, STUDY_FAIL_TEST_TAG),
    ) { matrixSession(card = writingCard(close = true)) },
    TargetCase(
        name = "typed card",
        tags = listOf(STUDY_TYPING_SUBMIT_TEST_TAG),
    ) { matrixSession(card = typedCard()) },
    TargetCase(
        name = "choice card",
        tags = listOf("脱", "説", "税").map(::studyChoiceTestTag),
    ) { matrixSession(card = choiceCard()) },
    TargetCase(
        name = "answered card offering Continue",
        tags = listOf(STUDY_CONTINUE_TEST_TAG),
    ) {
        matrixSession(
            card = flashcard(),
            feedback = StudyFeedback(phase = StudyFeedbackPhase.APPLIED, outcome = StudyOutcome.CORRECT),
        )
    },
    TargetCase(
        name = "done screen",
        tags = listOf(STUDY_DONE_HOME_TEST_TAG),
    ) { matrixSession(state = StudySessionState.DONE) },
)

@OptIn(ExperimentalTestApi::class)
internal fun assertNoControlOverflowsTheWindowSidewaysAtAnyScale() {
    // Vertical overflow is expected and handled by the scroll; horizontal overflow is a
    // defect, because nothing scrolls sideways and a control past the right edge is
    // unreachable by pointer while still reporting itself displayed.
    for (window in StudyWindow.entries) {
        for (fontScale in STUDY_FONT_SCALES) {
            renderStudyAt(
                window = window,
                fontScale = fontScale,
                content = {
                    StudySessionScreen(
                        matrixSession(card = choiceCard()),
                        studyCopy(),
                        TestUiTextResolver,
                        dispatch = {},
                    )
                },
            ) {
                val where = "$window at ${fontScale}x"
                val tags = listOf(STUDY_PROGRESS_TEST_TAG, STUDY_CARD_TEST_TAG) +
                    listOf("脱", "説", "税").map(::studyChoiceTestTag)
                for (tag in tags) {
                    val bounds = onNodeWithTag(tag).performScrollTo().getBoundsInRoot()
                    assertTrue(
                        bounds.left.value >= -BOUNDS_SLACK.value &&
                            bounds.right.value <= window.width.value + BOUNDS_SLACK.value,
                        "$tag spans ${bounds.left}..${bounds.right} at $where, outside ${window.width}",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertALongTranslationStillRendersWholeLabelsAndSubstitutes() {
    // Pseudo-localization at the largest font and the smallest window — the corner where a
    // long translation actually breaks. The closing bracket is the assertion: a label that
    // lost it was truncated, which is the failure a `assertIsDisplayed` cannot see.
    val copy = pseudoLocalizedStudyCopy()
    renderStudyAt(
        window = StudyWindow.DESKTOP_MINIMUM,
        fontScale = 2f,
        content = {
            StudySessionScreen(
                matrixSession(card = flashcard(), progress = StudyProgress(completed = 3, target = 7)),
                copy,
                TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        val reveal = onNodeWithTag(STUDY_REVEAL_TEST_TAG).performScrollTo().subtreeTextOrEmpty()
        assertEquals(copy.reveal, reveal, "the pseudo-localized reveal label must render whole")
        assertTrue(reveal.endsWith("]"), "the reveal label was truncated: $reveal")

        // The progress line substitutes through a pseudo-localized template too: a
        // translator's reordered or re-spaced template must still carry both numbers.
        val progress = onNodeWithTag(STUDY_PROGRESS_TEST_TAG).contentDescriptionOrEmpty()
        assertEquals(copy.progress(3, 7), progress)
        assertTrue("3" in progress && "7" in progress, "the progress line lost its numbers: $progress")
        assertTrue("%" !in progress, "the progress line kept a placeholder: $progress")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertALongTranslationKeepsTheDoneAndEmptyScreensWhole() {
    val copy = pseudoLocalizedStudyCopy()
    renderStudyAt(
        window = StudyWindow.DESKTOP_MINIMUM,
        fontScale = 2f,
        content = {
            StudySessionScreen(matrixSession(state = StudySessionState.DONE), copy, TestUiTextResolver, dispatch = {})
        },
    ) {
        val text = onNodeWithTag(STUDY_DONE_TEST_TAG).performScrollTo().subtreeTextOrEmpty()
        for (expected in listOf(copy.doneTitle, copy.doneBody, copy.doneHome)) {
            assertTrue(expected in text, "the done screen dropped $expected: $text")
        }
    }
    renderStudyAt(
        window = StudyWindow.DESKTOP_MINIMUM,
        fontScale = 2f,
        content = {
            StudySessionScreen(matrixSession(state = StudySessionState.EMPTY), copy, TestUiTextResolver, dispatch = {})
        },
    ) {
        // The empty screen's whole announcement is a content description, so a translation
        // that broke it would silence a screen reader rather than look wrong.
        assertEquals(
            "${copy.emptyTitle}. ${copy.emptyBody}",
            onNodeWithTag(STUDY_EMPTY_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheWholeSessionIsCompletableWithoutAPointerAtEveryScale() {
    // "No critical route is mouse-only", asserted for study: reveal and grade a card using
    // only keys, at every font scale and in the smallest window. Scale matters here because
    // the keyboard path goes through the session's focus anchor, and a layout change that
    // moved focus would break keys and nothing else.
    for (fontScale in STUDY_FONT_SCALES) {
        val recorded = mutableListOf<KaniAction>()
        renderStudyAt(
            window = StudyWindow.DESKTOP_MINIMUM,
            fontScale = fontScale,
            content = {
                StudySessionScreen(
                    matrixSession(card = flashcard()),
                    studyCopy(),
                    TestUiTextResolver,
                    dispatch = { recorded += it },
                    keyboardPlatform = KeyboardPlatform.LINUX,
                )
            },
        ) {
            val session = onNodeWithTag(STUDY_SESSION_TEST_TAG).requestFocus()
            session.performKeyInput { pressKey(Key.Spacebar) }
            session.performKeyInput { pressKey(Key.Three) }
            assertEquals(
                listOf<KaniAction>(KaniAction.Study.Reveal, KaniAction.Study.Grade(rating = "good")),
                recorded,
                "the keyboard path must reveal then grade at ${fontScale}x",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryAnnouncementSurvivesALongTranslationAndALargeFont() {
    // The accessibility half of the matrix: the accelerator announcement is a semantics
    // label, so it is exactly the thing that a layout change cannot break and a *threading*
    // change silently can. Asserted at the corner rather than the default, because that is
    // where a control gets rebuilt.
    val copy = pseudoLocalizedStudyCopy()
    renderStudyAt(
        window = StudyWindow.DESKTOP_MINIMUM,
        fontScale = 2f,
        content = {
            StudySessionScreen(
                matrixSession(card = flashcard()).copy(undoable = true),
                copy,
                TestUiTextResolver,
                dispatch = {},
                keyboardPlatform = KeyboardPlatform.LINUX,
            )
        },
    ) {
        assertEquals("Space", onNodeWithTag(STUDY_REVEAL_TEST_TAG).performScrollTo().clickLabelOrEmpty())
        assertEquals("Ctrl+Z", onNodeWithTag(STUDY_UNDO_TEST_TAG).performScrollTo().clickLabelOrEmpty())
        // The key label is not translated and must not be: it names a physical key, and
        // `Space` on a German keyboard is still the key the policy honours.
        assertTrue(
            "[" !in onNodeWithTag(STUDY_REVEAL_TEST_TAG).clickLabelOrEmpty(),
            "the accelerator label went through the localizer",
        )
    }
}

/**
 * Scrolls to a node and clicks it, naming [where] if either step fails.
 *
 * A bare `performClick` reports only that a click failed, which in a matrix is the least
 * useful half of the information — the interesting part is which of sixteen configurations
 * it was.
 */
@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteraction.performClickAt(where: String): SemanticsNodeInteraction =
    runCatching { performScrollTo().performClick() }
        .getOrElse { failure -> throw AssertionError("click failed at $where: ${failure.message}", failure) }

private fun matrixSession(
    state: StudySessionState = StudySessionState.CARD,
    card: dev.bee.kanjianki.presentation.StudyCard? = null,
    progress: StudyProgress = StudyProgress(completed = 1, target = 5),
    feedback: StudyFeedback = StudyFeedback(),
) = StudySession(state = state, progress = progress, card = card, feedback = feedback)
