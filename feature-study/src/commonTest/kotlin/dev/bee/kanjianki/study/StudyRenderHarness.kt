package dev.bee.kanjianki.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyChoice
import dev.bee.kanjianki.presentation.StudyGradeAction
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme

/**
 * A [StudyCopy] from marker strings, keeping the progress placeholders so a failed
 * substitution shows up as a literal `%1$d`.
 */
internal fun studyCopy(): StudyCopy = StudyCopy(
    pass = "Pass",
    fail = "Fail",
    cont = "Continue",
    reveal = "Show answer",
    submit = "Submit",
    undo = "Undo",
    clear = "Clear",
    doneTitle = "Session complete",
    doneBody = "You finished.",
    doneHome = "Back to home",
    emptyTitle = "Nothing to study",
    emptyBody = "No card is due.",
    progressTemplate = "%1\$d of %2\$d",
)

/** A resolver that passes literals through and blanks host-only text, like Home's. */
internal val TestUiTextResolver: UiTextResolver = UiTextResolver { text ->
    when (text) {
        is UiText.Literal -> text.text
        is UiText.Key, is UiText.Quantity -> ""
    }
}

/** A self-graded flashcard with an answer to reveal. */
internal fun flashcard(): StudyCard.Flashcard = StudyCard.Flashcard(
    prompt = UiText.Literal("脱"),
    subject = "脱",
    answer = UiText.Literal("take off"),
    pass = grade("Pass", "good"),
    fail = grade("Fail", "again"),
)

/** A typed-answer card. */
internal fun typedCard(): StudyCard.Typed = StudyCard.Typed(
    prompt = UiText.Literal("Type the meaning of 脱"),
    subject = "脱",
    answer = UiText.Literal("take off"),
    submit = grade("Submit", "good"),
    inputLabel = UiText.Literal("Meaning"),
)

/** A multiple-choice card with a known correct answer. */
internal fun choiceCard(): StudyCard.Choice = StudyCard.Choice(
    prompt = UiText.Literal("Which kanji means take off?"),
    subject = "脱",
    choices = listOf(
        StudyChoice(value = "脱", grade = grade("脱", "good")),
        StudyChoice(value = "説", grade = grade("説", "again")),
        StudyChoice(value = "税", grade = grade("税", "again")),
    ),
    correct = "脱",
)

/** A writing card; [close] adds the "Save hard" exception. */
internal fun writingCard(close: Boolean = false): StudyCard.Writing = StudyCard.Writing(
    prompt = UiText.Literal("Write 脱"),
    subject = "脱",
    pass = grade("Pass", "good"),
    fail = grade("Fail", "again"),
    saveHard = if (close) grade("Save hard", "hard") else null,
)

internal fun grade(label: String, rating: String) =
    StudyGradeAction(label = UiText.Literal(label), rating = rating)

private val STUDY_WINDOW_WIDTH: Dp = 411.dp
private val STUDY_WINDOW_HEIGHT: Dp = 891.dp

@Composable
private fun FixedWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density / scale, fontScale = density.fontScale),
        ) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) { content() }
        }
    }
}

/**
 * Composes [content] in the theme at a fixed window, inside a scroll.
 *
 * The scroll matches how each host wraps the session route — a full card plus its
 * grades is taller than the window — so `performScrollTo` can reach a control below
 * the fold, the same technique the Home detail assertions use.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderStudy(content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = STUDY_WINDOW_WIDTH, height = STUDY_WINDOW_HEIGHT) {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
        block()
    }
}

/** All text in this node's semantics subtree, joined. */
internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

/** The node's content description, joined. */
internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()

/**
 * The node's click action label, or empty when it announces none.
 *
 * The label, not the action: a screen reader reads it as "double-tap to <label>", and the
 * label is where a control names the key that invokes it. Empty rather than null because an
 * unannounced control and one announcing nothing are the same thing to a caller.
 */
internal fun SemanticsNodeInteraction.clickLabelOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label.orEmpty()
