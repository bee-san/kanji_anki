package dev.bee.kanjianki.study

import androidx.compose.ui.input.key.Key
import dev.bee.kanjianki.presentation.StudyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The adapter's own claims: Compose keycodes map to the portable names and nothing else
 * does, and a held key is recognized as auto-repeat.
 *
 * What a key *means* is [dev.bee.kanjianki.presentation.StudyKeyboardPolicy]'s and is
 * covered by `StudyCommandsTest` in `:presentation-api`. Restating those cases here
 * would pin the same behavior in two places and let the two drift, so this covers only
 * the translation — which is the part that can be wrong per host, because Compose's
 * `Key` is a platform keycode behind a value class.
 */
class StudyKeyboardTest {
    @Test
    fun everyBindableKeyHasExactlyOneComposeKeycode() {
        // A missing entry is a key the Settings editor offers and the host silently
        // never receives; a duplicated one binds two names to one physical key.
        val unmapped = StudyKey.entries.filter { portable ->
            COMPOSE_KEYS.none { StudyKeyboardAdapter.portableKey(it) == portable }
        }
        assertEquals(emptyList(), unmapped)
        assertEquals(COMPOSE_KEYS.size, StudyKey.entries.size)
        assertEquals(
            COMPOSE_KEYS.size,
            COMPOSE_KEYS.mapNotNull(StudyKeyboardAdapter::portableKey).toSet().size,
        )
    }

    @Test
    fun theKeysTheDefaultsBindMapToTheNamesTheyAreWrittenAgainst() {
        // Spot-pinned rather than derived: a wrong keycode here silently unbinds an
        // Anki-compatible default on one host and nothing else notices.
        assertEquals(StudyKey.SPACE, StudyKeyboardAdapter.portableKey(Key.Spacebar))
        assertEquals(StudyKey.ENTER, StudyKeyboardAdapter.portableKey(Key.Enter))
        assertEquals(StudyKey.NUMPAD_ENTER, StudyKeyboardAdapter.portableKey(Key.NumPadEnter))
        assertEquals(StudyKey.DIGIT_1, StudyKeyboardAdapter.portableKey(Key.One))
        assertEquals(StudyKey.DIGIT_3, StudyKeyboardAdapter.portableKey(Key.Three))
        assertEquals(StudyKey.NUMPAD_1, StudyKeyboardAdapter.portableKey(Key.NumPad1))
        assertEquals(StudyKey.NUMPAD_3, StudyKeyboardAdapter.portableKey(Key.NumPad3))
        assertEquals(StudyKey.P, StudyKeyboardAdapter.portableKey(Key.P))
        assertEquals(StudyKey.F, StudyKeyboardAdapter.portableKey(Key.F))
        assertEquals(StudyKey.Z, StudyKeyboardAdapter.portableKey(Key.Z))
    }

    @Test
    fun escapeAndTheNavigationKeysAreNotStudyKeysAtAll() {
        // Escape is the shell's back: mapping it would let a binding turn "leave this
        // card" into "fail this card". The rest fall through to the shell and the
        // focused field rather than being swallowed.
        for (key in listOf(Key.Escape, Key.Back, Key.Tab, Key.DirectionUp, Key.Delete, Key.F1)) {
            assertNull(StudyKeyboardAdapter.portableKey(key), key.toString())
        }
    }

    @Test
    fun aHeldKeyIsARepeatFromItsSecondKeyDownUntilItIsReleased() {
        // Auto-repeat as a host delivers it: key-down again with no intervening key-up.
        // Without this, holding `3` would grade the card, continue past the feedback, and
        // grade the next card at the keyboard's repeat rate. Asserted on the filter
        // rather than through a rendered key injection because Compose's test input
        // dispatcher refuses to send a second key-down for a key it thinks is held —
        // it cannot express the event this exists to catch.
        val repeats = StudyKeyRepeatFilter()
        assertFalse(repeats.isRepeat(Key.Three, isKeyDown = true), "the first press is not a repeat")
        assertTrue(repeats.isRepeat(Key.Three, isKeyDown = true), "a held key repeats")
        assertTrue(repeats.isRepeat(Key.Three, isKeyDown = true))
        // Release, then a genuine second press — which always sends key-up first.
        assertFalse(repeats.isRepeat(Key.Three, isKeyDown = false), "a release is not a repeat")
        assertFalse(repeats.isRepeat(Key.Three, isKeyDown = true), "a real second press is not a repeat")
    }

    @Test
    fun holdingOneKeyDoesNotMakeAnotherKeyARepeat() {
        // Held state is per key, so a chord and a second grade key both still land while
        // the first is down. One shared "last key" flag would swallow them.
        val repeats = StudyKeyRepeatFilter()
        assertFalse(repeats.isRepeat(Key.Three, isKeyDown = true))
        assertFalse(repeats.isRepeat(Key.One, isKeyDown = true))
        assertTrue(repeats.isRepeat(Key.Three, isKeyDown = true))
        assertTrue(repeats.isRepeat(Key.One, isKeyDown = true))
    }

    @Test
    fun bothDigitKeysForANumberReportThatNumber() {
        for (digit in 1..9) {
            // The number row and the numpad are different keys with the same meaning,
            // which is why the policy matches on the digit rather than the name — and
            // why that is layout-independent.
            assertEquals(digit, StudyKeyboardAdapter.portableKey(NUMBER_ROW[digit - 1])?.digit)
            assertEquals(digit, StudyKeyboardAdapter.portableKey(NUMPAD[digit - 1])?.digit)
        }
    }
}

private val NUMBER_ROW = listOf(
    Key.One,
    Key.Two,
    Key.Three,
    Key.Four,
    Key.Five,
    Key.Six,
    Key.Seven,
    Key.Eight,
    Key.Nine,
)

private val NUMPAD = listOf(
    Key.NumPad1,
    Key.NumPad2,
    Key.NumPad3,
    Key.NumPad4,
    Key.NumPad5,
    Key.NumPad6,
    Key.NumPad7,
    Key.NumPad8,
    Key.NumPad9,
)

private val LETTERS = listOf(
    Key.A,
    Key.B,
    Key.C,
    Key.D,
    Key.E,
    Key.F,
    Key.G,
    Key.H,
    Key.I,
    Key.J,
    Key.K,
    Key.L,
    Key.M,
    Key.N,
    Key.O,
    Key.P,
    Key.Q,
    Key.R,
    Key.S,
    Key.T,
    Key.U,
    Key.V,
    Key.W,
    Key.X,
    Key.Y,
    Key.Z,
)

/** Every Compose key the adapter must know, listed independently of the adapter. */
private val COMPOSE_KEYS: List<Key> =
    listOf(Key.Spacebar, Key.Enter, Key.NumPadEnter) + NUMBER_ROW + NUMPAD + LETTERS
