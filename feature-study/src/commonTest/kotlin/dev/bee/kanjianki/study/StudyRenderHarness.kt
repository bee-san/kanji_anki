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

/**
 * The same [StudyCopy] with every label pseudo-localized.
 *
 * Goal 203 asks for long-string pseudo-localization, and this is what that means in
 * practice: German and Finnish routinely run 40-60% longer than English, so a label sized
 * to "Pass" is a label sized to nothing. Each string is bracketed and padded to roughly
 * twice its length, which is past any real translation and therefore a genuine ceiling
 * rather than a lucky pass.
 *
 * The brackets are load-bearing, not decoration: an assertion can then tell a *truncated*
 * string from a short one, because a rendered label missing its closing `]` was cut off.
 * The accented substitutions cover the other half of the same failure — a font fallback
 * that silently drops non-ASCII glyphs — which is exactly what a Latin-1-only bundled
 * font does to a German translation.
 */
internal fun pseudoLocalizedStudyCopy(): StudyCopy = StudyCopy(
    pass = pseudo("Pass"),
    fail = pseudo("Fail"),
    cont = pseudo("Continue"),
    reveal = pseudo("Show answer"),
    submit = pseudo("Submit"),
    undo = pseudo("Undo"),
    clear = pseudo("Clear"),
    doneTitle = pseudo("Session complete"),
    doneBody = pseudo("You finished."),
    doneHome = pseudo("Back to home"),
    emptyTitle = pseudo("Nothing to study"),
    emptyBody = pseudo("No card is due."),
    // The placeholders survive verbatim: a pseudo-localizer that mangled them would be
    // testing its own bug rather than the layout, and the substitution assertions below
    // read the numbers back out.
    progressTemplate = "[%1\$d ǿf %2\$d — ẍẍẍẍẍẍ]",
)

/**
 * One pseudo-localized string: accented, padded to about double, and bracketed.
 *
 * Accents applied to the vowels only, so the result is still recognizable in a failure
 * message — `[Pȧss — ẍẍẍẍ]` reads as "Pass" while being neither ASCII nor short.
 */
internal fun pseudo(text: String): String {
    val accented = text.map { character -> PSEUDO_ACCENTS[character] ?: character }.joinToString("")
    return "[$accented — ${"ẍ".repeat(text.length)}]"
}

private val PSEUDO_ACCENTS: Map<Char, Char> = mapOf(
    'a' to 'ȧ', 'e' to 'ḗ', 'i' to 'ï', 'o' to 'ǿ', 'u' to 'ü',
    'A' to 'Ȧ', 'E' to 'Ḗ', 'I' to 'Ï', 'O' to 'Ǿ', 'U' to 'Ü',
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

/**
 * A window the study session is rendered at, named so a failure reads as a device.
 *
 * Goal 203 asks for desktop coverage at fractional scaling and large fonts, which is
 * two independent axes: the window is how much room the layout has in dp, and the font
 * scale is how much of it the text wants. Naming the windows means an assertion says
 * `DESKTOP_MINIMUM` rather than `800.0.dp x 600.0.dp`, and adding one is a single entry.
 *
 * The fractional widths are not decoration. A 125% or 150% display scale on Windows and
 * on fractional-scaled Wayland does not change the dp *window* — it changes how many
 * pixels a dp is — but the dp window a desktop user ends up with is their pixel size
 * divided by that scale, which is very often not a round number. `DESKTOP_FRACTIONAL`
 * is a 1280x800 panel at 125%, and its 1024x640 dp window is the size a real
 * fractional-scaled laptop actually hands the layout.
 */
internal enum class StudyWindow(val width: Dp, val height: Dp) {
    /** The phone window the existing assertions render at. */
    PHONE(411.dp, 891.dp),

    /** A short, wide desktop window: the axis a phone layout never exercises. */
    DESKTOP_SMALL(1280.dp, 800.dp),

    /** A 1280x800 panel at 125% display scale, which is where fractional dp comes from. */
    DESKTOP_FRACTIONAL(1024.dp, 640.dp),

    /** The floor the desktop window enforces; the session must still be usable. */
    DESKTOP_MINIMUM(800.dp, 600.dp),
    ;

    override fun toString(): String = "$name(${width.value.toInt()}x${height.value.toInt()})"
}

/**
 * The font scales the matrices cover, from the OS text-size setting.
 *
 * 2x is not a paranoid ceiling: it is roughly what Windows' 200% text size and GNOME's
 * large-text setting produce, and it is the scale at which a fixed-height action button
 * clips its own label. A study grade the user cannot read is a grade they will get wrong.
 */
internal val STUDY_FONT_SCALES: List<Float> = listOf(1f, 1.3f, 1.5f, 2f)

@Composable
private fun FixedWindow(width: Dp, height: Dp, fontScale: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density / scale, fontScale = fontScale),
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
    renderStudyAt(content = content, block = block)
}

/**
 * Composes [content] at a named [window] and OS [fontScale].
 *
 * Two axes rather than one because they fail differently: a narrow window truncates or
 * pushes a control off the side, and a large font scale grows the text inside a control
 * whose height the layout fixed. The font scale is applied to the density rather than to
 * the `sp` values, which is what an OS text-size setting actually does — so the tokens
 * under test stay the shipped ones.
 *
 * The window is *not* a hint. The density is divided until the logical size fits the
 * host's test root, exactly as `feature-shell`'s harness does, so `BoxWithConstraints`
 * reports the intended dp width and every node stays inside the root and stays
 * clickable. Sizing a child `Box` instead would let the host silently coerce the window
 * back to its own bounds and the assertions would pass at a size never rendered.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderStudyAt(
    window: StudyWindow = StudyWindow.PHONE,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
    block: ComposeUiTest.() -> Unit,
) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = window.width, height = window.height, fontScale = fontScale) {
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
