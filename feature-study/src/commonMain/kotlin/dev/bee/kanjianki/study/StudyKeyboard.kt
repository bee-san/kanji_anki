package dev.bee.kanjianki.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyKey
import dev.bee.kanjianki.presentation.StudyKeyPress
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeyboardPolicy
import dev.bee.kanjianki.presentation.StudyKeystroke
import dev.bee.kanjianki.presentation.StudySession

/**
 * The Compose half of the study keyboard: native key events in, portable presses out.
 *
 * What a key *means* is [StudyKeyboardPolicy]'s, in `:presentation-api`, so both hosts
 * share one decision and a Settings editor has named commands to remap (Goal 203).
 * This file is only the translation, and it is deliberately thin — everything here is
 * either a keycode mapping or Compose plumbing.
 *
 * The translation is not quite trivial in one respect. Compose's [Key] is a platform
 * keycode behind a value class, so the same physical key is a different value on
 * Android and on the desktop JVM; [KEYS] maps the keys Kani can bind to their portable
 * names and everything else to null, which is how "not a study key" is expressed.
 * Escape is absent on purpose — it is the shell's back, and it has no portable name to
 * map to.
 */
internal object StudyKeyboardAdapter {
    /**
     * The portable name of a Compose key, or null for one Kani cannot bind.
     *
     * The whole platform-specific part of the keyboard path. Null is not a gap: an
     * unmapped key falls through to whoever else wants it — the shell's Escape-is-back,
     * a focused field's own typing, a menu's accelerator.
     */
    fun portableKey(key: Key): StudyKey? = KEYS[key]

    /** The portable press for a Compose event, or null for a key Kani cannot bind. */
    fun press(event: KeyEvent, isRepeat: Boolean): StudyKeyPress? {
        val portable = portableKey(event.key) ?: return null
        return StudyKeyPress(
            stroke = StudyKeystroke(
                key = portable,
                ctrl = event.isCtrlPressed,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                meta = event.isMetaPressed,
            ),
            isKeyDown = event.type == KeyEventType.KeyDown,
            isRepeat = isRepeat,
        )
    }

    /** The action a Compose key event should dispatch, or null when it is not ours. */
    fun actionFor(
        event: KeyEvent,
        session: StudySession,
        context: StudyInputContext,
        isRepeat: Boolean,
        bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
    ): KaniAction? {
        val press = press(event, isRepeat) ?: return null
        return StudyKeyboardPolicy.actionFor(press, session, context, bindings)
    }
}

/**
 * Compose keycode to portable name, for every key Kani can bind.
 *
 * At file top level rather than in a companion object: a companion holding only
 * constants compiles to a class with no reachable instructions, which the module's
 * 100%-CLASS coverage gate cannot cover.
 */
private val KEYS: Map<Key, StudyKey> = mapOf(
    Key.Spacebar to StudyKey.SPACE,
    Key.Enter to StudyKey.ENTER,
    Key.NumPadEnter to StudyKey.NUMPAD_ENTER,
    Key.One to StudyKey.DIGIT_1,
    Key.Two to StudyKey.DIGIT_2,
    Key.Three to StudyKey.DIGIT_3,
    Key.Four to StudyKey.DIGIT_4,
    Key.Five to StudyKey.DIGIT_5,
    Key.Six to StudyKey.DIGIT_6,
    Key.Seven to StudyKey.DIGIT_7,
    Key.Eight to StudyKey.DIGIT_8,
    Key.Nine to StudyKey.DIGIT_9,
    Key.NumPad1 to StudyKey.NUMPAD_1,
    Key.NumPad2 to StudyKey.NUMPAD_2,
    Key.NumPad3 to StudyKey.NUMPAD_3,
    Key.NumPad4 to StudyKey.NUMPAD_4,
    Key.NumPad5 to StudyKey.NUMPAD_5,
    Key.NumPad6 to StudyKey.NUMPAD_6,
    Key.NumPad7 to StudyKey.NUMPAD_7,
    Key.NumPad8 to StudyKey.NUMPAD_8,
    Key.NumPad9 to StudyKey.NUMPAD_9,
    Key.A to StudyKey.A,
    Key.B to StudyKey.B,
    Key.C to StudyKey.C,
    Key.D to StudyKey.D,
    Key.E to StudyKey.E,
    Key.F to StudyKey.F,
    Key.G to StudyKey.G,
    Key.H to StudyKey.H,
    Key.I to StudyKey.I,
    Key.J to StudyKey.J,
    Key.K to StudyKey.K,
    Key.L to StudyKey.L,
    Key.M to StudyKey.M,
    Key.N to StudyKey.N,
    Key.O to StudyKey.O,
    Key.P to StudyKey.P,
    Key.Q to StudyKey.Q,
    Key.R to StudyKey.R,
    Key.S to StudyKey.S,
    Key.T to StudyKey.T,
    Key.U to StudyKey.U,
    Key.V to StudyKey.V,
    Key.W to StudyKey.W,
    Key.X to StudyKey.X,
    Key.Y to StudyKey.Y,
    Key.Z to StudyKey.Z,
)

/**
 * Recognizes a held key's repeated key-down events as auto-repeat.
 *
 * Compose exposes no portable auto-repeat flag: a held key simply delivers key-down
 * again. So the adapter tracks which keys are down and reports the second and later
 * key-downs of the same key as a repeat, which is what [StudyKeyboardPolicy.claims]
 * drops. Without it, holding `3` would submit a grade, continue past the feedback, and
 * grade the next card, at the keyboard's repeat rate.
 *
 * Held-key state rather than a debounce interval, so it needs no clock and is exact: a
 * genuine second press always delivers key-up first.
 *
 * Takes the key and the direction rather than a [KeyEvent] so it is testable from common
 * code: `KeyEvent` is an expect class wrapping the host's own native event, with no
 * portable way to synthesize one, and Compose's test input dispatcher refuses to inject
 * a second key-down for a key it believes is already held — which is precisely the event
 * this exists to handle.
 */
internal class StudyKeyRepeatFilter {
    private val held = mutableSetOf<Key>()

    /** Records the event and reports whether it is a repeat of a still-held key. */
    fun isRepeat(key: Key, isKeyDown: Boolean): Boolean {
        if (!isKeyDown) {
            held.remove(key)
            return false
        }
        return !held.add(key)
    }
}

/**
 * Applies the study keyboard shortcuts to a subtree.
 *
 * [context] carries what else holds the keyboard — a focused field, an IME
 * composition, a modal, and whether the answer is showing — because every one of them
 * is a reason a key must not grade. The handler claims an event only when it produced
 * an action, so a key it does not map (Escape, an arrow, an unbound chord) still
 * reaches the shell and the focused field.
 */
@Composable
internal fun Modifier.studyKeyboardShortcuts(
    session: StudySession,
    context: StudyInputContext,
    dispatch: (KaniAction) -> Unit,
    bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
): Modifier {
    val repeats = remember { StudyKeyRepeatFilter() }
    return onPreviewKeyEvent { event ->
        // Every event updates the held-key set, including ones no binding wants, or a
        // key-up outside the mapping would leave its key stuck as held.
        val isRepeat = repeats.isRepeat(event.key, event.type == KeyEventType.KeyDown)
        val action = StudyKeyboardAdapter.actionFor(event, session, context, isRepeat, bindings)
        if (action != null) {
            dispatch(action)
            true
        } else {
            false
        }
    }
}
